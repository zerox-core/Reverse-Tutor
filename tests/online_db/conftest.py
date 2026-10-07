from __future__ import annotations

import os
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, text

from online_db.session import build_online_session_factory


@pytest.fixture
def postgres_url() -> str:
    url = os.getenv("TEST_POSTGRES_URL", "").strip()
    if not url:
        pytest.skip("TEST_POSTGRES_URL is required for PostgreSQL contract tests")
    return url


@pytest.fixture
def postgres_session_factory(postgres_url: str, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ONLINE_DATABASE_URL", postgres_url)
    engine = create_engine(postgres_url)
    with engine.begin() as connection:
        connection.execute(text("DROP SCHEMA public CASCADE"))
        connection.execute(text("CREATE SCHEMA public"))
    engine.dispose()

    config = Config(str(Path("alembic.ini").resolve()))
    config.set_main_option("sqlalchemy.url", postgres_url)
    command.upgrade(config, "head")

    factory = build_online_session_factory(postgres_url)
    yield factory
    factory.kw["bind"].dispose()
