from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from sqlalchemy import URL

from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.challenge01_days import load_challenge01_days
from online_db.challenge01_seed import seed_challenge01
from online_db.challenge01_tasks import (
    CHALLENGE01_SLUG,
    CHALLENGE01_TASKS,
    CHALLENGE01_TOTAL_DAYS,
)
from online_db.session import build_online_session_factory

NOW = datetime(2026, 9, 25, 8, 0, tzinfo=timezone.utc)
DAYS_DIR = Path("activities/challenge-01-agent-app-dev/days")


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "challenge01.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def test_seed_challenge01_creates_activity_with_17_tasks(sqlite_session_factory):
    created = seed_challenge01(sqlite_session_factory, NOW)

    assert created is True
    activity = SqlAlchemyActivityStore(sqlite_session_factory).get_activity(
        CHALLENGE01_SLUG
    )
    assert activity is not None
    assert activity.state == "scheduled"
    assert activity.total_days == CHALLENGE01_TOTAL_DAYS
    assert activity.starts_at == NOW
    assert activity.ends_at == NOW + timedelta(days=CHALLENGE01_TOTAL_DAYS)
    assert activity.requires_online_confirmation is True
    assert activity.allows_deferred_progress is True
    assert activity.session_template_id == "challenge-agent-app-dev-17d-v1"
    assert len(activity.tasks) == CHALLENGE01_TOTAL_DAYS
    assert [task.day_number for task in activity.tasks] == list(range(1, 18))
    first = activity.tasks[0]
    assert first.title == CHALLENGE01_TASKS[0]["title"]
    assert first.task_markdown == CHALLENGE01_TASKS[0]["task_markdown"]
    assert first.stage_goal == CHALLENGE01_TASKS[0]["stage_goal"]
    for task in activity.tasks:
        assert task.title
        assert task.task_markdown
        assert task.stage_goal


def test_seed_challenge01_is_repeatable(sqlite_session_factory):
    assert seed_challenge01(sqlite_session_factory, NOW) is True
    assert seed_challenge01(sqlite_session_factory, NOW) is False

    activity = SqlAlchemyActivityStore(sqlite_session_factory).get_activity(
        CHALLENGE01_SLUG
    )
    assert activity is not None
    assert len(activity.tasks) == CHALLENGE01_TOTAL_DAYS


def test_seed_challenge01_never_overwrites_existing_slug(sqlite_session_factory):
    store = SqlAlchemyActivityStore(sqlite_session_factory)
    existing = store.create_activity(
        ActivityDefinition(
            slug=CHALLENGE01_SLUG,
            title="Operator-managed challenge",
            description="Keep this challenge unchanged.",
            revision=4,
            rule_version=3,
            total_days=7,
            starts_at=NOW - timedelta(days=2),
            ends_at=NOW + timedelta(days=5),
            requires_online_confirmation=False,
            allows_deferred_progress=False,
            state="active",
        ),
        NOW - timedelta(days=2),
    )

    assert seed_challenge01(sqlite_session_factory, NOW) is False
    assert store.get_activity(CHALLENGE01_SLUG) == existing


def test_frozen_tasks_match_day_markdown_files():
    days = load_challenge01_days(DAYS_DIR)
    assert len(days) == CHALLENGE01_TOTAL_DAYS
    assert len(CHALLENGE01_TASKS) == CHALLENGE01_TOTAL_DAYS
    for day, frozen in zip(days, CHALLENGE01_TASKS):
        assert frozen["day_number"] == day.day_number
        assert frozen["title"] == day.title
        assert frozen["task_markdown"] == day.task_markdown
        assert frozen["stage_goal"] == day.stage_goal


def test_load_challenge01_days_rejects_bad_front_matter(tmp_path):
    bad = tmp_path / "days"
    bad.mkdir()
    (bad / "day-01.md").write_text("---\ntitle: 缺字段\n---\n正文\n", encoding="utf-8")
    with pytest.raises(ValueError, match="missing"):
        load_challenge01_days(bad)
