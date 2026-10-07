# PostgreSQL Online Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish an isolated PostgreSQL/Alembic persistence foundation for `/api/v1` authentication and idempotency without importing local-first learning data.

**Architecture:** Create a new `online_db` package with its own SQLAlchemy metadata, engine, sessions, models and repositories. Alembic targets only this metadata and reads `ONLINE_DATABASE_URL`. Legacy `db.py`, Native Room, SQLite FTS5 and learning entities remain outside the online database.

**Tech Stack:** Python 3, SQLAlchemy 2.x, PostgreSQL 16, psycopg 3, Alembic, UUID, timezone-aware UTC, JSON, pytest, GitHub Actions.

---

### Task 1: Add Online Database Dependencies

**Files:**
- Modify: `requirements.txt`
- Modify: `requirements-dev.txt`
- Create: `tests/online_db/test_dependencies.py`

- [ ] **Step 1: Write a failing dependency import test**

```python
def test_online_database_dependencies_are_importable():
    import alembic
    import cryptography
    import jwt
    import psycopg
    import yaml

    assert alembic.__version__
    assert psycopg.__version__
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/online_db/test_dependencies.py -v
```

Expected: one or more packages are missing.

- [ ] **Step 3: Add pinned lower bounds**

Append to `requirements.txt`:

```text
alembic>=1.13.2
psycopg[binary]>=3.2.1
PyJWT>=2.9.0
cryptography>=43.0.0
```

Append to `requirements-dev.txt`:

```text
PyYAML>=6.0.2
```

- [ ] **Step 4: Install and verify GREEN**

```powershell
py -m pip install -r requirements-dev.txt
py -m pytest tests/online_db/test_dependencies.py -v
```

- [ ] **Step 5: Commit dependencies**

```powershell
git add requirements.txt requirements-dev.txt tests/online_db/test_dependencies.py
git commit -m "build: add postgres migration dependencies"
```

### Task 2: Create Isolated Online SQLAlchemy Infrastructure

**Files:**
- Create: `online_db/__init__.py`
- Create: `online_db/base.py`
- Create: `online_db/settings.py`
- Create: `online_db/session.py`
- Create: `tests/online_db/test_online_metadata.py`

- [ ] **Step 1: Write failing metadata isolation tests**

```python
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
    import ast
    from pathlib import Path

    for path in Path("online_db").rglob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        imported = {
            node.names[0].name
            for node in ast.walk(tree)
            if isinstance(node, ast.Import)
        }
        assert "db" not in imported, path
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/online_db/test_online_metadata.py -v
```

- [ ] **Step 3: Add the online base and settings**

```python
ONLINE_NAMING_CONVENTION = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_N_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class OnlineBase(DeclarativeBase):
    metadata = MetaData(naming_convention=ONLINE_NAMING_CONVENTION)
```

`OnlineDatabaseSettings.from_env()` reads `ONLINE_DATABASE_URL`; no production default is allowed. Tests may inject a URL explicitly.

- [ ] **Step 4: Add engine/session builders**

```python
def build_online_engine(database_url: str) -> Engine:
    return create_engine(database_url, pool_pre_ping=True, future=True)


def build_online_session_factory(database_url: str) -> sessionmaker[Session]:
    return sessionmaker(
        bind=build_online_engine(database_url),
        autoflush=False,
        expire_on_commit=False,
    )
```

Do not pass SQLite `check_same_thread` arguments and do not call `create_all`.

- [ ] **Step 5: Run and verify GREEN**

Use the Task 2 Step 2 command.

- [ ] **Step 6: Commit online DB infrastructure**

```powershell
git add online_db tests/online_db/test_online_metadata.py
git commit -m "feat: isolate online database metadata"
```

### Task 3: Model Auth, Idempotency And Migration Audit Tables

**Files:**
- Create: `online_db/models/__init__.py`
- Create: `online_db/models/auth.py`
- Create: `online_db/models/idempotency.py`
- Create: `online_db/models/migration.py`
- Create: `tests/online_db/test_auth_schema.py`

- [ ] **Step 1: Write failing schema tests**

```python
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


def test_slice_zero_metadata_contains_only_online_tables():
    import online_db.models  # noqa: F401
    from online_db.base import OnlineBase

    assert set(OnlineBase.metadata.tables) == ALLOWED_TABLES
    assert not {
        "sessions", "messages", "documents", "kg_nodes", "graph_nodes", "world_tree_drafts"
    } & set(OnlineBase.metadata.tables)


def test_refresh_tokens_have_hash_only():
    table = OnlineBase.metadata.tables["refresh_tokens"]
    assert "token_hash" in table.c
    assert "token" not in table.c
    assert table.c.token_hash.unique
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/online_db/test_auth_schema.py -v
```

