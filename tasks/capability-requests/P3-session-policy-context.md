# P3 Capability Request: Session Policy / Context Injection into ChatGenerationInput

> Status: **requested** — not yet implemented. The frozen `core/data/llm`
> `ChatGenerationInput` must not be modified without a change request +
> user approval. This document is the change request.

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

This means the strategy decision and rich context are computed but never
reach the LLM prompt in the frozen path. This is acceptable for P2 (the
wiring forms a single entry point and the contract is testable), but the gap
must be closed in P3.

## Requested minimal change (P3)

Add a backward-compatible, optional policy payload to `ChatGenerationInput`
(or a sibling input the frozen planner can consume):

```
data class ChatGenerationInput(
    … // existing fields unchanged
    val sessionPolicy: SessionPolicyWireInput? = null   // NEW, default null
)

data class SessionPolicyWireInput(
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
3. **Adapter wiring** — once P3 lands, `ChatGenerationPortAdapter.buildInput`
   maps `GenerationRequest.policy` → `SessionPolicyWireInput`. The adapter's
   honesty doc-comment is updated to "policy injected" (replacing the current
   "not injected" note). No other wiring change.

## Test plan

- Existing frozen `ChatGenerationRepository` / `LlmGenerationPlanner` tests
  must pass unchanged (null policy = current behaviour).
- New test: when `sessionPolicy` is non-null, the planner's prompt/profile
  includes the action/role/knowledgePoint (assertion on the assembled prompt
  string, no network).
- New test: `sessionPolicy = null` produces the same prompt as before the
  change (regression guard).
- NATIVE-P2-007 adapter test `provider failure maps to safe code …` remains
  green (the mapping path is unchanged).

## Rollback

The field is additive and default-null. Rollback = revert the commit; no
schema/DAO/migration involvement. No data migration is needed because the
payload is transient (per-turn, not persisted).

## Frozen-layer impact

- `core/data/llm/ChatGenerationInput` — one additive field.
- `core/llm/LlmGenerationPlanner` — consume the new field when non-null.
- No `core/model`, `core/protocol`, Room, DAO, migration, or `SecretStore`
  change.

## Approval

Requires user approval per `wave0-a3-freeze-boundary.md` change-request flow
before any frozen-layer edit.
