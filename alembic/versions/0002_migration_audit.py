"""Create the online database migration audit tables."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0002_migration_audit"
down_revision: str | None = "0001_online_auth_foundation"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "migration_runs",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("source_sha256", sa.String(length=64), nullable=False),
        sa.Column("target_revision", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=24), server_default=sa.text("'running'"), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("summary_json", sa.JSON(), nullable=False),
        sa.CheckConstraint(
            sa.func.length(sa.column("source_sha256")) == 64,
            name="ck_migration_runs_source_hash_length",
        ),
        sa.CheckConstraint(
            "status IN ('running','passed','failed')",
            name="ck_migration_runs_run_status",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_migration_runs"),
    )
    op.create_table(
        "migration_validation_results",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("run_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("check_name", sa.String(length=64), nullable=False),
        sa.Column("passed", sa.Boolean(), nullable=False),
        sa.Column("expected_json", sa.JSON(), nullable=True),
        sa.Column("actual_json", sa.JSON(), nullable=True),
        sa.Column("diagnostic", sa.String(length=1000), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.ForeignKeyConstraint(
            ["run_id"],
            ["migration_runs.id"],
            name="fk_migration_validation_results_run_id_migration_runs",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_migration_validation_results"),
        sa.UniqueConstraint(
            "run_id",
            "check_name",
            name="uq_migration_validation_results_run_id_check_name",
        ),
    )


def downgrade() -> None:
    op.drop_table("migration_validation_results")
    op.drop_table("migration_runs")
