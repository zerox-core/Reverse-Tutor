package com.reversetutor.core.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic teaching-action selector (NEWMP-V1-002 Task 2.2).
 *
 * Given a [GuidedLearningTurnInput] it scores the [TeachingAction] whitelist and
 * returns one bounded, normalized [TurnPlan]. The algorithm is pure and frozen:
 *
 *  - no random values, no wall clock, no model/Provider call;
 *  - identical input always produces an identical plan;
 *  - hard constraints (hint escalation cap, fragile-failure repair, stable
 *    promotion, goal change, off-topic) override the raw score so the model can
 *    never steer learning state by suggestion alone.
 *
 * Scoring combines intent fit, learner-state fit, recent-failure fit, template
 * strategy fit, action novelty and output-format fit — matching the weight
 * shape in the algorithm plan. Ties break on a fixed priority order, never on
 * evaluation order or time.
 */
object TeachingActionSelector {

    // Fixed tie-break priority: lower index wins when scores are equal.
    private val priority = listOf(
        TeachingAction.ClarifyGoal,
        TeachingAction.Diagnose,
        TeachingAction.SocraticQuestion,
        TeachingAction.WorkedExample,
        TeachingAction.Hint,
        TeachingAction.Explain,
        TeachingAction.CounterExample,
        TeachingAction.Practice,
        TeachingAction.Reflect,
        TeachingAction.Summarize
    )

    private const val W_INTENT = 0.30
    private const val W_STATE = 0.25
    private const val W_FAILURE = 0.15
    private const val W_TEMPLATE = 0.15
    private const val W_NOVELTY = 0.10
    private const val W_FORMAT = 0.05

    /**
     * The whitelist an intent may act on. The selector restricts candidate
     * scoring to these so an intent can never pick an off-whitelist action.
     */
    fun candidatesFor(intent: UserIntent): Set<TeachingAction> = when (intent) {
        UserIntent.AnswerAttempt -> setOf(
            TeachingAction.Diagnose, TeachingAction.SocraticQuestion,
            TeachingAction.WorkedExample, TeachingAction.CounterExample,
            TeachingAction.Practice, TeachingAction.Reflect, TeachingAction.Explain
        )
        UserIntent.AskQuestion -> setOf(
            TeachingAction.Explain, TeachingAction.SocraticQuestion,
            TeachingAction.Diagnose, TeachingAction.WorkedExample
        )
        UserIntent.AskHint -> setOf(
            TeachingAction.Hint, TeachingAction.SocraticQuestion,
            TeachingAction.WorkedExample, TeachingAction.Diagnose
        )
        UserIntent.AskExample -> setOf(
            TeachingAction.WorkedExample, TeachingAction.CounterExample,
            TeachingAction.Explain
        )
        UserIntent.Reflect -> setOf(
            TeachingAction.Reflect, TeachingAction.Summarize,
            TeachingAction.Practice
        )
        UserIntent.GoalChange -> setOf(
            TeachingAction.ClarifyGoal, TeachingAction.Diagnose
        )
        UserIntent.OffTopic -> setOf(
            TeachingAction.ClarifyGoal, TeachingAction.Summarize
        )
        UserIntent.ToolRequest -> setOf(
            TeachingAction.Summarize, TeachingAction.Explain
        )
        UserIntent.Ambiguous -> setOf(
            TeachingAction.ClarifyGoal, TeachingAction.Diagnose,
            TeachingAction.SocraticQuestion
        )
    }

    fun select(rawInput: GuidedLearningTurnInput): TurnPlan {
        val input = rawInput.normalized()
        val intent = input.userIntentHint
        val state = input.conceptStateFor(input.conceptKey)

        // ---- Hard rules: deterministic overrides before scoring ----
        hardRule(intent, state, input)?.let { return it.normalized() }

        // ---- Otherwise score the intent's candidate whitelist ----
        val candidates = candidatesFor(intent)
        val chosen = candidates
            .map { action -> action to score(action, intent, state, input) }
            .sortedWith(
                compareByDescending<Pair<TeachingAction, Double>> { it.second }
                    .thenBy { priority.indexOf(it.first) }
            )
            .first()
            .first

        return buildPlan(chosen, intent, state, input).normalized()
    }

