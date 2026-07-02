# NATIVE-P4-007 Device Validation

Device: HMA-AL00
Android: 10 / API 29
Package: `com.reversetutor.preview`
Date: 2026-07-01

## Passing Evidence

- `android-test-results/TEST-HMA-AL00-10-app.xml`
  - `Phase4ImportExportDeviceTest`
  - tests=1, failures=0, errors=0
- `09_aapt_badging.txt`
  - package remains `com.reversetutor.preview`
  - versionName remains `0.1.0-native-preview`
- `11_launch_after_install.png`
- `11_launch_after_install.xml`
- `11_pid_logcat.txt`
  - pid-scoped relaunch logcat for the preview app; no app crash/exception signatures found.

## Covered Flow

The instrumentation test covers:

- valid session-export JSON dry run
- append import
- new-space import
- overwrite dry run plus `OVERWRITE` destructive confirmation
- imported session visible in Chat with imported message
- current-session export readiness
- full-backup export readiness
- Share and Save actions enabled after export preparation
- local data wipe confirmation with `WIPE`
- package id assertion for `com.reversetutor.preview`

## Non-Passing Manual Attempts

Files `02_*` through `08_*` record earlier ADB ACTION_SEND/ACTION_VIEW attempts. Those attempts are retained as diagnostic evidence only because their dry-run result showed `sessions: 0` and `messages: 0`.

They must not be used as pass evidence for valid import.
