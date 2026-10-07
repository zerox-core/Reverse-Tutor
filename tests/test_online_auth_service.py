from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from adapters.online.auth_memory import InMemoryAuthStore, InMemoryIdempotencyStore
from adapters.online.auth_models import AnonymousBootstrapRequest, RefreshRequest
from adapters.online.auth_service import AuthService
from adapters.online.auth_tokens import AuthSettings, AuthTokenCodec
from adapters.online.errors import OnlineApiError


class Clock:
    def __init__(self) -> None:
        self.now = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)

    def __call__(self) -> datetime:
        return self.now


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def auth_service(clock: Clock) -> AuthService:
    settings = AuthSettings(
        signing_key="test-signing-key-with-at-least-32-bytes",
        refresh_pepper=b"test-refresh-pepper-with-at-least-32-bytes",
        idempotency_sealing_key=b"0123456789abcdef0123456789abcdef",
        issuer="reverse-tutor-test",
        audience="reverse-tutor-native-test",
    )
    return AuthService(
        auth_store=InMemoryAuthStore(),
        idempotency_store=InMemoryIdempotencyStore(),
        token_codec=AuthTokenCodec(settings),
        settings=settings,
        clock=clock,
    )


def bootstrap(service: AuthService):
    return service.bootstrap(
        AnonymousBootstrapRequest(
            deviceId="device-0123456789",
            idempotencyKey="bootstrap-1",
        )
    )


def test_access_token_contains_required_identity_claims_only(auth_service, clock):
    response = bootstrap(auth_service)

    claims = auth_service.token_codec.decode_access(response.access_token, now=clock.now)

    assert claims.subject == response.account_id
    assert claims.session_id == response.session_id
    assert claims.device_id == response.device_id
    assert set(claims.raw) == {"iss", "aud", "sub", "sid", "did", "jti", "iat", "exp"}
    assert "refreshToken" not in claims.raw
    assert response.access_token_expires_at_epoch_millis == int(
        (clock.now + timedelta(minutes=15)).timestamp() * 1000
    )


def test_refresh_rotates_and_different_key_replay_revokes_family(auth_service, clock):
    issued = bootstrap(auth_service)
    rotated = auth_service.refresh(
        RefreshRequest(refreshToken=issued.refresh_token, idempotencyKey="refresh-1")
    )

    assert rotated.refresh_token != issued.refresh_token

    with pytest.raises(OnlineApiError) as replay:
        auth_service.refresh(
            RefreshRequest(refreshToken=issued.refresh_token, idempotencyKey="refresh-2")
        )

    assert replay.value.code == "refresh_token_replay"
    with pytest.raises(OnlineApiError) as inactive:
        auth_service.authenticate(rotated.access_token)
    assert inactive.value.code == "unauthorized"


def test_refresh_retry_with_same_idempotency_key_replays_same_response(auth_service):
    issued = bootstrap(auth_service)
    request = RefreshRequest(
        refreshToken=issued.refresh_token,
        idempotencyKey="refresh-1",
    )

    first = auth_service.refresh(request)
    second = auth_service.refresh(request)

    assert second == first


def test_bootstrap_rejects_changed_request_for_same_idempotency_key(auth_service):
    bootstrap(auth_service)

    with pytest.raises(OnlineApiError) as reused:
        auth_service.bootstrap(
            AnonymousBootstrapRequest(
                deviceId="device-0123456789",
                idempotencyKey="bootstrap-1",
                appVersionCode=2,
            )
        )

    assert reused.value.code == "idempotency_key_reused"


def test_me_sessions_and_revoke_use_authenticated_context(auth_service):
    issued = bootstrap(auth_service)
    context = auth_service.authenticate(issued.access_token)

    me = auth_service.me(context)
    sessions = auth_service.list_sessions(context)
    auth_service.revoke_session(context, context.session_id)

    assert me.account_id == issued.account_id
    assert sessions.items[0].current is True
    with pytest.raises(OnlineApiError) as revoked:
        auth_service.authenticate(issued.access_token)
    assert revoked.value.code == "unauthorized"


def test_production_settings_require_all_secrets():
    with pytest.raises(RuntimeError, match="ONLINE_AUTH_SIGNING_KEY"):
        AuthSettings.from_env({})
