from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import URL

import online_db.models  # noqa: F401
from adapters.online.ports import (
    BootstrapCommand,
    IdempotencyClaimInProgress,
    RotateRefreshCommand,
    SealedResponse,
)
from online_db.auth_store import SqlAlchemyAuthStore
from online_db.base import OnlineBase
from online_db.idempotency_store import SqlAlchemyIdempotencyStore
from online_db.session import build_online_session_factory


NOW = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def test_auth_store_normalizes_sqlite_datetimes_to_utc(sqlite_session_factory):
    store = SqlAlchemyAuthStore(sqlite_session_factory)
    issued = store.bootstrap(
        BootstrapCommand(
            device_id="device-0123456789",
            initial_refresh_token_hash="a" * 64,
            now=NOW,
            refresh_expires_at=NOW + timedelta(days=30),
        )
    )

    rotated = store.rotate_refresh(
        RotateRefreshCommand(
            presented_token_hash="a" * 64,
            replacement_token_hash="b" * 64,
            now=NOW + timedelta(minutes=1),
            refresh_expires_at=NOW + timedelta(days=30, minutes=1),
        )
    )

    active = store.active_session(issued.session.session_id, NOW + timedelta(minutes=2))
    listed = store.list_sessions(issued.session.account_id)
    assert active is not None
    assert rotated.session.expires_at.tzinfo == timezone.utc
    assert active.created_at.tzinfo == timezone.utc
    assert listed[0].expires_at.tzinfo == timezone.utc


def test_idempotency_store_normalizes_sqlite_datetimes_and_lease(
    sqlite_session_factory,
):
    store = SqlAlchemyIdempotencyStore(sqlite_session_factory)
    first = store.claim("auth.bootstrap:device", "request-1", "sha256:one", NOW)

    with pytest.raises(IdempotencyClaimInProgress):
        store.claim(
            "auth.bootstrap:device",
            "request-1",
            "sha256:one",
            NOW + timedelta(seconds=29),
        )

    second = store.claim(
        "auth.bootstrap:device",
        "request-1",
        "sha256:one",
        NOW + timedelta(seconds=30),
    )
    response = SealedResponse(
        ciphertext=b"ciphertext",
        nonce=b"twelve-bytes",
        key_id="fixture",
        expires_at=NOW + timedelta(minutes=20),
    )
    store.complete(second.claim_id, response, NOW + timedelta(seconds=31))

    replay = store.claim(
        "auth.bootstrap:device",
        "request-1",
        "sha256:one",
        NOW + timedelta(seconds=32),
    )
    assert replay.replayed_response == response
    assert replay.replayed_response.expires_at.tzinfo == timezone.utc
    store.release(first.claim_id, NOW + timedelta(seconds=33))
