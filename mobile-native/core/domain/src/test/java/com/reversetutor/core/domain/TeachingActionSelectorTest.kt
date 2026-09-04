package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.2 acceptance for the deterministic [TeachingActionSelector].
 *
 * Every assertion is derived from a frozen input — no clock, no random, no
 * model. The selector must be reproducible (same input ⇒ same [TurnPlan]) and
 * must honour the hard constraints in the execution checklist.
 */
class TeachingActionSelectorTest {

    private fun concept(
        key: String = "函数单调性",
        status: ConceptStatus = ConceptStatus.Exploring,
        successStreak: Int = 0,
        failureStreak: Int = 0,
        lastAction: TeachingAction? = null
    ) = key to ConceptLearningState(
        status = status,
        successStreak = successStreak,
        failureStreak = failureStreak,
        lastAction = lastAction
    )

    private fun input(
        intent: UserIntent,
        conceptKey: String = "函数单调性",
        state: Pair<String, ConceptLearningState> = concept(),
        recentActions: List<TeachingAction> = emptyList()
    ) = GuidedLearningTurnInput(
        sessionId = "s-1",
        windowId = "w-1",
        spaceId = "sp-1",
        conceptKey = conceptKey,
        userIntentHint = intent,
        conceptStates = mapOf(state),
        recentActions = recentActions
    )

    // ---- 确定性 ----

    @Test
    fun identicalInputsProduceIdenticalPlans() {
        val one = TeachingActionSelector.select(
            input(UserIntent.AskHint, recentActions = listOf(TeachingAction.Diagnose))
        )
        val two = TeachingActionSelector.select(
            input(UserIntent.AskHint, recentActions = listOf(TeachingAction.Diagnose))
        )
        assertEquals(one, two)
    }

    // ---- 意图直接映射 ----

    @Test
    fun askExampleSelectsWorkedExample() {
        assertEquals(
            TeachingAction.WorkedExample,
            TeachingActionSelector.select(input(UserIntent.AskExample)).actionType
        )
    }

    @Test
    fun reflectSelectsReflectAction() {
        assertEquals(
            TeachingAction.Reflect,
            TeachingActionSelector.select(input(UserIntent.Reflect)).actionType
        )
    }

    @Test
    fun toolRequestSelectsSummarizeWithToolReceiptEvidence() {
        val plan = TeachingActionSelector.select(input(UserIntent.ToolRequest))
        assertEquals(TeachingAction.Summarize, plan.actionType)
        assertEquals(EvidenceRequirement.ToolReceipt, plan.evidenceRequirement)
    }

    // ---- 硬规则 ----

    @Test
    fun twoConsecutiveHintsWithoutAnswerMustChangeHintStrategy() {
        // 连续两轮 AskHint 且未作答：不得再给同级 Hint，须转 SocraticQuestion 或 WorkedExample。
        val plan = TeachingActionSelector.select(
            input(
                intent = UserIntent.AskHint,
                recentActions = listOf(TeachingAction.Hint, TeachingAction.Hint)
            )
        )
        assertTrue(
            "连续求提示后动作必须是 ${TeachingAction.SocraticQuestion} 或 ${TeachingAction.WorkedExample}，实际 ${plan.actionType}",
            plan.actionType == TeachingAction.SocraticQuestion ||
                plan.actionType == TeachingAction.WorkedExample
        )
    }

    @Test
    fun fragileWithRecentFailurePrefersDiagnoseOrWorkedExample() {
        val plan = TeachingActionSelector.select(
            input(
                intent = UserIntent.AnswerAttempt,
                state = concept(status = ConceptStatus.Fragile, failureStreak = 2)
            )
        )
        assertTrue(
            "Fragile+失败应优先 Diagnose/WorkedExample，实际 ${plan.actionType}",
            plan.actionType == TeachingAction.Diagnose ||
                plan.actionType == TeachingAction.WorkedExample
        )
    }

    @Test
    fun stableWithSuccessStreakPrefersPracticeOrReflect() {
        val plan = TeachingActionSelector.select(
            input(
                intent = UserIntent.AnswerAttempt,
                state = concept(status = ConceptStatus.Stable, successStreak = 3)
            )
        )
        assertTrue(
            "Stable+连续成功应优先 Practice/Reflect，实际 ${plan.actionType}",
            plan.actionType == TeachingAction.Practice ||
                plan.actionType == TeachingAction.Reflect
        )
    }

    @Test
    fun goalChangeSelectsClarifyGoal() {
        val plan = TeachingActionSelector.select(input(UserIntent.GoalChange))
        assertEquals(TeachingAction.ClarifyGoal, plan.actionType)
    }

    @Test
    fun offTopicSelectsClarifyGoalAndRequiresNoLearningEvidence() {
        val plan = TeachingActionSelector.select(input(UserIntent.OffTopic))
        assertEquals(TeachingAction.ClarifyGoal, plan.actionType)
        assertEquals(
            "OffTopic 不得产生学习事实相关证据要求",
            EvidenceRequirement.None,
            plan.evidenceRequirement
        )
    }

