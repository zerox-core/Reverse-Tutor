# Task 2A Report — Session home and local actions

## Status

COMPLETE

- Base: `96f28e670cae080da2943f529a4412966504b266`
- Implementation commit: `dc2135bca4452341b3ee4f9f3e31fd44a8beaae5`
- Clean verification worktree: `F:\xw\reverse-tutor-task2a-clean`

## Changed files

- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridFrontendPortAdapters.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalHomeScreen.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionHomeViewModel.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionListModels.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionHomeViewModelTest.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionListUiStateTest.kt`

No frozen core, PWA, backend, protocol, Room, SecretStore, signing, or build files were changed.

## Acceptance coverage

- Cards use stable `80dp` regular and `92dp` pinned heights, approximately 11–12% above the prior `72dp`/`82dp` baseline. They show optional avatar, title, learner role, latest-message summary, relative time, pin ribbon, and learning-state badge.
- Global avatar hiding and per-session hiding both remove the avatar node and its reserved width; text reflows from the normal leading edge.
- Tap retains the existing chat navigation. Long press uses a restrained `0.985` press scale, long-press haptic feedback, and a modal action sheet.
- The sheet contains Rename, Pin/Unpin, disabled `导出（稍后提供）`, and Delete only. No swipe actions were added.
- Rename is prefilled, trimmed, accepts duplicate titles, and validates 1–30 non-whitespace characters.
- Pinned cards sort above unpinned cards by persisted pin time. Rename preserves the pin timestamp; latest-message display time comes from the message record instead of overwriting pin order.
- Delete confirmation includes the session title and impact statement. Delete first archives the session, exposes exactly five seconds of Undo, then uses the existing session-owned deletion repository. Shared Sources and favorites are outside that deletion contract.
- Undo restores the original persisted session row before hard deletion, so its full owned state and Mock suppression state remain intact.
- First-use Mock creation uses stable local repository data with title `欢迎来到反转家教`, learner `小六学`, and opening line `老师老师，第一节课我来教你，以后你就要好好来教我啦。`.
- Mock creation happens only when there are no other sessions. An archived Mock row or existing deletion tombstone permanently suppresses recreation; Undo unarchives the original row.
- Session loading, empty, failure, retry, existing offline content, and disabled Export states are explicit. Session overlays notify the workspace shell so paging chrome does not compete with modal actions.

## Tests and exact output

Pre-commit focused checks:

- `:feature:chat:testDebugUnitTest` — `BUILD SUCCESSFUL in 19s`, 96 Gradle tasks; 34 chat JVM tests, 0 failures.
- `:app:testDebugUnitTest` — `BUILD SUCCESSFUL in 41s`, 181 Gradle tasks; 84 app JVM tests, 0 failures.
- Combined affected JVM rerun — `BUILD SUCCESSFUL in 18s`, 186 Gradle tasks.

Final committed-HEAD gate at `dc2135b`:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 43s
439 actionable tasks: 25 executed, 414 up-to-date
```

The same full gate also passed immediately before the final pin-time refinement in `1m 26s` with 439 actionable tasks. The final result above is the authoritative committed-HEAD proof.

Focused Task 2A JVM coverage includes card height/action invariants, title validation, duplicate titles, pin-time ordering, global/per-session avatar reflow, rename/pin persistence calls, staged delete, five-second commit, Undo restoration, exact Mock content, and tombstone/other-session suppression.

## Clean-worktree proof

- The implementation was built and committed from detached clean worktree `F:\xw\reverse-tutor-task2a-clean` based directly on `96f28e6`.
- `git status --short` was empty at implementation commit `dc2135b` before the final gate.
- The original dirty checkout `F:\xw\reverse-tutor` was not used for builds or edits during implementation, so pre-existing user work remained intact.
- The commit range contains only the Task 2A app/chat files listed above.

## Concerns

- No connected-device Compose interaction run was requested or performed; JVM, lint, Android-test compilation, and debug assembly are green.
- Per-session avatar visibility is modeled and honored without adding a frozen persistence field. Existing sessions default to visible; a later session-settings contract can supply persisted per-session values without changing this card behavior.
- If the process is killed during the five-second Undo window, the staged archive remains suppressed and safe, but the transient Undo affordance cannot survive process death. No new persistence contract was introduced for a cross-process Undo timer.

## Round 1/5 review fixes (2026-07-26)

### Status and commit

- Round status: COMPLETE
- Review base: `45a70e79bbbb88eecb335234d17892843115e9dd`
- Fix implementation commit: `5fdfbbea8734e8cfeed0d59b9e50aa5921f99b88`
- Clean implementation worktree: `F:\xw\reverse-tutor-task2a-clean`

### Findings addressed

- Fresh-install Mock detection now records feature-local welcome-state observation and explicit-deletion suppression in persistent app storage. A genuinely absent row on first launch creates the Mock; an adapter-owned hard deletion or a later observed core tombstone suppresses recreation. The exact persisted content is title `欢迎来到反转家教`, learner `小六子`, and opening line `老师老师，第一节课我来教你，以后你就要好好来教我啦。`.
- Per-session avatar visibility now reads and writes through `SessionHomePersistence`, whose production implementation is `SharedPreferencesSessionHomePersistence`. `RepositorySessionHomePortAdapter.setAvatarVisible()` is the production action path; global visibility still overrides it in the existing UI mapping and removes the avatar slot.
- Delete staging persists the finalization deadline before the Undo period. The production adapter schedules hard deletion in an adapter-owned supervisor scope, independent of route disposal, and `loadSessionCards()` recovers pending deadlines after process recreation: future deadlines are rescheduled and expired deadlines are finalized immediately through the existing `SessionDeletionRepository`. Undo cancels the durable job, clears the persisted deadline, and restores the archived row.
- Rename now uses only the existing atomic `SessionRepository.renameSession()` action. Pin time is persisted independently in feature-local state when pin/unpin succeeds, so rename never restores a stale full row or changes latest-pin ordering.
- Rename input no longer discards raw text above 30 code units. Validation is applied to the trimmed value, so whitespace-padded valid titles of 30 characters are accepted and pasted over-limit values remain visible with the existing validation error.

