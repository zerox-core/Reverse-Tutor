from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    JSON,
    String,
    UniqueConstraint,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


# 教学法降级链（docs/specs/stage-progress-model.md §11.5）：
# 直接教学 → 类比/故事化 → 慢拆解 → 前置补课。
# 序号越大 = 脚手架越重；「升档」= 向 direct 方向移动（起步档过轻才升，防震荡）。
TEACHING_LEVELS: tuple[str, ...] = (
    "direct",
    "analogy_story",
    "slow_decompose",
    "prereq_remedy",
)

_TEACHING_LEVEL_SQL = "'" + "','".join(TEACHING_LEVELS) + "'"


class ActivityProbePlan(OnlineBase):
    """探针计划存档（spec §11.3）。

    会话创建时由 LLM 按 `online_db.probe_plan_prompts` 生成的
    逐阶段探针计划 + 判定 rubric，随会话策略一起定稿后按
    (participation, stage) 落档；`plan` 列存的是已通过
    `validate_stage_plan` 校验的 JSON payload。
    """

    __tablename__ = "activity_probe_plans"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    participation_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_participations.id",
            name="fk_activity_probe_plans_participation_id_participations",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    stage_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_stages.id",
            name="fk_activity_probe_plans_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    plan: Mapped[list] = mapped_column(JSON, nullable=False)
    model: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "participation_id",
            "stage_id",
            name="uq_activity_probe_plans_participation_id_stage_id",
        ),
        CheckConstraint("model <> ''", name="probe_plan_model"),
    )


class ActivityTeachingProfile(OnlineBase):
    """教学偏好画像（spec §11.5）。

    确认档泛化为下一知识点的起步档；起步档过轻（秒过 + 主动
    加码「吃不饱」）才升档，避免来回震荡。按 (account, activity)
    维度持久化——画像在活动内跨知识点、跨阶段生效。
    """

    __tablename__ = "activity_teaching_profiles"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    account_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_activity_teaching_profiles_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    activity_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activities.id",
            name="fk_activity_teaching_profiles_activity_id_activities",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    current_level: Mapped[str] = mapped_column(
        String(32), nullable=False, server_default=text("'direct'")
    )
    sample_count: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    last_adjust_reason: Mapped[str] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "account_id",
            "activity_id",
            name="uq_activity_teaching_profiles_account_id_activity_id",
        ),
        CheckConstraint(
            f"current_level IN ({_TEACHING_LEVEL_SQL})",
            name="profile_current_level",
        ),
        CheckConstraint("sample_count >= 0", name="profile_sample_count"),
    )


class ActivityKnowledgePath(OnlineBase):
    """细路径记账（spec §11.5）：{知识点, 起始档, 确认档, 尝试序列, 探针记录}。

    逐知识点小循环的审计台账——不上面板、不参与晋升判定，
    画像与后续算法调整（§7 埋点）从这份数据取真实参考。
    """

    __tablename__ = "activity_knowledge_paths"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    participation_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_participations.id",
            name="fk_activity_knowledge_paths_participation_id_participations",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    stage_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_stages.id",
            name="fk_activity_knowledge_paths_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    knowledge_point: Mapped[str] = mapped_column(String(255), nullable=False)
    start_level: Mapped[str] = mapped_column(String(32), nullable=False)
    confirmed_level: Mapped[str | None] = mapped_column(String(32), nullable=True)
    attempt_sequence: Mapped[list] = mapped_column(
        JSON, nullable=False, server_default=text("'[]'")
    )
    probe_records: Mapped[list] = mapped_column(
        JSON, nullable=False, server_default=text("'[]'")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "participation_id",
            "stage_id",
            "knowledge_point",
            name="uq_activity_knowledge_paths_participation_stage_kp",
        ),
        CheckConstraint(
            f"start_level IN ({_TEACHING_LEVEL_SQL})",
            name="path_start_level",
        ),
        CheckConstraint(
            f"(confirmed_level IS NULL OR confirmed_level IN ({_TEACHING_LEVEL_SQL}))",
            name="path_confirmed_level",
        ),
        Index(
            "ix_activity_knowledge_paths_participation_id_updated_at",
            "participation_id",
            "updated_at",
        ),
    )
