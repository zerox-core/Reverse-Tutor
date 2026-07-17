from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from adapters.online.ports import (
    IdempotencyClaimInProgress,
    IdempotencyClaimNotFound,
    IdempotencyKeyReused,
    SealedResponse,
)
from online_db.idempotency_store import SqlAlchemyIdempotencyStore
from online_db.models import IdempotencyRecord


NOW = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)


def sealed_response() -> SealedResponse:
    return SealedResponse(
        ciphertext=b"encrypted-response",
        nonce=b"twelve-bytes",
        key_id="fixture-key",
        expires_at=NOW + timedelta(minutes=10),
    )


@pytest.mark.parametrize(
    "scope",
    [
        "auth.bootstrap:device-0123456789",
        f"auth.refresh:{'a' * 64}",
    ],
)
def test_public_auth_claims_persist_without_account(
    postgres_session_factory, scope: str
):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)

    claim = store.claim(scope, "request-1", "sha256:one", NOW)

    assert claim.replayed_response is None
    with postgres_session_factory() as session:
        record = session.scalar(select(IdempotencyRecord))
        assert record is not None
        assert record.account_id is None
        assert record.operation == scope.split(":", 1)[0]


def test_same_fingerprint_replays_only_encrypted_response(postgres_session_factory):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)
    first = store.claim(
        "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
    )
    store.complete(first.claim_id, sealed_response(), NOW)

    replay = store.claim(
        "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
    )

    assert replay.replayed_response == sealed_response()
    with postgres_session_factory() as session:
        record = session.get(IdempotencyRecord, first.claim_id)
        assert record is not None
        assert record.response_ciphertext == b"encrypted-response"
        assert record.response_nonce == b"twelve-bytes"
        assert record.response_key_id == "fixture-key"
        assert record.response_status_code == 201


def test_refresh_idempotency_records_success_as_200(postgres_session_factory):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)
    claim = store.claim(
        f"auth.refresh:{'a' * 64}", "request-1", "sha256:one", NOW
    )

    store.complete(claim.claim_id, sealed_response(), NOW)

    with postgres_session_factory() as session:
        record = session.get(IdempotencyRecord, claim.claim_id)
        assert record is not None
        assert record.response_status_code == 200


def test_pending_or_reused_claim_is_rejected(postgres_session_factory):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)
    store.claim(
        "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
    )

    with pytest.raises(IdempotencyClaimInProgress):
        store.claim(
            "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
        )
    with pytest.raises(IdempotencyKeyReused):
        store.claim(
            "auth.bootstrap:device-0123456789", "request-1", "sha256:two", NOW
        )


def test_expired_pending_claim_can_be_reacquired(postgres_session_factory):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)
    first = store.claim(
        "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
    )

    second = store.claim(
        "auth.bootstrap:device-0123456789",
        "request-1",
        "sha256:one",
        NOW + timedelta(minutes=5),
    )

    assert second.claim_id != first.claim_id
    assert second.replayed_response is None


def test_release_deletes_only_the_current_pending_claim(postgres_session_factory):
    store = SqlAlchemyIdempotencyStore(postgres_session_factory)
    first = store.claim(
        "auth.bootstrap:device-0123456789", "request-1", "sha256:one", NOW
    )

    store.release(first.claim_id, NOW + timedelta(seconds=1))
    second = store.claim(
        "auth.bootstrap:device-0123456789",
        "request-1",
        "sha256:one",
        NOW + timedelta(seconds=2),
    )
    store.complete(second.claim_id, sealed_response(), NOW + timedelta(seconds=3))
    store.release(second.claim_id, NOW + timedelta(seconds=4))

    replay = store.claim(
        "auth.bootstrap:device-0123456789",
        "request-1",
        "sha256:one",
        NOW + timedelta(seconds=5),
    )
    assert replay.replayed_response == sealed_response()


def test_complete_rejects_unknown_claim(postgres_session_factory):
    from uuid import uuid4

    store = SqlAlchemyIdempotencyStore(postgres_session_factory)

    with pytest.raises(IdempotencyClaimNotFound):
        store.complete(uuid4(), sealed_response(), NOW)
