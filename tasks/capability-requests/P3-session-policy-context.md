# P3 Capability Request: Session Policy / Context Injection into ChatGenerationInput

> Status: **implemented** — approved by the user on 2026-08-22. The frozen
> `core:data` / `core:llm` change was additive, test-gated, and introduces no
> storage schema, DAO, migration, or secret-store change.

## Background

NATIVE-P2-007 wires the non-frozen `ConversationSessionCoordinator` /
`ChatGenerationPortAdapter` to the frozen `ChatGenerationRepository`. The
coordinator produces a `SessionPolicyOutput` (evaluation / action /
processSummary) and a `ConversationContextContract` per turn. The
`ChatGenerationPortAdapter.buildInput` maps the domain `GenerationRequest` to
the frozen `ChatGenerationInput`.

## Problem

`ChatGenerationInput` accepts `contextEvidence: List<LlmContextEvidence>` (a
structured body/kind payload) but has **no field** for:

- `SessionPolicyOutput` — the per-turn strategy decision (action type, student
  role, knowledge point, difficulty, evaluation, correction timing, user
  emotion, process summary).
- Explicit session-context identifiers that let the frozen planner select /
  prioritize evidence deterministically.

The adapter currently:
- Maps context-evidence strings to `LlmContextEvidence` (body = real gap / review
  text). ✅ honest.
- **Does not** inject `SessionPolicyOutput`. ✅ honest — it does not fake
  "已注入提示词".

This meant the strategy decision and rich context were computed but never
reached the LLM prompt in the frozen path. The P3 implementation closes that
gap while preserving the null-policy behaviour exactly.

## Implemented minimal change (P3)

Add a backward-compatible, optional wire payload to `core:llm`, then reference
it from `ChatGenerationInput`. Defining the type in `core:llm` keeps the
planner independent of `core:data` and avoids a reverse module dependency:

```
data class ChatGenerationInput(
    … // existing fields unchanged
    val sessionPolicy: LlmSessionPolicyContext? = null   // NEW, default null
)

data class LlmSessionPolicyContext(
    val actionType: String,
    val studentRole: String,
    val knowledgePoint: String,
    val difficulty: Float,
    val processSummary: String,
    val evaluationCorrectness: Float = 0f,
    val userEmotion: String = "neutral",
    val correctionTiming: String = "immediate"
)
```

### Why wire strings, not frozen enums

`SessionTurnContracts` deliberately uses stable wire strings (not `core.model`
enums) so the strategy layer evolves without touching the frozen model. The
P3 input should follow the same convention.

## Compatibility strategy

1. **Additive only** — the new field defaults to `null`; existing callers and
   tests are unaffected. No existing `ChatGenerationInput` construction breaks.
2. **Frozen planner opt-in** — `LlmGenerationPlanner.plan` consumes
   `sessionPolicy` only when non-null; when `null`, behaviour is identical to
   today (no prompt injection).
3. **Adapter wiring** — `ChatGenerationPortAdapter.buildInput` maps
   `GenerationRequest.policy` → `LlmSessionPolicyContext`, using bounded
   redaction before the frozen boundary. `SessionPolicyOutput` also carries the
   normalized correction timing so `summary_only` cannot silently become
   `immediate`.

## Test plan

- Null-policy regression is preserved by the existing exact context-payload
  tests (no `Teaching policy` block when the field is null).
- New planner, frozen repository, preview runtime, production runtime, and
  adapter tests prove the optional policy reaches the assembled provider payload
  without a network call.
- P2-007 provider-failure and stale-token tests remain green.

## Rollback

The field is additive and default-null. Rollback = revert the commit; no
schema/DAO/migration involvement. No data migration is needed because the
payload is transient (per-turn, not persisted).

## Frozen-layer impact

- `core/llm/LlmGenerationLifecycle.kt` — `LlmSessionPolicyContext`, optional
  request/planner field, bounded normalization, and prompt block.
- `core/data/llm/ChatGenerationInput` — one additive optional field and
  forwarding to the planner.
- `core/llm/ProductionLlmGenerationRuntime.kt` — include the policy block only
  when non-null.
- No `core/model`, `core/protocol`, Room, DAO, migration, or `SecretStore`
  change.

## Approval

Approved and implemented under the `wave0-a3-freeze-boundary.md` change-request
flow on 2026-08-22.
