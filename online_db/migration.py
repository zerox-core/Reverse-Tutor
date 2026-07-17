from __future__ import annotations

import argparse
import hashlib
import json
import os
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Sequence
from uuid import UUID, uuid4

from sqlalchemy import (
    JSON,
    DateTime,
    MetaData,
    Uuid,
    URL,
    UniqueConstraint,
    and_,
    create_engine,
    func,
    inspect,
    select,
    text,
)
from sqlalchemy.orm import Session, sessionmaker

import online_db.models  # noqa: F401
from online_db.base import OnlineBase
from online_db.models import MigrationRun, MigrationValidationResult


TARGET_REVISION = "0002_migration_audit"
ONLINE_MIGRATION_TABLES = {
    "anonymous_accounts",
    "account_devices",
    "auth_sessions",
    "refresh_tokens",
    "auth_audit_events",
    "idempotency_records",
}
COPY_ORDER = (
    "anonymous_accounts",
    "account_devices",
    "auth_sessions",
    "refresh_tokens",
    "auth_audit_events",
    "idempotency_records",
)
CHECK_ORDER = (
    "row_counts",
    "foreign_keys",
    "unique_constraints",
    "utc",
    "json",
    "secrets",
    "alembic_revision",
    "sequence_identity",
)
PROHIBITED_SOURCE_COLUMNS = {
    "access_token",
    "raw_token",
    "refresh_token",
    "secret",
    "token",
}


class OnlineMigrationError(RuntimeError):
    pass


class ForbiddenSourceTable(OnlineMigrationError):
    pass


class ForbiddenSourceColumn(OnlineMigrationError):
    pass


class MigrationRunConflict(OnlineMigrationError):
    pass


class TargetRevisionMismatch(OnlineMigrationError):
    pass


class _MigrationValidationFailed(OnlineMigrationError):
    def __init__(
        self,
        report: "MigrationReport",
        source_counts: dict[str, int],
    ) -> None:
        super().__init__("Online migration validation failed")
        self.report = report
        self.source_counts = source_counts


@dataclass(frozen=True)
class MigrationCheck:
    name: str
    passed: bool
    expected: object
    actual: object
    diagnostic: str | None = None

    def to_dict(self) -> dict[str, object]:
        return {
            "name": self.name,
            "passed": self.passed,
            "expected": self.expected,
            "actual": self.actual,
            "diagnostic": self.diagnostic,
        }


@dataclass(frozen=True)
class MigrationReport:
    migration_run_id: UUID
    source_sha256: str
    target_revision: str
    status: str
    checks: tuple[MigrationCheck, ...]

    def to_dict(self) -> dict[str, object]:
        return {
            "migrationRunId": str(self.migration_run_id),
            "sourceSha256": self.source_sha256,
            "targetRevision": self.target_revision,
            "status": self.status,
            "checks": [check.to_dict() for check in self.checks],
        }


