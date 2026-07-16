# Reverse Tutor Contract-First Slice 0 Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the Slice 0 foundation for spatial Compose navigation, canonical FastAPI authentication, and PostgreSQL/Alembic persistence without moving local-first learning data to the server.

**Architecture:** The main window freezes contracts and integration seams. Three workers execute independent frontend, FastAPI/protocol, and PostgreSQL plans in parallel. FastAPI is developed against an `AuthStore` port and in-memory test store; PostgreSQL implements that port in a separate package, and the main window performs the final wiring and cross-layer verification.

**Tech Stack:** Jetpack Compose, Kotlin 1.9, AndroidX Foundation Pager, Python 3, FastAPI, Pydantic 2, SQLAlchemy 2, PostgreSQL 16, Alembic, psycopg 3, pytest, JUnit 4.

---

## Locked Inputs

- Design: `docs/superpowers/specs/2026-07-16-contract-first-three-track-design.md`
- Canonical OpenAPI: `docs/contracts/openapi-online-v1.yaml`
- Android contract fixtures: `docs/contracts/mock-online-v1.json`
- Frontend plan: `docs/superpowers/plans/2026-07-16-spatial-workspace-navigation.md`
- FastAPI plan: `docs/superpowers/plans/2026-07-16-fastapi-auth-contract.md`
- PostgreSQL plan: `docs/superpowers/plans/2026-07-16-postgresql-online-foundation.md`

## File Ownership

Frontend worker owns only:

```text
mobile-native/app/src/main/java/com/reversetutor/preview/shell/Workspace*
mobile-native/app/src/main/java/com/reversetutor/preview/shell/HomeChallengePagerHost.kt
mobile-native/app/src/main/java/com/reversetutor/preview/shell/GraphEdgePagingOverlay.kt
mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChallengeRoute.kt
mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppNavigation.kt
mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt
mobile-native/app/src/test/java/com/reversetutor/preview/shell/Workspace*
mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt
mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt
mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/KnowledgeGraphPanel.kt
```

FastAPI/protocol worker owns only:

```text
adapters/online/auth_*.py
adapters/online/errors.py
adapters/online/request_context.py
adapters/online/ports.py
adapters/online/routes.py
adapters/online/models.py
adapters/online/service.py
server.py
docs/contracts/openapi-online-v1.yaml
docs/contracts/mock-online-v1.json
mobile-native/core/remote/**
tests/test_online_*.py
```

PostgreSQL worker owns only:

```text
online_db/**
alembic.ini
alembic/**
scripts/migrate_online_sqlite_to_postgres.py
tests/online_db/**
requirements.txt
requirements-dev.txt
.github/workflows/postgres-contract.yml
```

Main window owns integration edits outside these lists and resolves any requested contract change.

### Task 1: Freeze Slice 0 Contracts

**Files:**
- Verify: `docs/superpowers/specs/2026-07-16-contract-first-three-track-design.md`
- Verify: `docs/contracts/openapi-online-v1.yaml`
- Verify: `.adworkflow/task_spec.json`

- [ ] **Step 1: Record the Slice 0 invariants**

The task spec must state all of the following exact invariants:

```text
Room remains authoritative for sessions, chat, world trees, sources, graphs and model secrets.
PostgreSQL is authoritative only for /api/v1 online data.
Ordinary sync accepts only activity_progress, study_plan, sync_summary and user_setting.
Bearer subject is the account identity; body userId is never trusted.
SQLite is not a production dual-write target.
Safe SQLite fallback ends before PostgreSQL production writes open.
```

- [ ] **Step 2: Check for contradictory server-ownership language**

Run:

```powershell
rg -n "Room.*(cache|缓存)|PostgreSQL.*(session|message|world.tree|资料正文|图谱)" docs .adworkflow
```

Expected: no active Slice 0 artifact claims that PostgreSQL owns local-first learning data.

- [ ] **Step 3: Commit contract-only corrections if required**

```powershell
git add docs/contracts .adworkflow
git commit -m "docs: freeze slice zero contracts"
```

Expected: the commit contains contract artifacts only, or no commit is created when no correction is needed.

### Task 2: Dispatch Three Independent Plans

**Files:**
- Execute: `docs/superpowers/plans/2026-07-16-spatial-workspace-navigation.md`
- Execute: `docs/superpowers/plans/2026-07-16-fastapi-auth-contract.md`
- Execute: `docs/superpowers/plans/2026-07-16-postgresql-online-foundation.md`

- [ ] **Step 1: Send each worker its plan and ownership list**

Each prompt must include:

```text
Read AGENTS.md and the assigned plan first.
Do not edit outside the owned path list.
Use TDD and commit only owned files.
Do not build a release APK.
Report commit id, changed files, exact tests and remaining risks.
```

- [ ] **Step 2: Confirm the workers are on GPT-5.6-Sol xhigh**

Read each task status and verify its configured model is `gpt-5.6-sol` with reasoning effort `xhigh`.

- [ ] **Step 3: Reject cross-owner edits before integration**

