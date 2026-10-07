from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest

from adapters.online.auth_memory import InMemoryAuthStore, InMemoryIdempotencyStore
from adapters.online.ports import (
    BootstrapCommand,
    DeviceAlreadyRegistered,
    IdempotencyKeyReused,
    RefreshTokenReplay,
    RotateRefreshCommand,
    SealedResponse,
)


NOW = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)


def sealed_fixture_response() -> SealedResponse:
    return SealedResponse(
        ciphertext=b"fixture-ciphertext",
        nonce=b"fixture-nonce",
        key_id="fixture-key",
        expires_at=NOW + timedelta(minutes=10),
    )


def test_idempotency_replays_same_fingerprint():
    store = InMemoryIdempotencyStore()
    first = store.claim(
        "auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", NOW
    )
    store.complete(first.claim_id, sealed_fixture_response(), NOW)

    second = store.claim(
        "auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", NOW
    )

    assert second.replayed_response == sealed_fixture_response()


def test_reusing_idempotency_key_with_other_fingerprint_is_rejected():
    store = InMemoryIdempotencyStore()
    store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", NOW)

    with pytest.raises(IdempotencyKeyReused):
        store.claim(
            "auth.bootstrap:device-0123456789",
            "bootstrap-1",
            "sha256:two",
            NOW,
        )


def test_bootstrap_and_refresh_rotation_use_hashes_and_preserve_identity():
    store = InMemoryAuthStore()
    bootstrapped = store.bootstrap(
        BootstrapCommand(
            device_id="device-0123456789",
            initial_refresh_token_hash="hash:first",
            now=NOW,
            refresh_expires_at=NOW + timedelta(days=30),
        )
    )

    rotated = store.rotate_refresh(
        RotateRefreshCommand(
            presented_token_hash="hash:first",
            replacement_token_hash="hash:second",
            now=NOW + timedelta(minutes=1),
            refresh_expires_at=NOW + timedelta(days=30, minutes=1),
        )
    )

    assert rotated.session.account_id == bootstrapped.session.account_id
    assert rotated.session.session_id == bootstrapped.session.session_id
    assert rotated.family_id == bootstrapped.family_id
    assert rotated.rotation == 1


def test_bootstrap_rejects_a_device_that_has_already_registered():
    store = InMemoryAuthStore()
    command = BootstrapCommand(
        device_id="device-0123456789",
        initial_refresh_token_hash="hash:first",
        now=NOW,
        refresh_expires_at=NOW + timedelta(days=30),
    )
    store.bootstrap(command)

    with pytest.raises(DeviceAlreadyRegistered):
        store.bootstrap(replace(command, initial_refresh_token_hash="hash:second"))


def test_refresh_replay_revokes_the_whole_family():
    store = InMemoryAuthStore()
    bootstrapped = store.bootstrap(
        BootstrapCommand(
            device_id="device-0123456789",
            initial_refresh_token_hash="hash:first",
            now=NOW,
            refresh_expires_at=NOW + timedelta(days=30),
        )
    )
    command = RotateRefreshCommand(
        presented_token_hash="hash:first",
        replacement_token_hash="hash:second",
        now=NOW + timedelta(minutes=1),
        refresh_expires_at=NOW + timedelta(days=30, minutes=1),
    )
    store.rotate_refresh(command)

    with pytest.raises(RefreshTokenReplay):
        store.rotate_refresh(replace(command, replacement_token_hash="hash:third"))

    assert store.active_session(bootstrapped.session.session_id, NOW) is None


def test_revoke_is_idempotent_for_owner_and_hides_other_accounts():
    store = InMemoryAuthStore()
    bootstrapped = store.bootstrap(
        BootstrapCommand(
            device_id="device-0123456789",
            initial_refresh_token_hash="hash:first",
            now=NOW,
            refresh_expires_at=NOW + timedelta(days=30),
        )
    )

    assert store.revoke_session(
        bootstrapped.session.account_id, bootstrapped.session.session_id, NOW
    )
    assert store.revoke_session(
        bootstrapped.session.account_id, bootstrapped.session.session_id, NOW
    )
    assert not store.revoke_session(uuid4(), bootstrapped.session.session_id, NOW)


def test_pending_idempotency_claim_can_be_released_after_mutation_failure():
    store = InMemoryIdempotencyStore()
    first = store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", NOW)
    store.release(first.claim_id, NOW)
    second = store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", NOW)
    assert second.replayed_response is None
    assert second.claim_id != first.claim_id
