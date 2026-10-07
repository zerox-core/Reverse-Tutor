from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from sqlalchemy import URL

import online_db.models  # noqa: F401
from adapters.online.content_activity_ports import (
    ActivityPortConflict,
    ActivityQuery,
    ActivityWriteCommand,
    ContentFeedQuery,
    LeaderboardQuery,
)
from adapters.online.sqlalchemy_content_activity import (
    SqlAlchemyActivityPort,
    SqlAlchemyPublicContentPort,
)
from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.content_store import ContentItemInput, SqlAlchemyContentStore
from online_db.models import AnonymousAccount
from online_db.session import build_online_session_factory


NOW = datetime(2026, 7, 17, 8, 0, tzinfo=UTC)


@pytest.fixture
def persistent_ports(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    account_id = uuid4()
    with factory() as database, database.begin():
        database.add(
            AnonymousAccount(
                id=account_id,
                status="active",
                created_at=NOW,
                last_seen_at=NOW,
            )
        )

    content_store = SqlAlchemyContentStore(factory)
    content = content_store.create_item(
        ContentItemInput(
            slug="verify-links",
            content_type="public_interest",
            title="Verify links",
            summary="Pause before opening a suspicious link.",
            body_markdown="# Verify links",
            illustration_template="dialogue-security-01",
            illustration_config={"dialogues": ["Safe?"]},
        ),
        NOW,
    )
    content_store.publish(content.id, NOW + timedelta(minutes=1))

    activity_store = SqlAlchemyActivityStore(factory)
    activity_store.create_activity(
        ActivityDefinition(
            slug="focus-week",
            title="Focus Week",
            description="Complete one focused session each day.",
            revision=3,
            rule_version=1,
            total_days=7,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=7),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
            state="active",
        ),
        NOW,
    )

    yield (
        account_id,
        SqlAlchemyPublicContentPort(content_store),
        SqlAlchemyActivityPort(activity_store),
    )
    factory.kw["bind"].dispose()


def test_sqlalchemy_content_port_maps_feed_detail_and_cursor(persistent_ports):
    _, content_port, _ = persistent_ports

    page = content_port.list_published(
        ContentFeedQuery(None, 1, frozenset({"public_interest"}), NOW + timedelta(days=1))
    )
    detail = content_port.get_by_slug("verify-links", NOW + timedelta(days=1))

    assert page.items[0].type == "public_interest"
    assert page.items[0].published_at == NOW + timedelta(minutes=1)
    assert page.version > 0
    assert page.updated_at == NOW + timedelta(minutes=1)
    assert page.next_cursor is None
    assert detail is not None
    assert detail.id == str(detail.id)
    assert detail.body_markdown == "# Verify links"


def test_sqlalchemy_activity_port_maps_lifecycle_conflicts_and_leaderboard(
    persistent_ports,
):
    account_id, _, activity_port = persistent_ports
    account = str(account_id)
    listed = activity_port.list_activities(ActivityQuery(None, 20, NOW))
    assert listed.items[0].id == "focus-week"

    joined = activity_port.join(
        ActivityWriteCommand(
            activity_id="focus-week",
            account_id=account,
            device_id="device-content-0123456789",
            revision=3,
            idempotency_key="join-1",
            at=NOW + timedelta(minutes=1),
        )
    )
    assert joined is not None
    assert joined.user_id == account
    assert joined.revision == 1

    progressed = activity_port.update_progress(
        ActivityWriteCommand(
            activity_id="focus-week",
            account_id=account,
            device_id="device-content-0123456789",
            revision=joined.revision,
            idempotency_key="progress-1",
            at=NOW + timedelta(minutes=2),
            progress=3,
        )
    )
    assert progressed is not None
    assert progressed.progress == 3
    leaderboard = activity_port.leaderboard(
        LeaderboardQuery("focus-week", None, 50, NOW + timedelta(minutes=3))
    )
    assert leaderboard is not None
    assert leaderboard.items[0].progress == 3
    assert not hasattr(leaderboard.items[0], "account_id")

    with pytest.raises(ActivityPortConflict) as idempotency_conflict:
        activity_port.update_progress(
            ActivityWriteCommand(
                activity_id="focus-week",
                account_id=account,
                device_id="device-content-0123456789",
                revision=joined.revision,
                idempotency_key="progress-1",
                at=NOW + timedelta(minutes=3),
                progress=4,
            )
        )
    assert idempotency_conflict.value.code == "idempotency_key_reused"

    with pytest.raises(ActivityPortConflict) as conflict:
        activity_port.update_progress(
            ActivityWriteCommand(
                activity_id="focus-week",
                account_id=account,
                device_id="device-content-0123456789",
                revision=joined.revision,
                idempotency_key="progress-stale",
                at=NOW + timedelta(minutes=4),
                progress=4,
            )
        )
    assert conflict.value.code == "revision_conflict"
