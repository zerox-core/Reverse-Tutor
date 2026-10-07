"""Seed the second Chinese challenge activity (challenge-02: gamer health, 7 days).

Idempotent: skips when the slug already exists; never overwrites
operator-managed rows. Task content comes from the frozen module
online_db/challenge02_tasks.py (regenerate via
`py -m scripts.build_challenge02_tasks`).
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
from online_db.challenge02_tasks import (
    CHALLENGE02_DESCRIPTION,
    CHALLENGE02_SESSION_TEMPLATE_ID,
    CHALLENGE02_SLUG,
    CHALLENGE02_TASKS,
    CHALLENGE02_TITLE,
    CHALLENGE02_TOTAL_DAYS,
)
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory
from online_db.settings import OnlineDatabaseSettings


def seed_challenge02(session_factory: sessionmaker[Session], now: datetime) -> bool:
    """Create the challenge-02 activity with its 7 daily tasks.

    Returns True when the activity was created, False when it already existed.
    Initial state is ``scheduled``; operators publish it via the admin API.
    """
    store = SqlAlchemyActivityStore(session_factory)
    if store.get_activity(CHALLENGE02_SLUG) is not None:
        return False
    try:
        store.create_activity(
            ActivityDefinition(
                slug=CHALLENGE02_SLUG,
                title=CHALLENGE02_TITLE,
                description=CHALLENGE02_DESCRIPTION,
                revision=1,
                rule_version=1,
                total_days=CHALLENGE02_TOTAL_DAYS,
                starts_at=now,
                ends_at=now + timedelta(days=CHALLENGE02_TOTAL_DAYS),
                requires_online_confirmation=True,
                allows_deferred_progress=True,
                state="scheduled",
                session_template_id=CHALLENGE02_SESSION_TEMPLATE_ID,
                tasks=tuple(
                    ActivityTaskDefinition(
                        day_number=task["day_number"],
                        title=task["title"],
                        task_markdown=task["task_markdown"],
                        stage_goal=task["stage_goal"],
                    )
                    for task in CHALLENGE02_TASKS
                ),
            ),
            now,
        )
    except IntegrityError:
        return False
    return True


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed the challenge-02 gamer-health activity"
    )
    parser.parse_args(argv)

    database_url = OnlineDatabaseSettings.from_env().database_url
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    created = seed_challenge02(session_factory, datetime.now(timezone.utc))
    print(
        "Challenge-02 seed complete: "
        f"activity {'created' if created else 'skipped (already exists)'}; "
        f"slug={CHALLENGE02_SLUG} tasks={len(CHALLENGE02_TASKS)}"
    )
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
