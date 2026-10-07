# Session Algorithm and Side Panel Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the old `main:engine.py` study-mode decision loop into a pure, tested native domain policy and expose UI-independent conversation-assistant and home-learning overview contracts.

**Architecture:** `SessionTurnPolicy` remains a pure Kotlin function in `core:domain`; `ConversationContextAssembler` and `ConversationSessionCoordinator` orchestrate existing repository capabilities through non-frozen ports. `feature:chat` receives immutable contracts through a Facade, while the home overview uses a separate read-model coordinator. No Compose implementation, graph renderer change, provider transport change, Room change, or frozen repository signature change is included.

**Tech Stack:** Kotlin/JVM, Android library modules, JUnit 4, kotlinx-coroutines-test, existing `core:model`, `core:data`, `core:llm`, and `feature:chat` contracts.

---

## Scope and file map

Create or modify only these non-frozen areas:

- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt` — wire-safe policy input/output and normalized evaluation/action values.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnPolicy.kt` — pure old-main decision rules.
- Create `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SessionTurnPolicyTest.kt` — policy matrix tests.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextContracts.kt` — context source ports and safe context read model.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextAssembler.kt` — bounded, deterministic context aggregation.
- Create `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationContextAssemblerTest.kt` — isolation, limits, and partial-failure tests.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationSessionCoordinator.kt` — policy/generation/session lifecycle orchestration.
- Create `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationSessionCoordinatorTest.kt` — stale/session-deleted/provider-failure tests.
- Create `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt` — immutable front-end contract and safe event types.
- Create `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationFacade.kt` — UI-facing adapter over the domain coordinator.
- Create `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionConversationContractTest.kt` — contract completeness and redaction tests.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt` — home learning overview read model and ports.
- Create `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewCoordinator.kt` — stable aggregation for today/week/weakness/token data.
- Create `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/LearningOverviewCoordinatorTest.kt` — scope, empty state, ordering, and degradation tests.
- Create `tasks/session-algorithm-parity-matrix.md` — old-main rule-to-test evidence map.

Do not modify `mobile-native/core/model`, `mobile-native/core/protocol`, `mobile-native/core/llm`, `mobile-native/core/data/*Repository`, Room schema/DAO/migrations, or SecretStore.

### Task 1: Define policy contracts and wire normalization

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt`
- Test: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SessionTurnPolicyTest.kt`

- [ ] **Step 1: Add failing normalization tests.** Cover invalid mode, action, role, evidence type/status, numeric values outside `0f..1f`, blank knowledge point, and length limits. Use `assertEquals` against the exact wire strings from the design spec.
- [ ] **Step 2: Run the focused test and verify failure.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.reversetutor.core.domain.SessionTurnPolicyTest"
~~~

Expected: compilation/test failure because the new contract types do not exist.
- [ ] **Step 3: Implement the contracts.** Define `SessionPolicyInput`, `SessionEvaluationContract`, `MasteryEvidenceContract`, `SessionActionContract\), `SessionPolicyOutput`, and bounded wire-value normalization functions. Keep values in `core:domain`; do not add enum members to `core:model`.
- [ ] **Step 4: Run the focused test and verify pass.** Expected: all normalization tests pass.
- [ ] **Step 5: Commit.**

~~~powershell
git add mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnContracts.kt mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SessionTurnPolicyTest.kt
git commit -m "feat(domain): add session policy contracts"
~~~

### Task 2: Implement the pure study-mode policy

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnPolicy.kt`
- Modify: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SessionTurnPolicyTest.kt`

- [ ] **Step 1: Add failing behavior tests.** Add tests for:
  - study action whitelist and fallback to `ask`;
  - `no_entry + ask/probe/next/recap -> clue`;
  - `has_entry + clue/scaffold_example -> probe`;
  - understood claim -> `examiner_verify`;
  - forced probe overriding generated action;
  - high probing intensity converting `ask -> probe`;
  - active error plus low correctness converting to `small_lecture`;
  - `summary_only` converting correction actions to `recap`;
  - goal/companion action whitelist and evidence reset;
  - first-turn constraints for all three modes.
- [ ] **Step 2: Run the policy test and verify the new cases fail.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.reversetutor.core.domain.SessionTurnPolicyTest"
~~~

- [ ] **Step 3: Implement `SessionTurnPolicy.normalize`.** Apply rules in this order: merge defaults, normalize evaluation, derive entry status, determine understood/forced flags, enforce mode whitelist, apply study rewrites, derive student role, normalize evidence, clamp fields, and build a safe process summary. Do not call a Repository, Android API, LLM, or clock.
- [ ] **Step 4: Run the policy test and verify all cases pass.**
- [ ] **Step 5: Commit.**

~~~powershell
git add mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/SessionTurnPolicy.kt mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/SessionTurnPolicyTest.kt
git commit -m "feat(domain): migrate session turn decision policy"
~~~

### Task 3: Add bounded conversation context aggregation

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextAssembler.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationContextAssemblerTest.kt`

