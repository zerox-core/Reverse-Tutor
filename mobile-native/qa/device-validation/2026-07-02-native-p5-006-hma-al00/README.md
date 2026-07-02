# NATIVE-P5-006 Device Validation

Date: 2026-07-02 +08:00

Device:
- HMA-AL00 / Android 10
- ADB serial observed: HJS0218B27008886

Scope:
- Phase5SourcesDeviceTest
- Native package remains `com.reversetutor.preview`
- No signed or release APK was built
- No real LLM provider call was made

Commands:
- `.\gradlew.bat '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase5SourcesDeviceTest' :app:connectedDebugAndroidTest --no-daemon --stacktrace`
- `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`
- `aapt dump badging app-debug.apk`

Result:
- Sources device smoke passed.
- Full JVM test, lint, and debug APK build passed.
- APK badging confirmed package `com.reversetutor.preview`.

Notes:
- The first connected-test command attempt used an unquoted PowerShell Gradle property and failed before tests started. The quoted command above is the passing run.
- Chat image attachment behavior is covered by JVM tests in this task; no dedicated chat attachment device test exists yet.
