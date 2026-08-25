# Native Companion Memory Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a contract-first native runtime for window-tree memory, Hermes-like companion-memory curation, learning-scope soft limits, and root-window heartbeat initiative without breaking the existing single-writer background-generation path.

**Architecture:** The work is split into three independently shippable packages. Package A defines pure Kotlin behavior and its tests with no frozen-layer changes. Package B only publishes safe feature/app ports and fake-runtime proofs. Package C is separately gated: it adds the approved P3/P6 persistence and Worker integration required for production behavior. A root window owns a heartbeat by default; a child has none until an explicit command enables it.

**Tech Stack:** Kotlin, JUnit4, coroutines, existing `core:domain` / `feature:chat` / app wiring, Room and WorkManager only after P6 approval.

---

## 0. Non-negotiable execution rules

- Work only on `newmp`; do not merge into `Android` and do not push or tag.
- Read [native-companion-memory-topology-design.md](F:/xw/reverse-tutor-newmp/tasks/native-companion-memory-topology-design.md), [AGENTS.md](F:/xw/reverse-tutor-newmp/AGENTS.md), and the frozen boundary document before every package.
- Frozen paths are `mobile-native/core/model`, `core/protocol`, `core/llm`, `core/data/*Repository`, `core/data/local`, `core/data/preferences`, `SecretStore`, Room schema exports, and migrations. Package A and B must leave their diff empty.
- The sole user-message writer remains `ChatSendCoordinator`; the sole Provider/assistant writer remains `BackgroundGenerationWorker` through `BackgroundGenerationRepository`. Do not call `SessionConversationAssembly.runTurn()` from the chat screen or from a heartbeat.
- Test code uses fakes only. No Gradle test may invoke a real Provider or read a production API key.
- Any API, Entity, DAO, migration, persisted job-payload, or Worker semantic change waits for an approved P3/P6 change request. Stop at that gate.

## 1. Package A — pure topology, memory, scope, and initiative contracts

This package is independently useful: it creates deterministic behavior and a test matrix but writes no Room data and sends no message. It may be implemented immediately.

### Task 1: Lock old-engine parity facts before introducing new contracts

**Files:**

- Create: `tasks/native-companion-old-main-parity-matrix.md`
- Read only: `main:engine.py`, `main:tests/test_engine.py`
- Read only: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt`
- Read only: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnPolicy.kt`

- [ ] **Step 1: Create the parity matrix with one row per behavior.**

  Include the old function/test, native target contract, native test name, runtime consumer, and status. Start with the following required rows: `build_system_prompt`, `build_messages`, runtime memory hint, action/role selection, evidence normalization, process summary, mastery projection, graph/context retrieval, and post-turn memory review.

- [ ] **Step 2: Explicitly mark unavailable behavior rather than mapping it optimistically.**

  The matrix must mark the current native Worker as consuming only `contextEvidence` and `sessionPolicy`; it must not claim that current production chat runs old `engine.py` prompt assembly or post-turn memory updates.

- [ ] **Step 3: Verify the matrix has no invented frozen API signatures.**

  Run:

  ```powershell
  rg -n 'ChatGenerationInput|BackgroundGenerationInput|SessionPolicyOutput' mobile-native/core mobile-native/app
  ```

  Expected: every signature quoted in the matrix exists in source; unknown future fields are labelled “proposed in P3 request”.

- [ ] **Step 4: Commit the documentation-only baseline.**

  ```text
  docs: map companion topology to old engine behavior
  ```

### Task 2: Define window-tree contracts and deterministic branch policy

**Files:**

- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/WindowTopologyContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/WindowTopologyPolicy.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/WindowTopologyPolicyTest.kt`

- [ ] **Step 1: Write the failing topology tests.**

  Cover these exact cases:

  ```kotlin
  @Test fun root_has_heartbeat_enabled_by_default()
  @Test fun child_has_no_heartbeat_until_explicit_command()
  @Test fun child_snapshot_stops_at_its_fork_revision()
  @Test fun only_direct_child_can_target_its_parent_for_merge()
  @Test fun siblings_cannot_merge()
  @Test fun duplicate_merge_commit_is_idempotent()
  @Test fun child_mutation_after_merge_cannot_change_parent_commit()
  @Test fun deleting_child_preserves_parent_merge_and_global_learning_receipt()
  ```

- [ ] **Step 2: Run the new test class and verify it fails because the policy does not exist.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.WindowTopologyPolicyTest" --console=plain
  ```

  Expected: compilation failure naming `WindowTopologyPolicy` or its contract types.