- [ ] **Step 3: Add shared UUID and UTC columns**

Use SQLAlchemy 2 typed mappings:

```python
class AnonymousAccount(OnlineBase):
    __tablename__ = "anonymous_accounts"

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    status: Mapped[str] = mapped_column(
        String(24), nullable=False, server_default=text("'active'")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    __table_args__ = (
        CheckConstraint(
            "status IN ('active','revoked','deleted')",
            name="account_status",
        ),
    )
```

Use `Uuid(as_uuid=True)` for all business primary keys and `DateTime(timezone=True)` for all timestamps.

- [ ] **Step 4: Add device, session, refresh and audit constraints**

Required constraints:

```text
UNIQUE(account_devices.account_id, account_devices.client_device_id)
UNIQUE(refresh_tokens.token_hash)
UNIQUE(refresh_tokens.session_id, refresh_tokens.rotation)
FK auth_sessions.account_id -> anonymous_accounts.id
FK auth_sessions.device_id -> account_devices.id
FK refresh_tokens.session_id -> auth_sessions.id
FK refresh_tokens.replaced_by_id -> refresh_tokens.id
```

`refresh_tokens` stores a 64-character keyed hash, token family UUID, rotation integer, expiry, used/revoked timestamps and replacement reference. It never stores token plaintext.

- [ ] **Step 5: Add encrypted idempotency and migration audit fields**

`idempotency_records` stores operation, scope key, idempotency key, request fingerprint, status code, encrypted response bytes, nonce, encryption key ID, response expiry and timestamps. It must not expose plaintext response JSON.

`migration_runs` stores UUID run ID, source SHA-256, target Alembic revision, status, started/completed timestamps and summary JSON. `migration_validation_results` stores run ID, check name, pass/fail, expected/actual JSON and a safe diagnostic.

- [ ] **Step 6: Run and verify GREEN**

Use the Task 3 Step 2 command.

- [ ] **Step 7: Commit the model**

```powershell
git add online_db/models tests/online_db/test_auth_schema.py
git commit -m "feat: model online auth persistence"
```

### Task 4: Initialize Alembic For Online Metadata

**Files:**
- Create: `alembic.ini`
- Create: `alembic/env.py`
- Create: `alembic/script.py.mako`
- Create: `alembic/versions/0001_online_auth_foundation.py`
- Create: `alembic/versions/0002_migration_audit.py`
- Create: `tests/online_db/test_alembic_migrations.py`

- [ ] **Step 1: Write a failing PostgreSQL migration test**

```python
def test_empty_postgres_upgrades_to_online_head(postgres_url):
    alembic_upgrade(postgres_url, "head")
    inspector = inspect(create_engine(postgres_url))
    assert set(inspector.get_table_names()) == ALLOWED_TABLES | {"alembic_version"}


def test_previous_revision_upgrades_to_head(postgres_url):
    alembic_upgrade(postgres_url, "0001_online_auth_foundation")
    assert "migration_runs" not in inspect(create_engine(postgres_url)).get_table_names()
    alembic_upgrade(postgres_url, "head")
    assert "migration_runs" in inspect(create_engine(postgres_url)).get_table_names()


def test_migration_constraints_are_named(postgres_url):
    alembic_upgrade(postgres_url, "head")
    inspector = inspect(create_engine(postgres_url))
    for table in ALLOWED_TABLES:
        assert all(item["name"] for item in inspector.get_foreign_keys(table))
        assert all(item["name"] for item in inspector.get_unique_constraints(table))
```

