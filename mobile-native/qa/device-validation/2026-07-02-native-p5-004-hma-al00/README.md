# NATIVE-P5-004 Device Validation

Date: 2026-07-02 +08:00

Device:
- HMA-AL00 / Android 10
- ADB serial observed earlier in this run: HJS0218B27008886

Scope:
- Phase5GraphDeviceTest
- Native package remains `com.reversetutor.preview`
- No signed or release APK was built
- No real LLM provider call was made

Commands:
- `.\gradlew.bat '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase5GraphDeviceTest' :app:connectedDebugAndroidTest --no-daemon --stacktrace`
- `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`
- `aapt dump badging app-debug.apk`

Result:
- Graph device smoke passed.
- The device flow covers seeded graph data, node selection/detail, review action visibility, marking a node as needs review, graph-to-chat evidence target handoff, and Global graph mode.
- Full JVM test, lint, and debug APK build passed.
- APK badging confirmed package `com.reversetutor.preview`.

Notes:
- This task is a native preview parity slice, not full graph replacement readiness.
- Semantic card deck, exact source row highlighting, per-session graph projection, and full legacy graph editor metadata remain follow-up risks.
