from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, JSON, String, UniqueConstraint, Uuid, column, func, text
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


class MigrationRun(OnlineBase):
    __tablename__ = "migration_runs"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    source_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    target_revision: Mapped[str] = mapped_column(String(64), nullable=False)
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'running'")
    )
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    summary_json: Mapped[dict[str, object]] = mapped_column(
        JSON, nullable=False, default=dict
    )

    __table_args__ = (
        CheckConstraint(
            func.length(column("source_sha256")) == 64,
            name="source_hash_length",
        ),
        CheckConstraint(
            "status IN ('running','passed','failed')",
            name="run_status",
        ),
    )


class MigrationValidationResult(OnlineBase):
    __tablename__ = "migration_validation_results"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    run_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "migration_runs.id",
            name="fk_migration_validation_results_run_id_migration_runs",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    check_name: Mapped[str] = mapped_column(String(64), nullable=False)
    passed: Mapped[bool] = mapped_column(Boolean, nullable=False)
    expected_json: Mapped[object | None] = mapped_column(JSON)
    actual_json: Mapped[object | None] = mapped_column(JSON)
    diagnostic: Mapped[str | None] = mapped_column(String(1000))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "run_id",
            "check_name",
            name="uq_migration_validation_results_run_id_check_name",
        ),
    )
