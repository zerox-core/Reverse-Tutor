"""Seed the first Chinese challenge activity (challenge-01: agent app dev, 17 days).

Idempotent: skips when the slug already exists; never overwrites
operator-managed rows. Task content comes from the frozen module
online_db/challenge01_tasks.py (regenerate via
`py -m scripts.build_challenge01_tasks`).
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
from typing import Sequence

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import (
    ActivityDefinition,
    ActivityTaskDefinition,
    SqlAlchemyActivityStore,
)
from online_db.challenge01_tasks import (
    CHALLENGE01_DESCRIPTION,
    CHALLENGE01_SESSION_TEMPLATE_ID,
    CHALLENGE01_SLUG,
    CHALLENGE01_TASKS,
    CHALLENGE01_TITLE,
    CHALLENGE01_TOTAL_DAYS,
)
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory
from online_db.settings import OnlineDatabaseSettings


def seed_challenge01(session_factory: sessionmaker[Session], now: datetime) -> bool:
    """Create the challenge-01 activity with its 17 daily tasks.

    Returns True when the activity was created, False when it already existed.
    Initial state is ``scheduled``; operators publish it via the admin API.
    """
    store = SqlAlchemyActivityStore(session_factory)
    if store.get_activity(CHALLENGE01_SLUG) is not None:
        return False
    try:
        store.create_activity(
            ActivityDefinition(
                slug=CHALLENGE01_SLUG,
                title=CHALLENGE01_TITLE,
                description=CHALLENGE01_DESCRIPTION,
                revision=1,
                rule_version=1,
                total_days=CHALLENGE01_TOTAL_DAYS,
                starts_at=now,
                ends_at=now + timedelta(days=CHALLENGE01_TOTAL_DAYS),
                requires_online_confirmation=True,
                allows_deferred_progress=True,
                state="scheduled",
                session_template_id=CHALLENGE01_SESSION_TEMPLATE_ID,
                tasks=tuple(
                    ActivityTaskDefinition(
                        day_number=task["day_number"],
                        title=task["title"],
                        task_markdown=task["task_markdown"],
                        stage_goal=task["stage_goal"],
                    )
                    for task in CHALLENGE01_TASKS
                ),
            ),
            now,
        )
    except IntegrityError:
        return False
    return True


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed the challenge-01 agent-app-dev activity"
    )
    parser.parse_args(argv)

    database_url = OnlineDatabaseSettings.from_env().database_url
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    created = seed_challenge01(session_factory, datetime.now(timezone.utc))
    print(
        "Challenge-01 seed complete: "
        f"activity {'created' if created else 'skipped (already exists)'}; "
        f"slug={CHALLENGE01_SLUG} tasks={len(CHALLENGE01_TASKS)}"
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
