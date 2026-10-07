# NATIVE-P3-001 Artifact

WorkManager generation jobs with stale/deleted-session isolation.

## Completed

- Added Room schema v2 for persisted generation job input, token, timing, and payload fields.
- Added migration 1->2 plus migration instrumentation test source.
- Added `BackgroundGenerationRepository` state transitions: queued, running, completed, failed, cancelled, discarded.
- Rejected archived/missing-session jobs and stale-token late results before assistant message persistence.
- Added WorkManager `BackgroundGenerationWorker` and app chat path enqueue by persisted job id.
- Updated LEG-016/LEG-017 registry rows and status counts.

## Verification

- Focused red-first tests failed before implementation as expected.
- Focused core:data background/schema tests passed.
- App worker request test passed.
- Android migration test source compiled.
- Full native `test lint :app:assembleDebug` passed.
- Debug APK package remains `com.reversetutor.preview`.

## Follow-ups

- `NATIVE-P3-002`: notifications and background settings.
- `NATIVE-P3-003`: diagnostics, retry/failure records, and reliability.
- Device/emulator execution for migration/background smoke when hardware is available.
- No live provider calls, signed APK, package switch, or PWA exit were done.
