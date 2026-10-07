"""Tests for the challenge-02 seed and stage ladder."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import URL

from online_db.activity_store import SqlAlchemyActivityStore
from online_db.base import OnlineBase
from online_db.challenge02_days import MODULE_DAYS, load_challenge02_days
from online_db.challenge02_seed import seed_challenge02
from online_db.challenge02_stages import (
    CHALLENGE02_STAGES,
    seed_challenge02_stages,
)
from online_db.challenge02_tasks import (
    CHALLENGE02_SESSION_TEMPLATE_ID,
    CHALLENGE02_SLUG,
    CHALLENGE02_TASKS,
    CHALLENGE02_TOTAL_DAYS,
)
from online_db.session import build_online_session_factory
from online_db.stage_store import SqlAlchemyStageStore

NOW = datetime(2026, 10, 7, 8, 0, tzinfo=timezone.utc)
DAYS_DIR = "activities/challenge-02-gamer-health/days"


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "challenge02.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


def test_days_dir_matches_frozen_tasks():
    days = load_challenge02_days(DAYS_DIR)
    assert len(days) == CHALLENGE02_TOTAL_DAYS == len(CHALLENGE02_TASKS)
    for day, task in zip(days, CHALLENGE02_TASKS):
        assert day.day_number == task["day_number"]
        assert day.title == task["title"]
        assert day.task_markdown == task["task_markdown"]
        assert day.stage_goal == task["stage_goal"]
        assert day.module == task["module"]
        assert day.day_number in MODULE_DAYS[day.module]


def test_seed_challenge02_creates_activity_with_7_tasks(sqlite_session_factory):
    assert seed_challenge02(sqlite_session_factory, NOW) is True

    activity = SqlAlchemyActivityStore(sqlite_session_factory).get_activity(
        CHALLENGE02_SLUG
    )
    assert activity is not None
    assert activity.state == "scheduled"
    assert activity.total_days == CHALLENGE02_TOTAL_DAYS
    assert activity.starts_at == NOW
    assert activity.ends_at == NOW + timedelta(days=CHALLENGE02_TOTAL_DAYS)
    assert activity.requires_online_confirmation is True
    assert activity.allows_deferred_progress is True
    assert activity.session_template_id == CHALLENGE02_SESSION_TEMPLATE_ID
    assert len(activity.tasks) == CHALLENGE02_TOTAL_DAYS
    assert [task.day_number for task in activity.tasks] == list(range(1, 8))
    first = activity.tasks[0]
    assert first.title == CHALLENGE02_TASKS[0]["title"]
    assert first.task_markdown == CHALLENGE02_TASKS[0]["task_markdown"]
    assert first.stage_goal == CHALLENGE02_TASKS[0]["stage_goal"]


def test_seed_challenge02_is_repeatable(sqlite_session_factory):
    assert seed_challenge02(sqlite_session_factory, NOW) is True
    assert seed_challenge02(sqlite_session_factory, NOW) is False

    activity = SqlAlchemyActivityStore(sqlite_session_factory).get_activity(
        CHALLENGE02_SLUG
    )
    assert activity is not None
    assert len(activity.tasks) == CHALLENGE02_TOTAL_DAYS


def test_seed_challenge02_stages(sqlite_session_factory):
    assert seed_challenge02_stages(sqlite_session_factory, NOW) is False

    assert seed_challenge02(sqlite_session_factory, NOW) is True
    assert seed_challenge02_stages(sqlite_session_factory, NOW) is True
    assert seed_challenge02_stages(sqlite_session_factory, NOW) is False

    stages = SqlAlchemyStageStore(sqlite_session_factory).list_activity_stages(
        CHALLENGE02_SLUG
    )
    assert len(stages) == len(CHALLENGE02_STAGES) == 3
    for got, expected in zip(stages, CHALLENGE02_STAGES):
        assert got.stage_index == expected.stage_index
        assert got.name == expected.name
        assert tuple(got.evidence_keys) == tuple(expected.evidence_keys)
        assert tuple(got.task_day_numbers) == tuple(expected.task_day_numbers)
    covered = sorted(
        day for stage in stages for day in stage.task_day_numbers
    )
    assert covered == list(range(1, 8))
