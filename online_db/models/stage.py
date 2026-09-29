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
    Text,
    UniqueConstraint,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


EVIDENCE_EVENT_KINDS: tuple[str, ...] = (
    "baseline_probe",
    "probe_recite",
    "probe_transfer",
    "probe_error",
    "artifact",
    "manual",
)


STAGE_EVIDENCE_LEVELS: tuple[str, ...] = ("evidenced", "inferred")

_STAGE_EVIDENCE_LEVEL_SQL = "'evidenced','inferred'"


class ActivityStage(OnlineBase):
    __tablename__ = "activity_stages"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    activity_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activities.id",
            name="fk_activity_stages_activity_id_activities",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    stage_index: Mapped[int] = mapped_column(Integer, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    capability: Mapped[str] = mapped_column(Text, nullable=False)
    evidence_keys: Mapped[list] = mapped_column(JSON, nullable=False)
    task_day_numbers: Mapped[list] = mapped_column(JSON, nullable=False)
    evidence_level: Mapped[str] = mapped_column(
        String(16), nullable=False, server_default=text("'evidenced'")
    )
    evidence_refs: Mapped[list] = mapped_column(
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
            "activity_id",
            "stage_index",
            name="uq_activity_stages_activity_id_stage_index",
        ),
        CheckConstraint("stage_index >= 1", name="stage_index"),
        CheckConstraint(
            f"evidence_level IN ({_STAGE_EVIDENCE_LEVEL_SQL})",
            name="stage_evidence_level",
        ),
        Index("ix_activity_stages_activity_id_stage_index", "activity_id", "stage_index"),
    )


class ActivityEvidenceEvent(OnlineBase):
    __tablename__ = "activity_evidence_events"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    participation_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_participations.id",
            name="fk_activity_evidence_events_participation_id_participations",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    stage_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_stages.id",
            name="fk_activity_evidence_events_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    evidence_key: Mapped[str] = mapped_column(String(255), nullable=False)
    kind: Mapped[str] = mapped_column(String(32), nullable=False)
    artifact_ref: Mapped[str] = mapped_column(Text, nullable=False)
    path_tag: Mapped[str] = mapped_column(
        String(64), nullable=False, server_default=text("'in_order'")
    )
    idempotency_key: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        UniqueConstraint(
            "participation_id",
            "stage_id",
            "evidence_key",
            name="uq_activity_evidence_events_participation_stage_key",
        ),
        CheckConstraint(
            "kind IN ('baseline_probe','probe_recite','probe_transfer',"
            "'probe_error','artifact','manual')",
            name="evidence_kind",
        ),
        CheckConstraint("path_tag <> ''", name="evidence_path_tag"),
        Index(
            "ix_activity_evidence_events_participation_id_created_at",
            "participation_id",
            "created_at",
        ),
    )


class ActivityProgressState(OnlineBase):
    __tablename__ = "activity_progress_states"

    participation_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_participations.id",
            name="fk_activity_progress_states_participation_id_participations",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )
    activity_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activities.id",
            name="fk_activity_progress_states_activity_id_activities",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    current_stage_index: Mapped[int] = mapped_column(Integer, nullable=False)
    stage_done: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        CheckConstraint("current_stage_index >= 1", name="progress_current_stage"),
        CheckConstraint("stage_done >= 0", name="progress_stage_done"),
        CheckConstraint(
            "current_stage_index >= stage_done", name="progress_stage_order"
        ),
        Index(
            "ix_activity_progress_states_activity_id_stage_done",
            "activity_id",
            "stage_done",
        ),
    )
