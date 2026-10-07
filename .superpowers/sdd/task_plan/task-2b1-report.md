# Task 2B1 Report — New session, presets, favorites, and drafts

## Status

- Status: COMPLETE
- Base: `c3bf009dc4577b52ced83bc58c048b04a650ae86`
- Isolated implementation worktree: `F:\xw\reverse-tutor-task2b1`
- Branch: `codex/task2b1-implementation`
- Write scope: `mobile-native/feature/chat/**`, Task 2B1 route/wiring and feature-owned persistence under `mobile-native/app/**`, and this requested report.
- Frozen core, PWA/Capacitor, backend, Room/schema, protocol, SecretStore, signing, and build files were not modified.

## Changed files

### Feature implementation and tests

- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/NewSessionLifecycle.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/NewSessionModels.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FigmaNewSessionScreen.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/NewSessionLifecycleTest.kt`

### App wiring, local persistence, and tests

- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/RepositoryNewSessionCreatePortAdapter.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/SharedPreferencesNewSessionPersistence.kt`
- `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/NewSessionSnapshotCodecTest.kt`
- `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/RepositoryNewSessionCreatePortAdapterTest.kt`
- `mobile-native/app/src/androidTest/java/com/reversetutor/preview/shell/NewSessionHubContractDeviceTest.kt`
- `mobile-native/app/src/androidTest/java/com/reversetutor/preview/FigmaResponsiveDeviceTest.kt`

## Implementation summary

- The Home `+` route now renders a hub with exactly `内置预设`, `收藏`, and `自定义`. The Draft Box is a top-right popup inside Custom.
- Preset cards expose name, learner role, goal, plan, source count, and favorite state. Card taps open detail; `使用此预设` is the only action that advances into Custom.
- Built-ins remain packaged immutable values. The first edit creates an unfavorited `原名称 · 副本` draft.
- Custom owns six exact grouped sections with completion summaries and dedicated editors. Basic information includes the configured opening message.
- A feature lifecycle coordinator saves only at Back, section/draft/tab switch, disposal/page departure, and background boundaries. It persists the last section and per-section/root scroll position.
- The Draft Box is latest-first, capacity 20, and evicts the oldest un-favorited draft while protecting the destination/current draft. If all 20 are favorited, a new save is rejected with an explicit retained-state error instead of deleting a favorite.
- Random produces a difference preview and changes only learner role, goal, and plan. Apply and Undo are explicit; story, source selections, and image references are restored from the original snapshot before preview.
- Favorites are complete immutable configuration/source-selection snapshots. Linked favorite updates require a difference preview and confirmation. Unfavorite deletes only the favorite record/link.
- Validation requires only a nonblank title and learner role. Existing core creation preconditions are satisfied through explicit `未填写` goal/profile values without changing frozen core.
- Create uses a stable attempt ID/session ID and stable opening-message ID, disables duplicate submissions, retains every in-memory edit on failure, exposes Retry, and safely replays repository writes. Success atomically removes the ordinary draft from feature persistence and stores an isolated per-session snapshot before navigation.
- App-owned SharedPreferences store drafts, favorites, editor locations, and isolated session snapshots via a deterministic length-prefixed codec. Core repositories create the session and configured assistant opening message.

## Acceptance and test coverage

| Acceptance area | Evidence |
| --- | --- |
| Exact hub tabs; Draft Box not a fourth page | `NewSessionLifecycleTest.hubHasExactlyThreeTabsAndCustomHasExactlySixSections`; `NewSessionHubContractDeviceTest` |
| Preset detail-only advance and six custom groups | `NewSessionHubContractDeviceTest`; updated `FigmaResponsiveDeviceTest` |
| Immutable built-ins and personal copy | `editingBuiltInCreatesUnfavoritedPersonalCopy` |
| Draft capacity, eviction, latest order, section/scroll recovery | `draftBoxEvictsOldestUnfavoritedAndRestoresDestinationPosition`; codec round-trip |
| Random difference preview / Apply / Undo and protected fields | `randomPreviewApplyAndUndoNeverChangeStorySourcesOrImages` |
| Complete favorite snapshot, confirmation, and session isolation | `favoriteUpdateRequiresDiffConfirmationAndSessionSnapshotStaysIsolated`; codec round-trip |
| Title/role-only validation with explicit incomplete state | `createValidationRequiresOnlyTitleAndLearnerRole`; repository adapter test |
| Duplicate-submit guard | `duplicateCreateIsRejectedWhileFirstSubmissionIsRunning` |
| Failure retention and Retry | `failureRetainsEveryEditAndRetryPromotesSnapshotWithOpeningMessage` |
| Success navigation, learner role, configured opening message | lifecycle test, repository adapter JVM test, and compiled Compose creation contract |
| Stable retry repository writes | `repositoryAdapterUsesStableAttemptAndPersistsConfiguredOpeningMessage` |

## Verification evidence

The ignored clean-worktree `local.properties` was intentionally absent. The first baseline command therefore failed before task execution with `SDK location not found`. All authoritative commands set process-local `ANDROID_HOME` and `ANDROID_SDK_ROOT` to the existing SDK at `C:\Users\Lenovo\AppData\Local\Android\Sdk`; no local/build file was created or changed.

Focused JVM tests and Android-test compilation after final source edits:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
FOCUSED_EXIT=0
feature:chat TESTS=43 FAILURES=0 ERRORS=0 SKIPPED=0
app TESTS=94 FAILURES=0 ERRORS=0 SKIPPED=0
```

Scoped lint:

```text
gradlew.bat :feature:chat:lintDebug :app:lintDebug
Wrote HTML report to .../feature/chat/build/reports/lint-results-debug.html
Wrote HTML report to .../app/build/reports/lint-results-debug.html
LINT_EXIT=0
```

Full requested pre-commit gate after final source edits:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 2m 8s
439 actionable tasks: 53 executed, 386 up-to-date
FULL_GATE_EXIT=0
```

