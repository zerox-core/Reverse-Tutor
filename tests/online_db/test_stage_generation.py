# -*- coding: utf-8 -*-
"""Material-driven stage generator tests (docs/specs/material-driven-stage-generator.md)."""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import URL, CheckConstraint

import online_db.models  # noqa: F401
from online_db.activity_store import (
    ActivityDefinition,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.models import STAGE_EVIDENCE_LEVELS
from online_db.session import build_online_session_factory
from online_db.stage_generation import (
    STAGE_GENERATION_SYSTEM_PROMPT,
    MaterialDigest,
    StagePlanValidationError,
    build_stage_generation_user_prompt,
    generate_stage_plan,
    stage_plan_to_definitions,
    validate_stage_plan,
)
from online_db.stage_store import SqlAlchemyStageStore, StageDefinition
from sqlalchemy import CheckConstraint


NOW = datetime(2026, 7, 17, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def _create_activity(sqlite_session_factory, *, slug="gen-activity", total_days=6):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    store.create_activity(
        ActivityDefinition(
            slug=slug,
            title="素材生成活动",
            description="素材驱动阶段生成测试",
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


def _valid_payload(total_days=6):
    return {
        "stages": [
            {
                "stage_index": 1,
                "name": "基础概念",
                "capability": "学生能讲清基础概念",
                "evidence_keys": ["explain", "verify"],
                "task_day_numbers": [1, 2],
                "evidence_level": "evidenced",
                "evidence_refs": ["素材1 · 第一章"],
            },
            {
                "stage_index": 2,
                "name": "进阶综合",
                "capability": "学生能综合运用并纠错",
                "evidence_keys": ["explain", "verify"],
                "task_day_numbers": [3, 4, 5, 6],
                "evidence_level": "inferred",
                "evidence_refs": [],
            },
        ]
    }


def test_stage_model_uses_short_check_names():
    check_names = {
        constraint.name
        for constraint in online_db.models.ActivityStage.__table__.constraints
        if isinstance(constraint, CheckConstraint)
    }
    assert "ck_activity_stages_stage_evidence_level" in check_names
    assert set(STAGE_EVIDENCE_LEVELS) == {"evidenced", "inferred"}


def test_build_user_prompt_renders_materials_and_budget():
    materials = [
        MaterialDigest(title="目录", text="第一章 绪论\n第二章 核心", ref="upload-1"),
        MaterialDigest(title="摘要", text="全书围绕主题 X 展开。"),
    ]
    prompt = build_stage_generation_user_prompt(
        materials,
        activity_title="活动名",
        activity_description="活动描述",
        total_days=17,
    )
    assert "活动名" in prompt
    assert "活动描述" in prompt
    assert "17" in prompt
    assert "## 素材1 · 目录" in prompt
    assert "第一章 绪论" in prompt
    assert "## 素材2 · 摘要" in prompt
    assert "全书围绕主题 X" in prompt
    assert "请按系统指令输出覆盖全部天数的阶段划分 JSON。" in prompt


def test_build_user_prompt_skips_blank_description():
    prompt = build_stage_generation_user_prompt(
        [MaterialDigest(title="t", text="x")],
        activity_title="活动",
        activity_description="   ",
        total_days=4,
    )
    assert "活动描述" not in prompt


def test_system_prompt_covers_design_points():
    for needle in (
        "不重不漏",
        "不跳章",
        "成对",
        "evidenced",
        "inferred",
        "线性拆解",
        "置信",
        "final_exam",
    ):
        assert needle in STAGE_GENERATION_SYSTEM_PROMPT


def test_validate_stage_plan_accepts_and_normalizes():
    payload = _valid_payload()
    payload["stages"][0]["name"] = " 基础概念 "
    plan = validate_stage_plan(payload, total_days=6)
    assert len(plan.stages) == 2
    assert plan.stages[0].name == "基础概念"
    assert plan.stages[0].evidence_keys == ("explain", "verify")
    assert plan.stages[0].task_day_numbers == (1, 2)
    assert plan.stages[0].evidence_level == "evidenced"
    assert plan.stages[0].evidence_refs == ("素材1 · 第一章",)
    assert plan.stages[1].evidence_level == "inferred"
    assert plan.stages[1].evidence_refs == ()


def test_validate_stage_plan_requires_complete_day_coverage():
    payload = _valid_payload()
    payload["stages"][1]["task_day_numbers"] = [3, 4, 5]
    with pytest.raises(StagePlanValidationError, match="does not cover all days"):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_day_overlap():
    payload = _valid_payload()
    payload["stages"][0]["task_day_numbers"] = [1, 2, 3]
    with pytest.raises(StagePlanValidationError, match="duplicate task day"):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_day_beyond_budget():
    payload = _valid_payload()
    payload["stages"][1]["task_day_numbers"] = [3, 4, 5, 7]
    with pytest.raises(StagePlanValidationError, match="exceeds total_days"):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_non_contiguous_indices():
    payload = _valid_payload()
    payload["stages"][1]["stage_index"] = 3
    with pytest.raises(StagePlanValidationError, match="contiguous"):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_unknown_evidence_level():
    payload = _valid_payload()
    payload["stages"][0]["evidence_level"] = "guessed"
    with pytest.raises(StagePlanValidationError, match="evidence_level must be one of"):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_blank_evidence_ref():
    payload = _valid_payload()
    payload["stages"][0]["evidence_refs"] = ["  "]
    with pytest.raises(
        StagePlanValidationError, match="evidence_refs must be non-empty strings"
    ):
        validate_stage_plan(payload, total_days=6)


def test_validate_stage_plan_rejects_empty_stage_list():
    with pytest.raises(StagePlanValidationError, match="stages"):
        validate_stage_plan({"stages": []}, total_days=6)


def test_stage_plan_to_definitions_carries_confidence():
    plan = validate_stage_plan(_valid_payload(), total_days=6)
    definitions = stage_plan_to_definitions(plan)
    assert definitions[0].evidence_level == "evidenced"
    assert definitions[0].evidence_refs == ("素材1 · 第一章",)
    assert definitions[1].evidence_level == "inferred"
    assert definitions[1].evidence_refs == ()
    assert definitions[0].task_day_numbers == (1, 2)


def test_generate_stage_plan_uses_chat_json():
    seen = {}

    async def fake_chat_json(system, messages, *, temperature=0.2, max_tokens=2000):
        seen["system"] = system
        seen["messages"] = messages
        seen["temperature"] = temperature
        return _valid_payload()

    materials = [MaterialDigest(title="目录", text="第一章 绪论")]
    plan = asyncio.run(
        generate_stage_plan(
            materials,
            activity_title="活动",
            total_days=6,
            chat_json=fake_chat_json,
        )
    )
    assert len(plan.stages) == 2
    assert seen["system"] == STAGE_GENERATION_SYSTEM_PROMPT
    assert len(seen["messages"]) == 1
    assert seen["messages"][0]["role"] == "user"
    assert "## 素材1 · 目录" in seen["messages"][0]["content"]
    assert seen["temperature"] == 0.3


def test_generate_stage_plan_propagates_validation_error():
    async def bad_chat_json(system, messages, *, temperature=0.2, max_tokens=2000):
        return {"stages": []}

    with pytest.raises(StagePlanValidationError):
        asyncio.run(
            generate_stage_plan(
                [MaterialDigest(title="t", text="x")],
                activity_title="活动",
                total_days=6,
                chat_json=bad_chat_json,
            )
        )


def test_define_activity_stages_persists_evidence_levels(sqlite_session_factory):
    _create_activity(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    records = store.define_activity_stages(
        "gen-activity",
        (
            StageDefinition(
                stage_index=1,
                name="基础",
                capability="讲清基础",
                evidence_keys=("explain", "verify"),
                task_day_numbers=(1, 2),
                evidence_level="evidenced",
                evidence_refs=("素材1",),
            ),
            StageDefinition(
                stage_index=2,
                name="综合",
                capability="综合运用",
                evidence_keys=("explain", "verify"),
                task_day_numbers=(3, 4, 5, 6),
                evidence_level="inferred",
                evidence_refs=(),
            ),
        ),
        NOW,
    )
    assert records[0].evidence_level == "evidenced"
    assert records[0].evidence_refs == ("素材1",)
    assert records[1].evidence_level == "inferred"

    listed = store.list_activity_stages("gen-activity")
    assert [r.evidence_level for r in listed] == ["evidenced", "inferred"]
    assert listed[0].evidence_refs == ("素材1",)
    assert listed[1].evidence_refs == ()

    updated = store.define_activity_stages(
        "gen-activity",
        (
            StageDefinition(
                stage_index=1,
                name="基础",
                capability="讲清基础",
                evidence_keys=("explain",),
                task_day_numbers=(1,),
            ),
            StageDefinition(
                stage_index=2,
                name="综合",
                capability="综合运用",
                evidence_keys=("verify",),
                task_day_numbers=(2, 3, 4, 5, 6),
                evidence_level="inferred",
                evidence_refs=("素材2",),
            ),
        ),
        NOW,
    )
    assert updated[0].evidence_level == "evidenced"
    assert updated[0].evidence_refs == ()
    assert updated[1].evidence_level == "inferred"
    assert updated[1].evidence_refs == ("素材2",)
    assert [r.stage_index for r in updated] == [1, 2]
    assert updated[0].id == records[0].id
    assert updated[1].id == records[1].id


def test_define_activity_stages_rejects_bad_evidence_level(sqlite_session_factory):
    _create_activity(sqlite_session_factory)
    store = SqlAlchemyStageStore(sqlite_session_factory)
    with pytest.raises(ValueError, match="evidence_level"):
        store.define_activity_stages(
            "gen-activity",
            (
                StageDefinition(
                    stage_index=1,
                    name="基础",
                    capability="讲清基础",
                    evidence_keys=("explain",),
                    task_day_numbers=(1, 2, 3, 4, 5, 6),
                    evidence_level="guessed",
                ),
            ),
            NOW,
        )