- [ ] **Step 1: Define source ports and safe read models.** Ports must return domain-safe records only: recent messages, memory references, error references, graph/prerequisite references, source evidence, pending review points. Include `spaceId`, `sessionId`, per-category limits, stable sort keys, and a `ContextWarning` list. Do not expose DAO/entity/protocol types.
- [ ] **Step 2: Add failing tests.** Verify session/space filtering, deterministic ordering, per-category limits, blank-field removal, no secret-like fields, and one source failure degrading only that category.
- [ ] **Step 3: Run the focused test and verify failure.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.reversetutor.core.domain.ConversationContextAssemblerTest"
~~~

- [ ] **Step 4: Implement the assembler.** Invoke ports sequentially or with bounded coroutines, catch only source-specific failures, cap text lengths, sort by explicit timestamp/priority/id, and return an empty category plus a safe warning when a source fails.
- [ ] **Step 5: Run the focused test and verify pass.**
- [ ] **Step 6: Commit.**

~~~powershell
git add mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextContracts.kt mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationContextAssembler.kt mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationContextAssemblerTest.kt
git commit -m "feat(domain): assemble bounded conversation context"
~~~

### Task 4: Add the session-turn coordinator without changing generation protocol

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationSessionCoordinator.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationSessionCoordinatorTest.kt`
- Modify only if required for adapter wiring: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/RepositoryContracts.kt`

- [ ] **Step 1: Define non-frozen ports.** Add a domain `ChatGenerationPort` whose input contains the existing `ChatGenerationInput`-equivalent safe values and whose output maps the existing generated/no-model/provider-failed/unsupported/stale outcomes. Add a `SessionTurnPersistencePort` for user-message acceptance, assistant-result acceptance, and terminal failure recording. These are adapters, not changes to frozen Repository signatures.
- [ ] **Step 2: Add failing lifecycle tests.** Verify:
  - policy output is passed into generation input;
  - user message is accepted before generation;
  - assistant result is accepted only when token/session remains current;
  - stale result writes nothing;
  - deleted session returns a safe discarded result;
  - no model and provider failure produce safe terminal contracts;
  - duplicate completion is idempotent.
- [ ] **Step 3: Run the focused test and verify failure.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.reversetutor.core.domain.ConversationSessionCoordinatorTest"
~~~

- [ ] **Step 4: Implement the coordinator.** Sequence context read → policy → user persistence → generation port → token/session gate → assistant persistence → result mapping. Keep all error text sanitized and keep the coordinator independent of Compose.
- [ ] **Step 5: Run the focused test and verify pass.**
- [ ] **Step 6: Commit.**

~~~powershell
git add mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/ConversationSessionCoordinator.kt mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/RepositoryContracts.kt mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/ConversationSessionCoordinatorTest.kt
git commit -m "feat(domain): coordinate session turn lifecycle"
~~~

### Task 5: Expose the UI-independent conversation contract through feature chat

