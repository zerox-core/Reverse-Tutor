from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Sequence

from sqlalchemy.orm import Session, sessionmaker

from online_db.activity_store import ActivityDefinition, SqlAlchemyActivityStore
from online_db.content_store import ContentItemInput, SqlAlchemyContentStore
from online_db.schema_check import assert_online_schema_at_head
from online_db.session import build_online_session_factory
from online_db.settings import OnlineDatabaseSettings


CONTENT_SLUG = "verify-before-opening-links"
ACTIVITY_SLUG = "python-21-day-challenge"


@dataclass(frozen=True)
class CatalogSeedResult:
    created_content: int
    created_activities: int


def seed_online_catalog(
    session_factory: sessionmaker[Session], now: datetime
) -> CatalogSeedResult:
    content_store = SqlAlchemyContentStore(session_factory)
    created_content = 0
    if content_store.get_by_slug(CONTENT_SLUG, published_only=False) is None:
        content = content_store.create_item(
            ContentItemInput(
                slug=CONTENT_SLUG,
                content_type="public_interest",
                title="Verify links before opening them",
                summary="Three checks for suspicious links.",
                body_markdown=(
                    "# Check the source\n\n"
                    "Pause before opening a link. Verify the sender, inspect the "
                    "domain, and avoid sharing information the page does not need."
                ),
                illustration_template="dialogue-security-01",
                illustration_config={
                    "dialogues": [
                        "Is this link safe?",
                        "Check its source before opening it.",
                    ],
                    "palette": "cool-blue-amber",
                },
                publisher_name="Reverse Tutor",
            ),
            now,
        )
        content_store.publish(content.id, now)
        created_content = 1

    activity_store = SqlAlchemyActivityStore(session_factory)
    created_activities = 0
    if activity_store.get_activity(ACTIVITY_SLUG) is None:
        activity_store.create_activity(
            ActivityDefinition(
                slug=ACTIVITY_SLUG,
                title="21-day Python learning challenge",
                description=(
                    "Build a consistent Python learning rhythm with one daily "
                    "challenge for 21 days."
                ),
                revision=1,
                rule_version=1,
                total_days=21,
                starts_at=now,
                ends_at=now + timedelta(days=21),
                requires_online_confirmation=True,
                allows_deferred_progress=True,
                state="active",
                session_template_id="challenge-python-21-days-v1",
            ),
            now,
        )
        created_activities = 1

    return CatalogSeedResult(
        created_content=created_content,
        created_activities=created_activities,
    )


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Seed the initial PostgreSQL online catalog"
    )
    parser.parse_args(argv)

    database_url = OnlineDatabaseSettings.from_env().database_url
    assert_online_schema_at_head(database_url)
    session_factory = build_online_session_factory(database_url)
    result = seed_online_catalog(session_factory, datetime.now(timezone.utc))
    print(
        "Catalog seed complete: "
        f"content created={result.created_content} "
        f"skipped={1 - result.created_content}; "
        f"activities created={result.created_activities} "
        f"skipped={1 - result.created_activities}"
    )
    return 0
