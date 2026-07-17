from __future__ import annotations

import os
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect, text


ALLOWED_TABLES = {
    "anonymous_accounts",
    "account_devices",
    "auth_sessions",
    "refresh_tokens",
    "auth_audit_events",
    "idempotency_records",
    "migration_runs",
    "migration_validation_results",
}


@pytest.fixture
def postgres_url() -> str:
    url = os.getenv("TEST_POSTGRES_URL", "").strip()
    if not url:
        pytest.skip("TEST_POSTGRES_URL is required for PostgreSQL contract tests")
    return url


@pytest.fixture(autouse=True)
def clean_postgres(postgres_url: str):
    engine = create_engine(postgres_url)
    with engine.begin() as connection:
        connection.execute(text("DROP SCHEMA public CASCADE"))
        connection.execute(text("CREATE SCHEMA public"))
    engine.dispose()
    yield


def alembic_upgrade(postgres_url: str, revision: str) -> None:
    config = Config(str(Path("alembic.ini").resolve()))
    config.set_main_option("sqlalchemy.url", postgres_url)
    command.upgrade(config, revision)


def test_empty_postgres_upgrades_to_online_head(postgres_url: str):
    alembic_upgrade(postgres_url, "head")

    engine = create_engine(postgres_url)
    try:
        inspector = inspect(engine)
        assert set(inspector.get_table_names()) == ALLOWED_TABLES | {"alembic_version"}
    finally:
        engine.dispose()


def test_configured_url_takes_precedence_over_environment(
    postgres_url: str, monkeypatch: pytest.MonkeyPatch
):
    monkeypatch.setenv(
        "ONLINE_DATABASE_URL",
        "postgresql+psycopg://invalid:invalid@127.0.0.1:1/invalid",
    )

    alembic_upgrade(postgres_url, "head")

    engine = create_engine(postgres_url)
    try:
        assert "anonymous_accounts" in inspect(engine).get_table_names()
    finally:
        engine.dispose()


def test_previous_revision_upgrades_to_head(postgres_url: str):
    alembic_upgrade(postgres_url, "0001_online_auth_foundation")
    engine = create_engine(postgres_url)
    try:
        inspector = inspect(engine)
        assert "migration_runs" not in inspector.get_table_names()
    finally:
        engine.dispose()

    alembic_upgrade(postgres_url, "head")
    engine = create_engine(postgres_url)
    try:
        inspector = inspect(engine)
        assert "migration_runs" in inspector.get_table_names()
        assert "migration_validation_results" in inspector.get_table_names()
    finally:
        engine.dispose()


def test_migration_constraints_are_named(postgres_url: str):
    alembic_upgrade(postgres_url, "head")
    engine = create_engine(postgres_url)
    try:
        inspector = inspect(engine)
        for table in ALLOWED_TABLES:
            assert inspector.get_pk_constraint(table)["name"]
            assert all(item["name"] for item in inspector.get_foreign_keys(table))
            assert all(item["name"] for item in inspector.get_unique_constraints(table))
            assert all(item["name"] for item in inspector.get_check_constraints(table))
    finally:
        engine.dispose()


def test_session_device_ownership_constraint_is_present(postgres_url: str):
    alembic_upgrade(postgres_url, "head")
    engine = create_engine(postgres_url)
    try:
        inspector = inspect(engine)
        device_uniques = {
            tuple(item["column_names"]): item["name"]
            for item in inspector.get_unique_constraints("account_devices")
        }
        session_foreign_keys = {
            tuple(item["constrained_columns"]): item
            for item in inspector.get_foreign_keys("auth_sessions")
        }

        assert device_uniques[("id", "account_id")] == (
            "uq_account_devices_id_account_id"
        )
        ownership = session_foreign_keys[("device_id", "account_id")]
        assert ownership["referred_table"] == "account_devices"
        assert tuple(ownership["referred_columns"]) == ("id", "account_id")
    finally:
        engine.dispose()
