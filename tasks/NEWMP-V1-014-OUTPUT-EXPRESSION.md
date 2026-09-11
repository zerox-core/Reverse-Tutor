# NEWMP-V1-014: Output expression layer (old persona into the native Worker)

> Date: 2026-09-11
> Scope: `core/llm` prompt assembly only. Approved frozen-layer change
> (user decision 2026-09-11, interaction 7684107069779102681: "批准动核心" +
> style "老版学生腔").
> Problem being solved: the teaching brain (L2 policy) was fine, but the
> production LLM request carried only machine-readable English field dumps +
> user text. No persona, no speaking rules — the chat read like a generic
> assistant ("只会聊天"). The old engine.py expression layer (SYSTEM_TEMPLATE
> persona, per-action strategy, clue/challenge discipline) was never migrated
> (parity row 1 = partial).

## What changed

1. `LlmGenerationLifecycle.kt` (frozen, approved):
   - `reverseTutorStudentPromptBlock()` rewritten from the English contract to
     the Chinese 反转教学·学生表达契约: 用户是老师/你是学生 AI、教即是学、
     全程学生口吻（角色以「会话模板」证据为准）、教学策略隐身、例子带具体数字
     最多 5 步用「→」衔接、一轮一个教学动作最多三小段或四短行、关键词
     **加粗**、最多一个问题绝不自问自答。Firing condition unchanged
     (only when sessionPolicy/guidedTurnPlan present; legacy unplanned turns
     keep the exact old shape).
   - `sessionPolicyPromptBlock()` now appends
     `表达要求: <sessionPolicyDirectiveFor(actionType)>` after `Turn intent`.
2. `LlmStudentExpressionPolicy.kt` (frozen, approved): new
   `sessionPolicyDirectiveFor(actionType)` — Chinese per-action expression
   directives for the legacy session-policy vocabulary: ask / probe /
   challenge / clue / scaffold_example / small_lecture / examiner_verify /
   emote / persuade / next / recap + goal-companion actions decompose /
   advance / verify_done / unblock / empathize / observe / soft_guide, with
   the old engine's discipline rules baked in (challenge: 把错误说成自己的
   困惑，禁「你错了/不对/正确答案是/我来纠正你」; clue: 「老师，据说……」开头，
   禁「我来教你/步骤如下/根据定义」). Existing English `directiveFor()` for
   guided-plan vocabulary is untouched.
3. Tests updated/added:
   - `GuidedLearningTurnPlanContextTest`: 3 contract assertions moved to the
     Chinese lines.
   - `LlmGenerationLifecycleTest`: rhythm-contract assertions moved to the
     Chinese lines; `providerPayloadsCarrySessionPolicyWithoutNetworkCalls`
     now also asserts `表达要求:` and the probe directive reach the payload.
   - NEW `LlmStudentExpressionPolicyTest`: locks the legacy-vocabulary Chinese
     mapping (incl. forbidden-phrase phrases present as discipline text),
     the unknown-action fallback, and that guided vocabulary keeps English.

## Verification

Full-project `gradle test` (all modules, debug + release): BUILD SUCCESSFUL,
exit 0, 465 actionable tasks. `:core:llm` XML results: LlmStudentExpressionPolicyTest
3/3, GuidedLearningTurnPlanContextTest 6/6, LlmGenerationLifecycleTest 10/10,
0 failures / 0 errors.

## Not claimed

- `engine._discipline_reply_for_role` post-generation text rewriting is NOT
  migrated (prompt-side directives carry the expression rules; a model can
  still violate them and no local fallback rewrites the reply).
- The persona fires only on the sessionPolicy/guidedTurnPlan path — unplanned
  legacy turns intentionally keep the old bare request shape.
- Effect on real devices is a UX question: needs manual chat acceptance
  (deferred to unified acceptance per project convention).
