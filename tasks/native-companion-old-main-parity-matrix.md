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
prompt assembly, post-turn memory curation, or mastery projection. Those remain `unavailable`/`proposed`.

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
| 7 | mastery projection | `engine.run_turn` mastery block (`db.upsert_mastery`, `upsert_error_log`, `resolve_error_pattern`) | `domain` `MasteryEvidenceContract`; `frozen` `MemoryRepository`/mastery write is **not** owned by the Worker | `ChatGenerationPortAdapterTest` (evidence only) | `BackgroundGenerationRepository` | **unavailable** 鈥?old mastery/error-log projection happens **inside** the old loop. The native Worker persists an assistant message and evidence meta, but there is **no** native post-turn mastery upsert. Mastery projection is future `PostTurnProjector` work 鈫?`proposed`. |
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