    /**
     * Returns a forced plan when a hard constraint applies, or null to continue
     * with scoring. These encode the checklist's mandatory rules and take
     * precedence so the model's [GuidedLearningTurnInput.userIntentHint] never
     * bypasses them.
     */
    private fun hardRule(
        intent: UserIntent,
        state: ConceptLearningState,
        input: GuidedLearningTurnInput
    ): TurnPlan? {
        val recentHints = input.recentSignals.askedForHintCount
        val failureCount = max(state.failureStreak, input.recentSignals.consecutiveFailureCount)
        val successCount = max(state.successStreak, input.recentSignals.consecutiveSuccessCount)

        return when {
            // Two consecutive AskHint without progress → change strategy, not level up.
            intent == UserIntent.AskHint && recentHints >= 2 ->
                buildPlan(TeachingAction.SocraticQuestion, intent, state, input)

            // Fragile + recent failure → repair the gap first.
            state.status == ConceptStatus.Fragile && failureCount > 0 ->
                buildPlan(TeachingAction.Diagnose, intent, state, input)

            // Stable + sustained success → promote to transfer practice.
            state.status == ConceptStatus.Stable && successCount >= 2 ->
                buildPlan(TeachingAction.Practice, intent, state, input)

            // Mastered → reflect / summarize rather than re-teach.
            state.status == ConceptStatus.Mastered &&
                (intent == UserIntent.AnswerAttempt || intent == UserIntent.Reflect) ->
                buildPlan(TeachingAction.Reflect, intent, state, input)

            intent == UserIntent.GoalChange ->
                buildPlan(TeachingAction.ClarifyGoal, intent, state, input)

            intent == UserIntent.OffTopic ->
                buildPlan(TeachingAction.ClarifyGoal, intent, state, input)

            // A pending visible question is resolved before broad explanation.
            input.visibleContext.unresolvedQuestionCount > 0 ->
                buildPlan(TeachingAction.Diagnose, intent, state, input)

            else -> null
        }
    }

    // ---- Deterministic scoring ----

    private fun score(
        action: TeachingAction,
        intent: UserIntent,
        state: ConceptLearningState,
        input: GuidedLearningTurnInput
    ): Double =
        W_INTENT * intentFit(action, intent) +
            W_STATE * learnerStateFit(action, state) +
            W_FAILURE * recentFailureFit(action, state, input) +
            W_TEMPLATE * templateStrategyFit(action, input) +
            W_NOVELTY * novelty(action, input.recentSignals.lastAction) +
            W_FORMAT * formatFit(action, intent)

    private fun intentFit(action: TeachingAction, intent: UserIntent): Int = when (intent) {
        UserIntent.AskExample -> if (action == TeachingAction.WorkedExample) 3 else when (action) {
            TeachingAction.CounterExample -> 2
            TeachingAction.Explain -> 1
            else -> 0
        }
        UserIntent.AskHint -> if (action == TeachingAction.Hint) 3 else when (action) {
            TeachingAction.SocraticQuestion -> 2
            TeachingAction.Diagnose -> 1
            else -> 0
        }
        UserIntent.AskQuestion -> if (action == TeachingAction.Explain) 3 else when (action) {
            TeachingAction.SocraticQuestion, TeachingAction.Diagnose -> 1
            else -> 0
        }
        UserIntent.AnswerAttempt -> if (action == TeachingAction.SocraticQuestion) 3 else when (action) {
            TeachingAction.Diagnose, TeachingAction.CounterExample -> 2
            else -> 0
        }
        UserIntent.Reflect -> if (action == TeachingAction.Reflect) 3 else when (action) {
            TeachingAction.Summarize -> 2
            TeachingAction.Practice -> 1
            else -> 0
        }
        UserIntent.ToolRequest -> if (action == TeachingAction.Summarize) 3 else if (action == TeachingAction.Explain) 2 else 0
        UserIntent.Ambiguous -> if (action == TeachingAction.Diagnose) 3 else if (action == TeachingAction.ClarifyGoal) 2 else 0
        else -> if (action == TeachingAction.ClarifyGoal) 3 else 0
    }

