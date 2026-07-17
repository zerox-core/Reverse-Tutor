from __future__ import annotations

import ast
from pathlib import Path

import pytest
from sqlalchemy import Engine
from sqlalchemy.orm import Session


def test_online_metadata_has_named_constraint_convention():
    from online_db.base import ONLINE_NAMING_CONVENTION

    assert ONLINE_NAMING_CONVENTION == {
        "ix": "ix_%(table_name)s_%(column_0_N_name)s",
        "uq": "uq_%(table_name)s_%(column_0_N_name)s",
        "ck": "ck_%(table_name)s_%(constraint_name)s",
        "fk": "fk_%(table_name)s_%(column_0_N_name)s_%(referred_table_name)s",
        "pk": "pk_%(table_name)s",
    }


def test_online_package_does_not_import_legacy_learning_database():
    for path in Path("online_db").rglob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        imported = {
            alias.name
            for node in ast.walk(tree)
            if isinstance(node, ast.Import)
            for alias in node.names
        }
        imported.update(
            node.module
            for node in ast.walk(tree)
            if isinstance(node, ast.ImportFrom) and node.module
        )
        assert "db" not in imported, path


def test_online_settings_require_dedicated_environment_variable(monkeypatch):
    from online_db.settings import OnlineDatabaseSettings

    monkeypatch.delenv("ONLINE_DATABASE_URL", raising=False)
    monkeypatch.setenv("DB_URL", "sqlite:///legacy.db")

    with pytest.raises(RuntimeError, match="ONLINE_DATABASE_URL"):
        OnlineDatabaseSettings.from_env()


def test_online_settings_accept_explicit_url(monkeypatch):
    from online_db.settings import OnlineDatabaseSettings

    url = "postgresql+psycopg://user:password@localhost/online"
    monkeypatch.setenv("ONLINE_DATABASE_URL", url)

    assert OnlineDatabaseSettings.from_env().database_url == url


def test_engine_and_session_factory_are_isolated_builders():
    from online_db.session import build_online_engine, build_online_session_factory

    engine = build_online_engine("sqlite+pysqlite:///:memory:")
    factory = build_online_session_factory("sqlite+pysqlite:///:memory:")

    assert isinstance(engine, Engine)
    assert isinstance(factory.kw["bind"], Engine)
    assert factory.kw["autoflush"] is False
    assert factory.kw["expire_on_commit"] is False
    assert issubclass(factory.class_, Session)
