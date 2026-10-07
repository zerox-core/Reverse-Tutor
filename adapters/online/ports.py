from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol
from uuid import UUID


class AuthStoreError(Exception):
    pass


class DeviceAlreadyRegistered(AuthStoreError):
    """The anonymous device has already been bootstrapped.

    Bootstrap is intentionally a one-time device registration operation.  A
    retry with the same idempotency key is replayed by the idempotency port;
    a different key must not mint another refresh-token family.
    """


class RefreshTokenInvalid(AuthStoreError):
    pass


class RefreshTokenReplay(AuthStoreError):
    pass


class IdempotencyStoreError(Exception):
    pass


class IdempotencyKeyReused(IdempotencyStoreError):
    pass


class IdempotencyClaimInProgress(IdempotencyStoreError):
    pass


class IdempotencyClaimNotFound(IdempotencyStoreError):
    pass


@dataclass(frozen=True)
class AuthSessionRecord:
    account_id: UUID
    device_id: str
    session_id: UUID
    status: str
    created_at: datetime
    expires_at: datetime


@dataclass(frozen=True)
class BootstrapCommand:
    device_id: str
    initial_refresh_token_hash: str
    now: datetime
    refresh_expires_at: datetime


@dataclass(frozen=True)
class BootstrapResult:
    session: AuthSessionRecord
    family_id: UUID


@dataclass(frozen=True)
class RotateRefreshCommand:
    presented_token_hash: str
    replacement_token_hash: str
    now: datetime
    refresh_expires_at: datetime


@dataclass(frozen=True)
class RotateRefreshResult:
    session: AuthSessionRecord
    family_id: UUID
    rotation: int


@dataclass(frozen=True)
class SealedResponse:
    ciphertext: bytes
    nonce: bytes
    key_id: str
    expires_at: datetime


@dataclass(frozen=True)
class IdempotencyClaim:
    claim_id: UUID
    replayed_response: SealedResponse | None


class AuthStore(Protocol):
    def bootstrap(self, command: BootstrapCommand) -> BootstrapResult: ...

    def rotate_refresh(self, command: RotateRefreshCommand) -> RotateRefreshResult: ...

    def active_session(
        self, session_id: UUID, now: datetime
    ) -> AuthSessionRecord | None: ...

    def list_sessions(self, account_id: UUID) -> list[AuthSessionRecord]: ...

    def revoke_session(
        self, account_id: UUID, session_id: UUID, now: datetime
    ) -> bool: ...


class IdempotencyStore(Protocol):
    def claim(
        self,
        scope: str,
        key: str,
        request_fingerprint: str,
        now: datetime,
    ) -> IdempotencyClaim: ...

    def complete(
        self,
        claim_id: UUID,
        sealed_response: SealedResponse,
        now: datetime,
    ) -> None: ...

    def release(self, claim_id: UUID, now: datetime) -> None: ...