    private fun learnerStateFit(action: TeachingAction, state: ConceptLearningState): Int =
        when (state.status) {
            ConceptStatus.Unknown -> if (action == TeachingAction.Diagnose || action == TeachingAction.Explain) 3 else 0
            ConceptStatus.Exploring ->
                if (action == TeachingAction.SocraticQuestion || action == TeachingAction.Hint) 2 else 0
            ConceptStatus.Fragile -> when (action) {
                TeachingAction.WorkedExample, TeachingAction.Diagnose -> 2
                TeachingAction.Hint -> 1
                else -> 0
            }
            ConceptStatus.Stable -> when (action) {
                TeachingAction.Practice, TeachingAction.CounterExample -> 2
                TeachingAction.Reflect -> 1
                else -> 0
            }
            ConceptStatus.Mastered -> when (action) {
                TeachingAction.Reflect, TeachingAction.Summarize, TeachingAction.Practice -> 2
                else -> 0
            }
        }

    private fun recentFailureFit(
        action: TeachingAction,
        state: ConceptLearningState,
        input: GuidedLearningTurnInput
    ): Int {
        val failing = state.failureStreak > 0 ||
            input.recentSignals.consecutiveFailureCount > 0 ||
            state.misconceptionTags.isNotEmpty() ||
            input.learnerProfile.knownGaps.isNotEmpty()
        return when {
            !failing -> 0
            action == TeachingAction.Diagnose || action == TeachingAction.WorkedExample -> 2
            action == TeachingAction.Hint -> 1
            else -> 0
        }
    }

    private fun templateStrategyFit(action: TeachingAction, input: GuidedLearningTurnInput): Int {
        val strategy = input.sessionTemplate.dialogueStrategy.lowercase()
        return when {
            strategy.contains("scaffold") && action == TeachingAction.Hint -> 2
            strategy.contains("probe") && action == TeachingAction.SocraticQuestion -> 2
            strategy.contains("examine") && action == TeachingAction.WorkedExample -> 2
            else -> 0
        }
    }

    private fun novelty(action: TeachingAction, lastAction: TeachingAction?): Int =
        if (lastAction == action) 0 else 1

    private fun formatFit(action: TeachingAction, intent: UserIntent): Int = when (action) {
        TeachingAction.WorkedExample, TeachingAction.CounterExample -> if (intent == UserIntent.AskExample) 2 else 1
        TeachingAction.Explain -> 2
        TeachingAction.Practice, TeachingAction.SocraticQuestion -> 1
        else -> 0
    }

    // ---- Plan construction ----

    private fun buildPlan(
        action: TeachingAction,
        intent: UserIntent,
        state: ConceptLearningState,
        input: GuidedLearningTurnInput
    ): TurnPlan = TurnPlan(
        actionType = action,
        secondaryAction = secondaryFor(action, intent),
        learningObjective = objectiveFor(action, intent, input.conceptKey),
        conceptKey = input.conceptKey,
        expectedUserMove = expectedMoveFor(action, intent),
        responseFormat = formatFor(action, input.learnerProfile),
        hintLevel = hintLevelFor(action, intent, state, input),
        evidenceRequirement = evidenceFor(action, intent),
        nextActionOnSuccess = nextOnSuccess(action, state),
        nextActionOnFailure = nextOnFailure(action, state)
    )

    private fun secondaryFor(action: TeachingAction, intent: UserIntent): TeachingAction? =
        when (action) {
            TeachingAction.Diagnose -> TeachingAction.WorkedExample
            TeachingAction.WorkedExample -> TeachingAction.Practice
            TeachingAction.SocraticQuestion -> TeachingAction.Hint
            else -> null
        }?.takeIf { it != action }

    private fun objectiveFor(action: TeachingAction, intent: UserIntent, conceptKey: String): String {
        val concept = conceptKey.ifBlank { "当前知识点" }
        return when (action) {
            TeachingAction.Diagnose -> "先定位「$concept」上的具体缺口"
            TeachingAction.SocraticQuestion -> "用一个引导问题让学习者自己推导「$concept」"
            TeachingAction.Hint -> "只给下一步线索，不直接给「$concept」的完整答案"
            TeachingAction.Explain -> "分层解释「$concept」已确认的知识缺口"
            TeachingAction.WorkedExample -> "给出一个有边界的「$concept」示例"
            TeachingAction.CounterExample -> "用反例帮助区分「$concept」的适用边界"
            TeachingAction.Practice -> "生成一道相近的「$concept」迁移练习"
            TeachingAction.Reflect -> "让学习者复述「$concept」的规则与迁移条件"
            TeachingAction.Summarize -> "压缩本轮要点，确认「$concept」的掌握情况"
            TeachingAction.ClarifyGoal -> "确认新的学习目标或范围，不直接改写模板"
        }
    }

