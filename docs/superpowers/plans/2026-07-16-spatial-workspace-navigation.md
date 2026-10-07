# Spatial Workspace Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the formal Weekly -> Home -> Global Graph -> Community horizontal workspace and a gesture-driven Challenge page above Home.

**Architecture:** Keep the existing four-page `HorizontalPager`. Render the Home slot as a two-page `VerticalPager` ordered Challenge then Home, with Home as the initial vertical page. Disable full-surface horizontal paging on Global Graph and drive the same pager from 24dp edge drag zones so graph pan/zoom keeps ownership of the canvas.

**Tech Stack:** Kotlin 1.9, Jetpack Compose BOM 2024.02.00, AndroidX Foundation Pager, coroutines, JUnit 4, Compose UI Test.

---

### Task 1: Freeze Workspace And Vertical Page State

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceViewModel.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`

- [ ] **Step 1: Write the failing state tests**

Add tests:

```kotlin
@Test
fun homeVerticalPagesPutChallengeAboveSessionHome() {
    assertEquals(
        listOf(WorkspaceVerticalPage.Challenge, WorkspaceVerticalPage.SessionHome),
        WorkspaceVerticalPage.entries
    )
    assertEquals(WorkspaceVerticalPage.SessionHome, WorkspaceUiState().verticalPage)
}

@Test
fun workspaceUsesAnExplicitStableHorizontalOrder() {
    assertEquals(
        listOf(
            WorkspacePage.WeeklyDashboard,
            WorkspacePage.SessionHome,
            WorkspacePage.GlobalGraph,
            WorkspacePage.Community
        ),
        WorkspacePage.FixedOrder
    )
}

@Test
fun challengeAndGraphEdgeGesturesLockTheCorrectPager() {
    val viewModel = WorkspaceViewModel()
    viewModel.onAction(WorkspaceUiAction.SetChallengeDragActive(true))
    assertFalse(viewModel.uiState.value.horizontalPagingEnabled)
    viewModel.onAction(WorkspaceUiAction.SetChallengeDragActive(false))
    viewModel.onAction(WorkspaceUiAction.SetGraphEdgePagingActive(true))
    assertFalse(viewModel.uiState.value.graphCanvasInteractionEnabled)
}

@Test
fun selectingChallengeKeepsHorizontalWorkspaceOnHome() {
    val viewModel = WorkspaceViewModel()
    viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.Challenge))
    assertEquals(WorkspacePage.SessionHome, viewModel.uiState.value.currentPage)
    assertEquals(WorkspaceVerticalPage.Challenge, viewModel.uiState.value.verticalPage)
}
```

- [ ] **Step 2: Run the tests and verify RED**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*WorkspaceViewModelTest" --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: Kotlin compilation fails because the vertical page and actions do not exist.

- [ ] **Step 3: Add the minimal state model**

Add:

```kotlin
enum class WorkspaceVerticalPage {
    Challenge,
    SessionHome
}

enum class WorkspacePage {
    WeeklyDashboard,
    SessionHome,
    GlobalGraph,
    Community;

    companion object {
        val FixedOrder: List<WorkspacePage> = listOf(
            WeeklyDashboard,
            SessionHome,
            GlobalGraph,
            Community
        )
    }
}

data class WorkspaceInteractionLocks(
    val composerInputActive: Boolean = false,
    val widgetDragActive: Boolean = false,
    val fullscreenGraphActive: Boolean = false,
    val challengeDragActive: Boolean = false,
    val graphEdgePagingActive: Boolean = false
) {
    val horizontalPagingEnabled: Boolean
        get() = !composerInputActive &&
            !widgetDragActive &&
            !fullscreenGraphActive &&
            !challengeDragActive

    val graphCanvasInteractionEnabled: Boolean
        get() = !graphEdgePagingActive
}

