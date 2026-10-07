from __future__ import annotations

from dataclasses import replace
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from threading import Barrier
from uuid import uuid4

import pytest
from sqlalchemy import func, select

from adapters.online.ports import (
    BootstrapCommand,
    DeviceAlreadyRegistered,
    RefreshTokenReplay,
    RotateRefreshCommand,
)
from online_db.auth_store import SqlAlchemyAuthStore
from online_db.models import AnonymousAccount, AuthAuditEvent, RefreshToken


NOW = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)
FIRST_HASH = "a" * 64
SECOND_HASH = "b" * 64
THIRD_HASH = "c" * 64


def bootstrap_command(*, token_hash: str = FIRST_HASH) -> BootstrapCommand:
    return BootstrapCommand(
        device_id="device-0123456789",
        initial_refresh_token_hash=token_hash,
        now=NOW,
        refresh_expires_at=NOW + timedelta(days=30),
    )


def test_bootstrap_and_rotation_persist_one_identity(postgres_session_factory):
    store = SqlAlchemyAuthStore(postgres_session_factory)
    issued = store.bootstrap(bootstrap_command())

    rotated = store.rotate_refresh(
        RotateRefreshCommand(
            presented_token_hash=FIRST_HASH,
            replacement_token_hash=SECOND_HASH,
            now=NOW + timedelta(minutes=1),
            refresh_expires_at=NOW + timedelta(days=30, minutes=1),
        )
    )

    assert rotated.session.account_id == issued.session.account_id
    assert rotated.session.session_id == issued.session.session_id
    assert rotated.family_id == issued.family_id
    assert rotated.rotation == 1
    with postgres_session_factory() as session:
        assert session.scalar(select(func.count()).select_from(AnonymousAccount)) == 1
        stored = session.scalars(
            select(RefreshToken).order_by(RefreshToken.rotation)
        ).all()
        assert [token.token_hash for token in stored] == [FIRST_HASH, SECOND_HASH]
        assert stored[0].used_at == NOW + timedelta(minutes=1)
        assert stored[0].replaced_by_id == stored[1].id


def test_repeat_bootstrap_rejects_registered_device(postgres_session_factory):
    store = SqlAlchemyAuthStore(postgres_session_factory)
    store.bootstrap(bootstrap_command())

    with pytest.raises(DeviceAlreadyRegistered):
        store.bootstrap(
            replace(
                bootstrap_command(token_hash=SECOND_HASH),
                now=NOW + timedelta(minutes=1),
                refresh_expires_at=NOW + timedelta(days=30, minutes=1),
            )
        )

    with postgres_session_factory() as session:
        assert session.scalar(select(func.count()).select_from(AnonymousAccount)) == 1
        assert session.scalar(select(func.count()).select_from(RefreshToken)) == 1


def test_concurrent_bootstrap_allows_one_device_registration(
    postgres_session_factory,
):
    barrier = Barrier(2)

    def issue(token_hash: str):
        barrier.wait()
        return SqlAlchemyAuthStore(postgres_session_factory).bootstrap(
            bootstrap_command(token_hash=token_hash)
        )

    def capture(token_hash: str):
        try:
            return issue(token_hash)
        except DeviceAlreadyRegistered as exc:
            return exc

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(capture, (FIRST_HASH, SECOND_HASH)))

    assert sum(not isinstance(result, Exception) for result in results) == 1
    assert sum(isinstance(result, DeviceAlreadyRegistered) for result in results) == 1
    with postgres_session_factory() as session:
        assert session.scalar(select(func.count()).select_from(AnonymousAccount)) == 1
        assert session.scalar(select(func.count()).select_from(RefreshToken)) == 1


def test_refresh_replay_commits_family_and_session_revocation(postgres_session_factory):
    store = SqlAlchemyAuthStore(postgres_session_factory)
    issued = store.bootstrap(bootstrap_command())
    command = RotateRefreshCommand(
        presented_token_hash=FIRST_HASH,
        replacement_token_hash=SECOND_HASH,
        now=NOW + timedelta(minutes=1),
        refresh_expires_at=NOW + timedelta(days=30, minutes=1),
    )
    store.rotate_refresh(command)

    with pytest.raises(RefreshTokenReplay):
        store.rotate_refresh(replace(command, replacement_token_hash=THIRD_HASH))

    assert store.active_session(issued.session.session_id, NOW) is None
    with postgres_session_factory() as session:
        family_tokens = session.scalars(
            select(RefreshToken).where(RefreshToken.family_id == issued.family_id)
        ).all()
        assert family_tokens
        assert all(token.revoked_at is not None for token in family_tokens)


def test_revoke_is_owner_scoped_and_audited_once(postgres_session_factory):
    store = SqlAlchemyAuthStore(postgres_session_factory)
    issued = store.bootstrap(bootstrap_command())

    assert not store.revoke_session(uuid4(), issued.session.session_id, NOW)
    assert store.revoke_session(issued.session.account_id, issued.session.session_id, NOW)
    assert store.revoke_session(issued.session.account_id, issued.session.session_id, NOW)
    assert store.active_session(issued.session.session_id, NOW) is None
    assert [item.session_id for item in store.list_sessions(issued.session.account_id)] == [
        issued.session.session_id
    ]

    with postgres_session_factory() as session:
        revoke_audits = session.scalar(
            select(func.count())
            .select_from(AuthAuditEvent)
            .where(AuthAuditEvent.event_type == "session.revoke")
        )
        assert revoke_audits == 1
        details = session.scalars(select(AuthAuditEvent.safe_details)).all()
        assert FIRST_HASH not in repr(details)
