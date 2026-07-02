# NATIVE-P1-002 Device Validation

Date: 2026-06-30

## Result

Passed.

## Device

- Serial: `HJS0218B27008886`
- Model: `HMA-AL00`
- Android: `10`
- SDK: `29`
- Package: `com.reversetutor.preview`
- Activity: `com.reversetutor.preview/.MainActivity`

## Build Verification

| Command | Result |
|---|---|
| `.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace` | Failed on missing FlowRow opt-in, then passed after fix |
| `.\gradlew.bat test --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat lint --no-daemon --stacktrace` | Passed |

## Device Verification

| Check | Result |
|---|---|
| `adb install -r app-debug.apk` | Passed |
| `adb shell am start -n com.reversetutor.preview/.MainActivity` | Passed |
| Foreground activity | `com.reversetutor.preview/.MainActivity` |
| Initial screen | Sessions placeholder visible |
| Destination strip Chat tap | Opened Chat route |
| Back from Chat | Returned to Sessions |
| Open context hub from Chat | Opened Context hub route |
| Back from Context hub | Returned to Chat |
| Back from Chat after Context hub | Returned to Sessions |
| Status dialog | Opened on Sessions |
| Back while Status dialog open | Closed dialog before navigating |
| Logcat crash scan | No app FATAL EXCEPTION or ANR found |

## Evidence Files

- `2026-06-30-native-p1-002-01-sessions.png`
- `2026-06-30-native-p1-002-02-chat-via-strip.png`
- `2026-06-30-native-p1-002-05-context-hub.png`
- `2026-06-30-native-p1-002-08-status-dialog.png`
- `2026-06-30-native-p1-002-01-sessions.xml`
- `2026-06-30-native-p1-002-02-chat-via-strip.xml`
- `2026-06-30-native-p1-002-03-back-chat-to-sessions.xml`
- `2026-06-30-native-p1-002-04-chat-before-context.xml`
- `2026-06-30-native-p1-002-05-context-hub.xml`
- `2026-06-30-native-p1-002-06-back-context-to-chat.xml`
- `2026-06-30-native-p1-002-07-back-chat-to-sessions.xml`
- `2026-06-30-native-p1-002-08-status-dialog.xml`
- `2026-06-30-native-p1-002-09-status-dismissed.xml`
- `2026-06-30-native-p1-002-logcat.txt`

## Notes

- This validation covers shell/navigation/back behavior only.
- Feature internals are intentionally placeholders for later tasks.
- Gradle emitted an Android SDK XML warning from local tooling, but required commands returned exit code 0.
