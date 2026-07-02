# NATIVE-P1-004 HMA-AL00 Settings Smoke

Date: 2026-06-30
Device: HUAWEI HMA-AL00
Android: 10 / API 29
Package: `com.reversetutor.preview`
APK: `mobile-native/app/build/outputs/apk/debug/app-debug.apk`

## Result

Passed.

## Coverage

- Installed debug APK with `adb install -r`.
- Launched native preview with monkey launcher intent.
- Verified Sessions screen is visible.
- Tapped `Settings` from the native app shell.
- Verified `Settings foundation`, `DataStore-backed preview`, `Theme`, `Avatar`, `Global memos`, and `About diagnostics`.
- Tapped `About diagnostics`.
- Verified native metadata:
  - `Reverse Tutor Native Preview`
  - `Package: com.reversetutor.preview`
  - `Version: 0.1.0-native-preview (1)`
  - `Local-first Android data uses Room and DataStore.`
  - `Distribution: Internal debug preview`
  - `Native Android diagnostics only.`
- Verified PWA/Capacitor install hints were absent from the visible native about UI.
- Captured pid-scoped logcat for `com.reversetutor.preview`; no app crash signatures were found.

## Evidence

- `2026-06-30-native-p1-004-final-01-sessions.xml`
- `2026-06-30-native-p1-004-final-01-sessions.png`
- `2026-06-30-native-p1-004-final-02-settings.xml`
- `2026-06-30-native-p1-004-final-02-settings.png`
- `2026-06-30-native-p1-004-final-03-about.xml`
- `2026-06-30-native-p1-004-final-03-about.png`
- `2026-06-30-native-p1-004-final-logcat-pid-19806.txt`

## Debugging Note

An earlier smoke rerun matched global `AndroidRuntime` lines emitted by adb shell `uiautomator` and `input`, not by the app process. The final check scopes logcat to the app pid to avoid that false positive.
