from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Integer, LargeBinary, String, UniqueConstraint, Uuid, column, func, text
from sqlalchemy.orm import Mapped, mapped_column

from online_db.base import OnlineBase


class IdempotencyRecord(OnlineBase):
    __tablename__ = "idempotency_records"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    account_id: Mapped[UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "anonymous_accounts.id",
            name="fk_idempotency_records_account_id_anonymous_accounts",
        ),
    )
    operation: Mapped[str] = mapped_column(String(64), nullable=False)
    scope_key: Mapped[str] = mapped_column(String(255), nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(255), nullable=False)
    request_fingerprint: Mapped[str] = mapped_column(String(128), nullable=False)
    state: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'pending'")
    )
    response_status_code: Mapped[int | None] = mapped_column(Integer)
    response_ciphertext: Mapped[bytes | None] = mapped_column(LargeBinary)
    response_nonce: Mapped[bytes | None] = mapped_column(LargeBinary)
    response_key_id: Mapped[str | None] = mapped_column(String(128))
    response_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            "scope_key",
            "idempotency_key",
            name="uq_idempotency_records_scope_key_idempotency_key",
        ),
        CheckConstraint(
            "state IN ('pending','completed')",
            name="record_state",
        ),
        CheckConstraint(
            column("operation").in_(("auth.bootstrap", "auth.refresh"))
            | column("account_id").is_not(None),
            name="public_auth_account",
        ),
        CheckConstraint(
            "response_status_code IS NULL OR response_status_code BETWEEN 100 AND 599",
            name="response_status_code",
        ),
        CheckConstraint(
            "state = 'pending' OR "
            "(response_status_code IS NOT NULL AND response_ciphertext IS NOT NULL "
            "AND response_nonce IS NOT NULL AND response_key_id IS NOT NULL "
            "AND response_expires_at IS NOT NULL)",
            name="completed_response",
        ),
    )
