# Native Backend Protocol Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize native graph and widget-layout backend contracts while preserving existing model, TurnRun, sync, import/export, and secret-isolation behavior without modifying frontend code.

**Architecture:** Add graph scope/result types to `core:model`, extend the existing data `GraphRepository` with a scope-aware suspend query, and derive session graphs through the existing provenance chain instead of changing Room entities. Add an independent `WidgetLayoutRepository` domain contract backed by a Room transaction that replaces the complete local layout and never writes Sync Outbox records.

**Tech Stack:** Kotlin 1.9.22, Room 2.6.1, AndroidX test, JUnit4, FastAPI/pytest regression.

---

## File Ownership

Allowed paths:

```text
mobile-native/core/model/
mobile-native/core/domain/
mobile-native/core/data/
mobile-native/core/protocol/
mobile-native/core/llm/
mobile-native/core/remote/
tests/
docs/superpowers/
.adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001/
```

Forbidden paths:

```text
mobile-native/app/
mobile-native/feature/
static/app/
mobile/
```

No Room entity or schema-version change is required.

## Task 1: Add Stable Graph Query Models

**Files:**
- Modify: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/GraphModels.kt`
- Create: `mobile-native/core/model/src/test/java/com/reversetutor/core/model/GraphSnapshotContractTest.kt`

- [ ] **Step 1: Write failing graph contract tests**

Add tests proving:

```kotlin
val global = GraphScope.Global("space-1")
val session = GraphScope.Session("session-1")
val empty = GraphSnapshotResult.Empty(GraphEmptyReason.NoExtractedNodes)
val ready = GraphSnapshotResult.Ready(GraphSnapshot(emptyList(), emptyList()))
val error = GraphSnapshotResult.Error(
    DomainError(
        code = DomainErrorCode.NotFound,
        retryable = false,
        safeMessage = "Graph scope was not found."
    )
)

assertEquals("space-1", global.spaceId)
assertEquals("session-1", session.sessionId)
assertEquals(GraphEmptyReason.NoExtractedNodes, empty.reason)
assertTrue(ready.snapshot.nodes.isEmpty())
assertEquals(DomainErrorCode.NotFound, error.error.code)
```

- [ ] **Step 2: Run the model test and verify RED**

Run:

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests com.reversetutor.core.model.GraphSnapshotContractTest --no-daemon --stacktrace
```

Expected: compilation fails because the graph scope/result types do not exist.

- [ ] **Step 3: Add graph protocol types**

Add to `GraphModels.kt`:

```kotlin
sealed interface GraphScope {
    data class Global(val spaceId: String) : GraphScope
    data class Session(val sessionId: String) : GraphScope
}

enum class GraphEmptyReason {
    NoExtractedNodes
}

data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val invalidEdgeCount: Int = 0
)

sealed interface GraphSnapshotResult {
    data class Empty(val reason: GraphEmptyReason) : GraphSnapshotResult
    data class Ready(val snapshot: GraphSnapshot) : GraphSnapshotResult
    data class Error(val error: DomainError) : GraphSnapshotResult
}
```

Do not add UI copy or a Loading result.

- [ ] **Step 4: Run model tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --no-daemon --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit graph models**

```powershell
git add mobile-native/core/model
git commit -m "feat: define graph snapshot contracts"
```

## Task 2: Implement Global And Session Graph Queries

**Files:**
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/Daos.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/graph/GraphRepository.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/DataModule.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/graph/GraphRepositoryTest.kt`
- Modify: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseDaoTest.kt`

- [ ] **Step 1: Write failing repository tests**

Cover:

```text
Global scope returns all nodes in the requested space.
Session scope returns only nodes whose sourceMemoryId traces through
memory_items.sourceMessageId to messages.sessionId.
Session edges are retained only when both endpoints belong to the session node set.
Zero nodes returns Empty(NoExtractedNodes).
Missing session returns Error(NotFound).
DAO failure returns Error(StorageUnavailable).
Invalid edges increment invalidEdgeCount without failing the complete snapshot.
The existing snapshot(spaceId) compatibility method still returns GraphSnapshot.
```

Use `FakeGraphDao` and `FakeSessionDao` for JVM tests. Add a Room instrumentation test for the real join query.