- [ ] **Step 3: Add only pure contracts and rules.**

  `WindowTopologyContracts.kt` must define wire-safe, Android-free types equivalent to:

  ```kotlin
  enum class WindowKind { COMPANION_ROOT, LEARNING_ROOT, TASK_ROOT, CHILD }
  data class WindowRef(val id: String, val rootId: String, val parentId: String?, val kind: WindowKind)
  data class WindowSnapshotRef(val ancestorRevision: Long, val forkedAtEpochMillis: Long)
  data class MergeCommit(val id: String, val childId: String, val parentId: String, val deltaId: String, val sourceRevision: Long)
  sealed interface WindowHeartbeatState { data object RootEnabled; data object ChildDisabled; data object ExplicitlyEnabled }
  ```

  `WindowTopologyPolicy.kt` must be a pure object. It validates direct-parent merge only, returns the same result for the same `(childId, parentId, deltaId, sourceRevision)`, and never reads a clock, Room, or an LLM.

- [ ] **Step 4: Run the focused tests.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.WindowTopologyPolicyTest" --console=plain
  ```

  Expected: `BUILD SUCCESSFUL`; every test above passes.

- [ ] **Step 5: Commit.**

  ```text
  feat(domain): define window topology contracts
  ```

### Task 3: Define memory-domain and Hermes-like evolution policy

**Files:**

- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/CompanionMemoryContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/CompanionMemoryEvolutionPolicy.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/CompanionMemoryEvolutionPolicyTest.kt`

- [ ] **Step 1: Write failing policy tests.**

  Required cases:

  ```kotlin
  @Test fun companion_domain_is_only_readable_by_companion_root()
  @Test fun task_window_can_emit_learning_fact_but_not_personality_observation()
  @Test fun one_observation_cannot_supersede_active_core_setting()
  @Test fun stable_independent_observations_can_supersede_active_setting()
  @Test fun short_lived_situation_expires_without_erasing_stable_preference()
  @Test fun learning_retention_signal_is_not_personality_evolution_evidence()
  @Test fun raw_transcript_is_not_a_field_of_memory_observation()
  ```

- [ ] **Step 2: Run the new test class and verify it fails.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.CompanionMemoryEvolutionPolicyTest" --console=plain
  ```

  Expected: compilation failure before implementation.

- [ ] **Step 3: Implement bounded domain contracts.**

  Define `MemoryDomain` (`COMPANION`, `WINDOW_LOCAL`, `GLOBAL_LEARNING`), `CompanionMemoryPartition`, `MemoryObservation`, `ActiveMemoryVersion`, and `LearningFactReceipt`. `MemoryObservation` may contain normalized value, source class, timestamp, confidence, and provenance handle; it must not have a raw message-text property.

  Implement `CompanionMemoryEvolutionPolicy.decide(active, observations, now)` with only these results: `Ignore`, `KeepShortLived`, `Reinforce`, `Supersede`, and `Conflict`. The decision must require independent, temporally stable support before a core/template/manual active version is superseded; do not encode a generic forgetting curve.

- [ ] **Step 4: Run focused tests.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.CompanionMemoryEvolutionPolicyTest" --console=plain
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit.**

  ```text
  feat(domain): add companion memory evolution policy
  ```

### Task 4: Define learning-scope guard without transcript persistence

**Files:**

- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningScopeContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningScopeGuard.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/LearningScopeGuardTest.kt`

- [ ] **Step 1: Write failing tests for permitted and prohibited trajectory outcomes.**

  ```kotlin
  @Test fun high_school_to_university_math_is_related_evolution()
  @Test fun changing_explanation_style_is_related_evolution()
  @Test fun one_off_unrelated_joke_is_ambiguous_not_out_of_scope()
  @Test fun sustained_unrelated_drift_returns_soft_reanchor_only()
  @Test fun companion_root_does_not_run_learning_scope_guard()
  @Test fun signal_has_category_and_count_but_no_raw_user_text()
  ```

- [ ] **Step 2: Run the class and verify it fails.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningScopeGuardTest" --console=plain
  ```

- [ ] **Step 3: Implement the contract and pure classifier.**

  Define `LearningIntentEnvelope`, `ScopeRelation` (`CONTINUOUS`, `RELATED_EVOLUTION`, `AMBIGUOUS`, `SUSTAINED_OUT_OF_SCOPE`), `ScopeSignal`, and `ScopeDecision`. `ScopeSignal` contains only category, count, source-turn handle, and occurrence time; it never contains user text. The only intervention is a `reanchorConstraint` returned in `ScopeDecision`; no result may block a turn or rewrite `LearningIntentEnvelope`.

- [ ] **Step 4: Run focused tests and commit.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningScopeGuardTest" --console=plain
  ```

  Commit message:

  ```text
  feat(domain): add soft learning scope guard
  ```

