# NATIVE-P1-003 Room DAO Device Validation

Date: 2026-06-30

## Result

Passed.

## Device

- Serial: `HJS0218B27008886`
- Model: `HMA-AL00`
- Android: `10`
- SDK: `29`

## Scope

This validation covers the `:core:data` in-memory Room DAO smoke test for the native Room schema foundation.

## Commands

| Command | Result |
|---|---|
| `.\gradlew.bat :core:model:testDebugUnitTest --no-daemon --stacktrace` | Failed as RED, then passed after implementation |
| `.\gradlew.bat :core:data:compileDebugUnitTestKotlin --offline --no-daemon --stacktrace` | Failed as RED before schema implementation |
| `.\gradlew.bat :core:data:testDebugUnitTest --offline --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat :core:data:connectedDebugAndroidTest --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat test --no-daemon --stacktrace` | Passed |
| `.\gradlew.bat lint --no-daemon --stacktrace` | Passed |

## Instrumentation Result

`mobile-native/core/data/build/outputs/androidTest-results/connected/debug/TEST-HMA-AL00 - 10-_core_data-.xml`

- Test suite: `com.reversetutor.core.data.ReverseTutorDatabaseDaoTest`
- Tests: `1`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- Test case: `daoBoundariesPersistSpaceSessionMessageAndOperationalRecords`

## DAO Flow Covered

- Insert/read `SpaceEntity`
- Insert/list `SessionEntity`
- Insert/list `MessageEntity`
- Insert/list `SourceEntity` and `SourceChunkEntity`
- Insert/list `GraphNodeEntity` and `GraphEdgeEntity`
- Upsert/read `BackgroundJobEntity`
- Insert/read `ImportBatchEntity`
- Insert/read `ExportRecordEntity`

## Schema Evidence

Room schema export:

`mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/1.json`

Confirmed tables:

- `spaces`
- `sessions`
- `messages`
- `message_attachments`
- `message_quotes`
- `llm_profiles`
- `session_settings`
- `anchors`
- `notes`
- `error_logs`
- `memory_items`
- `graph_nodes`
- `graph_edges`
- `sources`
- `source_chunks`
- `background_jobs`
- `import_batches`
- `export_records`

## Notes

- This is a schema and DAO boundary validation, not a feature UI validation.
- The schema is version `1`; later schema changes require explicit migration tests.
- Gradle emitted the known Android SDK XML warning from local tooling, but all required commands returned exit code 0.
