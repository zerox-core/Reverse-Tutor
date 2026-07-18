# Phase 12 Real-device Visual and Interaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved native Android real-device visual and interaction pass without modifying frozen repository, database, protocol, or export contracts.

**Architecture:** Keep shared colors and typography in `core:design`; keep view-only draft/editor state inside `feature:chat`; keep workspace gesture ownership and one-shot navigation state in `app:shell`. Existing repositories remain consumers only. New interactions expose callbacks and local presentation state, while durable mutations remain deferred.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Foundation pagers/gestures/animation, JUnit 4, Android instrumentation, Gradle.

## Global Constraints

- Target only `mobile-native/app/src/main`, `mobile-native/feature/*`, and `mobile-native/core/design` UI code.
- Do not change Room entities, DAO queries, migrations, repository contracts, protocol schemas, export payloads, or secret storage.
- Keep system font selection and device display settings untouched.
- Verify on Huawei Mate 60 at native `1216x2688 / 520 dpi`.
- Use test-first RED/GREEN cycles for every behavior change.
- Preserve challenge-detail and announcement frame-free material styling.

---

### Task 1: Neutral Grouped Palette and Readable Type Tokens

**Files:**
- Modify: `mobile-native/core/design/src/main/java/com/reversetutor/core/design/FormalDesign.kt`
- Modify: `mobile-native/core/design/src/test/java/com/reversetutor/core/design/FormalDesignTokensTest.kt`

**Interfaces:**
- Produces: `FormalColors.Background`, `Surface`, `SurfaceSubtle`, `SurfaceElevated`, `Ink`, `Muted`, `Tertiary`, `Border`, `BorderStrong`, and unchanged semantic typography helpers.

- [ ] **Step 1: Write failing palette assertions**

```kotlin
@Test
fun formalPaletteUsesNeutralGroupedSurfaces() {
    assertEquals(Color(0xFFF2F2F7), FormalColors.Background)
    assertEquals(Color.White, FormalColors.Surface)
    assertEquals(Color(0xFFF7F7FA), FormalColors.SurfaceSubtle)
    assertEquals(Color(0xFF1C1C1E), FormalColors.Ink)
    assertEquals(Color(0xFF6E6E73), FormalColors.Muted)
    assertEquals(Color(0xFF8E8E93), FormalColors.Tertiary)
    assertEquals(Color(0xFFD1D1D6), FormalColors.Border)
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `gradlew.bat :core:design:testDebugUnitTest --tests "com.reversetutor.core.design.FormalDesignTokensTest"`

Expected: FAIL because `SurfaceSubtle`/`Tertiary` do not exist and old colors differ.

- [ ] **Step 3: Implement the neutral token set**

```kotlin
object FormalColors {
    val Background = Color(0xFFF2F2F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF7F7FA)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val Primary = Color(0xFF2E5CDE)
    val PrimarySoft = Color(0xFFE8EEFF)
    val Ink = Color(0xFF1C1C1E)
    val Muted = Color(0xFF6E6E73)
    val Tertiary = Color(0xFF8E8E93)
    val Border = Color(0xFFD1D1D6)
    val BorderStrong = Color(0xFFB9BAC0)
}
```

- [ ] **Step 4: Run token tests and dependent feature compilation**

Run: `gradlew.bat :core:design:testDebugUnitTest :feature:chat:compileDebugKotlin :feature:memory:compileDebugKotlin`

Expected: PASS.

### Task 2: Home Indicators, Public Card, Avatar, and Long-press Sheet

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePageIndicator.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/HomeChallengePagerHost.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalHomeScreen.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt`
- Modify: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/FormalHomeUiStateTest.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`

**Interfaces:**
- Produces: `TransientIndicatorSpec(holdMillis = 700, fadeMillis = 240)`, `FormalHomeSessionUi.avatarLabel`, and `HomeSessionAction`.

- [ ] **Step 1: Add failing state and layout tests**

```kotlin
@Test
fun homeSessionRowsExposeStableAvatarAndActions() {
    val ui = sessionListState.toFormalHomeUiState(publicContent())
    assertEquals("高", ui.visibleSessions.single().avatarLabel)
    assertEquals(listOf(Rename, Pin, Export, Delete), HomeSessionAction.entries)
}