    private fun expectedMoveFor(action: TeachingAction, intent: UserIntent): String = when (action) {
        TeachingAction.Diagnose -> "回答一个最小诊断问题"
        TeachingAction.SocraticQuestion -> "自己推导下一步，而不是等待答案"
        TeachingAction.Hint -> "利用线索尝试下一步"
        TeachingAction.Explain -> "复述关键步骤以确认理解"
        TeachingAction.WorkedExample -> "仿照示例完成一次独立求解"
        TeachingAction.CounterExample -> "比较正反例并说明差异"
        TeachingAction.Practice -> "独立完成相近练习"
        TeachingAction.Reflect -> "总结规则与适用条件"
        TeachingAction.Summarize -> "确认要点或补充遗漏"
        TeachingAction.ClarifyGoal -> "明确调整后的目标、学科或难度"
    }

    private fun formatFor(action: TeachingAction, learnerProfile: LearnerProfileSnapshot): ResponseFormat {
        val pace = learnerProfile.preferredPace.lowercase()
        return when {
            pace == "compact" && action == TeachingAction.Explain -> ResponseFormat.Checklist
            pace == "slow" && action in setOf(
                TeachingAction.Explain,
                TeachingAction.WorkedExample,
                TeachingAction.Diagnose
            ) -> ResponseFormat.Steps
            action in setOf(
                TeachingAction.WorkedExample,
                TeachingAction.Explain,
                TeachingAction.Diagnose
            ) -> ResponseFormat.Steps
            action == TeachingAction.Practice -> ResponseFormat.Checklist
            action == TeachingAction.CounterExample -> ResponseFormat.Table
            else -> ResponseFormat.Plain
        }
    }

    private fun hintLevelFor(
        action: TeachingAction,
        intent: UserIntent,
        state: ConceptLearningState,
        input: GuidedLearningTurnInput
    ): Int {
        if (action != TeachingAction.Hint) return GuidedLearningContracts.HINT_LEVEL_MIN
        val hints = input.recentSignals.askedForHintCount.coerceAtLeast(1)
        return min(GuidedLearningContracts.HINT_LEVEL_MAX, hints)
    }

    private fun evidenceFor(action: TeachingAction, intent: UserIntent): EvidenceRequirement = when {
        intent == UserIntent.ToolRequest -> EvidenceRequirement.ToolReceipt
        intent == UserIntent.OffTopic || intent == UserIntent.GoalChange -> EvidenceRequirement.None
        intent == UserIntent.AnswerAttempt ||
            action == TeachingAction.Practice ||
            action == TeachingAction.SocraticQuestion ||
            action == TeachingAction.Hint ||
            action == TeachingAction.Diagnose -> EvidenceRequirement.UserAnswer
        else -> EvidenceRequirement.None
    }

    private fun nextOnSuccess(action: TeachingAction, state: ConceptLearningState): TeachingAction? =
        when (action) {
            TeachingAction.Diagnose -> TeachingAction.SocraticQuestion
            TeachingAction.SocraticQuestion, TeachingAction.Hint -> TeachingAction.WorkedExample
            TeachingAction.WorkedExample -> TeachingAction.Practice
            TeachingAction.Practice -> TeachingAction.Reflect
            TeachingAction.Explain -> TeachingAction.SocraticQuestion
            else -> null
        }

    private fun nextOnFailure(action: TeachingAction, state: ConceptLearningState): TeachingAction? =
        when (action) {
            TeachingAction.Practice, TeachingAction.SocraticQuestion, TeachingAction.Reflect -> TeachingAction.Diagnose
            TeachingAction.WorkedExample -> TeachingAction.CounterExample
            TeachingAction.Hint -> TeachingAction.Diagnose
            else -> null
        }
}