def migrate_online_database(
    source_sqlite: str | Path,
    target_url: str,
    migration_run_id: UUID,
    *,
    report_path: str | Path | None = None,
) -> MigrationReport:
    source_path = Path(source_sqlite).resolve()
    if not source_path.is_file():
        raise FileNotFoundError(source_path)
    source_sha256 = hashlib.sha256(source_path.read_bytes()).hexdigest()

    source_engine = create_engine(
        URL.create("sqlite+pysqlite", database=str(source_path))
    )
    try:
        source_tables = _validated_source_tables(source_engine)
        _validate_source_columns(source_engine, source_tables)

        target_engine = create_engine(target_url, pool_pre_ping=True)
        session_factory = sessionmaker(
            bind=target_engine,
            class_=Session,
            autoflush=False,
            expire_on_commit=False,
        )
        try:
            target_revision = _target_revision(target_engine)
            if target_revision != TARGET_REVISION:
                raise TargetRevisionMismatch(
                    f"Target is at {target_revision!r}, expected {TARGET_REVISION!r}"
                )

            existing = _existing_report(
                session_factory, migration_run_id, source_sha256
            )
            if existing is not None:
                if report_path is not None:
                    _write_report_atomic(Path(report_path), existing)
                return existing

            started_at = datetime.now(timezone.utc)
            try:
                with session_factory() as database, database.begin():
                    database.add(
                        MigrationRun(
                            id=migration_run_id,
                            source_sha256=source_sha256,
                            target_revision=target_revision,
                            status="running",
                            started_at=started_at,
                            summary_json={"phase": "copy"},
                        )
                    )
                    database.flush()
                    source_counts = _copy_source_tables(
                        source_engine, database, source_tables
                    )
                    checks = _validate_target(
                        database.connection(), source_counts, target_revision
                    )
                    report = MigrationReport(
                        migration_run_id=migration_run_id,
                        source_sha256=source_sha256,
                        target_revision=target_revision,
                        status=(
                            "passed"
                            if all(check.passed for check in checks)
                            else "failed"
                        ),
                        checks=checks,
                    )
                    if report.status != "passed":
                        raise _MigrationValidationFailed(report, source_counts)
                    _finish_run(database, report, source_counts)
            except _MigrationValidationFailed as exc:
                report = exc.report
                _record_failed_run(
                    session_factory,
                    report,
                    exc.source_counts,
                    started_at,
                )
            except Exception as exc:
                _record_failed_run(
                    session_factory,
                    MigrationReport(
                        migration_run_id=migration_run_id,
                        source_sha256=source_sha256,
                        target_revision=target_revision,
                        status="failed",
                        checks=(),
                    ),
                    None,
                    started_at,
                    error=exc,
                )
                raise

            if report_path is not None:
                _write_report_atomic(Path(report_path), report)
            return report
        finally:
            target_engine.dispose()
    finally:
        source_engine.dispose()


def _validated_source_tables(source_engine) -> set[str]:
    table_names = {
        name
        for name in inspect(source_engine).get_table_names()
        if not name.startswith("sqlite_") and name != "alembic_version"
    }
    forbidden = sorted(table_names - ONLINE_MIGRATION_TABLES)
    if forbidden:
        raise ForbiddenSourceTable(
            f"Source contains forbidden online migration table: {', '.join(forbidden)}"
        )
    return table_names


def _validate_source_columns(source_engine, source_tables: set[str]) -> None:
    inspector = inspect(source_engine)
    for table_name in sorted(source_tables):
        allowed = set(OnlineBase.metadata.tables[table_name].c.keys())
        for column_info in inspector.get_columns(table_name):
            column_name = str(column_info["name"])
            lowered = column_name.lower()
            if lowered in PROHIBITED_SOURCE_COLUMNS or "secret" in lowered:
                raise ForbiddenSourceColumn(
                    f"Source contains prohibited column: {table_name}.{column_name}"
                )
            if column_name not in allowed:
                raise ForbiddenSourceColumn(
                    f"Source contains unknown column: {table_name}.{column_name}"
                )


def _target_revision(target_engine) -> str | None:
    with target_engine.connect() as connection:
        return connection.scalar(text("SELECT version_num FROM alembic_version"))


def _existing_report(
    session_factory: sessionmaker[Session],
    migration_run_id: UUID,
    source_sha256: str,
) -> MigrationReport | None:
    with session_factory() as database:
        run = database.get(MigrationRun, migration_run_id)
        if run is None:
            return None
        if run.source_sha256 != source_sha256:
            raise MigrationRunConflict(
                "Migration run ID already exists for another source snapshot"
            )
        results = database.scalars(
            select(MigrationValidationResult).where(
                MigrationValidationResult.run_id == migration_run_id
            )
        ).all()
        by_name = {result.check_name: result for result in results}
        checks = tuple(
            MigrationCheck(
                name=name,
                passed=by_name[name].passed,
                expected=by_name[name].expected_json,
                actual=by_name[name].actual_json,
                diagnostic=by_name[name].diagnostic,
            )
            for name in CHECK_ORDER
            if name in by_name
        )
        return MigrationReport(
            migration_run_id=run.id,
            source_sha256=run.source_sha256,
            target_revision=run.target_revision,
            status=run.status,
            checks=checks,
        )


