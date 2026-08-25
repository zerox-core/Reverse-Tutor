# Native Companion Package C Execution Plan

> **For agentic workers:** Execute one task at a time. Stop at each gate and return the exact test output plus `git diff --name-only`; do not combine tasks or start a later task early.

**Goal:** Persist the approved window-tree, memory, learning-ledger, and heartbeat contracts, then safely deliver immutable topology-aware turn snapshots through the existing background-generation single-writer path.

**Architecture:** `core:domain` remains the policy authority. P6 adds three forward-only Room migrations and domain-safe repository adapters. P3 adds optional bounded generation fields and an optional structured result; malformed structured output always falls back to the existing plain assistant reply. The app only schedules an `InitiativePlan` through the existing background preparation path; the Worker remains the sole Provider and assistant-message writer.

**Tech Stack:** Kotlin, Room, WorkManager, JUnit4, Android instrumentation, existing background-generation repository and Worker.

---

## Execution status (2026-08-25)

> Changes for Package C are left **uncommitted** for Codex review; no push or tag (per §0).

- **Gate A** — ✅ P3 and P6 approved separately by the user.
- **Task 0** — ✅ Accepted pending non-frozen correction (stored heartbeat state projection, `ScopeSignal` minimal provenance). Focused `:app` + `:core:domain` tests green.
- **Task 1 (P6 6→7)** — ✅ `WindowEntity/WindowSnapshotEntity/WindowDeltaEntity/MergeCommitEntity` + `WindowTopologyDao` + `WindowTopologyRepository`. JVM `WindowTopologyRepositoryTest` 5/5. `7.json` exported. Migration `ReverseTutorDatabaseMigration6To7Test` authored + compiles.
- **Task 2 (P6 7→8)** — ✅ `LearningFactReceiptEntity/ScopeSignalEntity` + `LearningLedgerDao` + `LearningLedgerRepository`. JVM `LearningLedgerRepositoryTest` 5/5. `8.json` exported. Migration 7→8 test authored + compiles.
- **Task 3 (P6 8→9)** — ✅ `CompanionMemoryVersionEntity/MemoryObservationEntity/WindowHeartbeatEntity` + `CompanionMemoryDao/WindowHeartbeatDao` + `CompanionMemoryRepository` + `WindowHeartbeatRepository`. JVM 3/3 + 3/3. `9.json` exported. Migration 8→9 test authored + compiles.
- **Task 4 (P3)** — ✅ `core:llm` `LlmWindowContext/LlmTurnPlan/LlmAssistantTurnEnvelope/StructuredTurnOutcome`, threaded through `LlmGenerationRequest/Planner`, `ChatGenerationInput`, `BackgroundGenerationInput/Job`; `background_jobs.assistantTurnEnvelopePayload` (migration `9→10`); tab-encoded payload + `buildStructuredOutcome`. JVM `AssistantTurnEnvelopeTest` 6/6 + `TopologyTurnSnapshotTest` 6/6. `10.json` exported. Migration 9→10 test authored + compiles. `SchemaPolicyTest` updated to v10/9-migration chain.
- **Task 5** — ✅ New lightweight `HeartbeatTurnDispatchPort` (feature:chat) + `WindowHeartbeatCoordinator` + idempotent `PostTurnProjector` + `WindowConversationAssembly.dispatchHeartbeat` seam (Worker remains sole writer). JVM `WindowHeartbeatCoordinatorTest` 4/4 + `PostTurnProjectorTest` 3/3.
- **Task 6** — ✅ Full native regression: `gradlew test` BUILD SUCCESSFUL; `:app:lint` + `:app:assembleDebug` BUILD SUCCESSFUL. Python regression: **510 passed, 28 skipped**. ⚠️ Device migration instrumentation blocked: no device/emulator attached (`adb devices` empty) — recorded as an RT-2026-015 analog environment blocker; migrations NOT claimed as device-verified.

## Device-blocker note (RT-2026-015 analog)

`ReverseTutorDatabaseMigration6To7Test` / `7To8` / `8To9` / `9To10` are authored and pass `:core:data:compileDebugAndroidTestKotlin`, but no target device or emulator is attached in this environment. Per RT-2026-015, do **not** substitute a JVM pass for device migration evidence and **do not** retry installs; a real one-run device migration test must be executed on an Android 12/13-compatible device before this counts as verified.

---

## 0. Authority, scope, and stop rules

Read these files before any task:

