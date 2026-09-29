from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import URL, select

import online_db.models  # noqa: F401
from online_db.activity_store import (
    ActivityDefinition,
    ActivityNotFound,
    ActivityParticipationNotFound,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.models import ActivityProbePlan, AnonymousAccount
from online_db.models.probe import TEACHING_LEVELS
from online_db.probe_plan_prompts import (
    MAX_FOLLOWUP_PROBES,
    PROBE_KINDS,
    PROBE_PLAN_SYSTEM_PROMPT,
    ProbePlanValidationError,
    build_probe_plan_user_prompt,
    generate_probe_plan,
    plan_to_payload,
    render_probe_plan_block,
    validate_probe_plan,
    validate_stage_plan,
)
from online_db.probe_plan_store import ProbePlanNotFound, SqlAlchemyProbePlanStore
from online_db.session import build_online_session_factory
from online_db.stage_store import (
    ActivityStagesNotDefined,
    StageDefinition,
    StageIndexNotFound,
    SqlAlchemyStageStore,
)
from online_db.teaching_profile_store import SqlAlchemyTeachingProfileStore


NOW = datetime(2026, 7, 17, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


SIMPLE_STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="S1",
        capability="First capability.",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(1, 2),
    ),
    StageDefinition(
        stage_index=2,
        name="S2",
        capability="Second capability.",
        evidence_keys=("explain", "verify"),
        task_day_numbers=(3, 4, 5),
    ),
)


def _create_account(sqlite_session_factory):
    account_id = uuid4()
    with sqlite_session_factory() as database, database.begin():
        database.add(
            AnonymousAccount(
                id=account_id,
                status="active",
                created_at=NOW,
                last_seen_at=NOW,
            )
        )
    return account_id


def _create_activity(sqlite_session_factory, *, slug="staged-activity", total_days=5):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    store.create_activity(
        ActivityDefinition(
            slug=slug,
            title="Staged Activity",
            description="Stage progress data layer.",
            revision=1,
            rule_version=1,
            total_days=total_days,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=total_days),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )


def _join(sqlite_session_factory, slug, account_id):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    return store.join_activity(
        slug,
        account_id,
        expected_activity_revision=1,
        idempotency_key=f"join-{account_id}",
        now=NOW,
    )


