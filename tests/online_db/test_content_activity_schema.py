from __future__ import annotations

from sqlalchemy import JSON, Uuid

import online_db.models  # noqa: F401
from online_db.base import OnlineBase


EXPECTED_TABLES = {
    "content_items",
    "content_assets",
    "activities",
    "activity_tasks",
    "activity_participations",
    "activity_progress_events",
}


def test_content_and_activity_tables_use_uuid_primary_keys_and_named_constraints():
    for table_name in EXPECTED_TABLES:
        table = OnlineBase.metadata.tables[table_name]
        assert isinstance(table.c.id.type, Uuid)
        assert table.primary_key.name == f"pk_{table_name}"
        assert all(constraint.name for constraint in table.constraints)
        assert all(index.name for index in table.indexes)
        assert all(len(constraint.name) <= 63 for constraint in table.constraints)
        assert all(len(index.name) <= 63 for index in table.indexes)


def test_content_json_columns_are_structured_and_tables_have_no_secret_columns():
    content = OnlineBase.metadata.tables["content_items"]
    assert isinstance(content.c.illustration_config.type, JSON)

    for table_name in EXPECTED_TABLES:
        columns = set(OnlineBase.metadata.tables[table_name].c.keys())
        assert not {
            column
            for column in columns
            if "secret" in column.lower() or "token" in column.lower()
        }


def test_activity_idempotency_and_participation_uniques_are_explicit():
    participation = OnlineBase.metadata.tables["activity_participations"]
    events = OnlineBase.metadata.tables["activity_progress_events"]

    participation_uniques = {
        (tuple(constraint.columns.keys()), constraint.name)
        for constraint in participation.constraints
        if constraint.__class__.__name__ == "UniqueConstraint"
    }
    event_uniques = {
        (tuple(constraint.columns.keys()), constraint.name)
        for constraint in events.constraints
        if constraint.__class__.__name__ == "UniqueConstraint"
    }

    assert (
        ("activity_id", "account_id"),
        "uq_activity_participations_activity_id_account_id",
    ) in participation_uniques
    assert (
        ("participation_id", "operation", "idempotency_key"),
        "uq_activity_progress_events_participation_operation_key",
    ) in event_uniques
