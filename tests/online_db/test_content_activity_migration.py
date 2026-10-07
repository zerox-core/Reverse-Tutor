from __future__ import annotations

from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect


NEW_TABLES = {
    "content_items",
    "content_assets",
    "activities",
    "activity_tasks",
    "activity_participations",
    "activity_progress_events",
}


def _config(database_path: Path) -> Config:
    config = Config(str(Path("alembic.ini").resolve()))
    config.set_main_option(
        "sqlalchemy.url",
        f"sqlite+pysqlite:///{database_path.as_posix()}",
    )
    return config


def test_sqlite_upgrade_to_0003_creates_named_content_and_activity_schema(tmp_path):
    database_path = tmp_path / "migration.sqlite3"
    config = _config(database_path)

    command.upgrade(config, "head")

    engine = create_engine(f"sqlite+pysqlite:///{database_path.as_posix()}")
    try:
        inspector = inspect(engine)
        assert NEW_TABLES <= set(inspector.get_table_names())
        for table_name in NEW_TABLES:
            assert inspector.get_pk_constraint(table_name)["name"]
            assert all(item["name"] for item in inspector.get_foreign_keys(table_name))
            assert all(
                item["name"] for item in inspector.get_unique_constraints(table_name)
            )
            assert all(item["name"] for item in inspector.get_check_constraints(table_name))
            assert all(item["name"] for item in inspector.get_indexes(table_name))
    finally:
        engine.dispose()


def test_0003_downgrade_removes_only_content_and_activity_tables(tmp_path):
    database_path = tmp_path / "downgrade.sqlite3"
    config = _config(database_path)
    command.upgrade(config, "head")

    command.downgrade(config, "0002_migration_audit")

    engine = create_engine(f"sqlite+pysqlite:///{database_path.as_posix()}")
    try:
        tables = set(inspect(engine).get_table_names())
        assert not (NEW_TABLES & tables)
        assert "anonymous_accounts" in tables
        assert "migration_runs" in tables
    finally:
        engine.dispose()