- `F:\xw\reverse-tutor-newmp\AGENTS.md`
- `F:\xw\reverse-tutor-newmp\tasks\native-companion-memory-topology-design.md`
- `F:\xw\reverse-tutor-newmp\tasks\native-companion-memory-topology-implementation-plan.md`
- `F:\xw\reverse-tutor-newmp\tasks\capability-requests\P3-window-turn-snapshot-and-outcome.md`
- `F:\xw\reverse-tutor-newmp\tasks\capability-requests\P6-window-topology-memory-and-heartbeat.md`
- `F:\CodexHome\skills\reverse-tutor-development-guard\references\known-issues.md`

The plan is blocked until the user separately approves **both** P3 and P6. Approval must cover only the request files above; P3 does not implicitly approve Room work, and P6 does not implicitly approve `core:llm` work.

Always set the Android SDK before Gradle commands:

```powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
```

Do not modify `core:model`, `core:protocol`, `core:data/preferences`, `SecretStore`, import/export semantics, signing assets, PWA, or Capacitor. Do not invoke a real Provider, load a production key, save raw user/assistant/provider text, or create a second assistant-message writing path. Do not push or tag. Leave each task's changes uncommitted for Codex review.

## 1. Task 0 — Accept the pending non-frozen correction

**Purpose:** Establish a clean, reviewed baseline before a frozen-layer task begins.

**Files already changed and in scope:**

- `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowConversationAssembly.kt`
- `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/WindowConversationAssemblyTest.kt`
- `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningScopeContracts.kt`
- `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/LearningScopeGuardTest.kt`
- `tasks/capability-requests/P3-window-turn-snapshot-and-outcome.md`
- `tasks/capability-requests/P6-window-topology-memory-and-heartbeat.md`

- [ ] Run the focused regression tests.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.WindowConversationAssemblyTest" :core:domain:testDebugUnitTest --tests "*.LearningScopeGuardTest" --console=plain
```

Expected: `BUILD SUCCESSFUL`, including `explicitly_enabled_child_projects_its_stored_heartbeat_state`.

- [ ] Verify that `WindowConversationAssembly.heartbeat(windowId)` calls `facade.heartbeat(readHeartbeatState(windowId))`, not `defaultHeartbeat(readWindow(windowId))`.

- [ ] Run safety checks.

```powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
```

Expected: no whitespace error and no frozen-path output. Report the working-tree list; do not reset, commit, or push it.

## 2. Gate A — Explicit P3 and P6 approval

- [ ] Obtain the user's separate, explicit approval for P3 and P6.
- [ ] Re-read the two request files and verify all of the following before editing frozen code:

```powershell
rg -n "version\s*=\s*2|migration\(2\s*->|raw transcript|raw message text|Authorization|Bearer" tasks/capability-requests/P3-window-turn-snapshot-and-outcome.md tasks/capability-requests/P6-window-topology-memory-and-heartbeat.md
```

Expected: only the deliberate prohibitions against raw text or credentials may match; no obsolete database-version or `2 ->` migration proposal may match.

- [ ] If either approval is absent, stop. No Entity, DAO, Repository, migration, input payload, runtime, Worker, or generated schema JSON may be changed.

## 3. Task 1 — P6 migration 6→7: topology, fork snapshots, and merge receipts

**Files:**

- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/Entities.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/Daos.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/window/WindowTopologyRepository.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/window/WindowTopologyRepositoryTest.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration6To7Test.kt`
- Generate only through Room: `mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/7.json`

- [ ] Write failing repository tests for these exact invariants:

```kotlin
rootSessionCreatesWindow(kind = TASK_ROOT, rootId = sessionId)
childSnapshotKeepsForkRevisionAfterParentChanges()
merge(child, directParent, sameDelta, sameRevision).isIdempotent()
merge(child, sibling, delta, revision).isRejected()
deleteChildKeepsCommittedParentMergeReceipt()
```

- [ ] Write the failing migration test: seed a version-6 database with existing sessions, migrate to 7, and assert each old session has exactly one `TASK_ROOT` topology row with `windowId == sessionId` and `rootId == sessionId`.

- [ ] Implement only these new persisted concepts: `WindowEntity`, `WindowSnapshotEntity`, `WindowDeltaEntity`, and `MergeCommitEntity`. Their identity is the existing session identity; do not add a window-to-session mapping table. Create the migration `6 -> 7`, retain migrations `1 -> 6`, export the Room schema, and expose only `WindowRef`, `WindowSnapshotRef`, `MergeCommit`, and `BranchDeletionEffect` through the repository.