For each worker result, compare its changed files to the ownership list. Any cross-owner edit returns to the originating worker before merge.

### Task 3: Integrate AuthStore With PostgreSQL

**Files:**
- Modify: `adapters/online/dependencies.py`
- Modify: `server.py`
- Test: `tests/test_online_auth_postgres_integration.py`

- [ ] **Step 1: Write the failing application wiring test**

```python
async def test_fastapi_auth_uses_configured_postgres_store(postgres_url, monkeypatch):
    monkeypatch.setenv("ONLINE_DATABASE_URL", postgres_url)
    app = create_test_app()
    async with app_client(app) as client:
        response = await client.post(
            "/api/v1/auth/anonymous",
            json={"deviceId": "device-test", "idempotencyKey": "bootstrap-test"},
        )
    assert response.status_code == 201
    assert await count_auth_sessions(postgres_url) == 1
```

- [ ] **Step 2: Run the wiring test and verify it fails**

Run:

```powershell
py -m pytest tests/test_online_auth_postgres_integration.py -v
```

Expected: FAIL because `create_test_app` does not inject the PostgreSQL `AuthStore` yet.

- [ ] **Step 3: Add one application-boundary store selection**

`adapters/online/dependencies.py` must expose a single provider:

```python
def build_auth_store(settings: OnlineSettings) -> AuthStore:
    if settings.database_url:
        return SqlAlchemyAuthStore(build_online_session_factory(settings.database_url))
    if settings.allow_in_memory_store:
        return InMemoryAuthStore()
    raise RuntimeError("ONLINE_DATABASE_URL is required")
```

`server.py` supplies the returned store to the auth service at application construction. Feature routes must not construct database sessions directly.

- [ ] **Step 4: Run the wiring test and online API tests**

```powershell
py -m pytest tests/test_online_auth_postgres_integration.py tests/test_online_auth_api.py tests/test_online_hybrid_api.py -v
```

Expected: all tests pass.

- [ ] **Step 5: Commit the integration seam**

```powershell
git add adapters/online/dependencies.py server.py tests/test_online_auth_postgres_integration.py
git commit -m "feat: wire postgres auth store"
```

### Task 4: Cross-Layer Contract Verification

**Files:**
- Verify: `docs/contracts/openapi-online-v1.yaml`
- Verify: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt`
- Verify: `tests/test_online_auth_api.py`
- Verify: `tests/online_db/`

- [ ] **Step 1: Parse the canonical OpenAPI**

```powershell
py -c "import pathlib,yaml; yaml.safe_load(pathlib.Path('docs/contracts/openapi-online-v1.yaml').read_text(encoding='utf-8')); print('openapi-ok')"
```

Expected: `openapi-ok`.

- [ ] **Step 2: Compare FastAPI and canonical paths**

```powershell
py -m pytest tests/test_online_openapi_contract.py -v
```

Expected: every Slice 0 path, method, required field and error envelope matches the canonical document.

- [ ] **Step 3: Run Android remote contract tests**

```powershell
mobile-native\gradlew.bat :core:remote:testDebugUnitTest --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run PostgreSQL migration and repository tests**

```powershell
py -m pytest tests/online_db tests/test_online_auth_postgres_integration.py -v
```

Expected: all PostgreSQL tests pass; no test silently falls back to SQLite.

### Task 5: Frontend Spatial Verification

**Files:**
- Verify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`
- Verify: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt`

- [ ] **Step 1: Run workspace JVM tests**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*Workspace*" --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: page order, initial home index, vertical challenge state and gesture locks pass.

- [ ] **Step 2: Compile device tests**

```powershell
mobile-native\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run device tests only when a device is available**

```powershell
adb devices
mobile-native\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: the workspace test verifies Weekly -> Home -> Graph -> Community, graph gesture protection, challenge drag cancellation and completed reveal. If no device is connected, record the check as unavailable rather than passed.

### Task 6: Slice 0 Regression And Completion Gate

**Files:**
- Update: `.adworkflow/verification_result.json`
- Update: `.adworkflow/review_findings.json`

- [ ] **Step 1: Run Python regression**

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

Expected: all tests pass with no real LLM or external network calls.

- [ ] **Step 2: Run native regression**

```powershell
mobile-native\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`. This is a debug build only; do not assemble a release APK.

- [ ] **Step 3: Inspect scope and secrets**

```powershell
git diff --check
rg -n "(sk-[A-Za-z0-9]|Bearer [A-Za-z0-9._-]+|refreshToken\"\s*:\s*\")" adapters online_db docs mobile-native
```

Expected: no whitespace errors and no committed secret values.

- [ ] **Step 4: Record completion evidence**

The verification result must contain exact commands, pass counts, skipped device checks, changed files, unresolved risks and the three worker commit IDs.

- [ ] **Step 5: Commit integration artifacts**

```powershell
git add .adworkflow/verification_result.json .adworkflow/review_findings.json
git commit -m "chore: record slice zero verification"
```

Slice 1 must not start until Tasks 3 through 6 pass.
