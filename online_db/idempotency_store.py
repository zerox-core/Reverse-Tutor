from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from adapters.online.ports import (
    IdempotencyClaim,
    IdempotencyClaimInProgress,
    IdempotencyClaimNotFound,
    IdempotencyKeyReused,
    SealedResponse,
)
from online_db.models import IdempotencyRecord


PUBLIC_AUTH_OPERATIONS = frozenset({"auth.bootstrap", "auth.refresh"})


class SqlAlchemyIdempotencyStore:
    def __init__(
        self,
        session_factory: sessionmaker[Session],
        *,
        pending_lease: timedelta = timedelta(seconds=30),
    ) -> None:
        self._session_factory = session_factory
        self._pending_lease = pending_lease

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def claim(
        self,
        scope: str,
        key: str,
        request_fingerprint: str,
        now: datetime,
    ) -> IdempotencyClaim:
        operation = scope.split(":", 1)[0]
        if operation not in PUBLIC_AUTH_OPERATIONS:
            raise ValueError(f"Unsupported Slice 0 idempotency operation: {operation}")

        for attempt in range(2):
            try:
                return self._claim_once(
                    operation, scope, key, request_fingerprint, now
                )
            except IntegrityError:
                if attempt == 1:
                    raise
        raise AssertionError("unreachable")

    def _claim_once(
        self,
        operation: str,
        scope: str,
        key: str,
        request_fingerprint: str,
        now: datetime,
    ) -> IdempotencyClaim:
        with self._session_factory() as database, database.begin():
            existing = database.scalar(
                select(IdempotencyRecord)
                .where(
                    IdempotencyRecord.scope_key == scope,
                    IdempotencyRecord.idempotency_key == key,
                )
                .with_for_update()
            )
            if existing is not None:
                if existing.request_fingerprint != request_fingerprint:
                    raise IdempotencyKeyReused(
                        "Idempotency key was reused with a different request"
                    )
                if existing.state == "pending":
                    lease_expires_at = _as_utc(existing.updated_at) + self._pending_lease
                    if lease_expires_at > _as_utc(now):
                        raise IdempotencyClaimInProgress(
                            "Idempotent request is in progress"
                        )
                    database.delete(existing)
                    database.flush()
                elif (
                    existing.response_expires_at is not None
                    and _as_utc(existing.response_expires_at) <= _as_utc(now)
                ):
                    database.delete(existing)
                    database.flush()
                else:
                    return IdempotencyClaim(
                        claim_id=existing.id,
                        replayed_response=self._sealed_response(existing),
                    )

            claim_id = uuid4()
            database.add(
                IdempotencyRecord(
                    id=claim_id,
                    account_id=None,
                    operation=operation,
                    scope_key=scope,
                    idempotency_key=key,
                    request_fingerprint=request_fingerprint,
                    state="pending",
                    created_at=now,
                    updated_at=now,
                )
            )
            return IdempotencyClaim(claim_id=claim_id, replayed_response=None)

    def complete(
        self,
        claim_id: UUID,
        sealed_response: SealedResponse,
        now: datetime,
    ) -> None:
        with self._session_factory() as database, database.begin():
            record = database.get(
                IdempotencyRecord, claim_id, with_for_update=True
            )
            if record is None:
                raise IdempotencyClaimNotFound("Unknown idempotency claim")
            record.state = "completed"
            record.response_status_code = (
                201 if record.operation == "auth.bootstrap" else 200
            )
            record.response_ciphertext = sealed_response.ciphertext
            record.response_nonce = sealed_response.nonce
            record.response_key_id = sealed_response.key_id
            record.response_expires_at = sealed_response.expires_at
            record.updated_at = now

    def release(self, claim_id: UUID, now: datetime) -> None:
        del now
        with self._session_factory() as database, database.begin():
            record = database.get(
                IdempotencyRecord, claim_id, with_for_update=True
            )
            if record is not None and record.state == "pending":
                database.delete(record)

    @staticmethod
    def _sealed_response(record: IdempotencyRecord) -> SealedResponse:
        if (
            record.response_ciphertext is None
            or record.response_nonce is None
            or record.response_key_id is None
            or record.response_expires_at is None
        ):
            raise RuntimeError("Completed idempotency response is incomplete")
        return SealedResponse(
            ciphertext=record.response_ciphertext,
            nonce=record.response_nonce,
            key_id=record.response_key_id,
            expires_at=_as_utc(record.response_expires_at),
        )


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)