`postgres_url` reads `TEST_POSTGRES_URL`. Locally it may skip when absent; the PostgreSQL CI job must set it and must report zero skips.

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/online_db/test_alembic_migrations.py -v
```

- [ ] **Step 3: Configure Alembic**

`alembic/env.py` must import `OnlineBase.metadata`, read `ONLINE_DATABASE_URL`, set `compare_type=True` and `compare_server_default=True`, and support offline/online migration modes. It must not import legacy `db.Base`.

- [ ] **Step 4: Write the explicit baseline revision**

`0001_online_auth_foundation` creates the five auth tables plus `idempotency_records`. `0002_migration_audit` creates `migration_runs` and `migration_validation_results`. Both revisions use named FK/UQ/CK/index constraints, PostgreSQL UUID columns, timezone-aware timestamps and server defaults. Do not use `OnlineBase.metadata.create_all()` inside either revision.

- [ ] **Step 5: Run upgrade tests and metadata drift check**

```powershell
$env:ONLINE_DATABASE_URL=$env:TEST_POSTGRES_URL
py -m alembic upgrade head
py -m alembic check
py -m pytest tests/online_db/test_alembic_migrations.py -v
```

Expected: Alembic reaches `0002_migration_audit`, `alembic check` reports no new upgrade operations, and both empty-to-head and previous-to-head tests pass.

- [ ] **Step 6: Commit Alembic baseline**

```powershell
git add alembic.ini alembic tests/online_db/test_alembic_migrations.py
git commit -m "feat: add online postgres alembic baseline"
```

### Task 5: Implement The SQLAlchemy AuthStore

**Files:**
- Create: `online_db/auth_store.py`
- Create: `online_db/idempotency_store.py`
- Create: `tests/online_db/test_sqlalchemy_auth_store.py`
- Create: `tests/online_db/test_sqlalchemy_idempotency_store.py`

- [ ] **Step 1: Parameterize the shared port contract tests**

```python
@pytest.fixture(params=["memory", "postgres"])
def auth_store(request, postgres_session_factory):
    if request.param == "memory":
        return InMemoryAuthStore()
    return SqlAlchemyAuthStore(postgres_session_factory)


def test_refresh_replay_revokes_token_family(auth_store, clock):
    issued = auth_store.bootstrap(bootstrap_command(clock))
    auth_store.rotate_refresh(rotate_command(issued, clock))
    with pytest.raises(RefreshTokenReplay):
        auth_store.rotate_refresh(replay_command(issued, clock))
    assert auth_store.family_is_revoked(issued.family_id)
```

Parameterize the shared `IdempotencyStore` contract over memory and PostgreSQL as well. Verify that the same scope/key/fingerprint replays the encrypted response, while the same key with another fingerprint raises `IdempotencyKeyReused`.

- [ ] **Step 2: Run PostgreSQL store tests and verify RED**

```powershell
py -m pytest tests/test_online_auth_store_contract.py tests/online_db/test_sqlalchemy_auth_store.py tests/online_db/test_sqlalchemy_idempotency_store.py -v
```

- [ ] **Step 3: Implement bootstrap transaction**

Use one transaction to lock or create account/device, create the auth session, store the refresh hash and append a safe audit event. Unique conflicts must be retried by reading the existing account/device state, not by creating a second session.

- [ ] **Step 4: Implement refresh rotation transaction**

Select the token row `FOR UPDATE`, verify active family/session/expiry, mark the old token used, create replacement, link `replaced_by_id` and write an audit event. Replay locks and revokes every active token in the family before raising `RefreshTokenReplay`.

- [ ] **Step 5: Implement session reads and revoke**

Every query includes account ownership. Revoke updates the session and all active refresh tokens atomically. Repeat revoke of the caller's session returns success without a second audit event.

`SqlAlchemyIdempotencyStore` persists only the request fingerprint and `SealedResponse` ciphertext, nonce, key ID and expiry. It never stores plaintext access or refresh tokens. `claim` uses the named unique scope/key constraint and row locking so concurrent retries produce one owner and deterministic replay.

- [ ] **Step 6: Run tests and verify GREEN**

Use the Task 5 Step 2 command.

- [ ] **Step 7: Commit repository implementation**

```powershell
git add online_db/auth_store.py online_db/idempotency_store.py tests/online_db/test_sqlalchemy_auth_store.py tests/online_db/test_sqlalchemy_idempotency_store.py tests/test_online_auth_store_contract.py
git commit -m "feat: persist anonymous auth in postgres"
```

### Task 6: Add Migration Run And Validation Skeleton

**Files:**
- Create: `online_db/migration.py`
- Create: `scripts/migrate_online_sqlite_to_postgres.py`
- Create: `tests/online_db/test_online_migration.py`

- [ ] **Step 1: Write failing allowlist and report tests**

```python
ONLINE_MIGRATION_TABLES = {
    "anonymous_accounts",
    "account_devices",
    "auth_sessions",
    "refresh_tokens",
    "auth_audit_events",
    "idempotency_records",
}