- [ ] Run JVM tests, then one compatible-device migration test.

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.WindowTopologyRepositoryTest" --console=plain
.\gradlew.bat :core:data:connectedDebugAndroidTest --console=plain
```

Expected: JVM tests pass. For instrumentation, a real executed test is required. If the Android 16 AVD rejects the module test APK, follow `RT-2026-015`: record the exact blocker once and run it on the Android 12 primary device rather than retrying installs.

## 4. Task 2 — P6 migration 7→8: global learning ledger and scope signals

**Files:**

- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/Entities.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/Daos.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/learning/LearningLedgerRepository.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/learning/LearningLedgerRepositoryTest.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration7To8Test.kt`
- Generate only through Room: `mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/8.json`

- [ ] Write failing tests for `spaceId` isolation, append-only `LearningFactReceipt`, task-window learning-fact write permission, companion-memory exclusion from the ledger, and scope signals limited to `category`, `count`, `sourceTurnId`, and `occurredAtEpochMillis`.

- [ ] Migrate `7 -> 8` by adding only `LearningFactReceiptEntity` and `ScopeSignalEntity`. Neither table may contain `message`, `text`, `transcript`, `provider`, `url`, `authorization`, or secret columns. Do not set a fixed operational “sustained drift” duration; stored occurrence time only enables a future policy to make that decision.

- [ ] Map Entity rows to the existing domain `LearningFactReceipt` and `ScopeSignal` types. The UI and feature layers must receive neither Entity nor DAO.

- [ ] Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.LearningLedgerRepositoryTest" --console=plain
.\gradlew.bat :core:data:connectedDebugAndroidTest --console=plain
```

Expected: focused JVM test passes; instrumentation has a real executed migration result or one documented environment blocker under `RT-2026-015`.

## 5. Task 3 — P6 migration 8→9: companion-memory versions and heartbeat state

**Files:**

- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/Entities.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/dao/Daos.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/companion/CompanionMemoryRepository.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/heartbeat/WindowHeartbeatRepository.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/companion/CompanionMemoryRepositoryTest.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/heartbeat/WindowHeartbeatRepositoryTest.kt`
- Create: `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/ReverseTutorDatabaseMigration8To9Test.kt`
- Generate only through Room: `mobile-native/core/data/schemas/com.reversetutor.core.data.local.ReverseTutorDatabase/9.json`

- [ ] Write failing tests that prove: companion-memory rows are accessible only through a `COMPANION_ROOT`; task and child windows cannot read them; root windows get enabled heartbeat state at creation; child windows start disabled and become enabled only after an explicit command; merge/delete do not transfer a heartbeat schedule or change a committed parent merge.

- [ ] Add bounded `CompanionMemoryVersionEntity`, provenance-only `MemoryObservationEntity`, `WindowHeartbeatEntity`, and the minimal pending-job/cooldown state necessary for idempotent schedule handling. No raw conversation, provider output, or pre-written initiative message may be persisted.

- [ ] Add the `8 -> 9` migration and domain-safe repositories. Repository methods return only `ActiveMemoryVersion`, `MemoryObservation`, `HeartbeatScheduleContract`, and the established topology contracts.

- [ ] Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.CompanionMemoryRepositoryTest" --tests "*.WindowHeartbeatRepositoryTest" --console=plain
.\gradlew.bat :core:data:connectedDebugAndroidTest --console=plain
```

Expected: focused JVM tests pass; device evidence follows the same one-run rule as Task 1.

## 6. Task 4 — P3: immutable turn snapshot and safe structured outcome

**Files:**

- Modify: `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/llm/ChatGenerationRepository.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/background/BackgroundGenerationRepository.kt`
- Modify: the background-job Entity/DAO/migration files approved in Tasks 1–3
- Create: `mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/AssistantTurnEnvelopeTest.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/background/TopologyTurnSnapshotTest.kt`

- [ ] Write failing tests for the exact P3 compatibility contract:

```kotlin
oldJobWithNullTopologyFieldsRunsWithExistingBehavior()
retryUsesItsPersistedSnapshotNotCurrentMemory()
plainProviderReplyPersistsAssistantWithEmptyOutcome()
malformedEnvelopePersistsAssistantWithEmptyOutcome()
validEnvelopePersistsReplyAndReturnsBoundedOutcome()
outcomeCannotContainRawTranscriptOrProviderDetails()
```

- [ ] Introduce optional nullable values only: immutable window/topology context, bounded `TurnPlan`, initiative source, and `StructuredTurnOutcome`. A missing or malformed envelope must preserve a normal assistant reply and produce an empty outcome. `reply` is the only visible assistant text written to the existing message record.

- [ ] Keep raw Provider output in memory only for parsing. Never place it in a job snapshot, outcome, Entity, diagnostics record, test golden file, or exception visible to UI.

- [ ] Preserve the existing sequence exactly:

```text
BackgroundGenerationWorker
  -> BackgroundGenerationRepository.runGenerationJob(jobId)
  -> ChatGenerationRepository.generateReply()
  -> existing assistant persistence
  -> outcome keyed by trusted job id
  -> idempotent post-turn projection
