# NATIVE-P5-003 Device Validation

Date: 2026-07-01
Device: HMA-AL00
Android: 10
Package: `com.reversetutor.preview`

## Scope

Validated the native Android knowledge graph engine on a real connected device.

## Command

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase5GraphDeviceTest" --no-daemon --stacktrace
```

## Result

Passed. The XML report records `tests=1`, `failures=0`, and `errors=0`.

## Covered Flow

- Seeded native `GraphRepository` with two nodes and one relation.
- Opened a real session and navigated Chat -> Context hub -> Graph.
- Verified native graph metrics, Canvas semantics, node labels, node selection detail, and relation text.
- Opened the `Global graph` top-level route and verified the global graph mode renders the same graph snapshot.

## Evidence Files

- `TEST-HMA-AL00-10-p5-003.xml`
- `test-result.textproto`
- `test-results.log`
- `logcat-Phase5GraphDeviceTest.txt`

## Notes

- No signed/release APK was built.
- No real LLM provider call was made.
- Current graph data is scoped by native `spaceId`; true per-session graph isolation would require a schema/model extension and remains a follow-up risk.
