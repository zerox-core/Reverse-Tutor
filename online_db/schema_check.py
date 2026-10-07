from __future__ import annotations

from pathlib import Path

from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from alembic.script import ScriptDirectory

from online_db.session import build_online_engine


def online_schema_head() -> str:
    repository_root = Path(__file__).resolve().parents[1]
    config = Config(str(repository_root / "alembic.ini"))
    config.set_main_option("script_location", str(repository_root / "alembic"))
    heads = ScriptDirectory.from_config(config).get_heads()
    if len(heads) != 1:
        raise RuntimeError(f"Expected one online Alembic head, found {heads}")
    return heads[0]


def assert_online_schema_at_head(database_url: str) -> None:
    expected = online_schema_head()
    engine = build_online_engine(database_url)
    try:
        with engine.connect() as connection:
            current = MigrationContext.configure(connection).get_current_revision()
    finally:
        engine.dispose()
    if current != expected:
        raise RuntimeError(
            f"Online database schema is at {current or 'base'}; expected {expected}"
        )
