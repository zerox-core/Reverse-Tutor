# Sync Push And WorldTree Blockers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hard-cut sync push to the canonical V1 response and add transaction-safe WorldTree persistence through Room schema version 4.

**Architecture:** FastAPI and Android Remote will share the canonical sync item identity and boolean acceptance contract without a legacy decoder. WorldTree domain types live in `core:model`; a versioned codec, three Room entities, one aggregate DAO, and a transactional repository live in `core:data`. Migration 3-to-4 only creates WorldTree tables and indexes.

**Tech Stack:** Python 3, FastAPI, Pydantic, pytest, Kotlin, kotlinx.serialization JSON, Room 2.6.1, JUnit 4, AndroidX Room migration testing.

---

### Task 1: Lock And Implement The Canonical FastAPI Sync Contract

**Files:**
- Modify: `tests/test_online_hybrid_api.py`
- Modify: `adapters/online/models.py`
- Modify: `adapters/online/service.py`

- [ ] **Step 1: Replace sync assertions with canonical response assertions**

Add `envelopeId` to every sync request item and assert exact item shapes:

```python
assert body["items"][0] == {
    "envelopeId": "env-ok",
    "entityId": "plan-1",
    "accepted": True,
    "remoteRevision": 2,
    "retryable": False,
}
assert body["items"][1] == {
    "envelopeId": "env-rejected",
    "entityId": "message-1",
    "accepted": False,
    "errorCode": "entity_type_not_syncable",
    "retryable": False,
}
assert all("status" not in item for item in body["items"])
```

- [ ] **Step 2: Run focused Python tests and verify the old implementation fails**

Run: `py -m pytest tests/test_online_hybrid_api.py -k sync_push -v`

Expected: FAIL because `SyncItem` rejects or omits `envelopeId` and the service emits `status`.

- [ ] **Step 3: Add the required request identity and canonical result builder**

Update `SyncItem`:

```python
class SyncItem(CamelModel):
    envelope_id: str = Field(min_length=1)
    entity_id: str = Field(min_length=1)
    entity_type: str = Field(min_length=1)
    revision: int = Field(ge=0)
    idempotency_key: str = Field(min_length=1)
    payload: dict[str, Any] = Field(default_factory=dict)
    deleted_at_epoch_millis: int | None = None
```

Return `accepted=True` for stored items and `accepted=False` for rejected or failed
items. Always echo `item.envelope_id`; do not emit `status`.

- [ ] **Step 4: Verify isolation and idempotency**

Run: `py -m pytest tests/test_online_hybrid_api.py -k sync_push -v`

Expected: all sync push tests PASS, including failure isolation and identical replay.

- [ ] **Step 5: Commit the FastAPI contract change**

```powershell
git add adapters/online/models.py adapters/online/service.py tests/test_online_hybrid_api.py
git commit -m "fix: canonicalize sync push item results"
```

### Task 2: Remove Android Legacy Sync Response Decoding

**Files:**
- Modify: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/HttpOnlineApi.kt`
- Test: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/OnlineRepositoryAdaptersTest.kt`

- [ ] **Step 1: Make the Remote test use only canonical response fields**

Use this response in the success test:

```kotlin
"""{"cursor":"7","items":[{"envelopeId":"envelope-1","entityId":"plan-1","accepted":true,"remoteRevision":8,"errorCode":null,"retryable":false}]}"""
```

Add a test returning the legacy item below and assert `OnlineResult.ProtocolError`:

```kotlin
"""{"cursor":"7","items":[{"entityId":"plan-1","status":"accepted","remoteRevision":8,"retryable":false}]}"""
```

- [ ] **Step 2: Run the Remote test and verify the legacy test fails**

Run: `./gradlew.bat :core:remote:testDebugUnitTest --tests "*HttpOnlineApiTest" --no-daemon`

Expected: FAIL because the existing decoder still derives acceptance from `status` and
can synthesize envelope identity from request order.

- [ ] **Step 3: Require canonical fields in the decoder**

