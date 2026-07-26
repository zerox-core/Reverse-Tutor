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

## Fix round 1/5 (review findings)

### Status and commit

- Fix implementation commit: `16487ddc14dcc1a4b601a6585ab878bdc6a2edf8` (`fix(native): complete shell contract review fixes`).
- The committed range from `52efa469325398a9003ca8387ac9abfced8aed36` through that commit contains only the Task 1 report plus allowed `mobile-native/app/**`, `mobile-native/feature/memory/**`, and `mobile-native/feature/settings/**` files. It contains no core, preference, PWA, backend, protocol, Room, SecretStore, signing, or build-file changes.

### Fix details

- Removed Task 1's committed dependency on unrelated dirty `NewSessionLaunchRequest`, challenge/chat additions, preference toggles, profile-save APIs, and uncommitted core-design tokens. The committed shell uses the APIs available at the base commit and the settings UI uses baseline `FormalTypeScale.style`, local elevation, and local border values.
- Enforced dominant-axis ownership in `WorkspacePagerHost`: the live owner is read by `HorizontalPager.userScrollEnabled`, and a non-workspace owner cancels any in-progress pager movement back to the settled page.
- Added `Modifier.workspaceHorizontalGestureControl`, which marks the inner-horizontal lock and consumes position changes before the parent pager. It is used by the workspace indicator in production and by a focused Compose device test fixture.
- Completed graph canvas integration in committed feature-memory code: explicit canvas entry, pan/zoom only while active, top-bar/outside tap and Back exit, and graph edge paging remains the explicit boundary path.
- Added page-local action-surface dismiss callbacks and a pure `workspaceBackTarget` priority model. Runtime Back order is app modal/drawer, page-local graph sheet, graph canvas, then existing child/chat/side/Home navigation.
- Added `WorkspaceSurfaceState` and `WorkspaceSurfaceShell`, applied around every workspace page. Loading, empty, offline, error/retry, and permission-denied states are reusable slots and are stored per page, so a failing graph/community page does not block Settings. Retry generations are page-local.
- Replaced clickable switch-role rows with `toggleable(value = ..., role = Role.Switch)` and hid duplicate decorative-toggle semantics, exposing checked state to accessibility services.
- Renamed the stale `workspaceStartsOnSessionHomeInFourPageOrder` test to `workspaceStartsOnSessionHomeInFivePageOrder`.
- Updated the existing spatial-navigation device fixture for the fifth Settings page and added `WorkspaceShellContractDeviceTest` for dominant-axis ownership, inner-horizontal ownership, and checked switch semantics.

### Main-checkout verification

Executed from `F:\xw\reverse-tutor\mobile-native`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :feature:settings:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
```

Output:

```text
BUILD SUCCESSFUL in 1m 54s
436 actionable tasks: 48 executed, 388 up-to-date
```

This dirty-checkout run was used only as an early compile/test check, not as the self-contained proof.

### Clean-worktree proof

Created a detached worktree without changing or deleting the main dirty checkout:

```powershell
git worktree add --detach F:\xw\reverse-tutor-task1-clean-6653a7f 6653a7f05d2c6c15fbfe3a0a7d6604fecb335655
git -C F:\xw\reverse-tutor-task1-clean-6653a7f checkout --detach 16487ddc14dcc1a4b601a6585ab878bdc6a2edf8
git -C F:\xw\reverse-tutor-task1-clean-6653a7f status --short
git -C F:\xw\reverse-tutor-task1-clean-6653a7f rev-parse HEAD
```

Output at the final code commit:

```text
(no status output; worktree clean)
16487ddc14dcc1a4b601a6585ab878bdc6a2edf8
```

The first clean Gradle invocation correctly failed before dependency resolution because ignored `local.properties` is absent from a new worktree:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path...
BUILD FAILED in 32s
```

No build file was copied or modified. The SDK path was exported only for the Gradle process, using the main checkout's local environment setting:

