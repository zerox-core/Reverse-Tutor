"""EngineActivitySessionHook 的存储级单测（真实 sqlite + 两个 store）。

覆盖：render_block 渲染存档探针计划；record_turn_evidence 的 kind 取自计划、
幂等键按会话+消息确定、重复写安全、不同内容首记保留、计划外证据键拒绝。
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import URL

import online_db.models  # noqa: F401
from adapters.online.engine_activity_hook import EngineActivitySessionHook
from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.models import AnonymousAccount
from online_db.probe_plan_prompts import (
    ProbeItem,
    ProbePlan,
    ProbeRubric,
    ProbeStagePlan,
)
from online_db.probe_plan_store import SqlAlchemyProbePlanStore
from online_db.session import build_online_session_factory
from online_db.stage_store import (
    StageDefinition,
    StageIndexNotFound,
    SqlAlchemyStageStore,
)

NOW = datetime(2026, 9, 29, 8, 0, tzinfo=timezone.utc)
SLUG = "hook-activity"

STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="基础概念",
        capability="学生能讲清基础概念",
        evidence_keys=("explain",),
        task_day_numbers=(1, 2),
    ),
)


def _plan() -> ProbePlan:
    return ProbePlan(
        stages=(
            ProbeStagePlan(
                stage_index=1,
                entry_question="先给我讲讲这个概念是什么？",
                probes=(
                    ProbeItem(
                        evidence_key="explain",
                        kind="probe_recite",
                        question="用自己的话复述概念定义",
                        rubric=ProbeRubric(
                            pass_criteria="定义要点齐全且有自己的话",
                            partial_criteria="要点不全但方向对",
                            fail_signals=("照本宣科", "概念混淆"),
                        ),
                        followups=("能举个反例吗？",),
                    ),
                ),
            ),
        )
    )


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


@pytest.fixture
def hooked(sqlite_session_factory):
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
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity_store.create_activity(
        ActivityDefinition(
            slug=SLUG,
            title="Hook Activity",
            description="Engine activity hook tests.",
            revision=1,
            rule_version=1,
            total_days=2,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=2),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        NOW,
    )
    activity_store.join_activity(
        SLUG,
        account_id,
        expected_activity_revision=1,
        idempotency_key="join-1",
        now=NOW,
    )
    stage_store = SqlAlchemyStageStore(sqlite_session_factory)
    stage_store.define_activity_stages(SLUG, STAGES, NOW)
    probe_plan_store = SqlAlchemyProbePlanStore(sqlite_session_factory)
    probe_plan_store.save_probe_plan(SLUG, account_id, plan=_plan(), model="test-model", now=NOW)
    hook = EngineActivitySessionHook(
        stage_store, probe_plan_store, clock=lambda: NOW
    )
    return hook, stage_store, account_id


def test_render_block_renders_archived_plan(hooked):
    hook, _, account_id = hooked
    block = hook.render_block(activity_slug=SLUG, account_id=str(account_id))
    assert "# 阶段探针计划" in block
    assert "## 阶段 1 · 基础概念" in block
    assert "入口问题（baseline_probe）：先给我讲讲这个概念是什么？" in block
    assert "证据 explain · probe_recite：用自己的话复述概念定义" in block
    assert "判定：过=定义要点齐全且有自己的话" in block


def test_record_turn_evidence_uses_kind_from_plan(hooked):
    hook, stage_store, account_id = hooked
    note = hook.record_turn_evidence(
        activity_slug=SLUG,
        account_id=str(account_id),
        session_id="sess-1",
        message_id=42,
        stage_index=1,
        evidence_key="explain",
    )
    assert "已记录证据 explain@阶段1（probe_recite）" in note
    events = stage_store.list_evidence_events(SLUG, account_id)
    assert len(events) == 1
    event = events[0]
    assert event.kind == "probe_recite"
    assert event.artifact_ref == "session:sess-1:message:42"
    assert event.idempotency_key == "engine:sess-1:42:1:explain"
    progress = stage_store.get_stage_progress(SLUG, account_id, now=NOW)
    assert progress.stage_done == 1


def test_record_turn_evidence_idempotent_same_turn(hooked):
    hook, stage_store, account_id = hooked
    kwargs = dict(
        activity_slug=SLUG,
        account_id=str(account_id),
        session_id="sess-1",
        message_id=42,
        stage_index=1,
        evidence_key="explain",
    )
    hook.record_turn_evidence(**kwargs)
    # 同一会话同一消息重放：同幂等键，安全返回不加行
    note = hook.record_turn_evidence(**kwargs)
    assert "已记录证据" in note
    assert len(stage_store.list_evidence_events(SLUG, account_id)) == 1


def test_record_turn_evidence_conflict_keeps_first(hooked):
    hook, stage_store, account_id = hooked
    hook.record_turn_evidence(
        activity_slug=SLUG,
        account_id=str(account_id),
        session_id="sess-1",
        message_id=42,
        stage_index=1,
        evidence_key="explain",
    )
    # 另一条消息再判同一条证据：内容不同（artifact_ref/幂等键不同）→ 保留首记
    note = hook.record_turn_evidence(
        activity_slug=SLUG,
        account_id=str(account_id),
        session_id="sess-1",
        message_id=77,
        stage_index=1,
        evidence_key="explain",
    )
    assert "保留首记" in note
    events = stage_store.list_evidence_events(SLUG, account_id)
    assert len(events) == 1
    assert events[0].artifact_ref == "session:sess-1:message:42"


def test_record_turn_evidence_rejects_key_outside_plan(hooked):
    hook, _, account_id = hooked
    with pytest.raises(ValueError, match="not in the probe plan"):
        hook.record_turn_evidence(
            activity_slug=SLUG,
            account_id=str(account_id),
            session_id="sess-1",
            message_id=1,
            stage_index=1,
            evidence_key="verify",
        )


def test_record_turn_evidence_rejects_unknown_stage(hooked):
    hook, _, account_id = hooked
    with pytest.raises(StageIndexNotFound):
        hook.record_turn_evidence(
            activity_slug=SLUG,
            account_id=str(account_id),
            session_id="sess-1",
            message_id=1,
            stage_index=9,
            evidence_key="explain",
        )