Decode each result using required accessors:

```kotlin
SyncPushItemResult(
    envelopeId = item.requiredString("envelopeId"),
    entityId = item.requiredString("entityId"),
    accepted = item.requiredBoolean("accepted"),
    remoteRevision = item.optionalLong("remoteRevision"),
    errorCode = item.optionalString("errorCode"),
    retryable = item.requiredBoolean("retryable")
)
```

Delete fallback logic based on `status`, result index, or request envelope IDs.

- [ ] **Step 4: Run Remote and adapter tests**

Run: `./gradlew.bat :core:remote:testDebugUnitTest :core:data:testDebugUnitTest --tests "*OnlineRepositoryAdaptersTest" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the Android contract hard cut**

```powershell
git add mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/HttpOnlineApi.kt mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/OnlineRepositoryAdaptersTest.kt
git commit -m "fix: reject legacy sync push responses"
```

### Task 3: Add WorldTree Domain Types And Versioned Payload Codec

**Files:**
- Create: `mobile-native/core/model/src/main/java/com/reversetutor/core/model/WorldTreeModels.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/worldtree/WorldTreePayloadCodec.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/worldtree/WorldTreePayloadCodecTest.kt`
- Modify: `mobile-native/core/data/build.gradle.kts`

- [ ] **Step 1: Write round-trip tests for all seven payload types**

Build one `WorldTreeSectionPayload` value for each type and assert:

```kotlin
val encoded = codec.encode(schemaVersion = 1, type = type, payload = payload)
val decoded = codec.decode(schemaVersion = 1, type = type, payloadJson = encoded)
assertEquals(payload, decoded)
```

Also assert schema version `2` and a payload/type mismatch throw
`WorldTreePayloadCodecException`.

- [ ] **Step 2: Run the codec test and verify it fails to compile**

Run: `./gradlew.bat :core:data:testDebugUnitTest --tests "*WorldTreePayloadCodecTest" --no-daemon`

Expected: FAIL because the WorldTree domain and codec do not exist.

- [ ] **Step 3: Define the frozen domain model**

Create immutable data classes and enums matching
`docs/contracts/world-tree-schema-v1.json`, including:

```kotlin
enum class WorldTreeMode { Learning, Review, Companion }
enum class WorldTreeDraftState { Draft, Ready, Archived }
enum class WorldTreeSectionType {
    StudentRole, LearningGoal, StudySchedule, PortraitSystem,
    StoryPlot, SourceLibrary, Custom
}
sealed interface WorldTreeSectionPayload
```

Use nullable timestamps and references exactly where the schema permits null. Preserve
list order and use data classes so codec round-trip equality is structural.

- [ ] **Step 4: Implement an explicit V1 codec**

Add `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")` to
`core:data`. Encode/decode with explicit `buildJsonObject` and required typed accessors.
Map enum values to the seven frozen `snake_case` wire values. Throw
`WorldTreePayloadCodecException` for unsupported versions, missing fields, wrong JSON
types, or a payload class that does not match its section type.

- [ ] **Step 5: Run model and codec tests**

Run: `./gradlew.bat :core:model:testDebugUnitTest :core:data:testDebugUnitTest --tests "*WorldTreePayloadCodecTest" --no-daemon`

Expected: PASS for seven round trips and invalid input cases.

- [ ] **Step 6: Commit domain and codec**

```powershell
git add mobile-native/core/model/src/main/java/com/reversetutor/core/model/WorldTreeModels.kt mobile-native/core/data/src/main/java/com/reversetutor/core/data/worldtree/WorldTreePayloadCodec.kt mobile-native/core/data/src/test/java/com/reversetutor/core/data/worldtree/WorldTreePayloadCodecTest.kt mobile-native/core/data/build.gradle.kts
git commit -m "feat: add world tree domain and payload codec"
```

### Task 4: Add Room Version 4 Schema And Migration