### Task 5: Define heartbeat eligibility and initiative-plan policy

**Files:**

- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/InitiativeContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/InitiativeEligibilityPolicy.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/InitiativeEligibilityPolicyTest.kt`

- [ ] **Step 1: Write failing tests.**

  ```kotlin
  @Test fun root_is_eligible_but_no_scenario_yields_silent()
  @Test fun child_is_ineligible_until_enable_window_heartbeat_command()
  @Test fun enabled_child_targets_itself_not_parent_or_sibling()
  @Test fun active_user_or_unread_message_holds_candidate()
  @Test fun cooldown_holds_candidate()
  @Test fun plan_contains_expiry_target_and_evidence_handles_not_fixed_copy()
  @Test fun learning_window_scope_reanchor_can_hold_or_constrain_plan()
  ```

- [ ] **Step 2: Run the tests to establish failure.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.InitiativeEligibilityPolicyTest" --console=plain
  ```

- [ ] **Step 3: Implement a pure policy only.**

  Define `EnableWindowHeartbeatCommand`, `HeartbeatScheduleContract`, `InitiativeEligibilityInput`, `InitiativePlan`, and an exhaustive `InitiativeDecision` (`Silent`, `Held`, `Eligible`). `InitiativePlan` must contain target window id, intent, evidence handles, tone constraints, expiry, and cooldown; it must not contain a Provider request or prewritten user-visible reminder.

- [ ] **Step 4: Run focused test and commit.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest --tests "*.InitiativeEligibilityPolicyTest" --console=plain
  ```

  Commit message:

  ```text
  feat(domain): add window initiative eligibility policy
  ```

### Task 6: Prove Package A has not crossed architecture boundaries

**Files:**

- Test: all new `core:domain` test classes
- Modify only if required: `tasks/native-companion-old-main-parity-matrix.md`

- [ ] **Step 1: Run Package A test suite.**

  ```powershell
  $env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
  $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
  .\gradlew.bat :core:domain:testDebugUnitTest --console=plain
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify no forbidden dependencies or frozen diff exist.**

  ```powershell
  rg -n 'android\.|androidx\.|Room|Dao|Repository|SecretStore|ChatGenerationRepository' mobile-native/core/domain/src/main
  git diff --check HEAD~5..HEAD
  git diff --name-only HEAD~5..HEAD -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
  ```

  Expected: the first command has no production-contract dependency matches; whitespace and frozen-path checks have no output.

- [ ] **Step 3: Commit an evidence update only if the matrix status changed.**

  ```text
  docs: record companion domain contract evidence
  ```

## 2. Package B — feature/app ports and fake runtime proof

Package B still does not persist new topology or memory data. It proves UI can call stable contracts while real persistence waits at the approval gate.

### Task 7: Publish a Compose-free window conversation facade

**Files:**

- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/WindowConversationContract.kt`
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/WindowConversationFacade.kt`
- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/WindowConversationFacadeTest.kt`
- Modify: `mobile-native/feature/chat/build.gradle.kts` only if `:core:domain` is not already available

- [ ] **Step 1: Write facade tests with fake domain ports.**

  Assert that the facade exposes a root default heartbeat state, child explicit-enable action, merge eligibility, scope state, and initiative state without importing Compose, DAO, Entity, Database, SecretStore, or `ChatGenerationRepository`.

- [ ] **Step 2: Run the new test class and verify it fails.**

  ```powershell
  .\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.WindowConversationFacadeTest" --console=plain
  ```

- [ ] **Step 3: Implement only contract projection.**

  Reuse the domain contracts from Package A. `WindowConversationFacade` maps policy outcomes into a `WindowConversationContract`; it must not create a Worker, write messages, or decide persistence.

- [ ] **Step 4: Run tests, lint, and commit.**

  ```powershell
  .\gradlew.bat :feature:chat:testDebugUnitTest :feature:chat:lint --console=plain
  ```

  Commit message:

  ```text
  feat(chat): publish window conversation contract
  ```

### Task 8: Add app-wiring fake integration without bypassing the Worker

**Files:**

- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowConversationAssembly.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/WindowConversationAssemblyTest.kt`
- Read only: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionConversationAssembly.kt`
- Read only: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinator.kt`

- [ ] **Step 1: Write failing integration tests.**

  Assert that a fake root projects `WindowHeartbeatState.RootEnabled`, a fake child stays disabled until `EnableWindowHeartbeatCommand`, an initiative decision produces an `InitiativePlan` only, and no fake Provider/message-write seam is called during eligibility evaluation.

