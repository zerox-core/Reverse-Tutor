"""Create public content and activity persistence tables."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0003_content_and_activities"
down_revision: str | None = "0002_migration_audit"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "content_items",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("slug", sa.String(length=255), nullable=False),
        sa.Column("content_type", sa.String(length=32), nullable=False),
        sa.Column("title", sa.String(length=80), nullable=False),
        sa.Column("summary", sa.String(length=180), nullable=False),
        sa.Column("body_markdown", sa.Text(), nullable=False),
        sa.Column("illustration_template", sa.String(length=128), nullable=False),
        sa.Column("illustration_config", sa.JSON(), nullable=False),
        sa.Column("publisher_name", sa.String(length=120), nullable=True),
        sa.Column(
            "status",
            sa.String(length=24),
            server_default=sa.text("'draft'"),
            nullable=False,
        ),
        sa.Column(
            "sort_order", sa.Integer(), server_default=sa.text("0"), nullable=False
        ),
        sa.Column("publish_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("offline_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "content_version",
            sa.Integer(),
            server_default=sa.text("1"),
            nullable=False,
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
            "content_type IN ('public_interest','announcement')",
            name="ck_content_items_content_type",
        ),
        sa.CheckConstraint(
            "status IN ('draft','scheduled','published','offline')",
            name="ck_content_items_content_status",
        ),
        sa.CheckConstraint("sort_order >= 0", name="ck_content_items_sort_order"),
        sa.CheckConstraint(
            "content_version >= 1", name="ck_content_items_content_version"
        ),
        sa.CheckConstraint(
            "status = 'draft' OR publish_at IS NOT NULL",
            name="ck_content_items_publish_timestamp",
        ),
        sa.CheckConstraint(
            "(status = 'offline' AND offline_at IS NOT NULL) OR "
            "(status <> 'offline' AND offline_at IS NULL)",
            name="ck_content_items_offline_timestamp",
        ),
        sa.CheckConstraint(
            "offline_at IS NULL OR publish_at IS NULL OR offline_at >= publish_at",
            name="ck_content_items_offline_after_publish",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_content_items"),
        sa.UniqueConstraint("slug", name="uq_content_items_slug"),
    )
    op.create_index(
        "ix_content_items_status_publish_at_sort_order",
        "content_items",
        ["status", "publish_at", "sort_order"],
        unique=False,
    )

    op.create_table(
        "activities",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("slug", sa.String(length=255), nullable=False),
        sa.Column("title", sa.String(length=120), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column(
            "revision", sa.Integer(), server_default=sa.text("1"), nullable=False
        ),
        sa.Column(
            "rule_version",
            sa.Integer(),
            server_default=sa.text("1"),
            nullable=False,
        ),
        sa.Column("total_days", sa.Integer(), nullable=False),
        sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ends_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "requires_online_confirmation",
            sa.Boolean(),
            server_default=sa.text("false"),
            nullable=False,
        ),
        sa.Column(
            "allows_deferred_progress",
            sa.Boolean(),
            server_default=sa.text("false"),
            nullable=False,
        ),
        sa.Column(
            "state",
            sa.String(length=24),
            server_default=sa.text("'scheduled'"),
            nullable=False,
        ),
        sa.Column("session_template_id", sa.String(length=255), nullable=True),
        sa.Column("public_feedback_summary", sa.Text(), nullable=True),
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
            "revision >= 1", name="ck_activities_activity_revision"
        ),
        sa.CheckConstraint(
            "rule_version >= 1", name="ck_activities_activity_rule_version"
        ),
        sa.CheckConstraint(
            "total_days > 0", name="ck_activities_activity_total_days"
        ),
        sa.CheckConstraint(
            "ends_at > starts_at", name="ck_activities_activity_duration"
        ),
        sa.CheckConstraint(
            "state IN ('scheduled','active','closed','offline')",
            name="ck_activities_activity_state",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activities"),
        sa.UniqueConstraint("slug", name="uq_activities_slug"),
    )
    op.create_index(
        "ix_activities_state_starts_at_ends_at",
        "activities",
        ["state", "starts_at", "ends_at"],
        unique=False,
    )

    op.create_table(
        "content_assets",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("content_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("role", sa.String(length=24), nullable=False),
        sa.Column(
            "position", sa.Integer(), server_default=sa.text("0"), nullable=False
        ),
        sa.Column("url", sa.String(length=2048), nullable=False),
        sa.Column("mime_type", sa.String(length=255), nullable=False),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("byte_size", sa.Integer(), nullable=True),
        sa.Column("sha256", sa.String(length=64), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.CheckConstraint(
            "role IN ('cover','body')", name="ck_content_assets_asset_role"
        ),
        sa.CheckConstraint(
            "position >= 0", name="ck_content_assets_asset_position"
        ),
        sa.CheckConstraint(
            "width IS NULL OR width > 0", name="ck_content_assets_asset_width"
        ),
        sa.CheckConstraint(
            "height IS NULL OR height > 0", name="ck_content_assets_asset_height"
        ),
        sa.CheckConstraint(
            "byte_size IS NULL OR byte_size >= 0",
            name="ck_content_assets_asset_byte_size",
        ),
        sa.CheckConstraint(
            "sha256 IS NULL OR length(sha256) = 64",
            name="ck_content_assets_asset_sha256_length",
        ),
        sa.ForeignKeyConstraint(
            ["content_id"],
            ["content_items.id"],
            name="fk_content_assets_content_id_content_items",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_content_assets"),
        sa.UniqueConstraint(
            "content_id",
            "role",
            "position",
            name="uq_content_assets_content_id_role_position",
        ),
    )
    op.create_index(
        "ix_content_assets_content_id_position",
        "content_assets",
        ["content_id", "position"],
        unique=False,
    )

    op.create_table(
        "activity_tasks",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("day_number", sa.Integer(), nullable=False),
        sa.Column("title", sa.String(length=120), nullable=False),
        sa.Column("task_markdown", sa.Text(), nullable=False),
        sa.Column("stage_goal", sa.String(length=255), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.CheckConstraint(
            "day_number > 0", name="ck_activity_tasks_task_day_number"
        ),
        sa.ForeignKeyConstraint(
            ["activity_id"],
            ["activities.id"],
            name="fk_activity_tasks_activity_id_activities",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_tasks"),
        sa.UniqueConstraint(
            "activity_id",
            "day_number",
            name="uq_activity_tasks_activity_id_day_number",
        ),
    )
    op.create_index(
        "ix_activity_tasks_activity_id_day_number",
        "activity_tasks",
        ["activity_id", "day_number"],
        unique=False,
    )

    op.create_table(
        "activity_participations",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column(
            "state",
            sa.String(length=24),
            server_default=sa.text("'joined'"),
            nullable=False,
        ),
        sa.Column(
            "progress", sa.Integer(), server_default=sa.text("0"), nullable=False
        ),
        sa.Column(
            "revision", sa.Integer(), server_default=sa.text("1"), nullable=False
        ),
        sa.Column("last_idempotency_key", sa.String(length=255), nullable=False),
        sa.Column("joined_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("left_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "state IN ('joined','pending_sync','completed','left')",
            name="ck_activity_participations_participation_state",
        ),
        sa.CheckConstraint(
            "progress >= 0", name="ck_activity_participations_participation_progress"
        ),
        sa.CheckConstraint(
            "revision >= 1", name="ck_activity_participations_participation_revision"
        ),
        sa.CheckConstraint(
            "(state = 'left' AND left_at IS NOT NULL) OR "
            "(state <> 'left' AND left_at IS NULL)",
            name="ck_activity_participations_participation_left_timestamp",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_activity_participations_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["activity_id"],
            ["activities.id"],
            name="fk_activity_participations_activity_id_activities",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_participations"),
        sa.UniqueConstraint(
            "activity_id",
            "account_id",
            name="uq_activity_participations_activity_id_account_id",
        ),
    )
    op.create_index(
        "ix_activity_participations_account_id_state",
        "activity_participations",
        ["account_id", "state"],
        unique=False,
    )
    op.create_index(
        "ix_activity_participations_activity_id_progress",
        "activity_participations",
        ["activity_id", "progress"],
        unique=False,
    )

    op.create_table(
        "activity_progress_events",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("participation_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("operation", sa.String(length=24), nullable=False),
        sa.Column("idempotency_key", sa.String(length=255), nullable=False),
        sa.Column("request_revision", sa.Integer(), nullable=False),
        sa.Column("request_progress", sa.Integer(), nullable=True),
        sa.Column("state", sa.String(length=24), nullable=False),
        sa.Column("progress", sa.Integer(), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "operation IN ('join','progress','leave')",
            name="ck_activity_progress_events_progress_operation",
        ),
        sa.CheckConstraint(
            "state IN ('joined','pending_sync','completed','left')",
            name="ck_activity_progress_events_progress_state",
        ),
        sa.CheckConstraint(
            "request_revision >= 0",
            name="ck_activity_progress_events_progress_request_revision",
        ),
        sa.CheckConstraint(
            "request_progress IS NULL OR request_progress >= 0",
            name="ck_activity_progress_events_request_progress",
        ),
        sa.CheckConstraint(
            "progress >= 0", name="ck_activity_progress_events_progress_value"
        ),
        sa.CheckConstraint(
            "revision >= 1", name="ck_activity_progress_events_progress_revision"
        ),
        sa.ForeignKeyConstraint(
            ["participation_id"],
            ["activity_participations.id"],
            name="fk_activity_progress_events_participation_id_participations",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_activity_progress_events"),
        sa.UniqueConstraint(
            "participation_id",
            "operation",
            "idempotency_key",
            name="uq_activity_progress_events_participation_operation_key",
        ),
    )
    op.create_index(
        "ix_activity_progress_events_participation_id_created_at",
        "activity_progress_events",
        ["participation_id", "created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_table("activity_progress_events")
    op.drop_table("activity_participations")
    op.drop_table("activity_tasks")
    op.drop_table("content_assets")
    op.drop_table("activities")
    op.drop_table("content_items")
