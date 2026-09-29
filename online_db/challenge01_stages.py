"""Stage ladder for challenge-01 (S1-S6, initial version per
docs/specs/stage-progress-model.md — pending first-round real-data review).

Each stage pairs the daily ``stage_goal`` acceptance structure: odd days are
"student can explain", even days are "verify by exam + fix loop". S6 adds
the final comprehensive exam. Idempotent like ``challenge01_seed``:
skips when stages are already defined; never overwrites operator rows.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from typing import Sequence

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import ActivityNotFound
from online_db.challenge01_tasks import CHALLENGE01_SLUG
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory
from online_db.settings import OnlineDatabaseSettings
from online_db.stage_store import (
    ActivityStagesNotDefined,
    StageDefinition,
    SqlAlchemyStageStore,
)


CHALLENGE01_STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="S1 亲历基线",
        capability=(
            "完成首批 agent 实操（Lab A-D 或降级观察）并填好观察记录表；"
            "能讲清 agent 与直接问 LLM 的差别"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(1, 2),
    ),
    StageDefinition(
        stage_index=2,
        name="S2 概念判据",
        capability=(
            "能给新场景正确归类并说理；能独立归类 3 个新场景，"
            "产生的误解能完成纠错闭环"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(3, 4),
    ),
    StageDefinition(
        stage_index=3,
        name="S3 Loop 与终止",
        capability=(
            "能手画 agent loop 并讲清每一步的输入输出；"
            "能说出至少 3 种终止条件并回扣 D1-D2 的亲历观察"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(5, 6),
    ),
    StageDefinition(
        stage_index=4,
        name="S4 工具与上下文",
        capability=(
            "能说清工具描述、参数 schema、数量控制，以及系统提示结构与上下文预算；"
            "能挑出工具定义的毛病并改对，能指出臃肿 prompt 的问题并重写"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(7, 8, 9, 10),
    ),
    StageDefinition(
        stage_index=5,
        name="S5 可靠性、反思与 eval",
        capability=(
            "能说清反思的边界与人机回环、eval 集/指标/badcase 驱动是什么；"
            "能列出必须人来确认的动作清单，能设计 ≥20 条的分层 eval 集并说分层理由"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(11, 12, 13, 14),
    ),
    StageDefinition(
        stage_index=6,
        name="S6 上线与综合大考",
        capability=(
            "能说清模型分级、超时重试限流、监控、灰度；能列出上线 checklist "
            "并解释每项防什么；能独立输出完整 agent 应用设计方案并讲给零基础第三者"
        ),
        evidence_keys=("explain", "verify", "final_exam"),
        task_day_numbers=(15, 16, 17),
    ),
)


def seed_challenge01_stages(
    session_factory: sessionmaker[Session], now: datetime
) -> bool:
    """Define the S1-S6 stage ladder for the challenge-01 activity.

    Returns True when stages were defined, False when they already existed
    or the activity itself has not been seeded yet.
    """
    store = SqlAlchemyStageStore(session_factory)
    try:
        store.list_activity_stages(CHALLENGE01_SLUG)
    except ActivityNotFound:
        return False
    except ActivityStagesNotDefined:
        pass
    else:
        return False
    try:
        store.define_activity_stages(CHALLENGE01_SLUG, CHALLENGE01_STAGES, now)
    except IntegrityError:
        return False
    return True


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed the challenge-01 S1-S6 stage ladder"
    )
    parser.parse_args(argv)

    database_url = OnlineDatabaseSettings.from_env().database_url
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    created = seed_challenge01_stages(session_factory, datetime.now(timezone.utc))
    print(
        "Challenge-01 stage seed complete: "
        f"stages {'defined' if created else 'skipped (already defined)'}; "
        f"slug={CHALLENGE01_SLUG} stages={len(CHALLENGE01_STAGES)}"
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
