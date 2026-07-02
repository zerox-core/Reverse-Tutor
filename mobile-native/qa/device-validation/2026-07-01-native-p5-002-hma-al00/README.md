# NATIVE-P5-002 Device Evidence

Task: `NATIVE-P5-002` Anchors, notes, and errors

Device:

- Model: HUAWEI HMA-AL00
- Android: 10 / API 29
- Package under test: `com.reversetutor.preview`
- Build type: internal debug preview

Validated:

- Chat message action `Note` creates a native memory note instead of showing the previous deferred-state copy.
- The note is linked to the source chat message.
- Context Hub reads native memory data and reports `Notes: 1`.
- Context Hub keeps error count visible as `Open errors: 0`.
- Package remains `com.reversetutor.preview`; no release/signed APK was built.

Evidence files:

- `TEST-HMA-AL00-10-p5-002.xml`: instrumentation result for `Phase5MemoryDeviceTest`.
- `test-result.textproto`: Android test platform result, status `PASSED`.
- `test-results.log`: instrumentation runner output, `OK (1 test)`.
- `logcat-Phase5MemoryDeviceTest.txt`: device logcat for the test run.

Not covered:

- Manual create/edit/delete UI for anchors, notes, and errors.
- Jump from Context Hub memory entries back to the exact chat message.
- Error correction workflow and diagnostics integration.
- Graph node/edge rendering and memory graph review parity.
- Signed or release APK build.
