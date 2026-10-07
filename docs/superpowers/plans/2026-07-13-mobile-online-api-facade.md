# Mobile Online API Facade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a typed, mockable Android frontend facade for every non-community online V1 endpoint so UI work can continue before the FastAPI implementation is complete.

**Architecture:** `core:remote` exposes capability-specific APIs and canonical wire models over the shared transport. `core:data` maps those models into domain repositories and deterministic contract mocks. `HybridAppGraph` selects local-only, mock, or HTTP services at the application boundary; feature code never sees HTTP DTOs.

**Tech Stack:** Kotlin 1.9, Android library modules, kotlinx.serialization JSON, coroutines, JUnit 4, Gradle 8.2.

---

### Task 1: Capability Interfaces

**Files:**
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineCapabilities.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineApi.kt`
- Create: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/OnlineCapabilitiesTest.kt`

- [ ] **Step 1: Write the failing capability test**

```kotlin
@Test fun onlineApiComposesAllV1Capabilities() {
    assertTrue(ActivityApi::class.java.isAssignableFrom(OnlineApi::class.java))
    assertTrue(ContentApi::class.java.isAssignableFrom(OnlineApi::class.java))
    assertTrue(SyncApi::class.java.isAssignableFrom(OnlineApi::class.java))
    assertTrue(InsightApi::class.java.isAssignableFrom(OnlineApi::class.java))
    assertTrue(ReleaseApi::class.java.isAssignableFrom(OnlineApi::class.java))
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew.bat :core:remote:testDebugUnitTest --tests "*OnlineCapabilitiesTest" --no-daemon --max-workers=1 '-Pkotlin.incremental=false'`

Expected: Kotlin compilation fails because the capability interfaces do not exist.

- [ ] **Step 3: Add minimal capability interfaces**

```kotlin
interface ContentApi {
    suspend fun contentFeed(cursor: String? = null, limit: Int = 20, types: Set<String> = emptySet(), etag: String? = null): OnlineResult<ContentFeedPage>
    suspend fun contentDetail(slug: String): OnlineResult<OnlineContentDetail>
}
interface ActivityApi { /* list, detail, join, progress, leave, leaderboard */ }
interface SyncApi { suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse>; suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse> }
interface InsightApi { suspend fun weeklyInsight(request: WeeklyInsightRequest): OnlineResult<WeeklyInsight> }
interface ReleaseApi { suspend fun latestRelease(): OnlineResult<OnlineRelease> }
interface OnlineApi : ActivityApi, ContentApi, SyncApi, InsightApi, ReleaseApi
```

Activity methods retain the current method names but accept pagination defaults, add `leaveActivity`, and return page wrappers where the V1 response is paginated.

- [ ] **Step 4: Run the capability test and existing remote tests**

Expected: capability test passes; existing tests may fail only where signatures intentionally changed and are updated in Task 2.

### Task 2: Canonical Content and Activity HTTP

**Files:**
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineContentModels.kt`
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineActivityModels.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/HttpOnlineApi.kt`
- Modify: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt`

- [ ] **Step 1: Add failing content request and decoding tests**

Tests assert:

```kotlin
assertEquals("GET", request.method)
assertEquals("/api/v1/content/feed?cursor=next&limit=10&types=announcement%2Cpublic_interest", request.path)
assertEquals("feed-v28", request.headers["If-None-Match"])
assertEquals("public-028", result.value.items.single().id)
```

Also test `/api/v1/content/{slug}`, nullable cover/body assets, and malformed required fields.

- [ ] **Step 2: Run the content tests and verify RED**

Expected: compilation fails because content methods and models are missing.

- [ ] **Step 3: Implement content wire models and JSON decoders**

Models mirror `IllustrationConfig`, `AssetRef`, `ContentFeedItem`, `ContentFeedResponse`, and `ContentDetail` from OpenAPI. Query values and path segments use the existing URL encoder. Public content requests do not require a token; an optional `If-None-Match` header is forwarded.

- [ ] **Step 4: Add failing activity pagination and leave tests**

Tests assert the canonical paths, cursor/limit parameters, request identity body, `X-Request-Id`, decoded activity description/state/template fields, participation state, and leaderboard ranking.

- [ ] **Step 5: Run activity tests and verify RED**

Expected: failures show missing pagination response fields and `leaveActivity`.

- [ ] **Step 6: Implement activity page models and missing endpoint**

```kotlin
override suspend fun leaveActivity(activityId: String, write: OnlineWriteIdentity) =
    request("DELETE", "/api/v1/activities/${activityId.pathSegment()}/participation", write.toJson()) {
        it.toActivityParticipation()
    }