def _define_stages(sqlite_session_factory):
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    if activity_store.get_activity("staged-activity") is None:
        _create_activity(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    store.define_activity_stages("staged-activity", SIMPLE_STAGES, NOW)
    return store.list_activity_stages("staged-activity")


def _probe_payload(evidence_key, kind="probe_recite", **overrides):
    probe = {
        "evidence_key": evidence_key,
        "kind": kind,
        "question": f"请用自己的话说明 {evidence_key} 的关键步骤。",
        "rubric": {
            "pass": "覆盖两个关键步骤且逻辑自洽",
            "partial": "只覆盖一个关键步骤",
            "fail_signals": ["只重复题面", "概念混淆", "答非所问"],
        },
        "followups": ["换一个新场景再说明一遍"],
    }
    probe.update(overrides)
    return probe


def _stage_payload(stage_index, probes):
    return {
        "stage_index": stage_index,
        "entry_question": f"第 {stage_index} 阶段的入口问题：你打算怎么开始？",
        "probes": probes,
    }


def _stage1_payload():
    return _stage_payload(
        1,
        [
            _probe_payload("explain"),
            _probe_payload("verify", "probe_error"),
        ],
    )


def _stage2_payload():
    return _stage_payload(
        2,
        [
            _probe_payload("explain", "probe_transfer"),
            _probe_payload("verify"),
        ],
    )


def _plan_payload():
    return {"stages": [_stage1_payload(), _stage2_payload()]}


# ---------------------------------------------------------------------------
# 表结构与命名规范
# ---------------------------------------------------------------------------


def test_probe_tables_use_named_constraints_and_no_secret_columns():
    for table_name in (
        "activity_probe_plans",
        "activity_teaching_profiles",
        "activity_knowledge_paths",
    ):
        table = OnlineBase.metadata.tables[table_name]
        assert all(constraint.name for constraint in table.constraints)
        assert all(index.name for index in table.indexes)
        assert all(len(constraint.name) <= 63 for constraint in table.constraints)
        assert all(len(index.name) <= 63 for index in table.indexes)
        columns = set(table.c.keys())
        assert not {
            column
            for column in columns
            if "secret" in column.lower() or "token" in column.lower()
        }
    plans = OnlineBase.metadata.tables["activity_probe_plans"]
    profiles = OnlineBase.metadata.tables["activity_teaching_profiles"]
    paths = OnlineBase.metadata.tables["activity_knowledge_paths"]
    assert plans.primary_key.name == "pk_activity_probe_plans"
    assert profiles.primary_key.name == "pk_activity_teaching_profiles"
    assert paths.primary_key.name == "pk_activity_knowledge_paths"
    assert TEACHING_LEVELS == ("direct", "analogy_story", "slow_decompose", "prereq_remedy")


# ---------------------------------------------------------------------------
# prompt 与校验
# ---------------------------------------------------------------------------


def test_build_probe_plan_user_prompt_renders_stage_ladder(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    prompt = build_probe_plan_user_prompt(
        stages,
        activity_title="挑战01",
        activity_description="算法活动",
    )
    assert "挑战01" in prompt
    assert "算法活动" in prompt
    assert "## 阶段 1 · S1" in prompt
    assert "## 阶段 2 · S2" in prompt
    assert "First capability." in prompt
    assert "Second capability." in prompt
    assert "explain, verify" in prompt
    assert prompt.endswith("请按系统指令输出覆盖以上全部阶段的完整探针计划 JSON。")
    # 系统指令覆盖探针三型与追问上限
    for kind in PROBE_KINDS:
        assert kind in PROBE_PLAN_SYSTEM_PROMPT
    assert str(MAX_FOLLOWUP_PROBES) in PROBE_PLAN_SYSTEM_PROMPT


def test_validate_probe_plan_happy_path_strips_strings(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    payload = _plan_payload()
    payload["stages"][0]["entry_question"] = "  开场问题  "
    payload["stages"][0]["probes"][0]["question"] = "  问题原文  "
    payload["stages"][0]["probes"][0]["rubric"]["pass"] = "  通过判据  "
    plan = validate_probe_plan(payload, stages)
    assert [stage_plan.stage_index for stage_plan in plan.stages] == [1, 2]
    first = plan.stages[0]
    assert first.entry_question == "开场问题"
    probe = first.probes[0]
    assert probe.evidence_key == "explain"
    assert probe.kind == "probe_recite"
    assert probe.question == "问题原文"
    assert probe.rubric.pass_criteria == "通过判据"
    assert probe.rubric.fail_signals == ("只重复题面", "概念混淆", "答非所问")
    assert probe.followups == ("换一个新场景再说明一遍",)
    # 数据类可往返 payload（存档形态）
    roundtrip = plan_to_payload(plan)
    assert roundtrip[0]["probes"][0]["rubric"]["pass"] == probe.rubric.pass_criteria
    assert roundtrip[1]["probes"][0]["kind"] == "probe_transfer"


def test_validate_probe_plan_requires_exact_stage_coverage(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)

    missing_stage = _plan_payload()
    missing_stage["stages"] = missing_stage["stages"][:1]
    with pytest.raises(ProbePlanValidationError, match="misses stages"):
        validate_probe_plan(missing_stage, stages)

    unknown_stage = _plan_payload()
    unknown_stage["stages"][1]["stage_index"] = 3
    with pytest.raises(ProbePlanValidationError, match="unknown stage_index"):
        validate_probe_plan(unknown_stage, stages)

    duplicate_stage = _plan_payload()
    duplicate_stage["stages"][1]["stage_index"] = 1
    with pytest.raises(ProbePlanValidationError, match="duplicate stage_index"):
        validate_probe_plan(duplicate_stage, stages)

    with pytest.raises(ProbePlanValidationError, match="stages"):
        validate_probe_plan({"stages": []}, stages)
    with pytest.raises(ProbePlanValidationError, match="JSON object"):
        validate_probe_plan(["not-a-mapping"], stages)


def test_validate_stage_plan_rejects_bad_probe_fields(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    stage = stages[0]

    bad_kind = _stage1_payload()
    bad_kind["probes"][0]["kind"] = "probe_quiz"
    with pytest.raises(ProbePlanValidationError, match="probe kind"):
        validate_stage_plan(bad_kind, stage)

    bad_key = _stage1_payload()
    bad_key["probes"][0]["evidence_key"] = "summarize"
    with pytest.raises(ProbePlanValidationError, match="acceptance"):
        validate_stage_plan(bad_key, stage)

    empty_question = _stage1_payload()
    empty_question["probes"][0]["question"] = "   "
    with pytest.raises(ProbePlanValidationError, match="question"):
        validate_stage_plan(empty_question, stage)

    empty_entry = _stage1_payload()
    empty_entry["entry_question"] = ""
    with pytest.raises(ProbePlanValidationError, match="entry_question"):
        validate_stage_plan(empty_entry, stage)

    no_probes = _stage1_payload()
    no_probes["probes"] = []
    with pytest.raises(ProbePlanValidationError, match="at least one probe"):
        validate_stage_plan(no_probes, stage)

    empty_pass = _stage1_payload()
    empty_pass["probes"][0]["rubric"]["pass"] = " "
    with pytest.raises(ProbePlanValidationError, match="pass criteria"):
        validate_stage_plan(empty_pass, stage)

    empty_signals = _stage1_payload()
    empty_signals["probes"][0]["rubric"]["fail_signals"] = []
    with pytest.raises(ProbePlanValidationError, match="fail_signals"):
        validate_stage_plan(empty_signals, stage)

    too_many_followups = _stage1_payload()
    too_many_followups["probes"][0]["followups"] = ["一", "二", "三", "四"]
    with pytest.raises(ProbePlanValidationError, match="followup"):
        validate_stage_plan(too_many_followups, stage)

    bad_followups = _stage1_payload()
    bad_followups["probes"][0]["followups"] = "换一个场景"
    with pytest.raises(ProbePlanValidationError, match="followups"):
        validate_stage_plan(bad_followups, stage)


def test_validate_stage_plan_requires_all_evidence_keys_covered(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    stage = stages[0]

    partial = _stage1_payload()
    partial["probes"] = partial["probes"][:1]  # 只覆盖 explain
    with pytest.raises(ProbePlanValidationError, match="without a probe"):
        validate_stage_plan(partial, stage)


def test_generate_probe_plan_with_injected_chat_json(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    calls: list[tuple[str, list[dict], float, int]] = []

    async def fake_chat_json(system, messages, *, temperature, max_tokens):
        calls.append((system, messages, temperature, max_tokens))
        return _plan_payload()

    plan = asyncio.run(
        generate_probe_plan(
            stages,
            activity_title="挑战01",
            activity_description="算法活动",
            chat_json=fake_chat_json,
        )
    )
    assert [stage_plan.stage_index for stage_plan in plan.stages] == [1, 2]
    assert len(calls) == 1
    system, messages, temperature, max_tokens = calls[0]
    assert system == PROBE_PLAN_SYSTEM_PROMPT
    assert messages[0]["role"] == "user"
    assert "挑战01" in messages[0]["content"]
    assert "S1" in messages[0]["content"]
    assert temperature == 0.3
    assert max_tokens == 6000

    async def bad_chat_json(system, messages, *, temperature, max_tokens):
        return {"stages": []}

    with pytest.raises(ProbePlanValidationError):
        asyncio.run(
            generate_probe_plan(
                stages,
                activity_title="挑战01",
                chat_json=bad_chat_json,
            )
        )


def test_render_probe_plan_block_content(sqlite_session_factory):
    stages = _define_stages(sqlite_session_factory)
    plan = validate_probe_plan(_plan_payload(), stages)
    block = render_probe_plan_block(plan, stages)
    assert "阶段 1 · S1" in block
    assert "阶段 2 · S2" in block
    assert "baseline_probe" in block
    assert "probe_error" in block
    assert "probe_transfer" in block
    assert "判定：过=" in block
    assert "部分过=" in block
    assert "不过信号=" in block
    assert "存疑追问" in block


# ---------------------------------------------------------------------------
# 探针计划存档
# ---------------------------------------------------------------------------


def test_save_probe_plan_roundtrip_get_and_list(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    stages = _define_stages(sqlite_session_factory)
    plan = validate_probe_plan(_plan_payload(), stages)

    store = SqlAlchemyProbePlanStore(sqlite_session_factory)
    records = store.save_probe_plan(
        "staged-activity", account_id, plan=plan, model="test-model", now=NOW
    )
    assert [record.stage_index for record in records] == [1, 2]
    first = records[0]
    assert first.stage_name == "S1"
    assert first.capability == "First capability."
    assert first.model == "test-model"
    assert first.entry_question.endswith("你打算怎么开始？")
    assert {probe.evidence_key for probe in first.probes} == {"explain", "verify"}
    assert all(probe.kind in PROBE_KINDS for probe in first.probes)
    assert all(probe.rubric.fail_signals for probe in first.probes)

    retrieved = store.get_stage_plan(
        "staged-activity", account_id, stage_index=2
    )
    assert retrieved.stage_index == 2
    assert retrieved.stage_name == "S2"
    assert {probe.kind for probe in retrieved.probes} == {
        "probe_transfer",
        "probe_recite",
    }

    listed = store.list_stage_plans("staged-activity", account_id)
    assert [record.stage_index for record in listed] == [1, 2]


def test_save_probe_plan_upsert_reuses_rows_and_replaces_plan(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    stages = _define_stages(sqlite_session_factory)
    store = SqlAlchemyProbePlanStore(sqlite_session_factory)

    plan = validate_probe_plan(_plan_payload(), stages)
    store.save_probe_plan(
        "staged-activity", account_id, plan=plan, model="m1", now=NOW
    )

    payload = _plan_payload()
    payload["stages"][0]["entry_question"] = "新的入口问题"
    plan2 = validate_probe_plan(payload, stages)
    records = store.save_probe_plan(
        "staged-activity",
        account_id,
        plan=plan2,
        model="m2",
        now=NOW + timedelta(minutes=5),
    )
    assert records[0].entry_question == "新的入口问题"
    assert records[0].model == "m2"
    with sqlite_session_factory() as database:
        rows = database.scalars(select(ActivityProbePlan)).all()
    assert len(rows) == 2  # 复用同一批阶段行，不翻倍
    listed = store.list_stage_plans("staged-activity", account_id)
    assert [record.model for record in listed] == ["m2", "m2"]


def test_save_probe_plan_requires_stages_and_participation(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory, slug="plain-activity", total_days=5)
    _join(sqlite_session_factory, "plain-activity", account_id)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    stages = _define_stages(sqlite_session_factory)
    plan = validate_probe_plan(_plan_payload(), stages)
    store = SqlAlchemyProbePlanStore(sqlite_session_factory)

    with pytest.raises(ActivityStagesNotDefined):
        store.save_probe_plan(
            "plain-activity", account_id, plan=plan, model="m", now=NOW
        )

    stranger = _create_account(sqlite_session_factory)
    with pytest.raises(ActivityParticipationNotFound):
        store.save_probe_plan(
            "staged-activity", stranger, plan=plan, model="m", now=NOW
        )

    with pytest.raises(ActivityNotFound):
        store.save_probe_plan(
            "missing-activity", account_id, plan=plan, model="m", now=NOW
        )


def test_save_probe_plan_revalidates_against_current_db_ladder(
    sqlite_session_factory,
):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory, slug="other-ladder", total_days=3)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    stages = _define_stages(sqlite_session_factory)

    other_store = SqlAlchemyStageStore(sqlite_session_factory)
    other_store.define_activity_stages(
        "other-ladder",
        (
            StageDefinition(
                stage_index=1,
                name="X1",
                capability="Other capability.",
                evidence_keys=("explain",),
                task_day_numbers=(1,),
            ),
        ),
        NOW,
    )
    other_stages = other_store.list_activity_stages("other-ladder")
    other_plan = validate_probe_plan(
        {"stages": [_stage_payload(1, [_probe_payload("explain")])]},
        other_stages,
    )

    store = SqlAlchemyProbePlanStore(sqlite_session_factory)
    with pytest.raises(ProbePlanValidationError, match=r"misses stages|without a probe"):
        store.save_probe_plan(
            "staged-activity", account_id, plan=other_plan, model="m", now=NOW
        )

    plan = validate_probe_plan(_plan_payload(), stages)
    with pytest.raises(ValueError, match="model"):
        store.save_probe_plan(
            "staged-activity", account_id, plan=plan, model="   ", now=NOW
        )
    with pytest.raises(ValueError, match="timezone-aware"):
        store.save_probe_plan(
            "staged-activity",
            account_id,
            plan=plan,
            model="m",
            now=datetime(2026, 7, 17, 8, 0),
        )


def test_get_stage_plan_not_found_errors(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    stages = _define_stages(sqlite_session_factory)
    plan = validate_probe_plan(_plan_payload(), stages)
    store = SqlAlchemyProbePlanStore(sqlite_session_factory)
    store.save_probe_plan(
        "staged-activity", account_id, plan=plan, model="m", now=NOW
    )

    with pytest.raises(StageIndexNotFound):
        store.get_stage_plan("staged-activity", account_id, stage_index=9)

    other = _create_account(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", other)
    with pytest.raises(ProbePlanNotFound):
        store.get_stage_plan("staged-activity", other, stage_index=1)
    with pytest.raises(ProbePlanNotFound):
        store.list_stage_plans("staged-activity", other)

    stranger = _create_account(sqlite_session_factory)
    with pytest.raises(ActivityParticipationNotFound):
        store.get_stage_plan("staged-activity", stranger, stage_index=1)


# ---------------------------------------------------------------------------
# 教学偏好画像与细路径记账
# ---------------------------------------------------------------------------


def test_get_starting_level_defaults_to_direct(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)

    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)
    assert store.get_profile("staged-activity", account_id) is None
    assert store.get_starting_level("staged-activity", account_id) == "direct"
    with pytest.raises(ActivityNotFound):
        store.get_starting_level("missing-activity", account_id)


def test_first_confirmation_creates_profile_and_generalizes(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)

    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)
    record = store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="direct",
        confirmed_level="analogy_story",
        attempts=("direct", "analogy_story"),
        probe_records=({"kind": "probe_recite", "result": "pass"},),
        now=NOW,
    )
    assert record.stage_index == 1
    assert record.stage_name == "S1"
    assert record.confirmed_level == "analogy_story"
    assert record.attempt_sequence == ("direct", "analogy_story")
    assert record.probe_records == ({"kind": "probe_recite", "result": "pass"},)

    profile = store.get_profile("staged-activity", account_id)
    assert profile is not None
    assert profile.current_level == "analogy_story"
    assert profile.sample_count == 1
    assert profile.last_adjust_reason is None
    # 确认档泛化为下一知识点的起步档
    assert store.get_starting_level("staged-activity", account_id) == "analogy_story"


def test_revisit_appends_attempts_and_counts_sample_once(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    # 第一次记账：未确认 → 不建画像
    store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="direct",
        confirmed_level=None,
        attempts=("direct",),
        now=NOW,
    )
    assert store.get_profile("staged-activity", account_id) is None

    # 第二次记账：确认 → 画像首次落档
    record = store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="direct",
        confirmed_level="analogy_story",
        attempts=("analogy_story",),
        probe_records=({"kind": "probe_transfer", "result": "partial"},),
        now=NOW + timedelta(minutes=1),
    )
    assert record.attempt_sequence == ("direct", "analogy_story")
    assert record.probe_records == (
        {"kind": "probe_transfer", "result": "partial"},
    )
    assert store.get_profile("staged-activity", account_id).sample_count == 1

    # 第三次记账（确认档不变）：尝试序列追加，sample_count 不重复计数
    record = store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="direct",
        confirmed_level="analogy_story",
        attempts=(),
        now=NOW + timedelta(minutes=2),
    )
    assert record.attempt_sequence == ("direct", "analogy_story")
    assert store.get_profile("staged-activity", account_id).sample_count == 1

    # 新知识点确认 → sample_count +1（每个知识点只计一次）
    store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=2,
        knowledge_point="复杂度分析",
        start_level="analogy_story",
        confirmed_level="analogy_story",
        attempts=("analogy_story",),
        now=NOW + timedelta(minutes=3),
    )
    profile = store.get_profile("staged-activity", account_id)
    assert profile.sample_count == 2
    assert profile.current_level == "analogy_story"


def test_generalization_uses_latest_confirmed_level(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="slow_decompose",
        confirmed_level="slow_decompose",
        attempts=("slow_decompose",),
        now=NOW,
    )
    assert store.get_starting_level("staged-activity", account_id) == "slow_decompose"

    store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=2,
        knowledge_point="复杂度分析",
        start_level="slow_decompose",
        confirmed_level="direct",
        attempts=("slow_decompose", "direct"),
        now=NOW + timedelta(minutes=1),
    )
    profile = store.get_profile("staged-activity", account_id)
    assert profile.current_level == "direct"
    assert profile.sample_count == 2
    assert store.get_starting_level("staged-activity", account_id) == "direct"


def test_unconfirmed_path_does_not_touch_profile(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    record = store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="prereq_remedy",
        confirmed_level=None,
        attempts=("prereq_remedy",),
        now=NOW,
    )
    assert record.confirmed_level is None
    assert store.get_profile("staged-activity", account_id) is None
    assert store.get_starting_level("staged-activity", account_id) == "direct"


def test_record_knowledge_path_input_validation(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory, slug="plain-activity", total_days=5)
    _join(sqlite_session_factory, "plain-activity", account_id)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    def record(**overrides):
        arguments = {
            "stage_index": 1,
            "knowledge_point": "循环不变量",
            "start_level": "direct",
            "confirmed_level": None,
            "attempts": (),
            "probe_records": (),
            "now": NOW,
        }
        arguments.update(overrides)
        return store.record_knowledge_path("staged-activity", account_id, **arguments)

    with pytest.raises(ValueError, match="start_level"):
        record(start_level="lecture")
    with pytest.raises(ValueError, match="confirmed_level"):
        record(confirmed_level="lecture")
    with pytest.raises(ValueError, match="knowledge_point"):
        record(knowledge_point="   ")
    with pytest.raises(ValueError, match="end with confirmed_level"):
        record(confirmed_level="analogy_story", attempts=("direct",))
    with pytest.raises(ValueError, match="probe_records"):
        record(probe_records=(["not", "a", "dict"],))
    with pytest.raises(ValueError, match="timezone-aware"):
        record(now=datetime(2026, 7, 17, 8, 0))
    with pytest.raises(StageIndexNotFound):
        record(stage_index=9)
    with pytest.raises(ActivityStagesNotDefined):
        store.record_knowledge_path(
            "plain-activity",
            account_id,
            stage_index=1,
            knowledge_point="循环不变量",
            start_level="direct",
            now=NOW,
        )


def test_lighten_starting_level_steps_toward_direct_and_stops(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    store.record_knowledge_path(
        "staged-activity",
        account_id,
        stage_index=1,
        knowledge_point="循环不变量",
        start_level="prereq_remedy",
        confirmed_level="prereq_remedy",
        attempts=("prereq_remedy",),
        now=NOW,
    )
    assert store.get_starting_level("staged-activity", account_id) == "prereq_remedy"

    profile = store.lighten_starting_level(
        "staged-activity", account_id, reason="秒过并主动加码", now=NOW
    )
    assert profile.current_level == "slow_decompose"
    assert profile.last_adjust_reason == "秒过并主动加码"

    profile = store.lighten_starting_level(
        "staged-activity", account_id, reason="再次吃不饱", now=NOW
    )
    assert profile.current_level == "analogy_story"

    profile = store.lighten_starting_level(
        "staged-activity", account_id, reason="第三次", now=NOW
    )
    assert profile.current_level == "direct"

    # 已在 direct：不再上移，只更新 reason
    profile = store.lighten_starting_level(
        "staged-activity", account_id, reason="第四次", now=NOW
    )
    assert profile.current_level == "direct"
    assert profile.last_adjust_reason == "第四次"
    assert store.get_profile("staged-activity", account_id).current_level == "direct"

    with pytest.raises(ValueError, match="reason"):
        store.lighten_starting_level(
            "staged-activity", account_id, reason="   ", now=NOW
        )
    with pytest.raises(ActivityNotFound):
        store.lighten_starting_level(
            "missing-activity", account_id, reason="r", now=NOW
        )


def test_lighten_starting_level_creates_profile_when_absent(sqlite_session_factory):
    account_id = _create_account(sqlite_session_factory)
    _create_activity(sqlite_session_factory)
    _join(sqlite_session_factory, "staged-activity", account_id)
    _define_stages(sqlite_session_factory)
    store = SqlAlchemyTeachingProfileStore(sqlite_session_factory)

    profile = store.lighten_starting_level(
        "staged-activity", account_id, reason="冷启动直接建档", now=NOW
    )
    assert profile.current_level == "direct"
    assert profile.sample_count == 0
    assert profile.last_adjust_reason == "冷启动直接建档"
    assert store.get_profile("staged-activity", account_id).current_level == "direct"