def test_migration_rejects_learning_tables(sqlite_online_fixture, postgres_url):
    sqlite_online_fixture.execute("CREATE TABLE sessions(id TEXT PRIMARY KEY)")
    with pytest.raises(ForbiddenSourceTable):
        migrate_online_database(sqlite_online_fixture.path, postgres_url, uuid4())


def test_migration_report_records_hash_revision_and_checks(result):
    assert len(result.source_sha256) == 64
    assert result.target_revision == "0002_migration_audit"
    assert {check.name for check in result.checks} >= {
        "row_counts", "foreign_keys", "unique_constraints", "utc", "json", "secrets"
    }
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/online_db/test_online_migration.py -v
```

- [ ] **Step 3: Implement repeatable run tracking**

The CLI requires:

```text
--source-sqlite <path>
--target-url <postgres URL or env reference>
--migration-run-id <UUID>
--report <UTF-8 JSON path>
```

It computes the source file hash before connecting, refuses unknown source tables, refuses a duplicate run ID with a different hash, and writes the machine-readable report atomically.

- [ ] **Step 4: Implement Slice 0 validation checks**

Validate row counts, FK orphans, UQ collisions, UTC-aware timestamps, JSON parseability, hash-only refresh storage, absence of prohibited token/secret fields and target Alembic revision. PostgreSQL sequence checks cover `sync_change_log.server_sequence` only after that table is introduced; UUID-only Slice 0 reports `not_applicable` explicitly.

- [ ] **Step 5: Run and verify GREEN**

Use the Task 6 Step 2 command.

- [ ] **Step 6: Commit migration tooling**

```powershell
git add online_db/migration.py scripts/migrate_online_sqlite_to_postgres.py tests/online_db/test_online_migration.py
git commit -m "feat: add online database migration audit"
```

### Task 7: Add PostgreSQL CI Contract Job

**Files:**
- Create: `.github/workflows/postgres-contract.yml`

- [ ] **Step 1: Add the PostgreSQL service and environment**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    env:
      POSTGRES_USER: reverse_tutor
      POSTGRES_PASSWORD: test-password
      POSTGRES_DB: reverse_tutor_test
    ports:
      - 5432:5432
    options: >-
      --health-cmd "pg_isready -U reverse_tutor -d reverse_tutor_test"
      --health-interval 5s
      --health-timeout 5s
      --health-retries 12
env:
  TEST_POSTGRES_URL: postgresql+psycopg://reverse_tutor:test-password@127.0.0.1:5432/reverse_tutor_test
  ONLINE_DATABASE_URL: postgresql+psycopg://reverse_tutor:test-password@127.0.0.1:5432/reverse_tutor_test
```

- [ ] **Step 2: Add deterministic CI commands**

```yaml
- uses: actions/setup-python@v5
  with:
    python-version: '3.12'
- run: python -m pip install -r requirements-dev.txt
- run: python -m alembic upgrade head
- run: python -m alembic check
- run: python -m pytest tests/online_db tests/test_online_auth_store_contract.py -v -W error
```

The job fails if PostgreSQL tests skip.

- [ ] **Step 3: Validate YAML locally**

```powershell
py -c "import pathlib,yaml; yaml.safe_load(pathlib.Path('.github/workflows/postgres-contract.yml').read_text(encoding='utf-8')); print('workflow-ok')"
```

Expected: `workflow-ok`.

- [ ] **Step 4: Commit CI**

```powershell
git add .github/workflows/postgres-contract.yml
git commit -m "ci: test online database on postgres"
```

### Task 8: PostgreSQL Slice 0 Regression

**Files:**
- Verify only.

- [ ] **Step 1: Run the online database suite**

```powershell
py -m pytest tests/online_db tests/test_online_auth_store_contract.py -v
```

Expected: all tests pass against PostgreSQL; no PostgreSQL contract test skips in the configured environment.

- [ ] **Step 2: Verify migration head and drift**

```powershell
py -m alembic current
py -m alembic check
```

Expected: current revision is `0002_migration_audit` and no drift exists.

- [ ] **Step 3: Verify forbidden tables and imports**

```powershell
rg -n "Session|Message|Document|KGNode|GraphNode|WorldTree|FTS5|doc_chunks_fts" online_db alembic scripts/migrate_online_sqlite_to_postgres.py
```

Expected: matches occur only in explicit forbidden-table validation tests or error messages, never as imported models or created tables.

- [ ] **Step 4: Inspect scope**

```powershell
git diff --check
git status --short
```

Expected: no Compose, `mobile-native/core/data`, Room schema, DAO, SecretStore or legacy `db.py` edits from this plan.