```

Use the shared canonical error decoder for all default responses.

- [ ] **Step 7: Run all remote tests**

Run: `./gradlew.bat :core:remote:testDebugUnitTest --no-daemon --max-workers=1 '-Pkotlin.incremental=false'`

Expected: all remote tests pass.

### Task 3: Domain Repositories and Contract Mock

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/OnlineContentContracts.kt`
- Modify: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/RepositoryContracts.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/online/ContractMockOnlineApi.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/online/OnlineRepositoryAdapters.kt`
- Modify: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/OnlineRepositoryAdaptersTest.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/ContractMockOnlineApiTest.kt`

- [ ] **Step 1: Add failing repository mapping tests**

Tests cover content success/empty/failure, activity list/detail/join/leave/progress/leaderboard, and preservation of stable error code plus retryability.

```kotlin
val result = repository.feed(limit = 20)
assertEquals("public-028", (result as OnlineData.Content).value.items.single().id)
```

- [ ] **Step 2: Run data tests and verify RED**

Expected: compilation fails because content contracts and repository methods do not exist.

- [ ] **Step 3: Add domain-facing result and content/activity contracts**

```kotlin
sealed interface OnlineData<out T> {
    data class Content<T>(val value: T) : OnlineData<T>
    data class Failure(val code: String, val retryable: Boolean) : OnlineData<Nothing>
}
```

Domain content models contain display data only and do not import `core:remote`.

- [ ] **Step 4: Implement repository adapters**

Map every `OnlineResult.Success` to domain data and every `OnlineResult.Failure` to `OnlineData.Failure`. Do not turn failures into empty content.

- [ ] **Step 5: Add failing deterministic mock tests**

Tests assert the canonical fixture identities `public-028` and `focus-week-2026-07`, stable pagination cursors, joined/left state transitions, monotonic progress, and the documented release metadata.

- [ ] **Step 6: Implement `ContractMockOnlineApi`**

Seed values match `docs/contracts/mock-online-v1.json`. The mock keeps participation state in memory and implements every capability method without network access.

- [ ] **Step 7: Run domain and data tests**

Run: `./gradlew.bat :core:domain:testDebugUnitTest :core:data:testDebugUnitTest --no-daemon --max-workers=1 '-Pkotlin.incremental=false'`

Expected: all tests pass.

### Task 4: App Graph Selection and Regression

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/HybridAppGraphConfigurationTest.kt`
- Modify: `mobile-native/app/build.gradle.kts` only if the existing unit-test dependencies cannot instantiate the graph configuration.

- [ ] **Step 1: Add failing configuration tests**

```kotlin
assertEquals(HybridOnlineMode.LocalOnly, HybridOnlineConfiguration.LocalOnly.mode)
assertEquals(HybridOnlineMode.ContractMock, HybridOnlineConfiguration.ContractMock.mode)
assertEquals("https://api.example.com", HybridOnlineConfiguration.Http("https://api.example.com").baseUrl)
```

- [ ] **Step 2: Run the test and verify RED**

Expected: compilation fails because online modes do not exist.

- [ ] **Step 3: Add explicit online modes and service wiring**

`LocalOnly` creates no online services, `ContractMock` uses `ContractMockOnlineApi`, and `Http` uses `HttpOnlineApi`. Both online modes expose content, activity, sync, insight, and release repositories through `HybridOnlineServices`.

- [ ] **Step 4: Run targeted and full regression checks**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest :core:domain:testDebugUnitTest :core:remote:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:lintDebug :core:remote:lintDebug --no-daemon --max-workers=1 '-Pkotlin.incremental=false'
py -m pytest tests/test_online_hybrid_api.py -q
```

Expected: Gradle `BUILD SUCCESSFUL`; pytest reports 7 passed.

- [ ] **Step 5: Inspect scope**

Run `git diff --check` and inspect only files listed in this plan. Confirm no community API, Room schema, PWA, UI layout, or backend behavior changed.
