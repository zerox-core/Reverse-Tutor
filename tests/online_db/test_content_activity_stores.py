from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from sqlalchemy import URL, func, select

import online_db.models  # noqa: F401
from online_db.activity_store import (
    ActivityDefinition,
    ActivityIdempotencyConflict,
    ActivityNotFound,
    ActivityRevisionConflict,
    ActivityTaskDefinition,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.content_store import (
    ContentAssetInput,
    ContentItemInput,
    SqlAlchemyContentStore,
)
from online_db.models import (
    ActivityProgressEvent,
    AnonymousAccount,
)
from online_db.session import build_online_session_factory


NOW = datetime(2026, 7, 17, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def test_content_store_publishes_parseable_content_and_hides_offline_items(
    sqlite_session_factory,
):
    store = SqlAlchemyContentStore(sqlite_session_factory)
    created = store.create_item(
        ContentItemInput(
            slug="verify-links",
            content_type="public_interest",
            title="Verify links",
            summary="Check a link before opening it.",
            body_markdown="# Verify\n\nCheck the source.",
            illustration_template="dialogue-security-01",
            illustration_config={
                "dialogues": ["Is this safe?", "Check the source first."]
            },
            publisher_name="Reverse Tutor",
            sort_order=2,
            assets=(
                ContentAssetInput(
                    role="cover",
                    position=0,
                    url="https://cdn.example/cover.webp",
                    mime_type="image/webp",
                    width=320,
                    height=180,
                    byte_size=20_000,
                    sha256="a" * 64,
                ),
            ),
        ),
        NOW,
    )

    assert created.status == "draft"
    assert store.list_published(NOW) == ()

    published = store.publish(created.id, NOW + timedelta(minutes=1))
    loaded = store.get_by_slug("verify-links")
    assert loaded == published
    assert loaded is not None
    assert loaded.illustration_config["dialogues"][0] == "Is this safe?"
    assert loaded.assets[0].sha256 == "a" * 64
    assert loaded.publish_at.tzinfo == timezone.utc
    assert store.list_published(NOW + timedelta(minutes=2)) == (published,)

    offline = store.take_offline(created.id, NOW + timedelta(minutes=3))
    assert offline.status == "offline"
    assert store.get_by_slug("verify-links", published_only=True) is None
    assert store.get_by_slug("verify-links", published_only=False) == offline
    assert store.list_published(NOW + timedelta(minutes=4)) == ()


def test_content_store_persists_scheduled_publication(sqlite_session_factory):
    store = SqlAlchemyContentStore(sqlite_session_factory)
    created = store.create_item(_content_input("scheduled-content"), NOW)
    publish_at = NOW + timedelta(days=1)

    scheduled = store.schedule(created.id, publish_at, NOW + timedelta(minutes=1))

    assert scheduled.status == "scheduled"
    assert scheduled.publish_at == publish_at
    assert store.list_published(publish_at + timedelta(minutes=1)) == ()


def test_stores_normalize_timezone_aware_inputs_to_utc(sqlite_session_factory):
    china_timezone = timezone(timedelta(hours=8))
    local_now = datetime(2026, 7, 17, 16, 0, tzinfo=china_timezone)
    content_store = SqlAlchemyContentStore(sqlite_session_factory)
    content_store.create_item(_content_input("utc-content"), local_now)

    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity_store.create_activity(
        ActivityDefinition(
            slug="utc-activity",
            title="UTC Activity",
            description="Check UTC storage.",
            revision=1,
            rule_version=1,
            total_days=1,
            starts_at=local_now,
            ends_at=local_now + timedelta(days=1),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
        ),
        local_now,
    )

    created = content_store.get_by_slug("utc-content", published_only=False)
    activity = activity_store.get_activity("utc-activity")
    assert created is not None
    assert activity is not None
    assert created.created_at == NOW
    assert activity.starts_at == NOW
    assert activity.ends_at == NOW + timedelta(days=1)


def test_stores_reject_naive_datetimes(sqlite_session_factory):
    naive_now = NOW.replace(tzinfo=None)
    content_store = SqlAlchemyContentStore(sqlite_session_factory)

    with pytest.raises(ValueError, match="timezone-aware"):
        content_store.create_item(_content_input("naive-content"), naive_now)

    account_id = _create_account(sqlite_session_factory)
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity_store.create_activity(
        ActivityDefinition(
            slug="naive-leave",
            title="Naive Leave",
            description="Reject a naive mutation timestamp.",
            revision=1,
            rule_version=1,
            total_days=1,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=1),
            requires_online_confirmation=False,
            allows_deferred_progress=False,
        ),
        NOW,
    )
    joined = activity_store.join_activity(
        "naive-leave",
        account_id,
        expected_activity_revision=1,
        idempotency_key="join-aware",
        now=NOW,
    )
    with pytest.raises(ValueError, match="timezone-aware"):
        activity_store.leave_activity(
            "naive-leave",
            account_id,
            expected_revision=joined.revision,
            idempotency_key="leave-naive",
            now=naive_now,
        )


def test_activity_definition_tasks_and_participation_lifecycle_are_persisted(
    sqlite_session_factory,
):
    account_id = _create_account(sqlite_session_factory)
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    activity = store.create_activity(
        ActivityDefinition(
            slug="focus-week",
            title="Focus Week",
            description="Complete one focused session each day.",
            revision=3,
            rule_version=2,
            total_days=7,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=7),
            requires_online_confirmation=False,
            allows_deferred_progress=True,
            session_template_id="focus-week-v2",
            public_feedback_summary="Most participants finish five days.",
            tasks=(
                ActivityTaskDefinition(
                    day_number=1,
                    title="Choose a focus",
                    task_markdown="Pick one local study session.",
                    stage_goal="Start",
                ),
                ActivityTaskDefinition(
                    day_number=7,
                    title="Review the week",
                    task_markdown="Review local statistics.",
                    stage_goal="Reflect",
                ),
            ),
        ),
        NOW,
    )

    loaded = store.get_activity("focus-week")
    assert loaded == activity
    assert loaded is not None
    assert loaded.rule_version == 2
    assert loaded.total_days == 7
    assert loaded.tasks[0].stage_goal == "Start"
    assert loaded.starts_at.tzinfo == timezone.utc

    joined = store.join_activity(
        "focus-week",
        account_id,
        expected_activity_revision=3,
        idempotency_key="join-1",
        now=NOW + timedelta(minutes=1),
    )
    replayed_join = store.join_activity(
        "focus-week",
        account_id,
        expected_activity_revision=3,
        idempotency_key="join-1",
        now=NOW + timedelta(minutes=2),
    )
    assert replayed_join == joined

    progressed = store.update_progress(
        "focus-week",
        account_id,
        expected_revision=joined.revision,
        progress=3,
        idempotency_key="progress-1",
        now=NOW + timedelta(minutes=3),
    )
    replayed_progress = store.update_progress(
        "focus-week",
        account_id,
        expected_revision=joined.revision,
        progress=3,
        idempotency_key="progress-1",
        now=NOW + timedelta(minutes=4),
    )
    assert replayed_progress == progressed
    assert progressed.progress == 3
    assert progressed.revision == joined.revision + 1

    with pytest.raises(ActivityIdempotencyConflict):
        store.update_progress(
            "focus-week",
            account_id,
            expected_revision=joined.revision,
            progress=4,
            idempotency_key="progress-1",
            now=NOW + timedelta(minutes=4),
        )
    assert store.list_participations(account_id) == (progressed,)
    assert store.leaderboard("focus-week")[0].account_id == account_id

    left = store.leave_activity(
        "focus-week",
        account_id,
        expected_revision=progressed.revision,
        idempotency_key="leave-1",
        now=NOW + timedelta(minutes=5),
    )
    assert left.state == "left"
    assert left.joined is False
    assert left.progress == 3
    assert left.updated_at.tzinfo == timezone.utc

    with sqlite_session_factory() as database:
        event_count = database.scalar(select(func.count(ActivityProgressEvent.id)))
    assert event_count == 3


