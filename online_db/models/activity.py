from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


class Activity(OnlineBase):
    __tablename__ = "activities"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    slug: Mapped[str] = mapped_column(String(255), nullable=False)
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    revision: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )
    rule_version: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )
    total_days: Mapped[int] = mapped_column(Integer, nullable=False)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ends_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    requires_online_confirmation: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false")
    )
    allows_deferred_progress: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false")
    )
    state: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'scheduled'")
    )
    session_template_id: Mapped[str | None] = mapped_column(String(255))
    public_feedback_summary: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint("slug", name="uq_activities_slug"),
        CheckConstraint("revision >= 1", name="activity_revision"),
        CheckConstraint("rule_version >= 1", name="activity_rule_version"),
        CheckConstraint("total_days > 0", name="activity_total_days"),
        CheckConstraint("ends_at > starts_at", name="activity_duration"),
        CheckConstraint(
            "state IN ('scheduled','active','closed','offline')",
            name="activity_state",
        ),
        Index("ix_activities_state_starts_at_ends_at", "state", "starts_at", "ends_at"),
    )


class ActivityTask(OnlineBase):
    __tablename__ = "activity_tasks"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    activity_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activities.id",
            name="fk_activity_tasks_activity_id_activities",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    day_number: Mapped[int] = mapped_column(Integer, nullable=False)
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    task_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    stage_goal: Mapped[str | None] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "activity_id",
            "day_number",
            name="uq_activity_tasks_activity_id_day_number",
        ),
        CheckConstraint("day_number > 0", name="task_day_number"),
        Index("ix_activity_tasks_activity_id_day_number", "activity_id", "day_number"),
    )


class ActivityParticipation(OnlineBase):
    __tablename__ = "activity_participations"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    activity_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activities.id",
            name="fk_activity_participations_activity_id_activities",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    account_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_activity_participations_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    state: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'joined'")
    )
    progress: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    revision: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )
    last_idempotency_key: Mapped[str] = mapped_column(String(255), nullable=False)
    joined_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    left_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        UniqueConstraint(
            "activity_id",
            "account_id",
            name="uq_activity_participations_activity_id_account_id",
        ),
        CheckConstraint(
            "state IN ('joined','pending_sync','completed','left')",
            name="participation_state",
        ),
        CheckConstraint("progress >= 0", name="participation_progress"),
        CheckConstraint("revision >= 1", name="participation_revision"),
        CheckConstraint(
            "(state = 'left' AND left_at IS NOT NULL) OR "
            "(state <> 'left' AND left_at IS NULL)",
            name="participation_left_timestamp",
        ),
        Index(
            "ix_activity_participations_account_id_state",
            "account_id",
            "state",
        ),
        Index(
            "ix_activity_participations_activity_id_progress",
            "activity_id",
            "progress",
        ),
    )


class ActivityProgressEvent(OnlineBase):
    __tablename__ = "activity_progress_events"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    participation_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "activity_participations.id",
            name="fk_activity_progress_events_participation_id_participations",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    operation: Mapped[str] = mapped_column(String(24), nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(255), nullable=False)
    request_revision: Mapped[int] = mapped_column(Integer, nullable=False)
    request_progress: Mapped[int | None] = mapped_column(Integer)
    state: Mapped[str] = mapped_column(String(24), nullable=False)
    progress: Mapped[int] = mapped_column(Integer, nullable=False)
    revision: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        UniqueConstraint(
            "participation_id",
            "operation",
            "idempotency_key",
            name="uq_activity_progress_events_participation_operation_key",
        ),
        CheckConstraint(
            "operation IN ('join','progress','leave')",
            name="progress_operation",
        ),
        CheckConstraint(
            "state IN ('joined','pending_sync','completed','left')",
            name="progress_state",
        ),
        CheckConstraint("request_revision >= 0", name="progress_request_revision"),
        CheckConstraint(
            "request_progress IS NULL OR request_progress >= 0",
            name="progress_request_progress",
        ),
        CheckConstraint("progress >= 0", name="progress_value"),
        CheckConstraint("revision >= 1", name="progress_revision"),
        Index(
            "ix_activity_progress_events_participation_id_created_at",
            "participation_id",
            "created_at",
        ),
    )
