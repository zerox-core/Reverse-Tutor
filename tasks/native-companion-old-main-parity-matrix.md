# Native Companion Old-Main Parity Matrix

> Purpose: lock the old `main` teaching-turn algorithm facts *before* introducing the window/memory/initiative contracts from
> [native-companion-memory-topology-design.md](native-companion-memory-topology-design.md).
>
> Scope: `newmp` native Android production branch. Documentation only; **no code changed by this document.**
>
> Status vocabulary used below:
> - `migrated` 鈥?the behavior is implemented by an existing native contract and consumed by the real Worker path.
> - `partial` 鈥?a subset is implemented, but the full old behavior is not yet reached by the production path.
> - `unavailable` 鈥?the old behavior is intentionally *not* claimed by the current native Worker; it is future/differential work.
> - `proposed` 鈥?a native contract exists only as a proposal inside a P3/P6 capability request, not in source today.
>
> Helper contract vocabulary:
> - `frozen` 鈥?a type/signature in the frozen layers (`core/model`, `core/protocol`, `core/llm`, `core/data/*Repository`,
>   `core/data/local`, `core/data/preferences`, `SecretStore`). Must not be modified by Package A/B.
> - `domain` 鈥?a pure type in `core:domain` (non-frozen). Safe to define and test now.

## 1. The single real turn path claim

The old `main` behavior is **not** currently reaching the native Worker as a migrated `TurnPlanner`. The current production
native chat route is:

```text
user message
  -> ChatSendCoordinator (sole user-message writer, not shown here)
  -> BackgroundTurnPreparationCoordinator
       -> BackgroundGenerationInput (frozen)
  -> BackgroundGenerationWorker (sole Provider call + assistant write)
       -> BackgroundGenerationRepository.runGenerationJob
       -> ChatGenerationRepository (frozen)
  -> token/session guards -> assistant record
```

The Worker consumes **`contextEvidence`** (bounded memory/source/message evidence list) and **`sessionPolicy`**
(a `LlmSessionPolicyContext` wire payload derived from `SessionPolicyOutput`). It does **not** run old `engine.py`
It does **not** run old `engine.py` prompt assembly, post-turn memory curation, or write-side mastery upsert. Prompt assembly and post-turn curation remain `unavailable`; the mastery read-model landed 2026-09-10 (NEWMP-V1-013, see §7) and feeds context evidence, but write-side upsert remains `proposed`.

The old single-turn loop (`engine.run_turn`): `load -> maybe_summarize -> retrieve_kg_context -> runtime_memory_hint ->
build_system_prompt -> citable_clues -> build_messages -> llm.chat_json -> _normalize_turn_payload -> discipline_reply ->
persist user+assistant -> upsert_mastery -> upsert_error_log -> anchor_updates -> kg_extract -> due_review_pending`.

## 2. Parity rows