def test_activity_store_rejects_stale_revisions_and_unknown_activities(
    sqlite_session_factory,
):
    account_id = _create_account(sqlite_session_factory)
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    store.create_activity(
        ActivityDefinition(
            slug="one-day",
            title="One Day",
            description="One task.",
            revision=2,
            rule_version=1,
            total_days=1,
            starts_at=NOW,
            ends_at=NOW + timedelta(days=1),
            requires_online_confirmation=True,
            allows_deferred_progress=False,
        ),
        NOW,
    )

    with pytest.raises(ActivityRevisionConflict):
        store.join_activity(
            "one-day",
            account_id,
            expected_activity_revision=1,
            idempotency_key="stale-join",
            now=NOW,
        )

    with pytest.raises(ActivityNotFound):
        store.join_activity(
            "missing",
            account_id,
            expected_activity_revision=1,
            idempotency_key="missing-join",
            now=NOW,
        )


def _create_account(sqlite_session_factory):
    account_id = uuid4()
    with sqlite_session_factory() as database, database.begin():
        database.add(
            AnonymousAccount(
                id=account_id,
                status="active",
                created_at=NOW,
                last_seen_at=NOW,
            )
        )
    return account_id


def _content_input(slug: str) -> ContentItemInput:
    return ContentItemInput(
        slug=slug,
        content_type="announcement",
        title="Announcement",
        summary="A short announcement.",
        body_markdown="Body",
        illustration_template="announcement-01",
        illustration_config={"tone": "calm"},
    )
