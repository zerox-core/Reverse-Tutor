# Online Runtime Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the PostgreSQL online catalog runnable and connect the formal Android challenge screen to confirmed server activity state.

**Architecture:** An explicit seed CLI owns initial catalog creation, FastAPI exposes read-only readiness, and an Android coordinator consumes the existing `ActivityRepository` plus the anonymous auth session identity. PostgreSQL remains the only online writer and local learning repositories remain untouched.

**Tech Stack:** Python 3.11+, FastAPI, SQLAlchemy 2, Alembic, PostgreSQL 16, Docker Compose, Kotlin, coroutines, Jetpack Compose, JUnit 4.

## Global Constraints

- No production dual-write and no runtime schema mutation.
- PostgreSQL is the only fact source for `/api/v1` online writes.
- SQLite is limited to local development and cross-dialect contract tests.
- Sessions, chat, sources, world trees, graphs, and model credentials remain local-first.
- Android remains `LocalOnly` when `REVERSE_TUTOR_ONLINE_BASE_URL` is blank.
- Joining a challenge is shown as successful only after the server confirms it.
- UUID database primary keys, timezone-aware UTC timestamps, and explicitly named constraints remain mandatory.
- No Release APK and no official package-id switch.

---

### Task 1: Repeatable PostgreSQL Catalog Bootstrap

**Files:**
- Create: `online_db/catalog_seed.py`
- Create: `scripts/seed_online_catalog.py`
- Create: `docker-compose.online.yml`
- Create: `.env.online.example`
- Test: `tests/online_db/test_catalog_seed.py`

**Interfaces:**
- Consumes: `SqlAlchemyContentStore`, `SqlAlchemyActivityStore`, `assert_online_schema_at_head`, `build_online_session_factory`.
- Produces: `seed_online_catalog(session_factory, now) -> CatalogSeedResult` and a CLI whose second run performs zero inserts.

- [ ] **Step 1: Write failing seed tests**

```python
first = seed_online_catalog(factory, NOW)
second = seed_online_catalog(factory, NOW)
assert first.created_content == 1
assert first.created_activities == 1
assert second.created_content == 0
assert second.created_activities == 0
```

- [ ] **Step 2: Verify RED**

Run: `py -m pytest tests/online_db/test_catalog_seed.py -q`
Expected: import failure for `online_db.catalog_seed`.

- [ ] **Step 3: Implement minimal explicit seed**

Use fixed slugs `verify-before-opening-links` and `python-21-day-challenge`. Query before creating; never overwrite existing rows. Publish the article explicitly and create one active 21-day activity with online-confirmed join and deferred progress enabled.

- [ ] **Step 4: Add runnable development configuration**

`docker-compose.online.yml` provides PostgreSQL 16 only, with a health check and a named volume. `.env.online.example` contains non-secret development sample values. The CLI validates Alembic head before seeding and prints created/skipped counts.

- [ ] **Step 5: Verify GREEN**

Run: `py -m pytest tests/online_db/test_catalog_seed.py tests/online_db/test_content_activity_stores.py -q`
Expected: all pass.

### Task 2: FastAPI Online Readiness

**Files:**
- Create: `adapters/online/health.py`
- Modify: `adapters/online/routes.py`
- Modify: `adapters/online/service.py`
- Modify: `server.py`
- Test: `tests/test_online_health_api.py`

**Interfaces:**
- Consumes: current Alembic head and the active content/activity ports.
- Produces: public `GET /api/v1/health` response with `status`, `schemaHead`, `catalog`, and `serverTimeEpochMillis`.

- [ ] **Step 1: Write failing readiness tests**

```python
response = await client.get('/api/v1/health')
assert response.status_code == 200
assert response.json()['status'] == 'ready'
assert 'databaseUrl' not in response.text
assert 'token' not in response.text.lower()
```

- [ ] **Step 2: Verify RED**

Run: `py -m pytest tests/test_online_health_api.py -q`
Expected: `404` for `/api/v1/health`.

- [ ] **Step 3: Implement read-only health projection**

The endpoint reads runtime configuration already established at startup. It must not run Alembic, create rows, seed catalog data, or return connection strings and secrets.

- [ ] **Step 4: Verify GREEN and error envelope compatibility**

Run: `py -m pytest tests/test_online_health_api.py tests/test_online_error_contract.py tests/test_online_openapi_contract.py -q`
Expected: all pass.

### Task 3: Android Confirmed Challenge Runtime

**Files:**
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineAuthSessionManager.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChallengeRuntimeCoordinator.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChallengeRoute.kt`
- Test: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/OnlineAuthSessionManagerTest.kt`
- Test: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/ChallengeRuntimeCoordinatorTest.kt`

**Interfaces:**
- Produces: `OnlineSessionIdentity(accountId, deviceId)` from the current authenticated token state.
- Produces: `ChallengeRuntimeState` with loading, confirmed activity/participation/leaderboard, and retryable failure.
- Consumes: `ActivityRepository.list`, `leaderboard`, and `join`.

- [ ] **Step 1: Write failing auth identity and coordinator tests**

```kotlin
assertEquals(OnlineSessionIdentity("account-1", "device-1"), manager.identity())
coordinator.load()
assertEquals("focus-week", coordinator.state.value.activity?.id)
coordinator.join()
assertTrue(coordinator.state.value.participation?.joined == true)
```

- [ ] **Step 2: Verify RED**

Run: `gradlew :core:remote:testDebugUnitTest :app:testDebugUnitTest`
Expected: unresolved identity/coordinator APIs.

- [ ] **Step 3: Implement identity and challenge state machine**

`identity()` must share the auth mutex and bootstrap/refresh path. The coordinator selects the first active activity, loads its leaderboard, and uses `join:<activityId>:<accountId>:<revision>` as the deterministic idempotency key. Failure never changes the confirmed participation.

- [ ] **Step 4: Wire formal UI without changing layout**

Replace `AppShell`'s remembered `challengeJoined/progress/total` values with coordinator state when online services exist. In `LocalOnly`, render the existing reference challenge as unjoined and do not fabricate a successful join. Add loading/retry semantics through existing controls without explanatory tutorial text.

- [ ] **Step 5: Verify GREEN**

Run: `gradlew :core:remote:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`
Expected: all pass and lint has zero errors.

### Task 4: Integrated Verification and Delivery

**Files:**
- Modify only files required by review findings.

- [ ] **Step 1: Validate Compose configuration**

Run: `docker compose -f docker-compose.online.yml config`
Expected: valid PostgreSQL 16 service with no source-tree bind mount for database files.

- [ ] **Step 2: Run Python contracts**

Run: `py -m pytest tests/online_db tests/test_online_health_api.py tests/test_online_content_activity_api.py tests/test_online_auth_api.py -q`
Expected: all local contracts pass; real PostgreSQL tests skip only when the daemon is unavailable.

- [ ] **Step 3: Run Android contracts**

Run: `gradlew :core:remote:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`
Expected: build successful and lint zero errors.

- [ ] **Step 4: Review, commit, and push**

Commit the database/runtime, FastAPI, and Android slices separately. Push `work/ui-design-system` without creating a PR or building a Release APK.
