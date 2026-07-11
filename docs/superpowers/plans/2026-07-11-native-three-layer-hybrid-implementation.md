# Native Three-Layer Hybrid Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the new-branch local-first hybrid architecture with frozen shared contracts, independently testable frontend/backend/data layers, and an initial end-to-end session/model/TurnRun/sync foundation.

**Architecture:** Android remains the local execution authority. Domain coordinators route optional online enhancement, activities, synchronization, and release metadata through Python APIs. The frontend consumes UseCases, the backend owns domain contracts and coordination, and the data layer owns persistence, migrations, protocol readers, and repository implementations.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose, Room 2.6.1, WorkManager 2.9.0, DataStore, Android Keystore, OkHttp, FastAPI, SQLite, pytest, JUnit4, Android instrumentation tests.

---

## File Ownership

Backend worker owns:

```text
mobile-native/core/model/
mobile-native/core/domain/
mobile-native/core/llm/
mobile-native/core/remote/
mobile-native/core/sync/contract/
server.py
adapters/online/
```

Data worker owns:

```text
mobile-native/core/data/
mobile-native/core/protocol/
mobile-native/core/data/schemas/
```

Frontend worker owns:

```text
mobile-native/app/
mobile-native/feature/
```

Workers must not revert existing dirty changes or edit another worker's owned files. Shared Gradle settings changes are proposed by the owning worker and integrated by the controller.

## Batch 0: Shared Contract Checkpoint

### Task 1: Create shared domain contracts

**Owner:** Backend worker

**Files:**
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/ModelConnectionModels.kt`
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/ConversationRunModels.kt`
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/LearningModels.kt`
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/SyncModels.kt`
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/DomainError.kt`
- Test: `mobile-native/core/model/src/test/java/com/reversetutor/core/model/HybridArchitectureModelsTest.kt`

- [ ] **Step 1: Write failing domain invariant tests**

Test these invariants:

```kotlin
assertEquals(TurnRunState.Waiting, waitingRun.state)
assertTrue(completedRun.isTerminal)
assertFalse(runningRun.isTerminal)
assertEquals(SyncOwnership.Local, localMessage.ownership)
assertEquals(ModelProtocol.OpenAiCompatible, connection.protocol)
assertTrue(tokenUsage.estimated)
```

- [ ] **Step 2: Run the model tests and verify RED**

Run:

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests com.reversetutor.core.model.HybridArchitectureModelsTest --no-daemon --stacktrace
```

Expected: compilation fails because the new models do not exist.

- [ ] **Step 3: Implement immutable models**

Required types:

```kotlin
data class ProviderConnection(...)
data class ModelBinding(...)
enum class ModelProtocol { OpenAiCompatible, AnthropicCompatible, GeminiNative }
enum class ModelAvailability { Untested, Available, InvalidUrl, InvalidCredential, ModelMissing, PermissionDenied, QuotaExceeded, RateLimited, ProviderUnavailable }
data class TurnRun(...)
enum class TurnRunState { Waiting, Running, Completed, Failed, Cancelled, Discarded }
data class ContextSnapshot(...)
data class StudyPlanTask(...)
data class WeeklySummary(...)
data class TokenUsageRecord(...)
data class SyncEnvelope(...)
data class SyncCursor(...)
data class SyncConflict(...)
data class DomainError(val code: DomainErrorCode, val retryable: Boolean, val safeMessage: String)
```

Every persisted user-owned model includes `spaceId`. `TurnRun` includes `turnId`, `sessionId`, `userMessageId`, `sequence`, `parentTurnId`, `contextVersion`, `modelBindingId`, `attempt`, and state timestamps.

- [ ] **Step 4: Run the model tests and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit the contract checkpoint**

```powershell
git add mobile-native/core/model
git commit -m "feat: define hybrid architecture domain contracts"
```

## Batch 1: Parallel Layer Implementation

### Task 2: Implement backend domain and online foundation

**Owner:** Backend worker

**Dependencies:** Task 1