```powershell
$sdkLine = Get-Content -LiteralPath 'F:\xw\reverse-tutor\mobile-native\local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if(-not $sdkLine){ throw 'sdk.dir missing from main local.properties' }
$sdkPath = $sdkLine.Substring('sdk.dir='.Length).Replace('\\', '\')
$env:ANDROID_HOME = $sdkPath
$env:ANDROID_SDK_ROOT = $sdkPath
.\gradlew.bat :app:testDebugUnitTest :feature:settings:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
```

Final clean-worktree output from `F:\xw\reverse-tutor-task1-clean-6653a7f\mobile-native`:

```text
> Task :app:compileDebugAndroidTestKotlin
> Task :app:testDebugUnitTest
> Task :app:lintDebug
BUILD SUCCESSFUL in 39s
436 actionable tasks: 22 executed, 414 up-to-date
```

JUnit XML totals from the clean worktree:

```text
app/build/test-results/testDebugUnitTest: tests=84 failures=0 errors=0 skipped=0 suites=16
feature/settings/build/test-results/testDebugUnitTest: tests=26 failures=0 errors=0 skipped=0 suites=6
```

The clean proof exposed and drove removal of three masked dirty dependencies before the final pass: preference/feature call sites in `AppShell`, uncommitted core-design tokens in `FormalSettingsScreen`, and the dirty `HomeChallengePagerHost` signature/tuning constant. The final successful run uses only committed files.

### Preservation of prior user `AppShell` changes

Before separating the unrelated new-session/chat/challenge/settings hunks, the exact original `AppShell` was copied outside the repository and hashed:

```powershell
Copy-Item -LiteralPath F:\xw\reverse-tutor\mobile-native\app\src\main\java\com\reversetutor\preview\shell\AppShell.kt -Destination F:\xw\reverse-tutor-task1-AppShell.before-fix.kt -Force
```

```text
SHA256 86DD1295EB4F2CC6B8C41378CCF0DF96FCD5727626FE6331D613270BBDF54545  F:\xw\reverse-tutor-task1-AppShell.before-fix.kt
```

After removing only those unrelated hunks, the Task-only intermediate copy and exact user-hunk patch were captured outside the repository:

```powershell
Copy-Item -LiteralPath mobile-native\app\src\main\java\com\reversetutor\preview\shell\AppShell.kt -Destination F:\xw\reverse-tutor-task1-AppShell.task-only.kt -Force
git diff --no-index --output=F:\xw\reverse-tutor-task1-AppShell.user-hunks.patch -- F:\xw\reverse-tutor-task1-AppShell.task-only.kt F:\xw\reverse-tutor-task1-AppShell.before-fix.kt
```

```text
SHA256 1107344CD380D82C1D4F454FDA60F60BBE47CE219AF95BAE60A4083398AA783F  F:\xw\reverse-tutor-task1-AppShell.task-only.kt
SHA256 61F5070D75C7D4CA9E5E351B902D5AF7EC5ACF5202148FA1F0521B46D924E809  F:\xw\reverse-tutor-task1-AppShell.user-hunks.patch
```

After the self-contained commit and clean proof, the saved hunks were restored to the main checkout only. Verification:

```powershell
git diff --cached --name-only
git status --short -- mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt
git diff --numstat -- mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt
```

```text
(no cached paths)
 M mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt
37  1  mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt
```

The unstaged diff contains the exact preserved `NewSessionLaunchRequest`, challenge active flag, chat session/settings/source callbacks, preference toggle persistence, and profile-save hunks. They were not staged or committed by Task 1.

### Remaining concerns

- The three new Compose device tests were compiled by `compileDebugAndroidTestKotlin` but were not executed on a connected Android device.
- Lint completes successfully with existing warnings; no lint errors were introduced.
- The temporary clean proof worktree remains at `F:\xw\reverse-tutor-task1-clean-6653a7f`. The main checkout retains all unrelated dirty user changes, including the restored unstaged `AppShell` hunks.