## Clean-worktree proof

- Worktree creation: `git worktree add -b codex/task2b1-implementation F:\xw\reverse-tutor-task2b1 c3bf009`
- Initial state: branch `codex/task2b1-implementation`, `HEAD c3bf009dc4577b52ced83bc58c048b04a650ae86`, no tracked or untracked changes.
- `git diff --check` passed after implementation.
- Implementation commit: `af229423f5d8191139d540ab80ae95f688676f44`.
- Immediately before the authoritative gate, `git status --porcelain` printed no entries and `git rev-parse HEAD` printed `af229423f5d8191139d540ab80ae95f688676f44`.
- Authoritative clean committed-HEAD gate:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 34s
439 actionable tasks: 16 executed, 423 up-to-date
CLEAN_HEAD_GATE_EXIT=0
```

## Concerns

- No Android device/emulator was attached, so the new and updated Compose device flows were compiled but not executed. JVM lifecycle, persistence codec, and repository adapter coverage ran locally.
- With 20 favorited drafts, capacity policy refuses a new draft rather than violating the requirement that only the oldest un-favorited draft may be evicted. The UI retains the destination and shows an explicit error.
- Source selections are preserved as complete feature-owned snapshot references. Frozen core currently has no session-source linking creation contract; Task 2B1 therefore does not add Room/protocol/schema linkage.

## Dirty main-checkout integration

- The dirty main checkout already contained overlapping user work in `FigmaNewSessionScreen.kt`, `AppShell.kt`, and an untracked `FormalDraftEditorScreen.kt`.
- The existing user-edited Figma/draft-editor files were left byte-for-byte in place. The Task 2B1 screen was integrated as `Task2B1NewSessionScreen.kt`, and only the New Session route import/call was merged into the user-edited `AppShell.kt`.
- All other Task 2B1 files were applied as unstaged app/chat/report changes. No pre-existing dirty file was staged, reverted, or removed.
- Integrated dirty-main verification passed:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 51s
194 actionable tasks: 25 executed, 169 up-to-date
feature:chat TESTS=50 FAILURES=0 ERRORS=0 SKIPPED=0
app TESTS=99 FAILURES=0 ERRORS=0 SKIPPED=0
DIRTY_MAIN_FOCUSED_EXIT=0
```

## Fix round 1/5 (2026-07-27)

### Status and commit

- Review artifact: `.superpowers/sdd/task_plan/task-2b1-review.md`
- Fix implementation commit: `7ed129059ba8d9a9e45436a7093f496cd7fe52dc`
- Round status: COMPLETE

### Findings addressed

- Boundary saves now accept a protected draft-ID set. Restore resolves and protects the requested destination before saving; tab switch, new draft, restore, favorite use, and section entry/exit all abort without changing navigation/current state when persistence refuses capacity.
- Random Undo stores and restores only learner role, goal, and plan. Story, sources, image references, dialogue strategy, and every unrelated edit made after Apply remain intact.
- Normal field edits, Random Apply, and Draft Box Rename now share one built-in-to-personal-copy transition. The first mutation produces `原名称 · 副本`, clears packaged origin/template identity, and removes favorite linkage.
- A root boundary now persists `lastSection = null`. Closing an editor records its editor scroll, returns to root, and persists root as the actual restore destination while retaining the previously stored root scroll.
- Deleting the active saved draft selects the latest remaining draft, or creates a usable blank draft when none remain; Custom never renders with a null current draft.

### Required follow-up coverage

- `restoringExactOldestEvictionCandidateProtectsCurrentAndDestination`
- `allFavoritedCapacityRefusalAbortsEveryBoundaryDestinationSwitch`
- `customRootBecomesRestoreLocationAfterLeavingEditor`
- `randomUndoRestoresOnlyOwnedFieldsAfterProtectedFieldEdits`
- `randomAndRenameBothCreateUnfavoritedPersonalCopiesOfBuiltIns`
- `deletingOnlyActiveSavedDraftCreatesUsableBlankCustomDraft`

Focused verification after final source edits:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 43s
194 actionable tasks: 21 executed, 173 up-to-date
feature:chat TESTS=49 FAILURES=0 ERRORS=0 SKIPPED=0
app TESTS=94 FAILURES=0 ERRORS=0 SKIPPED=0
FOCUSED_EXIT=0
```

Authoritative gate at clean committed implementation HEAD `7ed1290`:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 1m 57s
439 actionable tasks: 28 executed, 411 up-to-date
CLEAN_HEAD_GATE_EXIT=0
```

No frozen core, PWA/Capacitor, backend, protocol, Room/schema, SecretStore, signing, or build file changed in this fix round. Device execution remains deferred because no emulator/device was attached; Compose tests compiled successfully.

### Fix-round dirty-main integration

- Replaced only the previously integrated Task 2B1 lifecycle and lifecycle-test files; the adapted `Task2B1NewSessionScreen.kt` route required no source change.
- Pre/post SHA-256 hashes for the user's `FigmaNewSessionScreen.kt`, untracked `FormalDraftEditorScreen.kt`, and merged `AppShell.kt` remained unchanged.
- The main checkout index remained empty; all Task 2B1 and user changes remain unstaged.

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 40s
194 actionable tasks: 21 executed, 173 up-to-date
feature:chat TESTS=56 FAILURES=0 ERRORS=0 SKIPPED=0
app TESTS=99 FAILURES=0 ERRORS=0 SKIPPED=0
DIRTY_MAIN_FOCUSED_EXIT=0
```