data class WorkspaceUiState(
    val pages: List<WorkspacePage> = WorkspacePage.FixedOrder,
    val currentPage: WorkspacePage = WorkspacePage.SessionHome,
    val verticalPage: WorkspaceVerticalPage = WorkspaceVerticalPage.SessionHome,
    val challengeCanReturnHome: Boolean = true,
    val interactionLocks: WorkspaceInteractionLocks = WorkspaceInteractionLocks(),
    val verticalScrollOffsets: Map<WorkspacePage, Int> = emptyMap()
)
```

Add reducer actions `SelectVerticalPage`, `SetChallengeExitBoundary`, `SetChallengeDragActive`, and `SetGraphEdgePagingActive`. `SelectVerticalPage` must also select `WorkspacePage.SessionHome`.

Extend `WorkspaceInteractionBindings` with:

```kotlin
fun onChallengeDragChanged(active: Boolean) {
    dispatch(WorkspaceUiAction.SetChallengeDragActive(active))
}

fun onGraphEdgePagingChanged(active: Boolean) {
    dispatch(WorkspaceUiAction.SetGraphEdgePagingActive(active))
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Use the Task 1 Step 2 command.

Expected: all `WorkspaceViewModelTest` tests pass.

- [ ] **Step 5: Commit the state contract**

```powershell
git add mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceViewModel.kt mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt
git commit -m "feat: model spatial workspace state"
```

### Task 2: Keep Challenge Inside The Workspace Host

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppNavigation.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/AppNavigationStateTest.kt`

- [ ] **Step 1: Write failing destination mapping tests**

```kotlin
@Test
fun challengeUsesTheHomeWorkspaceSlot() {
    assertEquals(WorkspacePage.SessionHome, AppDestination.Challenge.workspacePage)
    assertEquals(WorkspaceVerticalPage.Challenge, AppDestination.Challenge.workspaceVerticalPage)
    assertEquals(WorkspaceVerticalPage.SessionHome, AppDestination.Sessions.workspaceVerticalPage)
}

@Test
fun backFromChallengeReturnsToSessions() {
    val state = AppNavigationState().navigate(AppDestination.Challenge)
    val result = state.handleSystemBack()
    assertEquals(AppDestination.Sessions, result.state.current)
}
```

- [ ] **Step 2: Run the tests and verify RED**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*WorkspaceViewModelTest" --tests "*AppNavigationStateTest" --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: the Challenge workspace mappings are absent.

- [ ] **Step 3: Add destination mappings**

```kotlin
val AppDestination.workspaceVerticalPage: WorkspaceVerticalPage?
    get() = when (this) {
        AppDestination.Challenge -> WorkspaceVerticalPage.Challenge
        AppDestination.Sessions -> WorkspaceVerticalPage.SessionHome
        else -> null
    }
```

Map `AppDestination.Challenge.workspacePage` to `WorkspacePage.SessionHome`. Preserve the explicit `Challenge -> Sessions` back transition.

- [ ] **Step 4: Run the tests and verify GREEN**

Use the Task 2 Step 2 command.

- [ ] **Step 5: Commit destination mapping**

```powershell
git add mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppNavigation.kt mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt mobile-native/app/src/test/java/com/reversetutor/preview/shell/AppNavigationStateTest.kt
git commit -m "feat: map challenge above session home"
```

### Task 3: Add The Nested Vertical Pager

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/HomeChallengePagerHost.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePageIndicator.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt`
- Test: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt`

- [ ] **Step 1: Write the failing Compose test**

Create a tagged host test:

```kotlin
@Test
fun homePullDownRevealsChallengeAndReverseSwipeReturnsHome() {
    composeRule.setContent { SpatialWorkspaceTestContent() }
    composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
    composeRule.onNodeWithTag("workspace-home").performTouchInput {
        swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 450)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("workspace-challenge").assertIsDisplayed()
    composeRule.onNodeWithTag("workspace-challenge").performTouchInput {
        swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 450)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
}
```

- [ ] **Step 2: Compile device tests and verify RED**

```powershell
mobile-native\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: compilation fails because `SpatialWorkspaceTestContent` and the vertical host do not exist.

- [ ] **Step 3: Implement `HomeChallengePagerHost`**

Use a two-page `VerticalPager`:

```kotlin
@Composable
internal fun HomeChallengePagerHost(
    selectedPage: WorkspaceVerticalPage,
    userScrollEnabled: Boolean,
    onDragActiveChanged: (Boolean) -> Unit,
    onPageSelected: (WorkspaceVerticalPage) -> Unit,
    challengeContent: @Composable () -> Unit,
    homeContent: @Composable () -> Unit
) {
    val pages = WorkspaceVerticalPage.entries
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(selectedPage)
    ) { pages.size }
    LaunchedEffect(selectedPage) {
        val target = pages.indexOf(selectedPage)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onDragActiveChanged)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPageSelected(pages[it]) }
    }
    VerticalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { index ->
        when (pages[index]) {
            WorkspaceVerticalPage.Challenge -> challengeContent()
            WorkspaceVerticalPage.SessionHome -> homeContent()
        }
    }
}
```

- [ ] **Step 4: Render Home through the nested host**

Extend `WorkspacePagerHost` with `challengeContent` and `onVerticalPageSelected`. For the `SessionHome` horizontal slot, call `HomeChallengePagerHost`; other slots render `pageContent` unchanged.

- [ ] **Step 5: Compile device tests and verify GREEN**

Use the Task 3 Step 2 command.

- [ ] **Step 6: Commit the vertical pager**

```powershell
git add mobile-native/app/src/main/java/com/reversetutor/preview/shell/HomeChallengePagerHost.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt
git commit -m "feat: add challenge vertical pager"
```

### Task 4: Wire Spatial Navigation Through AppShell

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`

- [ ] **Step 1: Add a failing reducer synchronization test**

```kotlin
@Test
fun destinationChangesSynchronizeBothWorkspaceAxes() {
    val challenge = workspaceSelectionFor(AppDestination.Challenge)
    assertEquals(WorkspacePage.SessionHome, challenge.horizontal)
    assertEquals(WorkspaceVerticalPage.Challenge, challenge.vertical)
    val graph = workspaceSelectionFor(AppDestination.GlobalGraph)
    assertEquals(WorkspacePage.GlobalGraph, graph.horizontal)
    assertNull(graph.vertical)
}
```

- [ ] **Step 2: Run the JVM test and verify RED**

Use the Task 1 Step 2 command.

Expected: `workspaceSelectionFor` is missing.

- [ ] **Step 3: Add deterministic destination selection**

```kotlin
data class WorkspaceSelection(
    val horizontal: WorkspacePage,
    val vertical: WorkspaceVerticalPage?
)

fun workspaceSelectionFor(destination: AppDestination): WorkspaceSelection? =
    destination.workspacePage?.let {
        WorkspaceSelection(it, destination.workspaceVerticalPage)
    }
```

`AppShell` must synchronize both axes in `LaunchedEffect(navigationState.current)`. It must keep rendering `WorkspacePagerHost` while the current destination is Challenge and supply `renderDestination(AppDestination.Challenge)` as `challengeContent`.

When a vertical swipe settles, navigate to Challenge or Sessions. When a horizontal swipe settles away from Home, first normalize the vertical page to SessionHome, then navigate to the horizontal destination.

Render a four-position `WorkspacePageIndicator` from `WorkspacePage.FixedOrder` at the bottom of the horizontal host. Challenge never adds a fifth dot. Give it the test tag `workspace-page-indicator`.

- [ ] **Step 4: Run the JVM test and compile app**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*WorkspaceViewModelTest" :app:compileDebugKotlin --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: tests pass and app compilation succeeds.

- [ ] **Step 5: Commit AppShell wiring**

```powershell
git add mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt
git commit -m "feat: wire spatial workspace navigation"
```

### Task 5: Remove The Old Threshold Jump

**Files:**
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChallengeRoute.kt`
- Test: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt`

- [ ] **Step 1: Add a failing cancellation test**

```kotlin
@Test
fun shortHomePullCancelsWithoutOpeningChallenge() {
    composeRule.setContent { SpatialWorkspaceTestContent() }
    composeRule.onNodeWithTag("workspace-home").performTouchInput {
        down(center)
        moveBy(Offset(0f, 36f), delayMillis = 120)
        up()
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
    composeRule.onNodeWithTag("workspace-challenge").assertIsNotDisplayed()
}
```

- [ ] **Step 2: Run or compile the test and verify RED against the old behavior**

Run connected test when a device is present; otherwise compile and inspect the existing `pullDistance > 84f` handler as the known failing implementation.

- [ ] **Step 3: Remove direct vertical gesture navigation from `SessionsScreen`**

Delete the `pullDistance` state and the root `detectVerticalDragGestures` modifier. Keep `onOpenChallenge` for explicit buttons and joined challenge cards. The parent `VerticalPager` becomes the only full-page pull gesture owner.

Expose `onExitBoundaryChanged(Boolean)` from `ChallengeRoute`. Report `true` only when its vertical content has reached the boundary from which an upward spatial swipe may return to Home. Pass `challengeCanReturnHome` to `HomeChallengePagerHost.userScrollEnabled` while Challenge is selected; Home remains pullable only from its own top boundary.

- [ ] **Step 4: Compile and run focused tests**

```powershell
mobile-native\gradlew.bat :feature:chat:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the gesture cleanup**

```powershell
git add mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChallengeRoute.kt mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt
git commit -m "fix: let spatial host own challenge pull"
```

### Task 6: Add Graph Edge Paging

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/GraphEdgePagingOverlay.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`
- Modify: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt`

- [ ] **Step 1: Write failing pure target-selection tests**

```kotlin
@Test
fun graphEdgePagingTargetsOnlyAdjacentPages() {
    assertEquals(
        WorkspacePage.SessionHome,
        graphEdgeTarget(edge = GraphEdge.Left, distancePx = 130f, velocityPx = 0f)
    )
    assertEquals(
        WorkspacePage.Community,
        graphEdgeTarget(edge = GraphEdge.Right, distancePx = 130f, velocityPx = 0f)
    )
    assertNull(graphEdgeTarget(edge = GraphEdge.Left, distancePx = 12f, velocityPx = 0f))
}
```

- [ ] **Step 2: Run and verify RED**

Use the Task 1 Step 2 command.

- [ ] **Step 3: Implement the edge target resolver**

```kotlin
enum class GraphEdge { Left, Right }

internal fun graphEdgeTarget(
    edge: GraphEdge,
    distancePx: Float,
    velocityPx: Float,
    distanceThresholdPx: Float = 96f,
    velocityThresholdPx: Float = 900f
): WorkspacePage? {
    if (distancePx < distanceThresholdPx && velocityPx < velocityThresholdPx) return null
    return when (edge) {
        GraphEdge.Left -> WorkspacePage.SessionHome
        GraphEdge.Right -> WorkspacePage.Community
    }
}
```

- [ ] **Step 4: Add 24dp edge drag zones**

`GraphEdgePagingOverlay` must use two transparent 24dp-wide `draggable` zones. During a drag, call `pagerState.scrollBy` so the transition follows the finger. On stop, animate to Home or Community when the resolver returns a target, otherwise animate back to Global Graph. Report edge-drag active state through `WorkspaceInteractionBindings` so the graph canvas is not updated simultaneously.

When `currentPage == GlobalGraph`, set normal `HorizontalPager.userScrollEnabled` to false. Other workspace pages retain full-surface horizontal paging.

- [ ] **Step 5: Add a device test for both graph edges**

The test must verify left-edge drag shows Home, navigate back to Graph, then right-edge drag shows Community. A center drag on the graph must keep Global Graph visible.

- [ ] **Step 6: Run JVM and compile device tests**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*WorkspaceViewModelTest" :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit graph edge paging**

```powershell
git add mobile-native/app/src/main/java/com/reversetutor/preview/shell/GraphEdgePagingOverlay.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt mobile-native/app/src/androidTest/java/com/reversetutor/preview/WorkspaceSpatialNavigationDeviceTest.kt
git commit -m "feat: add graph edge workspace paging"
```

### Task 7: Frontend Slice 0 Regression

**Files:**
- Verify only; do not build release APK.

- [ ] **Step 1: Run focused unit tests**

```powershell
mobile-native\gradlew.bat :app:testDebugUnitTest --tests "*Workspace*" :feature:chat:testDebugUnitTest --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: all focused tests pass.

- [ ] **Step 2: Run app lint and debug build**

```powershell
mobile-native\gradlew.bat :app:lintDebug :app:assembleDebug --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run connected tests when available**

```powershell
adb devices
mobile-native\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: spatial navigation tests pass on a connected device. Record unavailable device execution honestly.

- [ ] **Step 4: Inspect owned scope**

```powershell
git diff --check
git status --short
```

Expected: no release APK, protocol, Python, Room schema, DAO or SecretStore edits from this plan.