def _copy_source_tables(
    source_engine,
    database: Session,
    source_tables: set[str],
) -> dict[str, int]:
    source_metadata = MetaData()
    source_metadata.reflect(source_engine, only=sorted(source_tables))
    source_counts: dict[str, int] = {name: 0 for name in COPY_ORDER}
    with source_engine.connect() as source:
        for table_name in COPY_ORDER:
            if table_name not in source_tables:
                continue
            source_table = source_metadata.tables[table_name]
            target_table = OnlineBase.metadata.tables[table_name]
            rows = source.execute(select(source_table)).mappings().all()
            source_counts[table_name] = len(rows)
            if rows:
                payload = [
                    _normalize_row(dict(row), target_table) for row in rows
                ]
                database.execute(target_table.insert(), payload)
    return source_counts


def _normalize_row(row: dict[str, object], target_table) -> dict[str, object]:
    normalized: dict[str, object] = {}
    for column_name, value in row.items():
        column = target_table.c[column_name]
        if value is None:
            normalized[column_name] = None
        elif isinstance(column.type, Uuid):
            normalized[column_name] = value if isinstance(value, UUID) else UUID(str(value))
        elif isinstance(column.type, DateTime):
            normalized[column_name] = _utc_datetime(value)
        elif isinstance(column.type, JSON) and isinstance(value, str):
            normalized[column_name] = json.loads(value)
        elif isinstance(value, memoryview):
            normalized[column_name] = bytes(value)
        else:
            normalized[column_name] = value
    return normalized


