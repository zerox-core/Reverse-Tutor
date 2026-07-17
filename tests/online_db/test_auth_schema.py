from __future__ import annotations

from sqlalchemy import CheckConstraint, DateTime, Float, Uuid, create_engine


ALLOWED_TABLES = {
    "anonymous_accounts",
    "account_devices",
    "auth_sessions",
    "refresh_tokens",
    "auth_audit_events",
    "idempotency_records",
    "migration_runs",
    "migration_validation_results",
    "content_items",
    "content_assets",
    "activities",
    "activity_tasks",
    "activity_participations",
    "activity_progress_events",
}

FORBIDDEN_TABLES = {
    "sessions",
    "messages",
    "documents",
    "kg_nodes",
    "graph_nodes",
    "world_tree_drafts",
    "sources",
    "provider_connections",
}


def metadata():
    import online_db.models  # noqa: F401
    from online_db.base import OnlineBase

    return OnlineBase.metadata


def test_slice_zero_metadata_contains_only_online_tables():
    tables = set(metadata().tables)

    assert tables == ALLOWED_TABLES
    assert not FORBIDDEN_TABLES & tables


def test_online_models_use_uuid_primary_keys_and_utc_timestamps():
    for table in metadata().tables.values():
        primary_keys = list(table.primary_key.columns)
        assert len(primary_keys) == 1, table.name
        assert isinstance(primary_keys[0].type, Uuid), table.name
        for column in table.columns:
            if isinstance(column.type, DateTime):
                assert column.type.timezone is True, f"{table.name}.{column.name}"
            assert not isinstance(column.type, Float), f"{table.name}.{column.name}"


def test_refresh_tokens_have_hash_only():
    table = metadata().tables["refresh_tokens"]

    assert "token_hash" in table.c
    assert table.c.token_hash.unique
    assert table.c.token_hash.type.length == 64
    assert "token" not in table.c
    assert "raw_token" not in table.c
    assert "secret" not in table.c


def test_idempotency_response_is_encrypted_at_rest():
    columns = set(metadata().tables["idempotency_records"].c.keys())

    assert {
        "response_ciphertext",
        "response_nonce",
        "response_key_id",
        "response_status_code",
        "response_expires_at",
    } <= columns
    assert "response_json" not in columns
    assert "response_body" not in columns


def test_public_auth_idempotency_can_precede_account_resolution():
    table = metadata().tables["idempotency_records"]

    assert table.c.account_id.nullable is True
    assert {foreign_key.target_fullname for foreign_key in table.c.account_id.foreign_keys} == {
        "anonymous_accounts.id"
    }
    checks = {
        constraint.name: str(
            constraint.sqltext.compile(compile_kwargs={"literal_binds": True})
        )
        for constraint in table.constraints
        if isinstance(constraint, CheckConstraint)
    }
    check = checks["ck_idempotency_records_public_auth_account"]
    assert "operation" in check
    assert "account_id" in check
    assert "auth.bootstrap" in check
    assert "auth.refresh" in check


def test_all_constraints_are_named_and_checks_are_explicit():
    for table in metadata().tables.values():
        for constraint in table.constraints:
            assert constraint.name, f"{table.name}: {constraint}"
        for constraint in table.constraints:
            if isinstance(constraint, CheckConstraint):
                assert constraint.name.startswith(f"ck_{table.name}_")


def test_required_auth_columns_are_not_nullable():
    tables = metadata().tables

    required = {
        "anonymous_accounts": {"status", "created_at", "last_seen_at"},
        "account_devices": {"account_id", "client_device_id", "status", "created_at", "last_seen_at"},
        "auth_sessions": {"account_id", "device_id", "status", "created_at", "expires_at"},
        "refresh_tokens": {"session_id", "family_id", "token_hash", "rotation", "expires_at", "created_at"},
        "auth_audit_events": {"event_type", "outcome", "request_id", "created_at"},
        "idempotency_records": {"operation", "scope_key", "idempotency_key", "request_fingerprint", "state", "created_at", "updated_at"},
    }
    for table_name, columns in required.items():
        for column_name in columns:
            assert tables[table_name].c[column_name].nullable is False


def test_device_identity_has_global_and_account_scoped_uniqueness():
    table = metadata().tables["account_devices"]
    unique_columns = {
        tuple(column.name for column in constraint.columns)
        for constraint in table.constraints
        if constraint.__class__.__name__ == "UniqueConstraint"
    }

    assert ("client_device_id",) in unique_columns
    assert ("account_id", "client_device_id") in unique_columns
    assert ("id", "account_id") in unique_columns


def test_session_device_ownership_is_enforced_by_composite_foreign_key():
    table = metadata().tables["auth_sessions"]
    foreign_keys = {
        tuple(column.name for column in constraint.columns): tuple(
            element.target_fullname for element in constraint.elements
        )
        for constraint in table.foreign_key_constraints
    }

    assert foreign_keys[("device_id", "account_id")] == (
        "account_devices.id",
        "account_devices.account_id",
    )


def test_online_metadata_can_create_on_sqlite_without_dialect_sql():
    engine = create_engine("sqlite+pysqlite:///:memory:")

    metadata().create_all(engine)

    assert set(metadata().tables) == ALLOWED_TABLES