- [ ] **Step 2: Run the test and verify it fails.**

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "*.WindowConversationAssemblyTest" --console=plain
  ```

- [ ] **Step 3: Implement wiring through function seams.**

  `WindowConversationAssembly` composes only policy and read-port fakes. It may expose a future dispatch seam returning “not persisted yet”; it must never call `SessionConversationAssembly.runTurn()` or `ChatGenerationRepository.generateReply()`.

- [ ] **Step 4: Run Package B verification and commit.**

  ```powershell
  .\gradlew.bat :core:domain:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testDebugUnitTest --console=plain
  git diff --check
  git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
  ```

  Expected: all tests pass and the final frozen-path command has no output.

  Commit message:

  ```text
  feat(app): assemble window conversation contracts
  ```

## 3. Approval gate — do not begin Package C until approved

### Task 9: Write and obtain two separate frozen-layer change requests

**Files:**

- Create: `tasks/capability-requests/P3-window-turn-snapshot-and-outcome.md`
- Create: `tasks/capability-requests/P6-window-topology-memory-and-heartbeat.md`

- [ ] **Step 1: Create P3 request.**

  The request must propose backward-compatible optional fields for immutable window/topology context, `TurnPlan`, `InitiativePlan` source, and validated `StructuredTurnOutcome`. It must state payload bounds, backward read behavior, failure mapping, no raw transcript duplication, and a complete test/rollback plan.

- [ ] **Step 2: Create P6 request.**

  The request must propose separately scoped persistence for window tree, immutable fork snapshot metadata, local deltas, merge receipt/idempotency key, normalized learning ledger receipts, minimal scope signals, companion-memory versions, and heartbeat jobs. It must list migration order and rollback behavior.

- [ ] **Step 3: Run documentation and frozen checks.**

  ```powershell
  git diff --check
  git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
  ```

  Expected: no output from the frozen path check; no code is changed in this task.

- [ ] **Step 4: Stop and request explicit user approval.**

  Do not add entities, DAO methods, migrations, repository APIs, generated-job fields, or Worker behavior until both requests are approved.

## 4. Package C — approved P3/P6 persistence and real Worker consumption

This package is intentionally blocked until Task 9 approval. It is listed now so the implementation order is fixed, but no agent may start it early.

### Task 10: Add P6 persistence in migration-safe slices

**Files:**

- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/DatabaseSchema.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/ReverseTutorDatabase.kt`
- Modify: the relevant Entity and DAO files under `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/entity/` and `.../dao/`
- Create: migration test(s) under `mobile-native/core/data/src/androidTest/java/com/reversetutor/core/data/`
- Create: repository tests under `mobile-native/core/data/src/test/java/com/reversetutor/core/data/`

- [ ] **Step 1: Write migration tests before schema code.**

  Required tests cover root default heartbeat, child default-disabled heartbeat, immutable parent revision at fork, direct-parent-only merge receipt, duplicate merge idempotency, branch deletion preserving ledger receipt, and no companion-memory rows visible from task-root queries.

- [ ] **Step 2: Run the targeted tests and record the expected pre-implementation failure.**

  ```powershell
  .\gradlew.bat :core:data:testDebugUnitTest --console=plain
  ```

- [ ] **Step 3: Implement the approved schema one aggregate at a time.**

  First add window/topology and merge receipt storage; then ledger/scope signal storage; then companion-version and heartbeat-job storage. Each aggregate must use `spaceId`, root/window ownership, foreign-key-safe deletion behavior, and an idempotency key where the design requires it. Do not add raw transcript columns.

- [ ] **Step 4: Add Repository methods that return domain-safe read/write models only.**

  Repositories translate Entity data into Package A contracts. UI and feature modules never receive entities or DAOs.

- [ ] **Step 5: Run JVM tests, then exactly one valid migration instrumentation run per available device.**

  ```powershell
  .\gradlew.bat :core:data:testDebugUnitTest --console=plain
  .\gradlew.bat :core:data:connectedDebugAndroidTest --console=plain
  ```

  If the instrumentation environment is blocked, record the exact blocker and do not replace it with a JVM pass.

- [ ] **Step 6: Commit only after approved migration evidence exists.**

  ```text
  feat(data): persist window memory topology
  ```

### Task 11: Add P3 snapshot and structured outcome compatibility

**Files:**

