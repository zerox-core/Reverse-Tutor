"""Create the Slice 0 online auth and idempotency tables."""

from typing import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "0001_online_auth_foundation"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "anonymous_accounts",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(length=24), server_default=sa.text("'active'"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "status IN ('active','revoked','deleted')",
            name="ck_anonymous_accounts_account_status",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_anonymous_accounts"),
    )
    op.create_table(
        "account_devices",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("client_device_id", sa.String(length=255), nullable=False),
        sa.Column("status", sa.String(length=24), server_default=sa.text("'active'"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "status IN ('active','revoked')",
            name="ck_account_devices_device_status",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_account_devices_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_account_devices"),
        sa.UniqueConstraint(
            "client_device_id",
            name="uq_account_devices_client_device_id",
        ),
        sa.UniqueConstraint(
            "account_id",
            "client_device_id",
            name="uq_account_devices_account_id_client_device_id",
        ),
        sa.UniqueConstraint(
            "id",
            "account_id",
            name="uq_account_devices_id_account_id",
        ),
    )
    op.create_table(
        "auth_sessions",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("device_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(length=24), server_default=sa.text("'active'"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "status IN ('active','revoked','expired')",
            name="ck_auth_sessions_session_status",
        ),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_auth_sessions_session_expiry",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_auth_sessions_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["device_id", "account_id"],
            ["account_devices.id", "account_devices.account_id"],
            name="fk_auth_sessions_device_id_account_id_account_devices",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_auth_sessions"),
    )
    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("session_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("family_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("rotation", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("used_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("replaced_by_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.CheckConstraint(
            sa.func.length(sa.column("token_hash")) == 64,
            name="ck_refresh_tokens_token_hash_length",
        ),
        sa.CheckConstraint("rotation >= 0", name="ck_refresh_tokens_token_rotation"),
        sa.CheckConstraint(
            "expires_at > created_at",
            name="ck_refresh_tokens_token_expiry",
        ),
        sa.CheckConstraint(
            "replaced_by_id IS NULL OR replaced_by_id <> id",
            name="ck_refresh_tokens_token_replacement",
        ),
        sa.ForeignKeyConstraint(
            ["replaced_by_id"],
            ["refresh_tokens.id"],
            name="fk_refresh_tokens_replaced_by_id_refresh_tokens",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["auth_sessions.id"],
            name="fk_refresh_tokens_session_id_auth_sessions",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_refresh_tokens"),
        sa.UniqueConstraint("token_hash", name="uq_refresh_tokens_token_hash"),
        sa.UniqueConstraint(
            "session_id",
            "rotation",
            name="uq_refresh_tokens_session_id_rotation",
        ),
    )
    op.create_table(
        "auth_audit_events",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("device_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("session_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("event_type", sa.String(length=64), nullable=False),
        sa.Column("outcome", sa.String(length=24), nullable=False),
        sa.Column("request_id", sa.String(length=128), nullable=False),
        sa.Column("safe_details", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint(
            "outcome IN ('success','failure','rejected')",
            name="ck_auth_audit_events_audit_outcome",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_auth_audit_events_account_id_anonymous_accounts",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["account_devices.id"],
            name="fk_auth_audit_events_device_id_account_devices",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["auth_sessions.id"],
            name="fk_auth_audit_events_session_id_auth_sessions",
            ondelete="SET NULL",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_auth_audit_events"),
    )
    op.create_table(
        "idempotency_records",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("account_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("operation", sa.String(length=64), nullable=False),
        sa.Column("scope_key", sa.String(length=255), nullable=False),
        sa.Column("idempotency_key", sa.String(length=255), nullable=False),
        sa.Column("request_fingerprint", sa.String(length=128), nullable=False),
        sa.Column("state", sa.String(length=24), server_default=sa.text("'pending'"), nullable=False),
        sa.Column("response_status_code", sa.Integer(), nullable=True),
        sa.Column("response_ciphertext", sa.LargeBinary(), nullable=True),
        sa.Column("response_nonce", sa.LargeBinary(), nullable=True),
        sa.Column("response_key_id", sa.String(length=128), nullable=True),
        sa.Column("response_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint(
            "state IN ('pending','completed')",
            name="ck_idempotency_records_record_state",
        ),
        sa.CheckConstraint(
            sa.column("operation").in_(("auth.bootstrap", "auth.refresh"))
            | sa.column("account_id").is_not(None),
            name="ck_idempotency_records_public_auth_account",
        ),
        sa.CheckConstraint(
            "response_status_code IS NULL OR response_status_code BETWEEN 100 AND 599",
            name="ck_idempotency_records_response_status_code",
        ),
        sa.CheckConstraint(
            "state = 'pending' OR "
            "(response_status_code IS NOT NULL AND response_ciphertext IS NOT NULL "
            "AND response_nonce IS NOT NULL AND response_key_id IS NOT NULL "
            "AND response_expires_at IS NOT NULL)",
            name="ck_idempotency_records_completed_response",
        ),
        sa.ForeignKeyConstraint(
            ["account_id"],
            ["anonymous_accounts.id"],
            name="fk_idempotency_records_account_id_anonymous_accounts",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_idempotency_records"),
        sa.UniqueConstraint(
            "scope_key",
            "idempotency_key",
            name="uq_idempotency_records_scope_key_idempotency_key",
        ),
    )


def downgrade() -> None:
    op.drop_table("idempotency_records")
    op.drop_table("auth_audit_events")
    op.drop_table("refresh_tokens")
    op.drop_table("auth_sessions")
    op.drop_table("account_devices")
    op.drop_table("anonymous_accounts")