| # | Behavior | Old function / test | Native target contract | Native test (existing) | Runtime consumer | Status |
|---|---|---|---|---|---|---|
| 1 | `build_system_prompt` | `engine.build_system_prompt`; `test_engine.py` (no direct test, exercised through `run_opening_turn`/`run_turn`) | `frozen` `LlmGenerationPlanner.plan(...)` 鈫?prompt payload; `domain` `SessionPolicyInput`/`SessionPolicyOutput` (mode/role/goal/companion) | `SessionTurnPolicyTest` (policy only), `ChatGenerationPortAdapterTest` | `BackgroundGenerationRepository` 鈫?`ChatGenerationRepository.generateReply` | **partial** 鈥?policy-level mode/role normalization is wired via `sessionPolicy`; the old persona/anchors/mastery/strategy prompt blocks and the mode templates are **not** migrated as a native `TurnPlanner`. Rich prompt injection is not claimed by the Worker. |
| 2 | `build_messages` | `engine.build_messages`; `test_engine.py::test_build_messages_skips_compressed_history` | `frozen` `ChatGenerationInput.userText` + `contextEvidence`; message windowing handled inside the frozen Worker | `BackgroundTurnPreparationCoordinatorTest`, `ChatGenerationPortAdapterTest` | `BackgroundGenerationRepository` | **partial** 鈥?the Worker reconstructs a bounded recent-message window, but the old `skip_until_id`/summary-cutoff compression semantics are **not** exposed as a native contract. No native summary-compression parity yet. |
| 3 | runtime memory hint | `engine.build_runtime_memory_hint`; (no direct test) | `frozen` `contextEvidence` list; `domain` `MemoryObservation`/`LearningFactReceipt` (proposed for companion topology) | `ChatGenerationPortAdapterTest` | `BackgroundGenerationRepository` | **partial** 鈥?the Worker accepts `contextEvidence` (memory/source/message evidence), so a bounded evidence projection reaches the prompt. The old `build_runtime_memory_hint` multi-source relevance/limiting heuristic is **not** reproduced; companion-memory evidence is `proposed`. |
| 4 | action/role selection | `engine._normalize_turn_payload` + `engine._student_role_for_action` + `engine._fallback_action_for_mode`; `test_engine.py::test_no_entry_routes_to_clue_student`, `::test_understood_triggers_examiner_without_mastery_increase` | `domain` `SessionTurnPolicy.normalize(input)` 鈫?`SessionActionContract` (+ `SessionPolicyOutput`); out of `core:domain` | `SessionTurnPolicyTest` | `ChatGenerationPortAdapter` 鈫?`LlmSessionPolicyContext` | **migrated** 鈥?`SessionTurnPolicy` reproduces entry-status derivation, understood鈫抏xaminer, no_entry鈫抍lue, has_entry鈫抪robe probe-intensity/low-correctness鈫抯mall_lecture, summary_only鈫抮ecap, and student-role derivation. This is the migrated old policy. |
| 5 | evidence normalization | `engine._normalize_evidence`; `test_engine.py::test_understood_triggers_examiner_without_mastery_increase` | `domain` `SessionTurnContracts`/`MasteryEvidenceContract` (`type/status/error_type/reason`) | `SessionTurnPolicyTest` | `SessionPolicyOutput` 鈫?`LlmSessionPolicyContext` | **migrated** 鈥?non-study forces `none`; probe+correctness/depth threshold 鈫?`explanation` passed/partial; bounded sanitization. |
| 6 | process summary | `engine._build_process_summary`; (no direct test) | `domain` `SessionPolicyOutput.processSummary` | `SessionTurnPolicyTest` (summary assertions) | `SessionPolicyOutput` 鈫?meta | **migrated** 鈥?`SessionTurnPolicy.buildProcessSummary` emits the `mode`-scoped summary string (goal/companion/study branches). |
| 7 | mastery projection | `engine.run_turn` mastery block (`db.upsert_mastery`, `upsert_error_log`, `resolve_error_pattern`) | `domain` `MasteryEvidenceContract`; `frozen` `MemoryRepository`/mastery write is **not** owned by the Worker | `ChatGenerationPortAdapterTest` (evidence only) | `BackgroundGenerationRepository` | **partial (read-model, 2026-09-10)** — mastery read-model migrated via NEWMP-V1-013 (see §7): deterministic fold over the `LearningFactReceipt` ledger, wired into context evidence; write-side `upsert_mastery`/error-log upsert **not** claimed. |
| 8 | graph/context retrieval | `engine.run_turn` (`retrieve_kg_context`, `make_default_retriever`, `_should_inject_clue_retrieval`, `_format_citable_clues`); `test_engine.py` (no direct test) | `frozen` `contextEvidence` (injected evidence); `domain` evidence handles | `ChatGenerationPortAdapterTest` | `BackgroundGenerationRepository` | **partial** 鈥?the Worker accepts bounded `contextEvidence`, so a source/message evidence projection is reachable. The old `retrieve_kg_context` + citable-clue injection heuristic and graph/persona-trait/preference hinting are **not** reproduced as a native contract. |
| 9 | post-turn memory review | `engine.run_turn` (`kg_extractor.extract_from_turn`, `kg_gate.should_extract`, `mark_review_pending`, anchor_updates) | `domain` `CompanionMemoryEvolutionPolicy`/`LearningScopeGuard` (proposed); `frozen` no post-turn contract | (none yet) | None | **unavailable** 鈥?the old loop ran `kg_extract` and `mark_review_pending` after a turn. The native Worker does **no** post-turn curation. Companion-memory curation and learning-scope guard are new, `proposed` contracts in this topology plan. |

## 3. Verified native signature claims