- Modify: `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/llm/ChatGenerationRepository.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/background/BackgroundGenerationRepository.kt`
- Modify: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/local/` job entity/DAO/migration files approved in Task 10
- Modify/Create tests in `core/llm`, `core/data`, and `app`

- [ ] **Step 1: Write failing serialization and compatibility tests.**

  Test that an old job with absent new fields is readable, a new job retains its immutable window snapshot across restart, a retry does not read newer memory, and malformed structured output maps to a safe outcome without Provider details.

- [ ] **Step 2: Implement optional, bounded fields with old-job fallback.**

  Persist only normalized `TurnPlan`, safe context/evidence handles, optional initiative source, and bounded structured outcome values approved in P3. Unknown/malformed values must yield safe no-op or failure codes, never raw Provider text.

- [ ] **Step 3: Preserve the one-writer path.**

  `BackgroundGenerationWorker` continues to invoke `BackgroundGenerationRepository.runGenerationJob`; it must not create a second message path. `PostTurnProjector` runs only after a validated generated assistant result and must be idempotent per job id.

- [ ] **Step 4: Run focused regression.**

  ```powershell
  .\gradlew.bat :core:llm:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest --console=plain
  ```

- [ ] **Step 5: Commit.**

  ```text
  feat(background): persist topology-aware turn snapshots
  ```

### Task 12: Wire real post-turn projection and heartbeat dispatch

**Files:**

- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinator.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/background/BackgroundGenerationWorker.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/WindowHeartbeatCoordinator.kt`
- Create: focused tests under `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/`

- [ ] **Step 1: Write failing app-wiring tests.**

  Assert that a root heartbeat with no scenario remains silent; an eligible root or explicitly enabled child creates exactly one topology-bound job; child target never changes; cooldown/unread/foreground generation holds the plan; and a validated outcome projects exactly once.

- [ ] **Step 2: Implement dispatch by reusing the established background port.**

  `WindowHeartbeatCoordinator` may create `InitiativePlan` and route it into `BackgroundTurnPreparationPort`; it must not call the Provider or write an assistant record. The preparation coordinator snapshots the plan and topology. The Worker executes the one accepted job.

- [ ] **Step 3: Verify default root and explicit child semantics.**

  Run focused tests for root creation, child creation, explicit enable, deletion cancellation, and merge non-transfer of schedules.

- [ ] **Step 4: Commit.**

  ```text
  feat(app): dispatch topology-aware heartbeat turns
  ```

## 5. Final acceptance and frontend handoff

### Task 13: Run complete acceptance without live-provider tests

**Files:**

- Modify: `tasks/native-companion-old-main-parity-matrix.md`
- Modify: `tasks/native-legacy-coverage-registry.md` only for rows with actual evidence

- [ ] **Step 1: Run Android regression.**

  ```powershell
  $env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
  $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
  .\gradlew.bat test :app:lint :app:assembleDebug --console=plain
  ```

- [ ] **Step 2: Run Python regression.**

  ```powershell
  py -m pytest -q --ignore=tests/test_project_homepage.py
  ```

- [ ] **Step 3: Run repository safety checks.**

  ```powershell
  git diff --check
  git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
  git status --short --branch
  ```

  Expected: the frozen diff only contains files covered by explicitly approved P3/P6 requests; all other frozen paths remain empty.

- [ ] **Step 4: Hand off only contracts to frontend.**

  Publish `WindowConversationContract`, branch actions, window heartbeat state, and initiative status. Frontend may add root-window launch, create branch, explicit child heartbeat enable, merge/delete, and the temporary heartbeat switch. It must not own policy, timer, persistence, scope classification, or Provider calls.

- [ ] **Step 5: Commit evidence.**

  ```text
  docs: record companion topology acceptance evidence
  ```

## 6. Plan self-review

| Design requirement | Covered by |
| --- | --- |
| companion root and isolated task roots | Tasks 2, 3, 7, 10 |
| fork-time snapshot and direct-parent-only merge | Tasks 2 and 10 |
| global learning facts without companion-memory leakage | Tasks 3 and 10 |
| Hermes-like curation without personality forgetting curve | Task 3 |
| soft learning scope guard | Task 4 |
| root-default / child-explicit heartbeat | Tasks 2, 5, 8, 10, 12 |
| heartbeat never directly calls Provider | Tasks 5, 8, 11, 12 |
| old-main teaching behavior reaches real Worker | Tasks 1, 11, 12, 13 |
| frozen changes gated and approved separately | Task 9 before Tasks 10–12 |

The plan intentionally contains no unapproved code change to the frozen P3/P6 layers. Package A can begin immediately; Package B follows after Package A tests are green; Package C is blocked until the user approves both change requests.
