from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timedelta
from threading import RLock
from uuid import UUID, uuid4

from .ports import (
    AuthSessionRecord,
    BootstrapCommand,
    BootstrapResult,
    DeviceAlreadyRegistered,
    IdempotencyClaim,
    IdempotencyClaimInProgress,
    IdempotencyClaimNotFound,
    IdempotencyKeyReused,
    RefreshTokenInvalid,
    RefreshTokenReplay,
    RotateRefreshCommand,
    RotateRefreshResult,
    SealedResponse,
)


@dataclass
class _RefreshRecord:
    token_hash: str
    session_id: UUID
    family_id: UUID
    rotation: int
    expires_at: datetime
    consumed: bool = False


@dataclass
class _IdempotencyRecord:
    claim_id: UUID
    fingerprint: str
    response: SealedResponse | None = None
    pending_until: datetime | None = None


class InMemoryAuthStore:
    """Locked protocol store used by Slice 0 tests, never as production persistence."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._sessions: dict[UUID, AuthSessionRecord] = {}
        self._session_by_device: dict[str, UUID] = {}
        self._family_by_session: dict[UUID, UUID] = {}
        self._revoked_families: set[UUID] = set()
        self._refresh_tokens: dict[str, _RefreshRecord] = {}

    def bootstrap(self, command: BootstrapCommand) -> BootstrapResult:
        with self._lock:
            # Device registration is one-shot.  Retries are handled by the
            # idempotency store before reaching this mutation port.
            if command.device_id in self._session_by_device:
                raise DeviceAlreadyRegistered("Device is already registered")

            session = AuthSessionRecord(
                account_id=uuid4(),
                device_id=command.device_id,
                session_id=uuid4(),
                status="active",
                created_at=command.now,
                expires_at=command.refresh_expires_at,
            )
            family_id = uuid4()
            self._sessions[session.session_id] = session
            self._session_by_device[session.device_id] = session.session_id
            self._family_by_session[session.session_id] = family_id
            rotation = 0

            self._refresh_tokens[command.initial_refresh_token_hash] = _RefreshRecord(
                token_hash=command.initial_refresh_token_hash,
                session_id=session.session_id,
                family_id=family_id,
                rotation=rotation,
                expires_at=command.refresh_expires_at,
            )
            return BootstrapResult(session=session, family_id=family_id)

    def rotate_refresh(self, command: RotateRefreshCommand) -> RotateRefreshResult:
        with self._lock:
            token = self._refresh_tokens.get(command.presented_token_hash)
            if token is None:
                raise RefreshTokenInvalid("Refresh token is invalid")
            if token.consumed:
                self._revoke_family(token.family_id, command.now)
                raise RefreshTokenReplay("Refresh token replay detected")
            if token.family_id in self._revoked_families or token.expires_at <= command.now:
                raise RefreshTokenInvalid("Refresh token is invalid or expired")

            session = self._sessions.get(token.session_id)
            if session is None or session.status != "active" or session.expires_at <= command.now:
                raise RefreshTokenInvalid("Refresh session is inactive")

            token.consumed = True
            rotation = token.rotation + 1
            session = replace(session, expires_at=command.refresh_expires_at)
            self._sessions[session.session_id] = session
            self._refresh_tokens[command.replacement_token_hash] = _RefreshRecord(
                token_hash=command.replacement_token_hash,
                session_id=session.session_id,
                family_id=token.family_id,
                rotation=rotation,
                expires_at=command.refresh_expires_at,
            )
            return RotateRefreshResult(
                session=session,
                family_id=token.family_id,
                rotation=rotation,
            )

    def active_session(
        self, session_id: UUID, now: datetime
    ) -> AuthSessionRecord | None:
        with self._lock:
            session = self._sessions.get(session_id)
            if session is None or session.status != "active":
                return None
            if session.expires_at <= now:
                self._sessions[session_id] = replace(session, status="expired")
                return None
            return session

    def list_sessions(self, account_id: UUID) -> list[AuthSessionRecord]:
        with self._lock:
            return sorted(
                (
                    session
                    for session in self._sessions.values()
                    if session.account_id == account_id
                ),
                key=lambda session: (session.created_at, str(session.session_id)),
                reverse=True,
            )

    def revoke_session(
        self, account_id: UUID, session_id: UUID, now: datetime
    ) -> bool:
        with self._lock:
            session = self._sessions.get(session_id)
            if session is None or session.account_id != account_id:
                return False
            family_id = self._family_by_session.get(session_id)
            if family_id is not None:
                self._revoke_family(family_id, now)
            return True

    def _existing_active_device_session(
        self, device_id: str, now: datetime
    ) -> AuthSessionRecord | None:
        session_id = self._session_by_device.get(device_id)
        if session_id is None:
            return None
        return self.active_session(session_id, now)

    def _next_rotation(self, family_id: UUID) -> int:
        return max(
            (
                token.rotation + 1
                for token in self._refresh_tokens.values()
                if token.family_id == family_id
            ),
            default=0,
        )

    def _revoke_family(self, family_id: UUID, now: datetime) -> None:
        self._revoked_families.add(family_id)
        for session_id, stored_family_id in self._family_by_session.items():
            if stored_family_id != family_id:
                continue
            session = self._sessions[session_id]
            if session.status == "active":
                self._sessions[session_id] = replace(session, status="revoked")


class InMemoryIdempotencyStore:
    def __init__(self, *, pending_lease: timedelta = timedelta(seconds=30)) -> None:
        self._lock = RLock()
        self._records: dict[tuple[str, str], _IdempotencyRecord] = {}
        self._keys_by_claim: dict[UUID, tuple[str, str]] = {}
        self._pending_lease = pending_lease

    def claim(
        self,
        scope: str,
        key: str,
        request_fingerprint: str,
        now: datetime,
    ) -> IdempotencyClaim:
        with self._lock:
            storage_key = (scope, key)
            existing = self._records.get(storage_key)
            if existing is not None:
                if existing.fingerprint != request_fingerprint:
                    raise IdempotencyKeyReused(
                        "Idempotency key was reused with a different request"
                    )
                if existing.response is None:
                    if existing.pending_until is None or existing.pending_until > now:
                        raise IdempotencyClaimInProgress(
                            "Idempotent request is in progress"
                        )
                    # A crashed worker must not pin an idempotency key forever.
                    self._keys_by_claim.pop(existing.claim_id, None)
                    existing.claim_id = uuid4()
                    existing.pending_until = now + self._pending_lease
                    self._keys_by_claim[existing.claim_id] = storage_key
                    return IdempotencyClaim(existing.claim_id, None)
                return IdempotencyClaim(existing.claim_id, existing.response)

            claim_id = uuid4()
            self._records[storage_key] = _IdempotencyRecord(
                claim_id=claim_id,
                fingerprint=request_fingerprint,
                pending_until=now + self._pending_lease,
            )
            self._keys_by_claim[claim_id] = storage_key
            return IdempotencyClaim(claim_id, None)

    def complete(
        self,
        claim_id: UUID,
        sealed_response: SealedResponse,
        now: datetime,
    ) -> None:
        del now
        with self._lock:
            storage_key = self._keys_by_claim.get(claim_id)
            if storage_key is None:
                raise IdempotencyClaimNotFound("Unknown idempotency claim")
            record = self._records.get(storage_key)
            if record is None or record.claim_id != claim_id:
                raise IdempotencyClaimNotFound("Stale idempotency claim")
            record.response = sealed_response
            record.pending_until = None

    def release(self, claim_id: UUID, now: datetime) -> None:
        del now
        with self._lock:
            storage_key = self._keys_by_claim.pop(claim_id, None)
            if storage_key is None:
                return
            record = self._records.get(storage_key)
            if record is not None and record.claim_id == claim_id and record.response is None:
                del self._records[storage_key]
