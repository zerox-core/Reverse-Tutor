from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import URL

from online_db import catalog_seed
from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.catalog_seed import CatalogSeedResult, seed_online_catalog
from online_db.content_store import ContentItemInput, SqlAlchemyContentStore
from online_db.session import build_online_session_factory


NOW = datetime(2026, 7, 18, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "catalog.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def test_seed_online_catalog_is_repeatable(sqlite_session_factory):
    first = seed_online_catalog(sqlite_session_factory, NOW)
    second = seed_online_catalog(sqlite_session_factory, NOW)

    assert first.created_content == 1
    assert first.created_activities == 1
    assert second.created_content == 0
    assert second.created_activities == 0

    content = SqlAlchemyContentStore(sqlite_session_factory).get_by_slug(
        "verify-before-opening-links"
    )
    activity = SqlAlchemyActivityStore(sqlite_session_factory).get_activity(
        "python-21-day-challenge"
    )
    assert content is not None
    assert content.status == "published"
    assert content.publish_at == NOW
    assert activity is not None
    assert activity.state == "active"
    assert activity.total_days == 21
    assert activity.starts_at == NOW
    assert activity.ends_at == NOW + timedelta(days=21)
    assert activity.requires_online_confirmation is True
    assert activity.allows_deferred_progress is True


def test_seed_online_catalog_never_overwrites_existing_slugs(
    sqlite_session_factory,
):
    content_store = SqlAlchemyContentStore(sqlite_session_factory)
    existing_content = content_store.create_item(
        ContentItemInput(
            slug="verify-before-opening-links",
            content_type="announcement",
            title="Operator-managed draft",
            summary="Keep this draft unchanged.",
            body_markdown="Operator content",
            illustration_template="operator-template",
            illustration_config={"owner": "operator"},
        ),
        NOW - timedelta(days=1),
    )
    activity_store = SqlAlchemyActivityStore(sqlite_session_factory)
    existing_activity = activity_store.create_activity(
        ActivityDefinition(
            slug="python-21-day-challenge",
            title="Operator-managed challenge",
            description="Keep this challenge unchanged.",
            revision=4,
            rule_version=3,
            total_days=7,
            starts_at=NOW - timedelta(days=2),
            ends_at=NOW + timedelta(days=5),
            requires_online_confirmation=False,
            allows_deferred_progress=False,
            state="scheduled",
        ),
        NOW - timedelta(days=2),
    )

    result = seed_online_catalog(sqlite_session_factory, NOW)

    assert result.created_content == 0
    assert result.created_activities == 0
    assert content_store.get_by_slug(
        "verify-before-opening-links", published_only=False
    ) == existing_content
    assert activity_store.get_activity("python-21-day-challenge") == existing_activity


def test_seed_cli_checks_schema_before_building_factory_and_prints_counts(
    monkeypatch,
    capsys,
):
    database_url = "postgresql+psycopg://catalog.example/reverse_tutor"
    events = []
    factory = object()
    monkeypatch.setenv("ONLINE_DATABASE_URL", database_url)
    monkeypatch.setattr(
        catalog_seed,
        "assert_online_schema_at_head",
        lambda value: events.append(("check", value)),
        raising=False,
    )
    monkeypatch.setattr(
        catalog_seed,
        "build_online_session_factory",
        lambda value: events.append(("build", value)) or factory,
        raising=False,
    )
    monkeypatch.setattr(
        catalog_seed,
        "seed_online_catalog",
        lambda value, now: events.append(("seed", value))
        or CatalogSeedResult(created_content=1, created_activities=0),
    )

    assert catalog_seed.main([]) == 0

    assert events == [
        ("check", database_url),
        ("build", database_url),
        ("seed", factory),
    ]
    assert capsys.readouterr().out.strip() == (
        "Catalog seed complete: content created=1 skipped=0; "
        "activities created=0 skipped=1"
    )