- [ ] **Step 2: Run graph tests and verify RED**

Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests com.reversetutor.core.data.graph.GraphRepositoryTest --no-daemon --stacktrace
```

Expected: compilation or assertions fail because scope-aware queries do not exist.

- [ ] **Step 3: Add the session provenance query**

Add to `GraphDao`:

```kotlin
@Query(
    """
    SELECT DISTINCT graph_nodes.*
    FROM graph_nodes
    INNER JOIN memory_items
        ON memory_items.id = graph_nodes.sourceMemoryId
    INNER JOIN messages
        ON messages.id = memory_items.sourceMessageId
    WHERE messages.sessionId = :sessionId
    ORDER BY graph_nodes.label ASC
    """
)
suspend fun listNodesBySession(sessionId: String): List<GraphNodeEntity>
```

Do not add `sessionId` to graph entities.

- [ ] **Step 4: Implement the scope-aware repository**

Change `GraphRepository` to accept `SessionDao` and implement:

```kotlin
suspend fun snapshot(scope: GraphScope): GraphSnapshotResult =
    runCatching {
        when (scope) {
            is GraphScope.Global -> buildResult(
                nodes = graphDao.listNodesBySpace(scope.spaceId).map { it.toDomain() },
                edges = graphDao.listEdgesBySpace(scope.spaceId).map { it.toDomain() }
            )
            is GraphScope.Session -> {
                val session = sessionDao.getById(scope.sessionId)
                    ?: return GraphSnapshotResult.Error(notFoundError())
                val nodes = graphDao.listNodesBySession(scope.sessionId).map { it.toDomain() }
                val nodeIds = nodes.mapTo(linkedSetOf()) { it.id }
                val edges = graphDao.listEdgesBySpace(session.spaceId)
                    .map { it.toDomain() }
                    .filter { it.fromNodeId in nodeIds && it.toNodeId in nodeIds }
                buildResult(nodes, edges)
            }
        }
    }.getOrElse {
        GraphSnapshotResult.Error(storageError())
    }
```

`buildResult` must:

```text
return Empty(NoExtractedNodes) when nodes is empty
remove edges whose endpoints are absent
report removed edge count as invalidEdgeCount
return Ready for a non-empty valid snapshot
```

Keep the existing compatibility method:

```kotlin
suspend fun snapshot(spaceId: String = defaultSpaceId): GraphSnapshot
```

It must continue returning raw lists for the frozen current frontend.

- [ ] **Step 5: Wire SessionDao in DataModule**

Construct:

```kotlin
GraphRepository(
    graphDao = database.graphDao(),
    sessionDao = database.sessionDao()
)
```

- [ ] **Step 6: Run graph unit and instrumentation compilation**

Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests com.reversetutor.core.data.graph.GraphRepositoryTest :core:data:compileDebugAndroidTestKotlin --no-daemon --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit graph repository implementation**

```powershell
git add mobile-native/core/data
git commit -m "feat: query global and session graph snapshots"
```

## Task 3: Add Transactional Local Widget Layout Repository

**Files:**
- Modify: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/RepositoryContracts.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/HybridDaos.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/learning/WidgetLayoutRepositoryImpl.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/DataModule.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/DomainRepositoryIntegrationTest.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/learning/WidgetLayoutValidationTest.kt`
- Modify: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseDaoTest.kt`

- [ ] **Step 1: Write failing domain and validation tests**

Add the domain contract:

```kotlin
interface WidgetLayoutRepository {
    suspend fun load(spaceId: String): List<WidgetLayoutPreference>
    suspend fun saveLayout(spaceId: String, preferences: List<WidgetLayoutPreference>)
    suspend fun reset(spaceId: String)
}
```

Tests must reject:

```text
blank spaceId
preference spaceId mismatch
blank widgetId
duplicate widgetId
negative order
duplicate order
```

Tests must accept an empty list as reset-equivalent input.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests com.reversetutor.core.data.learning.WidgetLayoutValidationTest --no-daemon --stacktrace
```

Expected: compilation fails because the repository and validation do not exist.

- [ ] **Step 3: Add DAO batch operations**

Add to `LearningDao`:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertWidgetPreferences(
    preferences: List<WidgetLayoutPreferenceEntity>
)

@Query("DELETE FROM widget_layout_preferences WHERE spaceId = :spaceId")
suspend fun deleteWidgetPreferences(spaceId: String): Int
```

- [ ] **Step 4: Implement the Repository transaction**

Create `WidgetLayoutRepositoryImpl`:

```kotlin
class WidgetLayoutRepositoryImpl(
    private val database: ReverseTutorDatabase
) : WidgetLayoutRepository {
    override suspend fun load(spaceId: String): List<WidgetLayoutPreference> =
        database.learningDao()
            .listWidgetPreferences(requireSpaceId(spaceId))
            .map { it.toDomain() }

    override suspend fun saveLayout(
        spaceId: String,
        preferences: List<WidgetLayoutPreference>
    ) {
        val normalizedSpaceId = requireSpaceId(spaceId)
        validateCompleteLayout(normalizedSpaceId, preferences)
        database.withTransaction {
            database.learningDao().deleteWidgetPreferences(normalizedSpaceId)
            if (preferences.isNotEmpty()) {
                database.learningDao().upsertWidgetPreferences(
                    preferences.sortedBy { it.order }.map { it.toEntity() }
                )
            }
        }
    }

    override suspend fun reset(spaceId: String) {
        database.learningDao().deleteWidgetPreferences(requireSpaceId(spaceId))
    }
}
```

The implementation must never access `syncDao`.

- [ ] **Step 5: Wire the Repository**

Add to `DataModule`:

```kotlin
fun widgetLayoutRepository(context: Context): WidgetLayoutRepositoryImpl =
    WidgetLayoutRepositoryImpl(database(context.applicationContext))
