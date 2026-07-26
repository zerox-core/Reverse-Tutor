# Task 3A Fix Round 2 Report

## Commit and isolation

- Fix2 implementation baseline: `c60b7b476b2b74b6d1b259b2a3bdeb28e3746e9b`
- Fix2 code commit: `85350a33fa538688332804c24887274de8c39109`
- Branch: `task/3a-chat-composer`
- Worktree: `F:\xw\reverse-tutor-task-3a`
- Main checkout remained read-only and was not integrated, modified, or reverted.

## Findings addressed

1. `ChatAttachmentSizeResult` now distinguishes `Oversize(actualSizeBytesAtLeast)` from ordinary IO/permission `Failed` results. Unknown-length reads still stop after at most `20 MB + 1 byte`; reaching that byte becomes `ImageTooLarge` rejection rather than a recoverable attachment failure.
2. AppShell now uses the tested `reduceChatAttachmentActivityResult` path. Oversize rejection keeps the attachment list unchanged, does not consume one of nine slots, leaves an otherwise valid draft sendable, and shows the 20 MB notice. Ordinary read failures continue to create recoverable Failed attachments.
3. Query success and error responses merge into the coordinator state that exists when the response arrives. A category selected during Loading is therefore preserved instead of being replaced by the request-start snapshot.

Existing scope request tokens/cancellation, reverse-order response protection, send retry/request ID behavior, draft/order persistence, permissions, false capabilities, and all prior 5 Important + 2 Minor fixes remain covered.

## RED evidence

Before production changes, the focused JVM command failed as expected:

```powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'
.\gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest --console=plain
```

- App test compilation failed because `ChatAttachmentSizeResult.Oversize` and `reduceChatAttachmentActivityResult` did not exist.
- `delayedResponsePreservesCategorySelectedWhileLoading` failed because the completed response restored the request-start category.

## Changed code and tests

- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell/ChatAttachmentPlatformAdapters.kt`
- `mobile-native/app/src/test/java/com/reversetutor/preview/shell/ChatAttachmentPlatformAdaptersTest.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatReferenceQueryContracts.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/ChatFix1ContractsTest.kt`

No core, Room/schema/DAO, backend, protocol, PWA, Capacitor, build, signing, or release files changed.

## Clean-HEAD verification

Pre-gate state:

```text
git rev-parse HEAD
85350a33fa538688332804c24887274de8c39109

git status --short --branch
## task/3a-chat-composer
```

Command:

```powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'
.\gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest :feature:chat:lintDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --rerun-tasks --console=plain
```

Exact summary:

```text
chat: 102 tests, 0 failures, 0 errors, 0 skipped
app: 118 tests, 0 failures, 0 errors, 0 skipped
:feature:chat:lintDebug PASSED
:app:lintDebug PASSED
:app:compileDebugAndroidTestKotlin PASSED
BUILD SUCCESSFUL in 1m 35s
411 actionable tasks: 411 executed
```

No device, AVD/emulator, APK install/run, `assembleDebug`, or signing/release task was used. The branch remains isolated and is ready for independent review.
