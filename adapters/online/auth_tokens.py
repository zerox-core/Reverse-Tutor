from __future__ import annotations

import hashlib
import hmac
import os
import secrets
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Mapping
from uuid import UUID, uuid4

import jwt

from .ports import AuthSessionRecord


class AccessTokenInvalid(Exception):
    pass


@dataclass(frozen=True)
class AuthSettings:
    signing_key: str
    refresh_pepper: bytes
    idempotency_sealing_key: bytes
    issuer: str = "reverse-tutor-online"
    audience: str = "reverse-tutor-native"
    access_lifetime: timedelta = field(default_factory=lambda: timedelta(minutes=15))
    refresh_lifetime: timedelta = field(default_factory=lambda: timedelta(days=30))

    def __post_init__(self) -> None:
        if len(self.signing_key.encode("utf-8")) < 32:
            raise ValueError("Auth signing key must contain at least 32 bytes")
        if len(self.refresh_pepper) < 32:
            raise ValueError("Refresh pepper must contain at least 32 bytes")
        if len(self.idempotency_sealing_key) != 32:
            raise ValueError("Idempotency sealing key must contain exactly 32 bytes")

    @classmethod
    def from_env(cls, environ: Mapping[str, str] | None = None) -> "AuthSettings":
        values = os.environ if environ is None else environ
        signing_key = values.get("ONLINE_AUTH_SIGNING_KEY")
        refresh_pepper = values.get("ONLINE_AUTH_REFRESH_PEPPER")
        sealing_secret = values.get("ONLINE_AUTH_IDEMPOTENCY_SEALING_KEY")
        missing = [
            name
            for name, value in (
                ("ONLINE_AUTH_SIGNING_KEY", signing_key),
                ("ONLINE_AUTH_REFRESH_PEPPER", refresh_pepper),
                ("ONLINE_AUTH_IDEMPOTENCY_SEALING_KEY", sealing_secret),
            )
            if not value
        ]
        if missing:
            raise RuntimeError(f"Missing required auth secret: {', '.join(missing)}")
        return cls(
            signing_key=signing_key,
            refresh_pepper=refresh_pepper.encode("utf-8"),
            idempotency_sealing_key=hashlib.sha256(
                sealing_secret.encode("utf-8")
            ).digest(),
            issuer=values.get("ONLINE_AUTH_ISSUER", "reverse-tutor-online"),
            audience=values.get("ONLINE_AUTH_AUDIENCE", "reverse-tutor-native"),
            access_lifetime=timedelta(
                seconds=int(values.get("ONLINE_AUTH_ACCESS_TTL_SECONDS", "900"))
            ),
            refresh_lifetime=timedelta(
                seconds=int(values.get("ONLINE_AUTH_REFRESH_TTL_SECONDS", "2592000"))
            ),
        )

    @classmethod
    def ephemeral_memory(cls) -> "AuthSettings":
        return cls(
            signing_key=secrets.token_urlsafe(48),
            refresh_pepper=secrets.token_bytes(32),
            idempotency_sealing_key=secrets.token_bytes(32),
        )


@dataclass(frozen=True)
class AccessClaims:
    subject: UUID
    session_id: UUID
    device_id: str
    raw: dict[str, object]


class AuthTokenCodec:
    def __init__(self, settings: AuthSettings) -> None:
        self.settings = settings

    def encode_access(
        self, session: AuthSessionRecord, now: datetime
    ) -> tuple[str, datetime]:
        expires_at = now + self.settings.access_lifetime
        payload = {
            "iss": self.settings.issuer,
            "aud": self.settings.audience,
            "sub": str(session.account_id),
            "sid": str(session.session_id),
            "did": session.device_id,
            "jti": str(uuid4()),
            "iat": int(now.timestamp()),
            "exp": int(expires_at.timestamp()),
        }
        return (
            jwt.encode(payload, self.settings.signing_key, algorithm="HS256"),
            expires_at,
        )

    def decode_access(self, token: str, *, now: datetime) -> AccessClaims:
        try:
            payload = jwt.decode(
                token,
                self.settings.signing_key,
                algorithms=["HS256"],
                audience=self.settings.audience,
                issuer=self.settings.issuer,
                options={
                    "require": ["iss", "aud", "sub", "sid", "did", "jti", "iat", "exp"],
                    "verify_exp": False,
                    "verify_iat": False,
                },
            )
            if int(payload["exp"]) <= int(now.timestamp()):
                raise AccessTokenInvalid("Access token is expired")
            if int(payload["iat"]) > int(now.timestamp()) + 60:
                raise AccessTokenInvalid("Access token issued-at is in the future")
            return AccessClaims(
                subject=UUID(str(payload["sub"])),
                session_id=UUID(str(payload["sid"])),
                device_id=str(payload["did"]),
                raw=dict(payload),
            )
        except AccessTokenInvalid:
            raise
        except (jwt.PyJWTError, KeyError, TypeError, ValueError) as exc:
            raise AccessTokenInvalid("Access token is invalid") from exc

    def generate_refresh_token(self) -> str:
        return secrets.token_urlsafe(48)

    def hash_refresh_token(self, token: str) -> str:
        return hmac.new(
            self.settings.refresh_pepper,
            token.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()


def utc_now() -> datetime:
    return datetime.now(timezone.utc)