### Focused production-adapter coverage

Added `RepositorySessionHomePortAdapterTest` under `app/src/androidTest` with real `DataModule` repositories and the production SharedPreferences adapter. It covers:

- fresh install and exact persisted Mock content;
- create/delete/Undo/reload and permanent Mock suppression;
- expired staged-delete recovery after adapter reload;
- persisted per-session avatar visibility through the production port action;
- rename preserving pin ordering and unrelated pin/archive fields.

No Android device was attached (`adb devices` returned an empty device list), so these real-repository instrumentation tests were compiled but not executed.

### Verification evidence

Focused affected checks after the final source edit:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 18s
194 actionable tasks: 23 executed, 171 up-to-date
CHAT_TESTS=34 CHAT_FAILURES=0 APP_TESTS=84 APP_FAILURES=0
```

Full requested gate before the final source edit (the subsequent edit only exposed the already-persisted avatar setter and its instrumentation-test call; the focused rerun above compiled and tested that final source):

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 1m 3s
439 actionable tasks: 40 executed, 399 up-to-date
```

The authoritative full committed-HEAD gate is recorded in the final handoff after committing this report.

### Scope and remaining concerns

- Round 1 changed only Task 2A app/chat sources, app adapter tests, and this report. No core, PWA, backend, protocol, Room, SecretStore, signing, or build file changed.
- Feature-owned SharedPreferences are used because the frozen settings repository has no per-session avatar/deletion-deadline keys. This is durable local app state and does not alter frozen contracts.
- The real-repository instrumentation tests require an attached Android device or emulator to execute; compilation is green, but this environment had none.

## Round 2/5 review fixes (2026-07-26)

### Status and commit

- Round status: COMPLETE
- Review base: `cc639348c0086efcf357352e957b78b00f5a33f4`
- Fix implementation commit: `6601c76ab70aa6aedcab784a6a5d73be0af0f7da`
- Clean implementation worktree: `F:\xw\reverse-tutor-task2a-r2-clean`

### Findings addressed

- Per-session avatar visibility now has a normal production owner and caller. `AppShell` passes the active session and production `SessionHomePort` into `SessionSettingsRoute`; that route owns a `SessionHomeViewModel`; and the session-personalization screen exposes an enabled `显示会话头像` switch. The ViewModel persists through `setAvatarVisible()` and updates its owned card state after repository success.
- Durable deletion is now coordinated by one adapter-owned `DurableSessionDeletionCoordinator` per adapter. Scheduled recovery and direct finalization share a per-session mutex and job registry. Direct finalization removes, cancels, and joins the owned job before repository deletion, preventing the previous background/direct race.
- A false deletion result is treated as success only after `SessionRepository` absence explicitly confirms the session was already deleted. Otherwise the persisted deadline, avatar state, and pin timestamp remain intact for retry. Retries reuse the persisted deadline as the revision/idempotency key instead of generating a new operation identity.
- Confirmed hard deletion clears the deadline plus only that session's feature-owned avatar and pin keys. Staging and Undo retain avatar/pin metadata; another session's metadata is unchanged.

### Focused coverage

- `SessionHomeViewModelTest.avatarActionPersistsAndUpdatesOwnedSessionState` verifies the ViewModel production action owner.
- `FormalPersonalizationAvatarTest` renders the production `SessionSettingsRoute`, toggles the tagged avatar switch, and verifies the call reaches `SessionHomeViewModel` and `SessionHomePort`.
- `DurableSessionDeletionCoordinatorTest` covers route-waiter cancellation, direct/scheduled competition with cancel-and-join, one repository delete, false-result retry retention, stable retry revision, explicit-absence idempotence, confirmed cleanup, staging metadata retention, and isolation of other-session metadata.
- `RepositorySessionHomePortAdapterTest` now also verifies avatar/pin metadata survive staging and Undo and are removed only after hard deletion.

No Android device was attached (`adb devices` returned an empty device list), so the Compose/real-repository instrumentation tests were compiled but not executed. All JVM coordinator and ViewModel tests ran.

### Verification evidence

Focused final-source checks:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 15s
194 actionable tasks: 19 executed, 175 up-to-date
CHAT_TESTS=35 CHAT_FAILURES=0 APP_TESTS=88 APP_FAILURES=0
```

Full requested gate after the API-23 lint correction:

```text
gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
BUILD SUCCESSFUL in 44s
439 actionable tasks: 26 executed, 413 up-to-date
```

The authoritative clean committed-HEAD gate is recorded in the final handoff after committing this report.

### Scope and remaining concerns

- Round 2 changes only Task 2A app/chat sources, app/chat tests, and this report. No core, PWA, backend, protocol, Room, SecretStore, signing, or build file changed.
- The session-personalization switch is reachable when an active chat session opens its settings. It remains disabled in presentation-only settings entry with no active session.
- Device-only Compose and real-Room adapter tests still require an attached emulator/device; this environment could compile but not execute them.
