from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    ForeignKey,
    ForeignKeyConstraint,
    Integer,
    String,
    UniqueConstraint,
    Uuid,
    column,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


class AnonymousAccount(OnlineBase):
    __tablename__ = "anonymous_accounts"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'active'")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        CheckConstraint(
            "status IN ('active','revoked','deleted')",
            name="account_status",
        ),
    )


class AccountDevice(OnlineBase):
    __tablename__ = "account_devices"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    account_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_account_devices_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    client_device_id: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'active'")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        UniqueConstraint(
            "client_device_id",
            name="uq_account_devices_client_device_id",
        ),
        UniqueConstraint(
            "account_id",
            "client_device_id",
            name="uq_account_devices_account_id_client_device_id",
        ),
        UniqueConstraint(
            "id",
            "account_id",
            name="uq_account_devices_id_account_id",
        ),
        CheckConstraint(
            "status IN ('active','revoked')",
            name="device_status",
        ),
    )


class AuthSession(OnlineBase):
    __tablename__ = "auth_sessions"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    account_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_auth_sessions_account_id_anonymous_accounts",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    device_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), nullable=False)
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'active'")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        ForeignKeyConstraint(
            ["device_id", "account_id"],
            ["account_devices.id", "account_devices.account_id"],
            name="fk_auth_sessions_device_id_account_id_account_devices",
            ondelete="CASCADE",
        ),
        CheckConstraint(
            "status IN ('active','revoked','expired')",
            name="session_status",
        ),
        CheckConstraint("expires_at > created_at", name="session_expiry"),
    )


class RefreshToken(OnlineBase):
    __tablename__ = "refresh_tokens"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    session_id: Mapped[UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "auth_sessions.id",
            name="fk_refresh_tokens_session_id_auth_sessions",
            ondelete="CASCADE",
        ),
        nullable=False,
    )
    family_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), nullable=False)
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    rotation: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    replaced_by_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "refresh_tokens.id",
            name="fk_refresh_tokens_replaced_by_id_refresh_tokens",
            ondelete="SET NULL",
        ),
    )

    __table_args__ = (
        UniqueConstraint(
            "session_id",
            "rotation",
            name="uq_refresh_tokens_session_id_rotation",
        ),
        CheckConstraint(
            func.length(column("token_hash")) == 64,
            name="token_hash_length",
        ),
        CheckConstraint("rotation >= 0", name="token_rotation"),
        CheckConstraint("expires_at > created_at", name="token_expiry"),
        CheckConstraint(
            "replaced_by_id IS NULL OR replaced_by_id <> id",
            name="token_replacement",
        ),
    )


class AuthAuditEvent(OnlineBase):
    __tablename__ = "auth_audit_events"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    account_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_auth_audit_events_account_id_anonymous_accounts",
            ondelete="SET NULL",
        ),
    )
    device_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "account_devices.id",
            name="fk_auth_audit_events_device_id_account_devices",
            ondelete="SET NULL",
        ),
    )
    session_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "auth_sessions.id",
            name="fk_auth_audit_events_session_id_auth_sessions",
            ondelete="SET NULL",
        ),
    )
    event_type: Mapped[str] = mapped_column(String(64), nullable=False)
    outcome: Mapped[str] = mapped_column(String(24), nullable=False)
    request_id: Mapped[str] = mapped_column(String(128), nullable=False)
    safe_details: Mapped[dict[str, object]] = mapped_column(
        JSON, nullable=False, default=dict
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        CheckConstraint(
            "outcome IN ('success','failure','rejected')",
            name="audit_outcome",
        ),
    )