**Files:**
- Create: `mobile-native/core/domain/build.gradle.kts`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/RepositoryContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationRunCoordinator.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ModelConnectionCoordinator.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SyncCoordinator.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationRunCoordinatorTest.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SyncCoordinatorTest.kt`
- Create: `mobile-native/core/remote/build.gradle.kts`
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineApi.kt`
- Create: `adapters/online/models.py`
- Create: `adapters/online/service.py`
- Create: `adapters/online/routes.py`
- Create: `tests/test_online_hybrid_api.py`
- Modify: `server.py`

- [ ] **Step 1: Write RED tests for concurrency and synchronization**

Required coordinator cases:

```text
two independent runs are immediately runnable
a follow-up referencing an unfinished turn waits
retry increments attempt without changing turnId
an old attempt cannot complete a newer attempt
one failed sync envelope does not block another envelope
duplicate idempotency keys do not create duplicate activity progress
```

- [ ] **Step 2: Implement Repository contracts**

Define interfaces without Room or HTTP types:

```kotlin
interface ConversationRunRepository
interface ModelConnectionRepository
interface StudyPlanRepository
interface LearningInsightRepository
interface TokenUsageRepository
interface GlobalSearchRepository
interface ActivityRepository
interface SyncRepository
interface UpdateRepository
```

- [ ] **Step 3: Implement conversation coordination**

`ConversationRunCoordinator` must:

```text
allocate sequence by logical send order
capture modelBindingId at send time
persist ContextSnapshot identity
classify explicit parent references as dependencies
allow independent runs concurrently
reject completion for cancelled/deleted/stale attempts
```

- [ ] **Step 4: Implement Python online API foundation**

Expose:

```text
GET  /api/v1/activities
GET  /api/v1/activities/{id}
POST /api/v1/activities/{id}/join
POST /api/v1/activities/{id}/progress
POST /api/v1/sync/push
POST /api/v1/sync/pull
POST /api/v1/insights/weekly
GET  /api/v1/app/releases/latest
```

Use deterministic local SQLite/in-memory adapters suitable for tests. Do not upload local learning正文 unless the request entity type is explicitly allowed.

- [ ] **Step 5: Run backend verification**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest :core:domain:testDebugUnitTest :core:llm:testDebugUnitTest --no-daemon --stacktrace
py -m pytest tests/test_online_hybrid_api.py -v
```

- [ ] **Step 6: Commit backend implementation**

```powershell
git add mobile-native/core/model mobile-native/core/domain mobile-native/core/llm mobile-native/core/remote adapters/online server.py tests/test_online_hybrid_api.py
git commit -m "feat: add hybrid domain and online coordination"
```

### Task 3: Implement Room v3 and repository foundation

**Owner:** Data worker

**Dependencies:** Task 1

**Files:**
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/Entities.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/Daos.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/model/ModelConnectionRepositoryImpl.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/run/ConversationRunRepositoryImpl.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/learning/LearningRepositoryImpl.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/sync/RoomSyncRepository.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/search/RoomGlobalSearchRepository.kt`
- Modify: `mobile-native/core/protocol/src/main/java/com/reversetutor/core/protocol/VersionedProtocolSchemas.kt`
- Modify: `mobile-native/core/protocol/src/main/java/com/reversetutor/core/protocol/ProtocolImportReader.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration2To3Test.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/HybridRepositoryContractTest.kt`

- [ ] **Step 1: Write RED migration and repository tests**

Tests must prove:

```text
v2 llm profile migrates to one connection and one model binding
session llmProfileId migrates to modelBindingId
two active TurnRuns in one session remain valid
deleting a session removes associated runs and search documents
local entity and Outbox are written atomically
API keys and secretRef never enter export JSON
```

- [ ] **Step 2: Add Room v3 tables**

Create:

```text
provider_connections
model_bindings
turn_runs
study_plan_tasks
weekly_summaries
token_usage_records
widget_layout_preferences
search_documents
sync_outbox
sync_cursors
sync_conflicts
entity_tombstones
```

Use foreign keys and indices for `spaceId`, `sessionId`, `state`, `connectionId`, `modelBindingId`, `entityType`, and retry scheduling.

- [ ] **Step 3: Implement migration 2 to 3**

Migration rules:

```text
each llm_profiles row -> ProviderConnection + ModelBinding
reuse the previous profile id as the ModelBinding id
derive a deterministic connection id from the previous profile id
move secretRef to ProviderConnection
preserve session references without losing model selection
preserve v1->v2 migration
```

