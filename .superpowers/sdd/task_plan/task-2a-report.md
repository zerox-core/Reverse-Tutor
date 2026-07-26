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
