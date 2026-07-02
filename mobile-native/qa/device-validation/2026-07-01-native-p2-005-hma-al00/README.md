# NATIVE-P2-005 Device Smoke Evidence

Device: HMA-AL00, Android 10, API 29

Package: `com.reversetutor.preview`

Result: Passed

## Covered Flow

- Installed `mobile-native/app/build/outputs/apk/debug/app-debug.apk`.
- Cleared app data and launched `com.reversetutor.preview/.MainActivity`.
- Verified no-model Chat behavior before profile setup:
  - Sent `P2-005-no-mode`.
  - Captured `No model configured` plus persisted user message.
- Verified Settings profile activation:
  - Created `MockProfile`.
  - Confirmed `OpenAiCompatible | gpt-4o-mini`, `Active`, `https://api.openai.com/v1`, and `No key saved`.
- Verified mock generation after active profile:
  - Sent `P2-005-mock-generation`.
  - Captured assistant reply `Mock generation ready`.
  - Confirmed no `No model configured` state in the final chat dump.
- Captured pid-scoped logcat for `com.reversetutor.preview`; no crash/ANR signatures were found.

## Evidence Files

- `no-model.xml`
- `no-model.png`
- `settings-profile-card.xml`
- `profile-card.png`
- `mock-generation.xml`
- `mock-generation.png`
- `pid-logcat.txt`
- `app.pid.txt`

## Boundary

This proves P2-005 preview generation lifecycle on one physical device with `FakeLlmGenerationRuntime`. It does not prove live provider networking, background WorkManager isolation, multi-device matrix coverage, import/export parity, or native replacement readiness.