- [ ] **Step 4: Implement repository transactions**

Repository implementations must return domain models, never Entity types. Session deletion must write a tombstone and remove session-owned data in one Room transaction.

- [ ] **Step 5: Upgrade import/export protocol**

Write the new current version while preserving v1 readers. Exclude secret material, Outbox, SyncCursor, SyncConflict, and diagnostics.

- [ ] **Step 6: Run data verification**

```powershell
.\gradlew.bat :core:protocol:test :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest --no-daemon --stacktrace
```

When no device is attached, run `:core:data:compileDebugAndroidTestKotlin` and record connected migration verification as pending.

- [ ] **Step 7: Commit data implementation**

```powershell
git add mobile-native/core/data mobile-native/core/protocol
git commit -m "feat: add hybrid Room and sync data foundation"
```

### Task 4: Implement frontend shell and layer adapters

**Owner:** Frontend worker

**Dependencies:** Task 1

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceViewModel.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppNavigation.kt`
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/HomeViewModel.kt`
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatRunsViewModel.kt`
- Create: `mobile-native/feature/settings/src/main/java/com/reversetutor/feature/settings/ModelConnectionsViewModel.kt`
- Create: `mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/WeeklyDashboardViewModel.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`
- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/ChatRunsViewModelTest.kt`

- [ ] **Step 1: Write RED ViewModel tests**

Tests must prove:

```text
workspace starts on the session-home page
horizontal paging can be disabled by input/drag/fullscreen graph state
two TurnRuns render independently
retry targets one run only
offline state does not hide local sessions
model switch affects the session scope without mutating active runs
```

- [ ] **Step 2: Introduce UiState/UiAction boundaries**

Every new ViewModel exposes immutable `StateFlow<UiState>` and accepts explicit actions. Compose must not import DAO, Entity, SecretStore, or HTTP types.

- [ ] **Step 3: Rebuild App Shell around the workspace state**

Implement the four-page order:

```text
Weekly dashboard | Session home | Global graph | Community
```

Default to Session home. Preserve independent vertical scroll state and disable horizontal paging during composer focus, widget drag, and fullscreen graph interaction.

- [ ] **Step 4: Connect feature screens through contract fakes**

Use contract-level fakes until controller integration replaces them with real implementations. Do not duplicate domain decisions in ViewModels.

- [ ] **Step 5: Run frontend verification**

```powershell
.\gradlew.bat :feature:chat:testDebugUnitTest :feature:memory:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :app:lint :app:assembleDebug --no-daemon --stacktrace
```

- [ ] **Step 6: Commit frontend implementation**

```powershell
git add mobile-native/app mobile-native/feature
git commit -m "feat: add hybrid frontend workspace foundation"
```

## Batch 2: Controller Integration

### Task 5: Wire modules and replace fakes

**Owner:** Controller

**Files:**
- Modify: `mobile-native/settings.gradle.kts`
- Modify: `mobile-native/app/build.gradle.kts`
- Modify: `mobile-native/core/data/build.gradle.kts`
- Modify: `mobile-native/core/llm/build.gradle.kts`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/MainActivity.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/DataModule.kt`
- Test: relevant app and integration tests

- [ ] **Step 1: Add `:core:domain` and `:core:remote` modules**
- [ ] **Step 2: Resolve contract type and Gradle dependency mismatches**
- [ ] **Step 3: Replace frontend fakes with application wiring**
- [ ] **Step 4: Verify session creation, model selection, TurnRun persistence, offline fallback, and sync queue creation**
- [ ] **Step 5: Run project verification**

```powershell
.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace
py -m pytest -q --ignore=tests/test_project_homepage.py
```

- [ ] **Step 6: Record ADworkflo worker, verification, and review artifacts**

## Batch 3: Review

### Task 6: Cross-layer architecture review

Review:

```text
no Compose -> DAO/HTTP imports
no data Entity escapes Repository implementation
no Python dependency for local chat
no latest-job-per-session concurrency rejection
no secret material in Room/export/diagnostics
no silent text conflict overwrite
no worker changed another worker's owned files
```

Run `git diff --check`, targeted race tests, migration tests, and the full native/Python suites before completion.
