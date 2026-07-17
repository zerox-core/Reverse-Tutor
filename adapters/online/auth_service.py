from __future__ import annotations

import hashlib
import json
import secrets
from collections.abc import Callable
from datetime import datetime
from uuid import UUID

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

from .auth_models import (
    AnonymousBootstrapRequest,
    AuthContext,
    AuthMeResponse,
    AuthSessionPage,
    AuthSessionResponse,
    AuthTokenResponse,
    RefreshRequest,
)
from .auth_tokens import AccessTokenInvalid, AuthSettings, AuthTokenCodec, utc_now
from .errors import OnlineApiError
from .ports import (
    AuthSessionRecord,
    AuthStore,
    DeviceAlreadyRegistered,
    BootstrapCommand,
    IdempotencyClaimInProgress,
    IdempotencyKeyReused,
    IdempotencyStore,
    RefreshTokenInvalid,
    RefreshTokenReplay,
    RotateRefreshCommand,
    SealedResponse,
)


def _epoch_millis(value: datetime) -> int:
    return int(value.timestamp() * 1000)


class AuthService:
    def __init__(
        self,
        *,
        auth_store: AuthStore,
        idempotency_store: IdempotencyStore,
        token_codec: AuthTokenCodec,
        settings: AuthSettings,
        clock: Callable[[], datetime] = utc_now,
    ) -> None:
        self.auth_store = auth_store
        self.idempotency_store = idempotency_store
        self.token_codec = token_codec
        self.settings = settings
        self.clock = clock
        self._sealer = AESGCM(settings.idempotency_sealing_key)
        self._sealing_key_id = "slice0-memory-v1"

    def bootstrap(self, request: AnonymousBootstrapRequest) -> AuthTokenResponse:
        now = self.clock()
        claim = self._claim(
            scope=f"auth.bootstrap:{request.device_id}",
            key=request.idempotency_key,
            fingerprint=self._fingerprint(
                {"deviceId": request.device_id, "appVersionCode": request.app_version_code}
            ),
            now=now,
        )
        if claim.replayed_response is not None:
            return self._unseal_response(claim.replayed_response)

        refresh_token = self.token_codec.generate_refresh_token()
        refresh_expires_at = now + self.settings.refresh_lifetime
        try:
            result = self.auth_store.bootstrap(
                BootstrapCommand(
                    device_id=request.device_id,
                    initial_refresh_token_hash=self.token_codec.hash_refresh_token(
                        refresh_token
                    ),
                    now=now,
                    refresh_expires_at=refresh_expires_at,
                )
            )
        except DeviceAlreadyRegistered as exc:
            self._release_claim(claim.claim_id, now)
            raise OnlineApiError(
                409,
                "device_already_registered",
                "Device is already registered",
                user_action="none",
            ) from exc
        except Exception:
            # The mutation did not return, so no response can be replayed from
            # this claim.  Release only the pending claim; a failed complete()
            # below must remain pending for the atomic store to reconcile.
            self._release_claim(claim.claim_id, now)
            raise
        response = self._issue_response(result.session, refresh_token, now)
        self.idempotency_store.complete(
            claim.claim_id,
            self._seal_response(response, refresh_expires_at),
            now,
        )
        return response

    def refresh(self, request: RefreshRequest) -> AuthTokenResponse:
        now = self.clock()
        presented_hash = self.token_codec.hash_refresh_token(request.refresh_token)
        claim = self._claim(
            scope=f"auth.refresh:{presented_hash}",
            key=request.idempotency_key,
            fingerprint=self._fingerprint({"refreshTokenHash": presented_hash}),
            now=now,
        )
        if claim.replayed_response is not None:
            return self._unseal_response(claim.replayed_response)

        replacement_token = self.token_codec.generate_refresh_token()
        refresh_expires_at = now + self.settings.refresh_lifetime
        try:
            result = self.auth_store.rotate_refresh(
                RotateRefreshCommand(
                    presented_token_hash=presented_hash,
                    replacement_token_hash=self.token_codec.hash_refresh_token(
                        replacement_token
                    ),
                    now=now,
                    refresh_expires_at=refresh_expires_at,
                )
            )
        except RefreshTokenReplay as exc:
            self._release_claim(claim.claim_id, now)
            raise OnlineApiError(
                401,
                "refresh_token_replay",
                "Refresh token replay detected",
                user_action="bootstrap_anonymous",
            ) from exc
        except RefreshTokenInvalid as exc:
            self._release_claim(claim.claim_id, now)
            raise OnlineApiError(
                401,
                "invalid_refresh_token",
                "Refresh token is invalid or expired",
                user_action="bootstrap_anonymous",
            ) from exc
        except Exception:
            self._release_claim(claim.claim_id, now)
            raise

        response = self._issue_response(result.session, replacement_token, now)
        self.idempotency_store.complete(
            claim.claim_id,
            self._seal_response(response, refresh_expires_at),
            now,
        )
        return response

    def authenticate(self, access_token: str) -> AuthContext:
        now = self.clock()
        try:
            claims = self.token_codec.decode_access(access_token, now=now)
        except AccessTokenInvalid as exc:
            raise self._unauthorized() from exc

        session = self.auth_store.active_session(claims.session_id, now)
        if (
            session is None
            or session.account_id != claims.subject
            or session.device_id != claims.device_id
        ):
            raise self._unauthorized()
        return AuthContext(
            account_id=session.account_id,
            device_id=session.device_id,
            session_id=session.session_id,
            session_expires_at_epoch_millis=_epoch_millis(session.expires_at),
        )

    def me(self, context: AuthContext) -> AuthMeResponse:
        return AuthMeResponse(
            account_id=context.account_id,
            device_id=context.device_id,
            session_id=context.session_id,
            account_type="anonymous",
            session_expires_at_epoch_millis=context.session_expires_at_epoch_millis,
        )

    def list_sessions(self, context: AuthContext) -> AuthSessionPage:
        now = self.clock()
        return AuthSessionPage(
            items=[
                self._session_response(
                    session,
                    current=session.session_id == context.session_id,
                    now=now,
                )
                for session in self.auth_store.list_sessions(context.account_id)
            ]
        )

    def revoke_session(self, context: AuthContext, session_id: UUID) -> None:
        if not self.auth_store.revoke_session(
            context.account_id, session_id, self.clock()
        ):
            raise OnlineApiError(404, "session_not_found", "Session not found")

    def close(self) -> None:
        for store in (self.auth_store, self.idempotency_store):
            close = getattr(store, "close", None)
            if callable(close):
                close()

    def _issue_response(
        self,
        session: AuthSessionRecord,
        refresh_token: str,
        now: datetime,
    ) -> AuthTokenResponse:
        access_token, access_expires_at = self.token_codec.encode_access(session, now)
        return AuthTokenResponse(
            account_id=session.account_id,
            device_id=session.device_id,
            session_id=session.session_id,
            token_type="Bearer",
            access_token=access_token,
            access_token_expires_at_epoch_millis=_epoch_millis(access_expires_at),
            refresh_token=refresh_token,
            refresh_token_expires_at_epoch_millis=_epoch_millis(session.expires_at),
        )

    def _claim(self, *, scope: str, key: str, fingerprint: str, now: datetime):
        try:
            return self.idempotency_store.claim(scope, key, fingerprint, now)
        except IdempotencyKeyReused as exc:
            raise OnlineApiError(
                409,
                "idempotency_key_reused",
                "Idempotency key was reused with a different request",
            ) from exc
        except IdempotencyClaimInProgress as exc:
            raise OnlineApiError(
                409,
                "idempotency_in_progress",
                "Idempotent request is still in progress",
                retryable=True,
                user_action="retry",
            ) from exc

    def _release_claim(self, claim_id: UUID, now: datetime) -> None:
        # Slice 0 keeps this optional so older injected stores remain usable;
        # production stores implement the same port method transactionally.
        release = getattr(self.idempotency_store, "release", None)
        if callable(release):
            try:
                release(claim_id, now)
            except Exception:
                # Never mask the original auth mutation failure.
                pass

    def _seal_response(
        self, response: AuthTokenResponse, expires_at: datetime
    ) -> SealedResponse:
        nonce = secrets.token_bytes(12)
        plaintext = response.model_dump_json(by_alias=True).encode("utf-8")
        ciphertext = self._sealer.encrypt(
            nonce, plaintext, self._sealing_key_id.encode("ascii")
        )
        return SealedResponse(
            ciphertext=ciphertext,
            nonce=nonce,
            key_id=self._sealing_key_id,
            expires_at=expires_at,
        )

    def _unseal_response(self, sealed: SealedResponse) -> AuthTokenResponse:
        if sealed.key_id != self._sealing_key_id:
            raise OnlineApiError(500, "server_error", "Internal server error")
        try:
            plaintext = self._sealer.decrypt(
                sealed.nonce,
                sealed.ciphertext,
                sealed.key_id.encode("ascii"),
            )
            return AuthTokenResponse.model_validate_json(plaintext)
        except (InvalidTag, ValueError, TypeError) as exc:
            raise OnlineApiError(500, "server_error", "Internal server error") from exc

    @staticmethod
    def _fingerprint(value: dict[str, object]) -> str:
        encoded = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return f"sha256:{hashlib.sha256(encoded).hexdigest()}"

    @staticmethod
    def _session_response(
        session: AuthSessionRecord, *, current: bool, now: datetime
    ) -> AuthSessionResponse:
        status = session.status
        if status == "active" and session.expires_at <= now:
            status = "expired"
        return AuthSessionResponse(
            session_id=session.session_id,
            device_id=session.device_id,
            status=status,
            created_at_epoch_millis=_epoch_millis(session.created_at),
            expires_at_epoch_millis=_epoch_millis(session.expires_at),
            current=current,
        )

    @staticmethod
    def _unauthorized() -> OnlineApiError:
        return OnlineApiError(
            401,
            "unauthorized",
            "Authentication is required",
            user_action="bootstrap_anonymous",
        )
