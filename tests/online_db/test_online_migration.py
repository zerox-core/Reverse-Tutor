from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from uuid import uuid4

import pytest
from sqlalchemy import create_engine, func, select, text
from sqlalchemy.exc import IntegrityError

from online_db.migration import (
    ForbiddenSourceColumn,
    ForbiddenSourceTable,
    MigrationCheck,
    MigrationRunConflict,
    _validate_target,
    migrate_online_database,
)
from online_db.models import (
    AccountDevice,
    AnonymousAccount,
    MigrationRun,
    MigrationValidationResult,
)
from online_db.schema_check import online_schema_head


EXPECTED_CHECKS = {
    "row_counts",
    "foreign_keys",
    "unique_constraints",
    "utc",
    "json",
    "secrets",
    "alembic_revision",
    "sequence_identity",
}
EXPECTED_TARGET_REVISION = online_schema_head()


def sqlite_path(tmp_path):
    path = tmp_path / "online-source.sqlite3"
    sqlite3.connect(path).close()
    return path


def test_migration_rejects_learning_tables(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    with sqlite3.connect(path) as source:
        source.execute("CREATE TABLE sessions(id TEXT PRIMARY KEY)")

    with pytest.raises(ForbiddenSourceTable, match="sessions"):
        migrate_online_database(path, postgres_url, uuid4())


def test_migration_rejects_plaintext_secret_columns(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    with sqlite3.connect(path) as source:
        source.execute(
            "CREATE TABLE refresh_tokens(id TEXT PRIMARY KEY, token TEXT NOT NULL)"
        )

    with pytest.raises(ForbiddenSourceColumn, match="refresh_tokens.token"):
        migrate_online_database(path, postgres_url, uuid4())


def test_empty_source_records_repeatable_validation_report(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    report_path = tmp_path / "reports" / "migration.json"
    run_id = uuid4()

    result = migrate_online_database(
        path,
        postgres_url,
        run_id,
        report_path=report_path,
    )

    assert result.migration_run_id == run_id
    assert len(result.source_sha256) == 64
    assert result.target_revision == EXPECTED_TARGET_REVISION
    assert result.status == "passed"
    assert {check.name for check in result.checks} == EXPECTED_CHECKS
    assert all(check.passed for check in result.checks)
    sequence = next(check for check in result.checks if check.name == "sequence_identity")
    assert sequence.actual == {"status": "not_applicable", "reason": "uuid_only_slice_0"}

    on_disk = json.loads(report_path.read_text(encoding="utf-8"))
    assert on_disk["migrationRunId"] == str(run_id)
    assert on_disk["sourceSha256"] == result.source_sha256

    with postgres_session_factory() as session:
        run = session.get(MigrationRun, run_id)
        assert run is not None
        assert run.status == "passed"
        assert session.scalar(
            select(func.count())
            .select_from(MigrationValidationResult)
            .where(MigrationValidationResult.run_id == run_id)
        ) == len(EXPECTED_CHECKS)


def test_same_run_and_hash_returns_existing_result(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    run_id = uuid4()
    first = migrate_online_database(path, postgres_url, run_id)

    second = migrate_online_database(path, postgres_url, run_id)

    assert second == first


def test_allowlisted_rows_copy_with_uuid_and_utc_normalization(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    account_id = uuid4()
    with sqlite3.connect(path) as source:
        source.execute(
            """
            CREATE TABLE anonymous_accounts(
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL,
                revoked_at TEXT,
                deleted_at TEXT
            )
            """
        )
        source.execute(
            "INSERT INTO anonymous_accounts VALUES (?, ?, ?, ?, NULL, NULL)",
            (
                str(account_id),
                "active",
                "2026-07-16T16:00:00+08:00",
                "2026-07-16T08:00:00Z",
            ),
        )

    result = migrate_online_database(path, postgres_url, uuid4())

    assert result.status == "passed"
    with postgres_session_factory() as session:
        account = session.get(AnonymousAccount, account_id)
        assert account is not None
        assert account.created_at == datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)
        assert account.last_seen_at.utcoffset() == timezone.utc.utcoffset(None)


def test_duplicate_run_with_different_source_hash_is_rejected(
    tmp_path, postgres_url, postgres_session_factory
):
    path = sqlite_path(tmp_path)
    run_id = uuid4()
    migrate_online_database(path, postgres_url, run_id)
    with sqlite3.connect(path) as source:
        source.execute("CREATE TABLE anonymous_accounts(id TEXT PRIMARY KEY)")

    with pytest.raises(MigrationRunConflict):
        migrate_online_database(path, postgres_url, run_id)


def test_validation_failure_rolls_back_copy_then_records_failed_run(
    tmp_path,
    postgres_url,
    postgres_session_factory,
    monkeypatch: pytest.MonkeyPatch,
):
    path = sqlite_path(tmp_path)
    account_id = uuid4()
    with sqlite3.connect(path) as source:
        source.execute(
            """
            CREATE TABLE anonymous_accounts(
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL,
                revoked_at TEXT,
                deleted_at TEXT
            )
            """
        )
        source.execute(
            "INSERT INTO anonymous_accounts VALUES (?, 'active', ?, ?, NULL, NULL)",
            (
                str(account_id),
                "2026-07-16T08:00:00Z",
                "2026-07-16T08:00:00Z",
            ),
        )

    def fail_validation(connection, source_counts, target_revision):
        del connection, target_revision
        return (
            MigrationCheck(
                name="row_counts",
                passed=False,
                expected=source_counts,
                actual={**source_counts, "anonymous_accounts": 0},
            ),
        )

    monkeypatch.setattr("online_db.migration._validate_target", fail_validation)
    run_id = uuid4()

    result = migrate_online_database(path, postgres_url, run_id)

    assert result.status == "failed"
    with postgres_session_factory() as session:
        assert session.scalar(select(func.count()).select_from(AnonymousAccount)) == 0
        run = session.get(MigrationRun, run_id)
        assert run is not None
        assert run.status == "failed"
        checks = session.scalars(
            select(MigrationValidationResult).where(
                MigrationValidationResult.run_id == run_id
            )
        ).all()
        assert [check.check_name for check in checks] == ["row_counts"]


def test_copy_error_rolls_back_rows_then_records_failed_run(
    tmp_path,
    postgres_url,
    postgres_session_factory,
):
    path = sqlite_path(tmp_path)
    account_id = uuid4()
    with sqlite3.connect(path) as source:
        source.execute(
            """
            CREATE TABLE anonymous_accounts(
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL
            )
            """
        )
        source.execute(
            """
            CREATE TABLE account_devices(
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL,
                client_device_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL
            )
            """
        )
        source.execute(
            "INSERT INTO anonymous_accounts VALUES (?, 'active', ?, ?)",
            (
                str(account_id),
                "2026-07-16T08:00:00Z",
                "2026-07-16T08:00:00Z",
            ),
        )
        source.execute(
            "INSERT INTO account_devices VALUES (?, ?, ?, 'active', ?, ?)",
            (
                str(uuid4()),
                str(uuid4()),
                "orphan-device",
                "2026-07-16T08:00:00Z",
                "2026-07-16T08:00:00Z",
            ),
        )

    run_id = uuid4()
    with pytest.raises(IntegrityError):
        migrate_online_database(path, postgres_url, run_id)

    with postgres_session_factory() as session:
        assert session.scalar(select(func.count()).select_from(AnonymousAccount)) == 0
        run = session.get(MigrationRun, run_id)
        assert run is not None
        assert run.status == "failed"
        assert run.summary_json["errorType"] == "IntegrityError"


def test_validation_checks_real_foreign_key_orphans_and_unique_duplicates(
    postgres_url,
    postgres_session_factory,
):
    del postgres_session_factory
    engine = create_engine(postgres_url)
    missing_account_id = uuid4()
    now = datetime(2026, 7, 16, 8, 0, tzinfo=timezone.utc)
    try:
        with engine.begin() as connection:
            connection.execute(
                text(
                    "ALTER TABLE account_devices DROP CONSTRAINT "
                    "fk_account_devices_account_id_anonymous_accounts"
                )
            )
            connection.execute(
                text(
                    "ALTER TABLE account_devices DROP CONSTRAINT "
                    "uq_account_devices_client_device_id"
                )
            )
            connection.execute(
                text(
                    "ALTER TABLE account_devices DROP CONSTRAINT "
                    "uq_account_devices_account_id_client_device_id"
                )
            )
            connection.execute(
                AccountDevice.__table__.insert(),
                [
                    {
                        "id": uuid4(),
                        "account_id": missing_account_id,
                        "client_device_id": "duplicate-device",
                        "status": "active",
                        "created_at": now,
                        "last_seen_at": now,
                    },
                    {
                        "id": uuid4(),
                        "account_id": missing_account_id,
                        "client_device_id": "duplicate-device",
                        "status": "active",
                        "created_at": now,
                        "last_seen_at": now,
                    },
                ],
            )
            source_counts = {
                table_name: 2 if table_name == "account_devices" else 0
                for table_name in (
                    "anonymous_accounts",
                    "account_devices",
                    "auth_sessions",
                    "refresh_tokens",
                    "auth_audit_events",
                    "idempotency_records",
                )
            }
            checks = {
                check.name: check
                for check in _validate_target(
                    connection,
                    source_counts,
                    EXPECTED_TARGET_REVISION,
                )
            }

        assert checks["foreign_keys"].passed is False
        assert checks["foreign_keys"].actual["orphans"]
        assert checks["unique_constraints"].passed is False
        assert checks["unique_constraints"].actual["duplicates"]
    finally:
        engine.dispose()
