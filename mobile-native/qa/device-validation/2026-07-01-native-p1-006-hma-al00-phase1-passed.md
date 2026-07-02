# NATIVE-P1-006 Phase 1 Preview Validation

Date: 2026-07-01
Task: `NATIVE-P1-006`
Device: HUAWEI HMA-AL00
Serial: `HJS0218B27008886`
Android: 10 / API 29
Package: `com.reversetutor.preview`
APK: `mobile-native/app/build/outputs/apk/debug/app-debug.apk`
Version: `0.1.0-native-preview (1)`

## Result

Passed.

## Scope

This is an internal Phase 1 preview validation. It verifies foundation integration only. It is not a signed release build and not a native replacement readiness approval.

## Automated Verification

- `.\gradlew.bat :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest --no-daemon --stacktrace`
- `.\gradlew.bat :core:data:connectedDebugAndroidTest --no-daemon --stacktrace`
- `.\gradlew.bat test --no-daemon --stacktrace`
- `.\gradlew.bat lint --no-daemon --stacktrace`
- `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace`
- `aapt dump badging app-debug.apk`

All commands returned exit code 0. Gradle emitted the known Android SDK XML version warning, but builds/tests completed successfully.

## Device Flow

- Installed debug APK with `adb install -r`.
- Launched `com.reversetutor.preview` with monkey launcher intent.
- Verified Sessions screen:
  - `Reverse Tutor`
  - `Sessions`
  - `Native preview home`
  - `Open chat`
  - `Settings`
- Opened Chat from Sessions.
- Opened Context hub from Chat.
- Verified Context hub placeholder tabs:
  - `Graph`
  - `Anchors`
  - `Notes`
  - `Errors`
  - `Session settings`
- Pressed Android Back from Context hub and verified return to Chat.
- Pressed Android Back from Chat and verified return to Sessions.
- Opened Settings from Sessions.
- Verified Settings foundation:
  - `Settings foundation`
  - `DataStore-backed preview`
  - `Theme`
  - `Avatar`
  - `Global memos`
  - `About diagnostics`
- Opened About diagnostics.
- Verified native metadata:
  - `Reverse Tutor Native Preview`
  - `Package: com.reversetutor.preview`
  - `Version: 0.1.0-native-preview (1)`
  - `Local-first Android data uses Room and DataStore.`
  - `Distribution: Internal debug preview`
  - `Native Android diagnostics only.`
- Verified PWA/Capacitor install hints were absent from visible native About UI.
- Captured app-pid-scoped logcat for pid `18977`; no app crash signatures were found.

## Evidence

- `2026-07-01-native-p1-006-phase1-01-sessions.xml`
- `2026-07-01-native-p1-006-phase1-01-sessions.png`
- `2026-07-01-native-p1-006-phase1-02-chat.xml`
- `2026-07-01-native-p1-006-phase1-02-chat.png`
- `2026-07-01-native-p1-006-phase1-03-context-hub.xml`
- `2026-07-01-native-p1-006-phase1-03-context-hub.png`
- `2026-07-01-native-p1-006-phase1-04-back-to-chat.xml`
- `2026-07-01-native-p1-006-phase1-04-back-to-chat.png`
- `2026-07-01-native-p1-006-phase1-05-back-to-sessions.xml`
- `2026-07-01-native-p1-006-phase1-05-back-to-sessions.png`
- `2026-07-01-native-p1-006-phase1-06-settings.xml`
- `2026-07-01-native-p1-006-phase1-06-settings.png`
- `2026-07-01-native-p1-006-phase1-07-about.xml`
- `2026-07-01-native-p1-006-phase1-07-about.png`
- `2026-07-01-native-p1-006-phase1-logcat-pid-18977.txt`
- `2026-07-01-native-p1-006-phase1-device-meta.txt`

## Coverage Registry Impact

- `LEG-001` remains `verified`: launch and package identity revalidated.
- `LEG-039` remains `verified`: current shell back behavior revalidated.
- `LEG-002` remains `in_progress` and a replacement blocker: Phase 1 top bar/navigation shell exists, but feature-specific context actions are not complete.

## Residual Risk

Phase 1 foundation is valid for preview. The app is not replacement-ready. The coverage registry still lists 29 P0 replacement blockers.