def _utc_datetime(value: object) -> datetime:
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, (int, float)):
        timestamp = float(value)
        if timestamp > 10_000_000_000:
            timestamp /= 1000
        parsed = datetime.fromtimestamp(timestamp, tz=timezone.utc)
    else:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _validate_target(
    connection,
    source_counts: dict[str, int],
    target_revision: str,
) -> tuple[MigrationCheck, ...]:
    inspector = inspect(connection)
    target_counts = {
        name: connection.scalar(
            select(func.count()).select_from(OnlineBase.metadata.tables[name])
        )
        for name in COPY_ORDER
    }

    utc_failures: list[str] = []
    json_failures: list[str] = []
    for table_name in COPY_ORDER:
        table = OnlineBase.metadata.tables[table_name]
        for column in table.c:
            if isinstance(column.type, DateTime):
                values = connection.scalars(
                    select(column).where(column.is_not(None))
                ).all()
                for value in values:
                    if (
                        value.tzinfo is None
                        or value.utcoffset() is None
                        or value.utcoffset() != timedelta(0)
                    ):
                        utc_failures.append(f"{table_name}.{column.name}")
            if isinstance(column.type, JSON):
                values = connection.scalars(
                    select(column).where(column.is_not(None))
                ).all()
                for value in values:
                    try:
                        json.dumps(value)
                    except (TypeError, ValueError):
                        json_failures.append(f"{table_name}.{column.name}")

    unnamed_foreign_keys = sorted(
        f"{table}.{item['constrained_columns']}"
        for table in COPY_ORDER
        for item in inspector.get_foreign_keys(table)
        if not item["name"]
    )
    unnamed_unique_constraints = sorted(
        f"{table}.{item['column_names']}"
        for table in COPY_ORDER
        for item in inspector.get_unique_constraints(table)
        if not item["name"]
    )
    actual_foreign_key_names = {
        item["name"]
        for table in COPY_ORDER
        for item in inspector.get_foreign_keys(table)
        if item["name"]
    }
    expected_foreign_key_names = {
        constraint.name
        for table in (OnlineBase.metadata.tables[name] for name in COPY_ORDER)
        for constraint in table.foreign_key_constraints
    }
    actual_unique_names = {
        item["name"]
        for table in COPY_ORDER
        for item in inspector.get_unique_constraints(table)
        if item["name"]
    }
    expected_unique_names = {
        constraint.name
        for table in (OnlineBase.metadata.tables[name] for name in COPY_ORDER)
        for constraint in table.constraints
        if isinstance(constraint, UniqueConstraint)
    }
    foreign_key_orphans = _foreign_key_orphans(connection)
    unique_duplicates = _unique_constraint_duplicates(connection)
    target_columns = {
        f"{table}.{column['name']}"
        for table in COPY_ORDER
        for column in inspector.get_columns(table)
    }
    prohibited_target_columns = sorted(
        name
        for name in target_columns
        if name.rsplit(".", 1)[1].lower() in PROHIBITED_SOURCE_COLUMNS
        or "secret" in name.rsplit(".", 1)[1].lower()
    )

    checks = {
        "row_counts": MigrationCheck(
            "row_counts",
            source_counts == target_counts,
            source_counts,
            target_counts,
        ),
        "foreign_keys": MigrationCheck(
            "foreign_keys",
            not unnamed_foreign_keys
            and not foreign_key_orphans
            and expected_foreign_key_names <= actual_foreign_key_names,
            {"unnamed": [], "missing": [], "orphans": []},
            {
                "unnamed": unnamed_foreign_keys,
                "missing": sorted(
                    expected_foreign_key_names - actual_foreign_key_names
                ),
                "orphans": foreign_key_orphans,
            },
        ),
        "unique_constraints": MigrationCheck(
            "unique_constraints",
            not unnamed_unique_constraints
            and not unique_duplicates
            and expected_unique_names <= actual_unique_names,
            {"unnamed": [], "missing": [], "duplicates": []},
            {
                "unnamed": unnamed_unique_constraints,
                "missing": sorted(expected_unique_names - actual_unique_names),
                "duplicates": unique_duplicates,
            },
        ),
        "utc": MigrationCheck(
            "utc",
            not utc_failures,
            {"timezone": "UTC", "failures": []},
            {"timezone": "UTC", "failures": sorted(set(utc_failures))},
        ),
        "json": MigrationCheck(
            "json",
            not json_failures,
            {"parse_failures": []},
            {"parse_failures": sorted(set(json_failures))},
        ),
        "secrets": MigrationCheck(
            "secrets",
            not prohibited_target_columns,
            {"plaintext_columns": []},
            {"plaintext_columns": prohibited_target_columns},
        ),
        "alembic_revision": MigrationCheck(
            "alembic_revision",
            target_revision == TARGET_REVISION,
            TARGET_REVISION,
            target_revision,
        ),
        "sequence_identity": MigrationCheck(
            "sequence_identity",
            True,
            {"status": "not_applicable"},
            {"status": "not_applicable", "reason": "uuid_only_slice_0"},
        ),
    }
    return tuple(checks[name] for name in CHECK_ORDER)


def _foreign_key_orphans(connection) -> list[dict[str, object]]:
    failures: list[dict[str, object]] = []
    for table_name in COPY_ORDER:
        table = OnlineBase.metadata.tables[table_name]
        for constraint in sorted(
            table.foreign_key_constraints,
            key=lambda item: item.name or "",
        ):
            child = table.alias(f"{table_name}_child")
            parent_table = next(iter(constraint.elements)).column.table
            parent = parent_table.alias(f"{table_name}_{constraint.name}_parent")
            join_condition = and_(
                *(
                    child.c[element.parent.name]
                    == parent.c[element.column.name]
                    for element in constraint.elements
                )
            )
            local_values_present = and_(
                *(
                    child.c[element.parent.name].is_not(None)
                    for element in constraint.elements
                )
            )
            parent_missing = and_(
                *(
                    parent.c[element.column.name].is_(None)
                    for element in constraint.elements
                )
            )
            count = connection.scalar(
                select(func.count())
                .select_from(child.outerjoin(parent, join_condition))
                .where(local_values_present, parent_missing)
            )
            if count:
                failures.append(
                    {"constraint": constraint.name, "orphanRows": int(count)}
                )
    return failures


