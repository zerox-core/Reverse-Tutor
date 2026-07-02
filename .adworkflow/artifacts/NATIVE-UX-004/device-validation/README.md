# NATIVE-UX-004 Device UI Evidence

Date: 2026-07-01
Device: HMA-AL00 / Android 10
Package: `com.reversetutor.preview`
Build: internal debug preview, not a signed/release APK

## Scope

This evidence covers the Phase 4 Import/export UI productization after protocol and device validation foundations were already in place.

Covered:

- Step-wise import UI: select, detect, validate, dry-run, mode, confirm, write, result.
- Import modes: append, overwrite, new space.
- Dry-run/result report copy: source/schema/result counts, target space, failed count, warnings/errors, next actions.
- API key handling copy for import and export.
- Export choices: current session, graph snapshot, full backup, preset deferred state.
- Wipe return affordance from Settings.
- Preview build keeps the replacement first-launch prompt disabled by default.

Not covered as replacement readiness:

- Full Phase 6 migration source matrix.
- Complete LEG-031/LEG-032 closure.
- PWA/Capacitor exit.
- Signed or official replacement package.

## Evidence Files

- `import-export-ui.png`: first viewport of Import/export page.
- `import-export-ui.xml`: UI hierarchy for first viewport.
- `import-export-export-section.png`: export-section screenshot.
- `import-export-export-section.xml`: UI hierarchy for export choices and key-material copy.
- `TEST-HMA-AL00-10-ux004.xml`: connected instrumentation report for the Phase 4 import/export/wipe flow after UX changes.

## Verification Commands

- `.\gradlew.bat :feature:settings:testDebugUnitTest :core:data:testDebugUnitTest :app:compileDebugKotlin --no-daemon --stacktrace`
- `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase4ImportExportDeviceTest" --no-daemon --stacktrace`
- `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`
- `adb install -r app-debug.apk`
- `adb shell uiautomator dump`
- `adb exec-out screencap -p`