```

Update `DomainRepositoryIntegrationTest` to assert the implementation satisfies `WidgetLayoutRepository`.

- [ ] **Step 6: Add Room transaction verification**

In `ReverseTutorDatabaseDaoTest`, verify:

```text
saveLayout writes the complete ordered list
a second save replaces removed widgets
hidden and size values survive reload
empty save clears the layout
reset clears the layout
sync_outbox remains empty throughout
invalid layout leaves the previous valid layout unchanged
```

- [ ] **Step 7: Run widget tests**

Run:

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :core:data:compileDebugAndroidTestKotlin --no-daemon --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit widget layout repository**

```powershell
git add mobile-native/core/domain mobile-native/core/data
git commit -m "feat: persist complete widget layouts locally"
```

## Task 4: Lock Existing Model, TurnRun, Sync, And Secret Boundaries

**Files:**
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/OnlineRepositoryAdaptersTest.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/migration/NativeExportRepositoryTest.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/migration/NativeImportRepositoryTest.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/background/BackgroundGenerationRepositoryTest.kt`
- Modify: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationRunCoordinatorTest.kt`

- [ ] **Step 1: Add explicit regression assertions**

Add tests proving:

```text
widget_layout is rejected by OnlineSyncTransport before the API is called
export JSON contains neither secretRef nor API key values
imported ProviderConnection and legacy LlmProfile have secretRef = null
background recovery retains the enqueue-time modelBindingId
retry retains turnId, modelBindingId, contextVersion, and snapshot identity
stale attempts cannot complete after a retry
```

- [ ] **Step 2: Run the targeted regression suite**

Run:

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :core:llm:testDebugUnitTest :core:remote:testDebugUnitTest :core:protocol:testDebugUnitTest --no-daemon --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Run static boundary checks**

Run:

```powershell
rg -n "当前聊天记录过少|无法生成节点|请再聊会天吧" mobile-native/core
rg -n "widget_layout" mobile-native/core/remote mobile-native/core/data/src/main/java/com/reversetutor/core/data/online
rg -n "secretRef|apiKey" mobile-native/core/protocol/src/main mobile-native/core/data/src/main/java/com/reversetutor/core/data/migration
```

Expected:

```text
no graph UI copy in core
no widget layout sync allowlist entry
secret fields appear only in explicit redaction or import-clearing code paths
```

- [ ] **Step 4: Commit regression locks**

```powershell
git add mobile-native/core
git commit -m "test: lock native backend protocol boundaries"
```

## Task 5: Full Verification And ADworkflo Evidence

**Files:**
- Create: `.adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001/worker_state.json`
- Create: `.adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001/verification_result.json`
- Create: `.adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001/review_findings.json`

- [ ] **Step 1: Run native backend verification**

Run:

```powershell
.\gradlew.bat :core:model:testDebugUnitTest :core:domain:testDebugUnitTest :core:protocol:testDebugUnitTest :core:llm:testDebugUnitTest :core:remote:testDebugUnitTest :core:data:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :core:data:compileDebugAndroidTestKotlin --no-daemon --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Run Python regression**

Run:

```powershell
py -m pytest tests/test_online_hybrid_api.py -q
py -m pytest -q --ignore=tests/test_project_homepage.py
```

Expected: PASS.

- [ ] **Step 3: Verify frontend freeze**

Capture the backend task change set and verify it contains no path under:

```text
mobile-native/app/
mobile-native/feature/
static/app/
mobile/
```

- [ ] **Step 4: Record artifacts**

Record:

```text
implemented contracts
verification commands and exact results
no real provider call
no frontend file changed
remaining risks
review findings
```

- [ ] **Step 5: Commit evidence**

```powershell
git add .adworkflow/artifacts/NATIVE-BACKEND-PROTOCOL-STABILIZE-001
git commit -m "docs: record backend protocol verification"
```

## Self-Review

- Spec coverage: graph scope/result, session provenance, local widget transaction, model/TurnRun locks, sync exclusion, secret redaction, verification, and frontend freeze are all mapped to tasks.
- Placeholder scan: no TBD, TODO, deferred implementation, or unspecified test steps remain.
- Type consistency: `GraphScope`, `GraphSnapshotResult`, `GraphEmptyReason`, `GraphSnapshot`, and `WidgetLayoutRepository` signatures match the approved specification.
- Schema impact: no Room entity field or database version change is planned.