def _unique_constraint_duplicates(connection) -> list[dict[str, object]]:
    failures: list[dict[str, object]] = []
    for table_name in COPY_ORDER:
        table = OnlineBase.metadata.tables[table_name]
        constraints = sorted(
            (
                constraint
                for constraint in table.constraints
                if isinstance(constraint, UniqueConstraint)
            ),
            key=lambda item: item.name or "",
        )
        for constraint in constraints:
            columns = [table.c[column.name] for column in constraint.columns]
            rows = connection.execute(
                select(*columns, func.count().label("duplicate_count"))
                .where(and_(*(column.is_not(None) for column in columns)))
                .group_by(*columns)
                .having(func.count() > 1)
            ).all()
            if rows:
                failures.append(
                    {
                        "constraint": constraint.name,
                        "duplicateGroups": len(rows),
                        "duplicateRows": sum(int(row[-1]) for row in rows),
                    }
                )
    return failures


def _finish_run(
    database: Session,
    report: MigrationReport,
    source_counts: dict[str, int],
) -> None:
    completed_at = datetime.now(timezone.utc)
    run = database.get(MigrationRun, report.migration_run_id, with_for_update=True)
    if run is None:
        raise OnlineMigrationError("Migration run disappeared during validation")
    for check in report.checks:
        database.add(
            MigrationValidationResult(
                id=uuid4(),
                run_id=run.id,
                check_name=check.name,
                passed=check.passed,
                expected_json=check.expected,
                actual_json=check.actual,
                diagnostic=check.diagnostic,
                created_at=completed_at,
            )
        )
    run.status = report.status
    run.completed_at = completed_at
    run.summary_json = {"row_counts": source_counts}


def _record_failed_run(
    session_factory: sessionmaker[Session],
    report: MigrationReport,
    source_counts: dict[str, int] | None,
    started_at: datetime,
    *,
    error: Exception | None = None,
) -> None:
    with session_factory() as database, database.begin():
        completed_at = datetime.now(timezone.utc)
        run = MigrationRun(
            id=report.migration_run_id,
            source_sha256=report.source_sha256,
            target_revision=report.target_revision,
            status="failed",
            started_at=started_at,
            completed_at=completed_at,
            summary_json=(
                {"row_counts": source_counts}
                if source_counts is not None
                else {
                    "phase": "failed",
                    "errorType": type(error).__name__ if error else "unknown",
                }
            ),
        )
        database.add(run)
        for check in report.checks:
            database.add(
                MigrationValidationResult(
                    id=uuid4(),
                    run_id=run.id,
                    check_name=check.name,
                    passed=check.passed,
                    expected_json=check.expected,
                    actual_json=check.actual,
                    diagnostic=check.diagnostic,
                    created_at=completed_at,
                )
            )


def _write_report_atomic(path: Path, report: MigrationReport) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{report.migration_run_id}.tmp")
    temporary.write_text(
        json.dumps(report.to_dict(), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def _resolve_target_url(value: str) -> str:
    if not value.startswith("env:"):
        return value
    variable = value.removeprefix("env:")
    resolved = os.getenv(variable, "").strip()
    if not resolved:
        raise RuntimeError(f"Missing target URL environment variable: {variable}")
    return resolved


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Migrate allowlisted online SQLite data to PostgreSQL"
    )
    parser.add_argument("--source-sqlite", required=True)
    parser.add_argument("--target-url", required=True)
    parser.add_argument("--migration-run-id", required=True, type=UUID)
    parser.add_argument("--report", required=True)
    arguments = parser.parse_args(argv)

    report = migrate_online_database(
        arguments.source_sqlite,
        _resolve_target_url(arguments.target_url),
        arguments.migration_run_id,
        report_path=arguments.report,
    )
    print(json.dumps(report.to_dict(), sort_keys=True))
    return 0 if report.status == "passed" else 1