**Files:**
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/WorldTreeEntities.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/WorldTreeDao.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/SchemaPolicyTest.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration3To4Test.kt`
- Create: `mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/4.json`

- [ ] **Step 1: Write the 3-to-4 preservation migration test**

Create a version 3 database, insert a space, session, message, source, graph node, and
graph edge, then migrate and assert each original row still exists with unchanged
values. Also query `sqlite_master` for all three WorldTree tables and their required
indexes.

- [ ] **Step 2: Update schema policy expectations and verify failure**

Change the policy assertions to:

```kotlin
assertEquals(4, DatabaseSchema.version)
assertEquals(3, DatabaseSchema.migrations.size)
assertEquals(listOf(1, 2, 3), DatabaseSchema.migrations.map { it.startVersion })
assertEquals(listOf(2, 3, 4), DatabaseSchema.migrations.map { it.endVersion })
```

Run: `./gradlew.bat :core:data:testDebugUnitTest --tests "*SchemaPolicyTest" --no-daemon`

Expected: FAIL while the database remains version 3.

- [ ] **Step 3: Add entities and aggregate DAO**

Define:

```kotlin
@Entity(
    tableName = "world_tree_drafts",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("spaceId"), Index(value = ["sessionId"], unique = true)]
)
data class WorldTreeDraftEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val sessionId: String?,
    val templateId: String?,
    val title: String,
    val mode: String,
    val schemaVersion: Int,
    val state: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "world_tree_sections",
    foreignKeys = [ForeignKey(
        entity = WorldTreeDraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["draftId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("draftId"), Index(value = ["draftId", "orderIndex"], unique = true)]
)
data class WorldTreeSectionEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val type: String,
    val title: String,
    val orderIndex: Int,
    val payloadJson: String,
    val required: Boolean,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "world_tree_source_cross_ref",
    primaryKeys = ["draftId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = WorldTreeDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sourceId"), Index(value = ["draftId", "orderIndex"], unique = true)]
)
data class WorldTreeSourceCrossRefEntity(
    val draftId: String,
    val sourceId: String,
    val orderIndex: Int
)
```

The DAO exposes `Flow<WorldTreeDraftAggregate?>`, direct aggregate reads, upserts,
ordered section/source reads, and delete/update primitives used inside repository
transactions.

- [ ] **Step 4: Implement migration 3-to-4**

Set `DatabaseSchema.version = 4`, add `migration3To4`, append it to `migrations`, and
execute only `CREATE TABLE` and `CREATE INDEX` statements for the WorldTree schema.
Register entities and `worldTreeDao()` in `ReverseTutorDatabase`.

- [ ] **Step 5: Generate and validate Room schema**

Run: `./gradlew.bat :core:data:assembleDebug --no-daemon`

Expected: BUILD SUCCESSFUL and Room writes schema `4.json`.

Run: `./gradlew.bat :core:data:compileDebugAndroidTestKotlin --no-daemon`

Expected: BUILD SUCCESSFUL for the migration test.

- [ ] **Step 6: Commit schema and migration**

```powershell
git add mobile-native/core/data/src/main/java/com/reversetutor/core/data/local mobile-native/core/data/src/test/java/com/reversetutor/core/data/SchemaPolicyTest.kt mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration3To4Test.kt mobile-native/core/data/schemas
git commit -m "feat: add room world tree schema"
```

### Task 5: Implement Transactional WorldTree Repository

**Files:**
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/worldtree/WorldTreeRepository.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/DataModule.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/worldtree/WorldTreeRepositoryContractTest.kt`

- [ ] **Step 1: Write repository contract tests against in-memory Room**

Cover these cases with a real `ReverseTutorDatabase`:

```text
create and observe a partial Draft
upsert all seven section payloads and survive repository recreation
update title and archive
reorder an exact section ID set
reject duplicate, unknown, and missing reorder IDs without mutation
reject removal of non-Custom sections
replace ordered source links and reject unknown sources without mutation
attach to an existing session
reject a missing session and a session already attached to another draft
```

