# NATIVE-P2-002 Device Validation

- Date: 2026-07-01
- Device: HUAWEI HMA-AL00
- Android: 10 / API 29
- Package: `com.reversetutor.preview`
- APK: `mobile-native/app/build/outputs/apk/debug/app-debug.apk`

## Result

Passed for the native new-session smoke slice.

## Checked

- Installed debug APK with `adb install -r`.
- Launched `com.reversetutor.preview/.MainActivity`.
- Verified Sessions screen exposes `New session`.
- Opened New session dialog and verified six templates are present:
  `School study`, `Exam sprint`, `Work assistant`, `Language practice`, `Habit builder`, `Skill learning`.
- Created a built-in template session from `Exam sprint`; list updated to `3 sessions`.
- Created a custom session `CustomPiano` with role/goal/profile fields and source handoff checked; app displayed `Initial source import is saved as a deferred handoff to Sources.`
- Reinstalled the final debug APK after protocol hardening and verified final launch still shows `4 sessions`, `New session`, and `CustomPiano`.
- Captured pid-scoped logcat; no `FATAL EXCEPTION`, `ANR`, `AndroidRuntime`, or `RuntimeException` match was found for the final app pid.

## Evidence Files

- `start.png` / `start.xml`
- `dialog.png` / `dialog.xml`
- `template-created.png` / `template-created.xml`
- `list-after-template.png` / `list-after-template.xml`
- `custom-created-2.png` / `custom-created-2.xml`
- `list-after-custom.png` / `list-after-custom.xml`
- `final-launch.png` / `final-launch.xml`
- `final-logcat-pid-2753.txt`

## Residual Risk

- Preset JSON creation is covered by JVM protocol and chat model tests; manual true-device JSON entry was not completed because ADB text injection for JSON is unreliable on this device/IME.
- Source import remains a safe deferred handoff only; file picker and parser behavior belong to `NATIVE-P5-005`.
