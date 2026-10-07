"""Probe plan archive + teaching preference profile (session-loop semantics v2).

Implements the persistence of docs/specs/stage-progress-model.md §11:
probe plans per participation/stage (auto evidence judgment), teaching
preference profiles per account/activity, and per-knowledge-point path
ledgers.
"""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0005_probe_plan_and_profiles"
down_revision: str | None = "0004_stage_progress"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


_TEACHING_LEVELS = "'direct','analogy_story','slow_decompose','prereq_remedy'"


def upgrade() -> None:
    op.create_table(
        "activity_probe_plans",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("participation_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("stage_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("plan", sa.JSON(), nullable=False),
        sa.Column("model", sa.String(length=120), nullable=False),
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
        sa.CheckConstraint("model <> ''", name="ck_activity_probe_plans_probe_plan_model"),
        sa.ForeignKeyConstraint(
            ["participation_id"],
            ["activity_participations.id"],
            name="fk_activity_probe_plans_participation_id_participations",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["stage_id"],
            ["activity_stages.id"],
            name="fk_activity_probe_plans_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_probe_plans"),
        sa.UniqueConstraint(
            "participation_id",
            "stage_id",
            name="uq_activity_probe_plans_participation_id_stage_id",
        ),
    )

    op.create_table(
        "activity_teaching_profiles",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column(
            "current_level",
            sa.String(length=32),
            server_default=sa.text("'direct'"),
            nullable=False,
        ),
        sa.Column(
            "sample_count", sa.Integer(), server_default=sa.text("0"), nullable=False
        ),
        sa.Column("last_adjust_reason", sa.String(length=255), nullable=True),
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
        sa.CheckConstraint(
            f"current_level IN ({_TEACHING_LEVELS})",
            name="ck_activity_teaching_profiles_profile_current_level",
        ),
        sa.CheckConstraint(
            "sample_count >= 0",
            name="ck_activity_teaching_profiles_profile_sample_count",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_activity_teaching_profiles_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["activity_id"],
            ["activities.id"],
            name="fk_activity_teaching_profiles_activity_id_activities",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_teaching_profiles"),
        sa.UniqueConstraint(
            "account_id",
            "activity_id",
            name="uq_activity_teaching_profiles_account_id_activity_id",
        ),
    )

    op.create_table(
        "activity_knowledge_paths",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("participation_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("stage_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("knowledge_point", sa.String(length=255), nullable=False),
        sa.Column("start_level", sa.String(length=32), nullable=False),
        sa.Column("confirmed_level", sa.String(length=32), nullable=True),
        sa.Column(
            "attempt_sequence",
            sa.JSON(),
            server_default=sa.text("'[]'"),
            nullable=False,
        ),
        sa.Column(
            "probe_records", sa.JSON(), server_default=sa.text("'[]'"), nullable=False
        ),
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
        sa.CheckConstraint(
            f"start_level IN ({_TEACHING_LEVELS})",
            name="ck_activity_knowledge_paths_path_start_level",
        ),
        sa.CheckConstraint(
            f"(confirmed_level IS NULL OR confirmed_level IN ({_TEACHING_LEVELS}))",
            name="ck_activity_knowledge_paths_path_confirmed_level",
        ),
        sa.ForeignKeyConstraint(
            ["participation_id"],
            ["activity_participations.id"],
            name="fk_activity_knowledge_paths_participation_id_participations",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["stage_id"],
            ["activity_stages.id"],
            name="fk_activity_knowledge_paths_stage_id_activity_stages",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_knowledge_paths"),
        sa.UniqueConstraint(
            "participation_id",
            "stage_id",
            "knowledge_point",
            name="uq_activity_knowledge_paths_participation_stage_kp",
        ),
    )
    op.create_index(
        "ix_activity_knowledge_paths_participation_id_updated_at",
        "activity_knowledge_paths",
        ["participation_id", "updated_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_activity_knowledge_paths_participation_id_updated_at",
        table_name="activity_knowledge_paths",
    )
    op.drop_table("activity_knowledge_paths")
    op.drop_table("activity_teaching_profiles")
    op.drop_table("activity_probe_plans")