Every frozen/domain signature named above exists in source (verified via `rg` on `mobile-native/core` and `mobile-native/app`):

| Signature | Location | Status |
|---|---|---|
| `ChatGenerationInput(sessionId, userMessageId, userText, token, capabilities?, quoteExcerpt?, imageAttachments, contextEvidence)` | `core/data/.../llm/ChatGenerationRepository.kt` | frozen, exists |
| `ChatGenerationOutcome = Generated \| ProviderFailed \| NoModelConfigured \| UnsupportedVision \| BlankPrompt \| Stale` | `core/data/.../llm/ChatGenerationRepository.kt` | frozen, exists |
| `BackgroundGenerationInput(spaceId, sessionId, userMessageId, userText, token, capabilities?, quoteExcerpt?, imageAttachments, contextEvidence)` | `core/data/.../background/BackgroundGenerationRepository.kt` | frozen, exists |
| `BackgroundGenerationOutput = Completed \| Failed \| Discarded \| Cancelled \| MissingJob` | `core/data/.../background/BackgroundGenerationRepository.kt` | frozen, exists |
| `SessionPolicyInput` / `SessionPolicyOutput` / `SessionEvaluationContract` / `SessionActionContract` / `MasteryEvidenceContract` | `core/domain/.../SessionTurnContracts.kt` | domain, exists |
| `SessionTurnPolicy.normalize` | `core/domain/.../SessionTurnPolicy.kt` | domain, exists |
| `LlmSessionPolicyContext` | `app/.../wiring/session/SessionPolicyInputMapper.kt` | app wiring, exists |

**Not yet in source (labelled proposed):** `TurnPlan`, `InitiativePlan`, `StructuredTurnOutcome`, `WindowTopologyContract`,
companion `MemoryObservation` evolution, `LearningScopeGuard`, `PostTurnProjector`. These appear in the P3/P6 capability
requests (Task 9) and are available only inside `core:domain` (Package A) and the capability-request documents (Task 9).

## 4. Explicitly not claimed (do not map optimistically)

- **Current production chat does NOT run old `engine.py` prompt assembly** (`build_system_prompt` mode templates,
  persona/anchors/mastery blocks, runtime memory hint, citable clues, error-log injection, due-review soft hint).
- **Current production chat does NOT run post-turn memory updates** (mastery upsert, error-log upsert/resolve,
  anchor_updates, kg extraction, review marking).
- The native Worker consumes only `contextEvidence` and `sessionPolicy`. Any richer old behavior is
  `partial`/`unavailable` above, not `migrated`.
- These two gaps are precisely the subject of the P3 (turn snapshot/structured outcome) and P6
  (window/memory/heartbeat persistence) capability requests in Task 9.

## 5. Package C evidence (2026-08-25, uncommitted)

P3 and P6 were approved separately. The following native paths now exist and carry real JVM test evidence
(see `native-companion-package-c-execution-plan.md`); row statuses above are intentionally not upgraded to
`migrated`/`closed` until the real Worker path consumes them end-to-end:

- P6 `6->7` window tree (windowId == sessionId), fork snapshots, direct-parent idempotent merge receipts.
- P6 `7->8` normalized global learning ledger + minimal scope signals (no raw transcript columns).
- P6 `8->9` companion-memory versions (root-exclusive), heartbeat state (root enabled / child explicit).
- P3 immutable turn envelope (window/topology + bounded `TurnPlan`) persisted per job (`background_jobs`, `9->10`)
  so retries use the persisted snapshot; malformed envelope keeps a plain reply and yields `StructuredTurnOutcome.EMPTY`.
- App: `WindowHeartbeatCoordinator` (InitiativePlan -> target-bound job via the Worker) + idempotent `PostTurnProjector`
  writing P6-approved records. The Worker remains the sole Provider/assistant-message writer.
- Migration instrumentation remains device-blocked (RT-2026-015 analog): 4 migration tests authored and
  `compileDebugAndroidTestKotlin` passed, but no device/emulator was attached, so migrations are not device-verified.

## 6. NEWMP-V1-001 teaching-policy audit (2026-09-02)

This addendum supersedes only the stale source-existence and "uncommitted" wording above. It does not upgrade a
behavior merely because a wire value exists. The audit compared `main:engine.py` with the current `newmp` production
path and ran the named JVM tests on the current branch.