@Test
fun transientIndicatorTimingUsesApprovedMotion() {
    assertEquals(700L, TransientIndicatorSpec.HoldMillis)
    assertEquals(240, TransientIndicatorSpec.FadeMillis)
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `gradlew.bat :feature:chat:testDebugUnitTest --tests "*FormalHomeUiStateTest" :app:testDebugUnitTest --tests "*WorkspaceViewModelTest"`

Expected: FAIL for missing avatar/action/timing contracts.

- [ ] **Step 3: Implement transient overlay indicators**

Use `pagerState.isScrollInProgress`, `interactionLocks.challengeDragActive`, `AnimatedVisibility`, `fadeIn()`, and `fadeOut(tween(240))`. Keep indicators in overlay `Box` layers so visibility does not move page content. Remove the always-visible fill from `ChallengePullHandle` while retaining its gesture target.

- [ ] **Step 4: Implement home card interaction presentation**

Use `combinedClickable` for session rows, a 36dp avatar frame, and a `ModalBottomSheet` containing rename, pin/unpin, export, and destructive delete rows. Public-interest cards remain pressable in every state; available invokes navigation, unavailable shows its local state message without backend work.

- [ ] **Step 5: Increase vertical challenge capture threshold**

Change the dedicated pull trigger from `72.dp` to `112.dp` and provide a `PagerDefaults.flingBehavior` positional threshold of `0.65f` to `HomeChallengePagerHost`.

- [ ] **Step 6: Run feature/app unit tests**

Run: `gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest`

Expected: PASS.

### Task 3: Preset Carousel, Typography, and Shared Local Editor

**Files:**
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalDraftEditorScreen.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FigmaNewSessionScreen.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalPresetModels.kt`
- Modify: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/FormalPresetModelsTest.kt`
- Modify: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/FormalBatch2ScreenshotTest.kt`

**Interfaces:**
- Produces: `FormalDraftField`, `FormalDraftEditorState`, `FormalPresetEpisode`, and `FormalLearningPreset.presentationEpisodes()`.
- Consumes: neutral tokens from Task 1.

- [ ] **Step 1: Add failing episode/editor model tests**

```kotlin
@Test
fun everyPresetProvidesThreePresentationEpisodes() {
    FormalLearningPresets.all.forEach { preset ->
        assertEquals(3, preset.presentationEpisodes().size)
        assertEquals(1, preset.presentationEpisodes().first().number)
    }
}

@Test
fun editorStateUpdatesOnlyTheSelectedField() {
    val state = FormalDraftEditorState.from(FormalLearningPresets.all.first())
    val changed = state.update(FormalDraftField.Goal, "新的学习目标")
    assertEquals("新的学习目标", changed.value(FormalDraftField.Goal))
    assertEquals(state.value(FormalDraftField.Identity), changed.value(FormalDraftField.Identity))
}
```

- [ ] **Step 2: Run focused model tests and confirm RED**

Run: `gradlew.bat :feature:chat:testDebugUnitTest --tests "*FormalPresetModelsTest"`

Expected: FAIL for missing episode and editor types.

- [ ] **Step 3: Implement local presentation models**

Create three presentation episodes per preset using the existing first episode plus two local-only follow-up/review stages. Keep drawable references in `feature:chat`; do not add protocol fields.

- [ ] **Step 4: Replace static episode content with `HorizontalPager`**

Use `rememberPagerState { episodes.size }`, `HorizontalPager`, and indicator dots driven by `currentPage`/`currentPageOffsetFraction`. Ensure each page has a stable 132dp height and swipes independently of the vertical list.

- [ ] **Step 5: Rebalance preset typography and identity card**

Set identity height to 132dp; use 18sp title, 13sp body/learner line, 12sp metadata, and explicit 18-24sp line heights. Replace 8-10sp primary copy throughout detail cards.

- [ ] **Step 6: Add the shared local editor route**

Every preset/custom field opens `FormalDraftEditorScreen(field, value, onValueChange, onSave, onBack)`. Save updates the in-memory `FormalDraftEditorState` and returns. Name, field rows, add-field, and preview surfaces use real `onClick` handlers.

- [ ] **Step 7: Run unit and screenshot instrumentation tests**

Run: `gradlew.bat :feature:chat:testDebugUnitTest :app:compileDebugAndroidTestKotlin`

Expected: PASS.

### Task 4: One-shot Challenge Join Navigation

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/ChallengeRuntimeCoordinatorTest.kt`
- Modify: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/FormalHomeUiStateTest.kt`

**Interfaces:**
- Produces: `NewSessionLaunchRequest(id: Long, context: NewSessionLaunchContext)` and `NewSessionLaunchContext.Challenge`.

- [ ] **Step 1: Add failing one-shot request tests**

```kotlin
@Test
fun challengeJoinCreatesAUniqueHomeSheetRequest() {
    val first = NewSessionLaunchRequest.challenge(1L)
    val second = NewSessionLaunchRequest.challenge(2L)
    assertEquals(NewSessionLaunchContext.Challenge, first.context)
    assertNotEquals(first.id, second.id)
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*ChallengeRuntimeCoordinatorTest"`

Expected: FAIL for missing request types.

- [ ] **Step 3: Implement request propagation**

After successful `challengeRuntimeCoordinator.join()`, create a new request id, select `SessionHome`, and navigate to `Sessions`. Pass the request to `SessionsRoute`; a `LaunchedEffect(request.id)` opens the sheet once and records the consumed id locally.

- [ ] **Step 4: Present challenge-specific sheet copy**

When context is `Challenge`, show title `创建挑战会话` and challenge summary while retaining the existing approved learning setup action. Do not add a new backend payload.

- [ ] **Step 5: Run app and chat unit tests**

Run: `gradlew.bat :app:testDebugUnitTest :feature:chat:testDebugUnitTest`

Expected: PASS.

### Task 5: Chat Settings Navigation, Overflow, and Composer Geometry

**Files:**
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatScreen.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ReverseTeachingChatScreen.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/ChatPresentationContractsTest.kt`

**Interfaces:**
- Produces: `ChatComposerLayout` constants and `ChatOverflowAction`.
- Adds: `ChatRoute(onOpenSessionSettings: () -> Unit = {})`.

- [ ] **Step 1: Add failing composer/menu contract tests**

```kotlin
@Test
fun composerKeepsSendButtonInsideStableBounds() {
    assertEquals(54.dp, ChatComposerLayout.Height)
    assertEquals(40.dp, ChatComposerLayout.SendSize)
    assertEquals(7.dp, ChatComposerLayout.TrailingInset)
    assertTrue(ChatComposerLayout.SendSize < ChatComposerLayout.Height)
}

@Test
fun overflowProvidesNonDestructiveSessionActions() {
    assertEquals(listOf(SessionSettings, Sources, Export), ChatOverflowAction.entries)
}
```

- [ ] **Step 2: Run test and confirm RED**

Run: `gradlew.bat :feature:chat:testDebugUnitTest --tests "*ChatPresentationContractsTest"`

Expected: FAIL for missing contracts.

- [ ] **Step 3: Wire active-session settings navigation**

Pass `onOpenSessionSettings = { navigate(SessionSettingsLibrary) }` from `AppShell` through `ChatRoute`. Keep context-hub navigation separate. Replace the empty overflow callback with a local menu.

- [ ] **Step 4: Fix composer geometry**

Use 54dp container height, 40dp send button, 7dp trailing inset, and a restrained 2dp external shadow. Apply navigation-bar padding outside the clipped input surface so shadows remain visible.

- [ ] **Step 5: Run feature/app tests**

Run: `gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest`

Expected: PASS.

### Task 6: Explicit Global Graph Canvas Mode and Normal Workspace Paging

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Modify: `mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/ContextHubScreen.kt`
- Modify: `mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/FormalKnowledgeGraphScreens.kt`
- Modify: `mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/KnowledgeGraphPanel.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`

**Interfaces:**
- Produces: `WorkspaceUiAction.SetGraphCanvasModeActive`, `WorkspaceInteractionLocks.graphCanvasModeActive`, and `FormalGraphCanvas(interactionEnabled, onRequestInteraction)`.

- [ ] **Step 1: Add failing gesture-ownership tests**

```kotlin
@Test
fun graphCanvasModeAloneLocksWorkspacePaging() {
    val viewModel = WorkspaceViewModel()
    viewModel.onAction(WorkspaceUiAction.SelectPage(WorkspacePage.GlobalGraph))
    assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
    viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(true))
    assertFalse(viewModel.uiState.value.horizontalPagingEnabled)
    viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(false))
    assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*WorkspaceViewModelTest"`

Expected: FAIL for missing action/state.

- [ ] **Step 3: Implement explicit canvas mode**

Enable normal `HorizontalPager` scrolling on `GlobalGraph` whenever canvas mode is false. In `FormalGraphCanvas`, install the pan/zoom `pointerInput` only when `interactionEnabled`; otherwise a tap requests canvas mode without consuming drag movement.

- [ ] **Step 4: Add graph back/mode controls**

Add top-left back. While canvas mode is active, back exits canvas mode first; otherwise it navigates to session home. Show a compact `画布模式` state control with an exit icon.

- [ ] **Step 5: Run app/memory tests and Android test compilation**

Run: `gradlew.bat :app:testDebugUnitTest :feature:memory:testDebugUnitTest :app:compileDebugAndroidTestKotlin`

Expected: PASS.

### Task 7: Full Verification, APK, and Mate 60 Acceptance Build

**Files:**
- Update evidence: `mobile-native/qa/phase12-visual-*`
- Modify status only: `task_plan.md`, `progress.md`

- [ ] **Step 1: Run complete native unit/build verification**

Run: `gradlew.bat :core:design:testDebugUnitTest :feature:chat:testDebugUnitTest :feature:memory:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run focused connected interaction tests**

Run the current Figma responsive/device navigation classes against `9CN0223C27017326`; expected zero failures.

- [ ] **Step 3: Install and launch**

Run: `adb -s 9CN0223C27017326 install -r app/build/outputs/apk/debug/app-debug.apk` and launch `com.reversetutor.preview/.MainActivity`.

Expected: install `Success`; app is the resumed activity.

- [ ] **Step 4: Capture real-device evidence**

Capture home at rest/during drag, session action sheet, preset carousel/editor, challenge join sheet, chat composer/settings, and graph page/canvas modes.

- [ ] **Step 5: Verify filesystem diff and APK hash**

Run: `git diff --check` and `Get-FileHash app-debug.apk -Algorithm SHA256`.

Expected: diff check exit 0 and a reported SHA-256.