**Files:**
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt`
- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationFacade.kt`
- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionConversationContractTest.kt`

- [ ] **Step 1: Add failing contract tests.** Verify the contract contains session/turn identity, timeline messages, generation state, optional evaluation/action, process summary, next step, context references, and safe UI events. Assert that serialized/displayable fields do not contain `sk-`, `Authorization`, provider URL, or secret references.
- [ ] **Step 2: Run the focused test and verify failure.**

~~~powershell
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "com.reversetutor.feature.chat.SessionConversationContractTest"
~~~

- [ ] **Step 3: Implement immutable contract mapping.** Map domain coordinator results to `SessionConversationContract`; preserve wire action values; do not add visual assumptions such as bubble, drawer, card, or dialog types. Use stable event IDs for retry/open-context/open-source actions.
- [ ] **Step 4: Run the focused test and verify pass.**
- [ ] **Step 5: Commit.**

~~~powershell
git add mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationFacade.kt mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionConversationContractTest.kt
git commit -m "feat(chat): expose session conversation contract"
~~~

### Task 6: Add the home learning overview read model

**Files:**
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt`
- Create: `mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewCoordinator.kt`
- Create: `mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/LearningOverviewCoordinatorTest.kt`

- [ ] **Step 1: Define overview ports and contracts.** Include scope, generated timestamp, active session count, progress, today plan, weekly mainline, weak points, and token usage with estimated flags. Commands for editing plans remain outside this read model.
- [ ] **Step 2: Add failing tests.** Verify all-session and selected-session scopes, empty-state output without fabricated values, stable ranking of weak points, current-week filtering, token aggregation, and partial failure warnings.
- [ ] **Step 3: Run the focused test and verify failure.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.reversetutor.core.domain.LearningOverviewCoordinatorTest"
~~~

- [ ] **Step 4: Implement the coordinator.** Read through non-frozen ports backed by existing repositories/adapters, filter by `spaceId` and requested session scope, sort deterministically, cap each list, and return explicit empty states. Do not generate weekly summaries synchronously in a chat turn.
- [ ] **Step 5: Run the focused test and verify pass.**
- [ ] **Step 6: Commit.**

~~~powershell
git add mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewCoordinator.kt mobile-native/core/domain/src/test/java/com/reversetutor/core/domain/LearningOverviewCoordinatorTest.kt
git commit -m "feat(domain): add learning overview read model"
~~~

### Task 7: Record parity evidence and run the acceptance gate

**Files:**
- Create: `F:\\xw\\reverse-tutor-newmp\\tasks\\session-algorithm-parity-matrix.md`

- [ ] **Step 1: Record every migrated old-main rule.** For each rule list the old source expression, native contract, test method, and status. Do not mark a rule verified without a passing test name.
- [ ] **Step 2: Run module regression.**

~~~powershell
.\gradlew.bat :core:domain:testDebugUnitTest :feature:chat:testDebugUnitTest
~~~

Expected: `BUILD SUCCESSFUL` with all existing and new JVM tests passing.
- [ ] **Step 3: Run the broader Android regression.**

~~~powershell
.\gradlew.bat test :app:lint :app:assembleDebug
~~~

Expected: `BUILD SUCCESSFUL`; no UI or frozen-layer behavior is changed by this plan.
- [ ] **Step 4: Run repository hygiene checks.**

~~~powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
~~~

Expected: clean whitespace check, empty frozen-path diff, and only intentional plan/spec/code commits.
- [ ] **Step 5: Update the development guard registry only if a reproducible issue occurred.** Use the existing template in `F:\\CodexHome\\skills\\reverse-tutor-development-guard\\references\\known-issues.md`; do not add speculative entries.
- [ ] **Step 6: Commit the parity matrix.**

~~~powershell
git add tasks/session-algorithm-parity-matrix.md
git commit -m "docs: record session algorithm parity evidence"
~~~

## Self-review checklist

- [ ] Every requirement in `2026-08-21-session-algorithm-and-side-panel-contract-design.md` maps to a task above.
- [ ] No task changes a frozen path or asks UI to read a Repository directly.
- [ ] The conversation-assistant contract and home-overview contract are separate and independently testable.
- [ ] All new parameters have safe defaults and no real network/API key is required.
- [ ] No visual layout, Compose component, graph renderer, Android multi-window, or PWA behavior is included.
- [ ] Tests cover old-main rule parity, token/session isolation, safety redaction, empty states, and partial degradation.
