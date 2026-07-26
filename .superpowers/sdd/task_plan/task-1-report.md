# Task 1 Report: Native shell and workspace navigation

## Status

Completed the native workspace shell contract in `mobile-native/app` and the global-settings grouping in `mobile-native/feature/settings`. No frozen core, PWA, backend, protocol, Room, preference, signing, or build configuration files were modified.

## Changed files

- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
  - Releases graph interaction resources on `ON_STOP` without changing the active route.
  - Gives an active graph canvas the Back action after modal/drawer surfaces have had priority.
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceUiState.kt`
  - Adds looping-pager, gesture-owner, graph-canvas Back-order, inner-horizontal-control, and heavy-resource-release contracts.
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspaceViewModel.kt`
  - Reduces the new interaction and background-release actions while preserving route selection.
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/WorkspacePagerHost.kt`
  - Uses a virtual looped pager for the five workspace pages, applies dominant-axis arbitration, keeps indicators transient, and recreates the graph page after a background release.
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/GraphEdgePagingOverlay.kt`
  - Resolves graph edge transitions against the closest virtual pager copy.
- `mobile-native/app/src/test/java/com/reversetutor/preview/shell/WorkspaceViewModelTest.kt`
  - Covers cold-start Home, fixed route order, bidirectional looping, closest route selection, gesture ownership, graph Back ordering, and background release.
- `mobile-native/feature/settings/src/main/java/com/reversetutor/feature/settings/FormalSettingsScreen.kt`
  - Organizes the final workspace page into `外观与布局`, `模型与连接`, `数据与资料`, `权限与系统`, and `关于与版本`.
- `mobile-native/feature/settings/src/test/java/com/reversetutor/feature/settings/FormalSettingsScreenModelTest.kt`
  - Verifies the five settings groups, row order, and existing action routing.

## Contract coverage

- Workspace order is `本周学习 -> 首页 -> 全局图谱 -> 社区 -> 全局设置`; the default route remains Home for cold start and process recreation.
- Horizontal navigation maps a large virtual range to the fixed five pages, so each direction loops without an end-of-list jump.
- Short background pauses retain the route but clear graph/canvas interaction state and recreate the graph composition on resume.
- Vertical-first gestures keep their inner content; inner horizontal controls and explicit graph-canvas mode lock workspace paging.
- Back closes the top navigation surface first, then exits graph canvas mode, then uses the established child/chat/side-page route handling.
- Global settings is reachable as the final workspace page and exposes the required five groups.

## Verification

Executed from `mobile-native`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :feature:settings:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: passed. `WorkspaceViewModelTest` reports 18 passing tests; `FormalSettingsScreenModelTest` reports 4 passing tests. Debug APK assembled successfully.

## Concerns

- No connected-device instrumentation run was performed in this task.
- `lintDebug` completes with pre-existing dependency/target-SDK warnings; no lint errors were introduced.