    @Test
    fun ambiguousSelectsClarifyGoalOrDiagnose() {
        val plan = TeachingActionSelector.select(input(UserIntent.Ambiguous))
        assertTrue(
            "Ambiguous 应先澄清或诊断，实际 ${plan.actionType}",
            plan.actionType == TeachingAction.ClarifyGoal ||
                plan.actionType == TeachingAction.Diagnose
        )
    }

    // ---- 输出格式与计划有界性 ----

    @Test
    fun explainRequestSelectsExplainWithLayeredFormat() {
        val plan = TeachingActionSelector.select(input(UserIntent.AskQuestion))
        assertEquals(TeachingAction.Explain, plan.actionType)
        assertEquals(ResponseFormat.Steps, plan.responseFormat)
    }

    @Test
    fun planOutputIsAlwaysNormalizedAndBounded() {
        val plan = TeachingActionSelector.select(input(UserIntent.AnswerAttempt))

        // 计划自身必须已归一化：hintLevel 落在合法区间，文本非空。
        assertTrue(
            "hintLevel 越界: ${plan.hintLevel}",
            plan.hintLevel in GuidedLearningContracts.HINT_LEVEL_MIN..GuidedLearningContracts.HINT_LEVEL_MAX
        )
        assertTrue(plan.learningObjective.isNotBlank())
        assertTrue(plan.conceptKey.isNotBlank())
    }

    // ---- 单一主动作，最多一个辅助动作 ----

    @Test
    fun atMostOneSecondaryActionAndNeverEqualsPrimary() {
        val intents = UserIntent.values()
        intents.forEach { intent ->
            val plan = TeachingActionSelector.select(input(intent))
            assertTrue(
                "$intent 的辅助动作不得等于主动作",
                plan.secondaryAction == null || plan.secondaryAction != plan.actionType
            )
        }
    }

    @Test
    fun unresolvedVisibleQuestionPrioritizesDiagnosis() {
        val plan = TeachingActionSelector.select(
            input(UserIntent.AskQuestion).copy(
                visibleContext = VisibleLearningContext(unresolvedQuestionCount = 1)
            )
        )
        assertEquals(TeachingAction.Diagnose, plan.actionType)
    }

    @Test
    fun hintEscalationUsesOnlyRecentTurnSignals() {
        val plan = TeachingActionSelector.select(
            input(UserIntent.AskHint).copy(
                recentActions = listOf(TeachingAction.Hint, TeachingAction.Hint),
                recentHintsWithoutAnswer = 9,
                recentSignals = RecentTurnSignals(askedForHintCount = 2)
            )
        )
        assertEquals(TeachingAction.SocraticQuestion, plan.actionType)
    }

    @Test
    fun goalChangeDoesNotMutateTheTemplateSnapshot() {
        val template = SessionTemplateSnapshot(
            subject = "数学",
            learningGoal = "掌握函数",
            dialogueStrategy = "probe"
        )
        val source = input(UserIntent.GoalChange).copy(sessionTemplate = template)

        val plan = TeachingActionSelector.select(source)

        assertEquals(TeachingAction.ClarifyGoal, plan.actionType)
        assertEquals(template, source.sessionTemplate)
    }

    @Test
    fun learnerPaceChangesOnlyThePresentationFormat() {
        val slow = TeachingActionSelector.select(
            input(UserIntent.AskQuestion).copy(
                learnerProfile = LearnerProfileSnapshot(preferredPace = "slow")
            )
        )
        val compact = TeachingActionSelector.select(
            input(UserIntent.AskQuestion).copy(
                learnerProfile = LearnerProfileSnapshot(preferredPace = "compact")
            )
        )

        assertEquals(TeachingAction.Explain, slow.actionType)
        assertEquals(TeachingAction.Explain, compact.actionType)
        assertEquals(ResponseFormat.Steps, slow.responseFormat)
        assertEquals(ResponseFormat.Checklist, compact.responseFormat)
    }

    @Test
    fun identicalRichInputsStillProduceIdenticalPlans() {
        val richInput = input(UserIntent.AskQuestion).copy(
            sessionTemplate = SessionTemplateSnapshot(
                subject = "数学",
                learningGoal = "掌握函数单调性",
                dialogueStrategy = "probe"
            ),
            visibleContext = VisibleLearningContext(
                visibleMessageIds = listOf("m-1", "m-2"),
                currentConceptKeys = listOf("函数单调性"),
                latestAssistantAction = TeachingAction.Hint
            ),
            learnerProfile = LearnerProfileSnapshot(
                declaredLevel = "高中",
                preferredPace = "slow",
                knownGaps = listOf("符号判断")
            ),
            recentSignals = RecentTurnSignals(
                lastIntent = UserIntent.AskHint,
                lastAction = TeachingAction.Hint,
                answeredLastPrompt = true,
                consecutiveFailureCount = 1,
                lastEvidenceAvailable = true
            )
        )

        assertEquals(
            TeachingActionSelector.select(richInput),
            TeachingActionSelector.select(richInput)
        )
    }
}