```

- [ ] Run:

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest --tests "*.AssistantTurnEnvelopeTest" :core:data:testDebugUnitTest --tests "*.TopologyTurnSnapshotTest" --console=plain
```

Expected: all compatibility and redaction tests pass with fake runtime data only.

## 7. Task 5 — Real heartbeat dispatch and post-turn projection

**Files:**

- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinator.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/background/BackgroundGenerationWorker.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowConversationAssembly.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowHeartbeatCoordinator.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/WindowHeartbeatCoordinatorTest.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/PostTurnProjectorTest.kt`

- [ ] Write failing tests for root-without-scenario silence, eligible root scheduling exactly one target-bound job, explicitly enabled child scheduling itself only, cooldown/unread/foreground generation holding a plan, branch deletion cancelling only its own pending work, and repeated job completion projecting exactly once.

- [ ] Implement `WindowHeartbeatCoordinator` as a converter from `InitiativePlan` to the existing `BackgroundTurnPreparationPort`. It must never call a Provider, write an assistant message, select a session by timestamp, or run `SessionConversationAssembly.runTurn()` directly.

- [ ] Add an idempotent `PostTurnProjector` that consumes only a trusted job id and validated bounded outcome. It may write the P6-approved local delta, learning receipt, memory observation, and heartbeat state; it must no-op for an empty outcome.

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.WindowHeartbeatCoordinatorTest" --tests "*.PostTurnProjectorTest" --tests "*.WindowConversationAssemblyTest" --console=plain
```

Expected: all tests pass, with fake repositories and no real Provider call.

## 8. Task 6 — Full acceptance, device evidence, and frontend handoff

**Files:**

- Modify only with real results: `tasks/native-companion-old-main-parity-matrix.md`
- Modify only with real results: `tasks/native-legacy-coverage-registry.md`
- Modify: `tasks/native-companion-package-c-execution-plan.md` to tick only completed tasks and record evidence links/commit IDs if the user later authorizes commits.

- [ ] Run complete native regression:

```powershell
.\gradlew.bat test :app:lint :app:assembleDebug --console=plain
```

- [ ] Run Python regression from `F:\xw\reverse-tutor-newmp`:

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

- [ ] Run safety checks:

```powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
```

Expected: frozen files are limited strictly to P3/P6-approved paths; all other frozen paths are absent. Record actual pass/fail counts—never close a legacy row solely from source review.

- [ ] On the compatible Android 12 primary device, run each new migration test once. If instrumentation runs, uninstall only its test package afterward and restore `com.reversetutor.preview/.MainActivity` to the foreground. Do not retry an unchanged device failure more than once.

- [ ] Hand frontend only the stable contracts: `WindowConversationContract`, window id/current session identity, branch create/merge/delete commands, heartbeat state and explicit enable command, and initiative availability/status. Frontend must not own timers, policy classification, Room, Repository, DAO, Worker, Provider calls, raw-memory inspection, or rollback UI.

## 9. Completion checklist

- [ ] P3 and P6 approvals are recorded separately.
- [ ] Migrations `6 -> 7`, `7 -> 8`, and `8 -> 9` are forward-only, exported, and tested.
- [ ] Existing sessions become `TASK_ROOT`; companion roots are never inferred from history.
- [ ] `windowId == sessionId` everywhere; no mapping table exists.
- [ ] Children read fork-time snapshots only; only direct-parent merge is possible and idempotent.
- [ ] The learning ledger holds normalized learning facts only; companion memory remains root-exclusive.
- [ ] Root heartbeat defaults enabled; child heartbeat requires an explicit command.
- [ ] Worker remains the sole Provider/assistant writer and retries use immutable snapshots.
- [ ] Plain replies survive structured-envelope parsing failure; no raw Provider text is persisted.
- [ ] JVM, build, lint, Python, and compatible-device migration evidence are recorded truthfully.
