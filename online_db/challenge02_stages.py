"""Stage ladder for challenge-02 (S1-S3: gamer health 7 days).

S1 builds the health-awareness baseline (D1-D2), S2 covers lifestyle
interventions (D3-D5 neck/shoulder/wrist, diet, sleep), S3 adds systematic
training and the D7 rubric-scored final review. Idempotent like
``challenge02_seed``: skips when stages are already defined; never
overwrites operator rows.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from typing import Sequence

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import ActivityNotFound
from online_db.challenge02_tasks import CHALLENGE02_SLUG
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory
from online_db.settings import OnlineDatabaseSettings
from online_db.stage_store import (
    ActivityStagesNotDefined,
    StageDefinition,
    SqlAlchemyStageStore,
)


CHALLENGE02_STAGES: tuple[StageDefinition, ...] = (
    StageDefinition(
        stage_index=1,
        name="S1 健康认知与工位基线",
        capability=(
            "能讲清久坐的生理伤害机制（血液循环/代谢/体态/认知四条线）"
            "并给出正确坐姿与工位布置要点；"
            "能指出游戏宅日常中至少 3 个健康误区并说清依据"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(1, 2),
    ),
    StageDefinition(
        stage_index=2,
        name="S2 生活方式干预方案",
        capability=(
            "能针对游戏宅场景给出可执行的颈肩腕缓解动作、外卖与零食替代策略、"
            "睡眠修复方案；建议必须碎片化、居家、零器械、兼容游戏作息，"
            "能识别并纠正「必须睡够 8 小时」「褪黑素当日常吃」等误区"
        ),
        evidence_keys=("explain", "verify"),
        task_day_numbers=(3, 4, 5),
    ),
    StageDefinition(
        stage_index=3,
        name="S3 训练体系与综合验收",
        capability=(
            "能按专项性/超负荷/疲劳管理/超量恢复概念设计每周 3-5 次的"
            "碎片化+居家训练安排；D7 能对老师给出的综合健康计划"
            "按标准区间 rubric 完成五维度打分并说清扣分依据"
        ),
        evidence_keys=("explain", "verify", "final_exam"),
        task_day_numbers=(6, 7),
    ),
)


def seed_challenge02_stages(
    session_factory: sessionmaker[Session], now: datetime
) -> bool:
    """Define the S1-S3 stage ladder for the challenge-02 activity.

    Returns True when stages were defined, False when they already existed
    or the activity itself has not been seeded yet.
    """
    store = SqlAlchemyStageStore(session_factory)
    try:
        store.list_activity_stages(CHALLENGE02_SLUG)
    except ActivityNotFound:
        return False
    except ActivityStagesNotDefined:
        pass
    else:
        return False
    try:
        store.define_activity_stages(CHALLENGE02_SLUG, CHALLENGE02_STAGES, now)
    except IntegrityError:
        return False
    return True


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed the challenge-02 S1-S3 stage ladder"
    )
    parser.parse_args(argv)

    database_url = OnlineDatabaseSettings.from_env().database_url
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    created = seed_challenge02_stages(session_factory, datetime.now(timezone.utc))
    print(
        "Challenge-02 stage seed complete: "
        f"stages {'defined' if created else 'skipped (already defined)'}; "
        f"slug={CHALLENGE02_SLUG} stages={len(CHALLENGE02_STAGES)}"
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
