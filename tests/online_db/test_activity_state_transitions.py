from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import URL
from sqlalchemy.exc import IntegrityError

import online_db.models  # noqa: F401
from online_db.activity_store import (
    ActivityDefinition,
    ActivityNotFound,
    ActivityStateTransitionError,
    ActivityTaskDefinition,
    SqlAlchemyActivityStore,
)
from online_db.base import OnlineBase
from online_db.session import build_online_session_factory

NOW = datetime(2026, 9, 24, 8, 0, tzinfo=timezone.utc)


@pytest.fixture
def sqlite_session_factory(tmp_path):
    url = URL.create("sqlite+pysqlite", database=str(tmp_path / "online.sqlite3"))
    factory = build_online_session_factory(url.render_as_string(hide_password=False))
    OnlineBase.metadata.create_all(factory.kw["bind"])
    yield factory
    factory.kw["bind"].dispose()


@pytest.fixture
def store(sqlite_session_factory):
    return SqlAlchemyActivityStore(sqlite_session_factory)


def _definition(slug: str = "teach-back-21") -> ActivityDefinition:
    return ActivityDefinition(
        slug=slug,
        title="21 天给 AI 讲懂一个知识点",
        description="每天把你学到的东西讲给 AI 听。",
        revision=1,
        rule_version=1,
        total_days=2,
        starts_at=NOW,
        ends_at=NOW + timedelta(days=30),
        requires_online_confirmation=True,
        allows_deferred_progress=True,
        tasks=(
            ActivityTaskDefinition(
                day_number=1, title="第 1 天", task_markdown="讲清一个概念"
            ),
            ActivityTaskDefinition(
                day_number=2, title="第 2 天", task_markdown="再讲一个"
            ),
        ),
    )


def test_publish_then_close_then_offline(store):
    created = store.create_activity(_definition(), NOW)
    assert created.state == "scheduled"

    record = store.set_activity_state("teach-back-21", "active", NOW)
    assert record.state == "active"

    record = store.set_activity_state("teach-back-21", "closed", NOW)
    assert record.state == "closed"

    record = store.set_activity_state("teach-back-21", "offline", NOW)
    assert record.state == "offline"
    assert store.get_activity("teach-back-21").state == "offline"


def test_offline_is_idempotent(store):
    store.create_activity(_definition(), NOW)
    store.set_activity_state("teach-back-21", "offline", NOW)
    record = store.set_activity_state("teach-back-21", "offline", NOW)
    assert record.state == "offline"


@pytest.mark.parametrize(
    ("path", "target"),
    [
        ((), "closed"),  # scheduled -> closed 必须经 active
        (("active",), "active"),  # 重复 publish 非幂等
        (("active", "closed"), "active"),  # closed 为冻结终态
        (("offline",), "active"),  # offline 只能再 offline
        (("active", "closed"), "closed"),  # 重复 close 非幂等
    ],
)
def test_illegal_transitions_rejected(store, path, target):
    store.create_activity(_definition(), NOW)
    for step in path:
        store.set_activity_state("teach-back-21", step, NOW)
    with pytest.raises(ActivityStateTransitionError):
        store.set_activity_state("teach-back-21", target, NOW)


def test_missing_activity_raises_not_found(store):
    with pytest.raises(ActivityNotFound):
        store.set_activity_state("missing", "active", NOW)


def test_duplicate_slug_rejected(store):
    store.create_activity(_definition(), NOW)
    with pytest.raises(IntegrityError):
        store.create_activity(_definition(), NOW)