- [ ] **Step 2: Compile tests and verify repository symbols are missing**

Run: `./gradlew.bat :core:data:compileDebugAndroidTestKotlin --no-daemon`

Expected: FAIL because `WorldTreeRepository` and `DataModule.worldTreeRepository` do
not exist.

- [ ] **Step 3: Implement mapping and validation**

Map aggregates to `WorldTreeDraft` through `WorldTreePayloadCodec`. Normalize required
IDs and titles with `trim()`. Validate section ownership, unique IDs/orders, exact
reorder sets, Custom-only deletion, unique source IDs, and existing FK targets before
starting writes.

- [ ] **Step 4: Implement every compound mutation with `withTransaction`**

Use the frozen public surface:

```kotlin
fun observeDraft(draftId: String): Flow<WorldTreeDraft?>
suspend fun createDraft(command: CreateWorldTreeDraftCommand): WorldTreeDraft
suspend fun updateTitle(draftId: String, title: String): WorldTreeDraft
suspend fun upsertSection(draftId: String, section: WorldTreeSection): WorldTreeDraft
suspend fun reorderSections(draftId: String, sectionIds: List<String>): WorldTreeDraft
suspend fun removeCustomSection(draftId: String, sectionId: String): WorldTreeDraft
suspend fun replaceSourceLinks(draftId: String, sourceIds: List<String>): WorldTreeDraft
suspend fun attachToSession(draftId: String, sessionId: String): WorldTreeDraft
suspend fun archive(draftId: String)
```

After each mutation, re-read and return the committed aggregate. Add
`DataModule.worldTreeRepository(context)` using the shared database instance.

- [ ] **Step 5: Compile and run available repository verification**

Run: `./gradlew.bat :core:data:testDebugUnitTest :core:data:compileDebugAndroidTestKotlin --no-daemon`

Expected: BUILD SUCCESSFUL. The device-only contract tests must compile; execute them
when an emulator/device is available.

- [ ] **Step 6: Commit repository implementation**

```powershell
git add mobile-native/core/data/src/main/java/com/reversetutor/core/data/worldtree mobile-native/core/data/src/main/java/com/reversetutor/core/data/DataModule.kt mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/worldtree/WorldTreeRepositoryContractTest.kt
git commit -m "feat: persist world tree drafts transactionally"
```

### Task 6: Contract And Regression Verification

**Files:**
- Modify only if a verification failure exposes a defect in files changed by Tasks 1-5.

- [ ] **Step 1: Validate Python online behavior**

Run: `py -m pytest tests/test_online_hybrid_api.py -q`

Expected: all online hybrid API tests PASS.

- [ ] **Step 2: Validate Kotlin unit tests**

Run:

```powershell
./gradlew.bat :core:model:testDebugUnitTest :core:domain:testDebugUnitTest :core:protocol:testDebugUnitTest :core:llm:testDebugUnitTest :core:remote:testDebugUnitTest :core:data:testDebugUnitTest --no-daemon --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Validate Android migration and repository test compilation**

Run: `./gradlew.bat :core:data:compileDebugAndroidTestKotlin --no-daemon --stacktrace`

Expected: BUILD SUCCESSFUL. If an emulator is connected, additionally run
`./gradlew.bat :core:data:connectedDebugAndroidTest --no-daemon` and require PASS.

- [ ] **Step 4: Run the Python regression suite**

Run: `py -m pytest -q --ignore=tests/test_project_homepage.py`

Expected: all existing tests PASS with no real network or LLM calls.

- [ ] **Step 5: Check scope and whitespace**

Run: `git diff --check`

Expected: no whitespace errors. Review `git status --short` and confirm no Compose,
feature, PWA, content, activity authentication, error-standardization, leave-activity,
or community files were changed by this implementation.

- [ ] **Step 6: Record final implementation commit if fixes remain uncommitted**

Stage only files belonging to this plan and commit with:

```powershell
git commit -m "test: verify sync and world tree contracts"
```
