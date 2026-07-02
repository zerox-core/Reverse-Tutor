# NATIVE-P2-006 HMA-AL00 Phase 2 Validation

Device: HMA-AL00 / Android 10 / API 29.

Scope: internal preview package `com.reversetutor.preview`.

Validated:

- Phase 2 core loop app instrumentation passed with `OK (1 test)`.
- The test creates an `Exam sprint` session, sends `P2-006-no-model`, verifies `No model configured`, creates an active local profile, sends `P2-006-mock-generation`, verifies assistant reply `Mock generation ready`, and verifies persistence after activity recreation.
- Post-instrumentation UI dumps/screenshots confirm the app package and persisted chat state.
- pid-scoped logcat contains no app crash/ANR signatures.

Key files:

- `10-instrumentation-phase2-core-loop.txt`
- `11-post-instrumentation-launch.xml`
- `11-post-instrumentation-launch.png`
- `12-post-instrumentation-chat.xml`
- `12-post-instrumentation-chat.png`
- `13-pid-logcat.txt`
- `android-test-report/`

Invalid manual evidence:

- Earlier `07-*` and `08b-*` captures are not used for final acceptance because the device focus moved to `tech.zeroxcore.nativeapp`.
- Final acceptance relies on the instrumentation output plus `11-*`, `12-*`, and `13-*` captures.
