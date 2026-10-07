from __future__ import annotations

from dataclasses import dataclass
from typing import Literal
from uuid import UUID

from pydantic import ConfigDict, Field

from .models import CamelModel


class AnonymousBootstrapRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    device_id: str = Field(min_length=16, max_length=128)
    idempotency_key: str = Field(min_length=1, max_length=128)
    app_version_code: int | None = Field(default=None, ge=1)


class RefreshRequest(CamelModel):
    model_config = ConfigDict(extra="forbid")

    refresh_token: str = Field(min_length=32, max_length=4096)
    idempotency_key: str = Field(min_length=1, max_length=128)


class AuthTokenResponse(CamelModel):
    account_id: UUID
    device_id: str
    session_id: UUID
    token_type: Literal["Bearer"]
    access_token: str
    access_token_expires_at_epoch_millis: int
    refresh_token: str
    refresh_token_expires_at_epoch_millis: int


@dataclass(frozen=True)
class AuthContext:
    account_id: UUID
    device_id: str
    session_id: UUID
    session_expires_at_epoch_millis: int


class AuthMeResponse(CamelModel):
    account_id: UUID
    device_id: str
    session_id: UUID
    account_type: Literal["anonymous"]
    session_expires_at_epoch_millis: int


class AuthSessionResponse(CamelModel):
    session_id: UUID
    device_id: str
    status: Literal["active", "revoked", "expired"]
    created_at_epoch_millis: int
    expires_at_epoch_millis: int
    current: bool


class AuthSessionPage(CamelModel):
    items: list[AuthSessionResponse]
