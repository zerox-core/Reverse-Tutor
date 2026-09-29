"""Create stage progress tables (stage ladder, evidence events, progress state)."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0004_stage_progress"
down_revision: str | None = "0003_content_and_activities"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "activity_stages",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("stage_index", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("capability", sa.Text(), nullable=False),
        sa.Column("evidence_keys", sa.JSON(), nullable=False),
        sa.Column("task_day_numbers", sa.JSON(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.CheckConstraint("stage_index >= 1", name="ck_activity_stages_stage_index"),
        sa.ForeignKeyConstraint(
            ["activity_id"],
            ["activities.id"],
            name="fk_activity_stages_activity_id_activities",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_stages"),
        sa.UniqueConstraint(
            "activity_id",
            "stage_index",
            name="uq_activity_stages_activity_id_stage_index",
        ),
    )
    op.create_index(
        "ix_activity_stages_activity_id_stage_index",
        "activity_stages",
        ["activity_id", "stage_index"],
        unique=False,
    )

    op.create_table(
        "activity_evidence_events",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("participation_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("stage_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("evidence_key", sa.String(length=255), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("artifact_ref", sa.Text(), nullable=False),
        sa.Column(
            "path_tag",
            sa.String(length=64),
            server_default=sa.text("'in_order'"),
            nullable=False,
        ),
        sa.Column("idempotency_key", sa.String(length=255), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "kind IN ('baseline_probe','probe_recite','probe_transfer',"
            "'probe_error','artifact','manual')",
            name="ck_activity_evidence_events_evidence_kind",
        ),
        sa.CheckConstraint(
            "path_tag <> ''", name="ck_activity_evidence_events_evidence_path_tag"
        ),
        sa.ForeignKeyConstraint(
            ["participation_id"],
            ["activity_participations.id"],
            name="fk_activity_evidence_events_participation_id_participations",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["stage_id"],
            ["activity_stages.id"],
            name="fk_activity_evidence_events_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_evidence_events"),
        sa.UniqueConstraint(
            "participation_id",
            "stage_id",
            "evidence_key",
            name="uq_activity_evidence_events_participation_stage_key",
        ),
    )
    op.create_index(
        "ix_activity_evidence_events_participation_id_created_at",
        "activity_evidence_events",
        ["participation_id", "created_at"],
        unique=False,
    )

    op.create_table(
        "activity_progress_states",
        sa.Column("participation_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("current_stage_index", sa.Integer(), nullable=False),
        sa.Column(
            "stage_done", sa.Integer(), server_default=sa.text("0"), nullable=False
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.CheckConstraint(
            "current_stage_index >= 1",
            name="ck_activity_progress_states_progress_current_stage",
        ),
        sa.CheckConstraint(
            "stage_done >= 0", name="ck_activity_progress_states_progress_stage_done"
        ),
        sa.CheckConstraint(
            "current_stage_index >= stage_done",
            name="ck_activity_progress_states_progress_stage_order",
        ),
        sa.ForeignKeyConstraint(
            ["participation_id"],
            ["activity_participations.id"],
            name="fk_activity_progress_states_participation_id_participations",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["activity_id"],
            ["activities.id"],
            name="fk_activity_progress_states_activity_id_activities",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("participation_id", name="pk_activity_progress_states"),
    )
    op.create_index(
        "ix_activity_progress_states_activity_id_stage_done",
        "activity_progress_states",
        ["activity_id", "stage_done"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_table("activity_progress_states")
    op.drop_table("activity_evidence_events")
    op.drop_table("activity_stages")