| Old teaching behavior | Current native boundary | Status | Evidence |
|---|---|---|---|
| Shallow answer can lead to `probe` | `SessionTurnPolicy.normalize` enforces the `has_entry`/high-probing rewrites after a bounded proposal enters the policy. There is no local semantic evaluator that independently infers shallowness from free text. | partial | `SessionTurnPolicyTest.highProbingIntensityConvertsAskToProbe` |
| Explicit misconception leads to counterexample `challenge` | A nonblank bounded misconception forces `challenge`; the guided plan carries a bounded correction level. | migrated | `studyMisconceptionForcesCounterexampleChallenge`; `GuidedLearningPlanTest.persistentChallengeCarriesBoundedCorrectionPlan` |
| No method entry leads to `clue` or `scaffold_example` | The policy deterministically rewrites eligible proposed actions to `clue`; `scaffold_example` remains an allowed, bounded proposal, not a locally inferred free-text decision. | partial | `noEntryWithAskBecomesClue`; `noEntryWithProbeBecomesClue` |
| "I understand" leads to `examiner_verify`, not mastery | The policy forces `examiner_verify` and zero evidence. A separate local verifier must approve any later learning-fact projection. | migrated | `understoodClaimRequiresExaminerVerificationWithoutMasteryEvidence`; `PostTurnProjectorTest.modelCandidateWithoutLocalVerificationDoesNotWriteMastery` |
| Due review produces `recap` / `delayed_retrieval` | Pending review points are bounded context evidence and the wire vocabulary exists, but no local due-review selector deterministically creates a recap/delayed-retrieval turn. | partial | `SessionPolicyInputMapperTest` review-evidence coverage; no selector test exists by design |
| Template role, goal, plan, and dialogue strategy reach the turn | The mapper sanitizes these fields and emits one bounded `Template` context evidence item for the queued job. | migrated | `BackgroundTurnPreparationCoordinatorTest.preparation_carries_bounded_template_context_into_generation_evidence` |

Focused verification on this audit: `:core:domain:testDebugUnitTest` for `SessionTurnPolicyTest` and
`GuidedLearningPlanTest`, plus `:app:testDebugUnitTest` for `BackgroundTurnPreparationCoordinatorTest` and
`PostTurnProjectorTest`, plus `SessionPolicyInputMapperTest`, completed successfully on 2026-09-02.

The remaining `partial` rows are not safe candidates for a prompt-only shortcut. A future local due-review selector
or semantic evaluator must first have its own bounded input contract, Red test, and explicit capability review if it
requires new persistence or generation fields.

## 7. NEWMP-V1-013 mastery read-model (2026-09-10)

Gap 6 / row 7 upgraded: mastery is now a deterministic read-model (pure fold) over the append-only
`LearningFactReceipt` ledger — `core/domain/.../MasteryLedgerProjection.kt`. Replay semantics match old
`engine.upsert_mastery`: evidence gate (`none`/unknown → no score change), EMA `round2(0.65·old + 0.35·target)`
clamped [0,100], partial → target×0.75, failed → rollback 8 only when score>50 (else unchanged), review
ladder [1,3,7,14]d with `nextReviewAt` anchored to fact.occurredAt (pure/replayable, no wall clock), band codes
stable English (untouched/intuitive_entry/guided_example/basic_application/variant_handling/transferable;
Chinese labels stay in the UI layer). Wiring: `MasteryFactContextPort` (core:domain) → adapter (space-scoped,
same caveat as Memory/Source adapters) → `ConversationContextAssembler.safeRead` → `masteryProjections` on the
contract → `SessionPolicyInputMapper` maps to `kind="Mastery"` evidence under the existing MaxContextEvidence=6
cap. Frozen layers untouched; zero new Room tables.

Tests (TDD Red→Green, BUILD SUCCESSFUL): `MasteryLedgerProjectionTest` 15/15, `ConversationContextAssemblerTest`
14/14 (+3), `SessionPolicyInputMapperTest` 8/8 (+1). Task doc: `tasks/NEWMP-V1-013-MASTERY-LEDGER-PROJECTION.md`.

Explicitly not claimed: write-side `upsert_mastery`, error-log upsert/resolve, session-scoped fact filtering
(space-scoped only), `review_frequency` high ladder [1,2,4,7].

