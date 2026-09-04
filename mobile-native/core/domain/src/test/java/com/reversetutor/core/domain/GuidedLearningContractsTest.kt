package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.1 acceptance for the guided-learning decision contracts.
 *
 * These tests assert the domain value sets exist, normalize safely, and enforce
 * the field bounds required by the execution checklist. They never reference
 * Android, Room, a Repository, or an LLM runtime — only the pure Kotlin
 * contracts declared in [GuidedLearningContracts].
 */
class GuidedLearningContractsTest {

    // ---- 意图 / 状态 / 动作 白名单存在且成员完整 ----

    @Test
    fun userIntentCoversRequiredCategories() {
        val required = setOf(
            UserIntent.AnswerAttempt, UserIntent.AskQuestion, UserIntent.AskHint,
            UserIntent.AskExample, UserIntent.Reflect, UserIntent.GoalChange,
            UserIntent.OffTopic, UserIntent.ToolRequest, UserIntent.Ambiguous
        )
        assertEquals(required, UserIntent.values().toSet())
    }

    @Test
    fun conceptStatusCoversRequiredCategories() {
        val required = setOf(
            ConceptStatus.Unknown, ConceptStatus.Exploring, ConceptStatus.Fragile,
            ConceptStatus.Stable, ConceptStatus.Mastered
        )
        assertEquals(required, ConceptStatus.values().toSet())
    }

    @Test
    fun teachingActionCoversRequiredCategories() {
        val required = setOf(
            TeachingAction.Diagnose, TeachingAction.SocraticQuestion, TeachingAction.Hint,
            TeachingAction.Explain, TeachingAction.WorkedExample, TeachingAction.CounterExample,
            TeachingAction.Practice, TeachingAction.Reflect, TeachingAction.Summarize,
            TeachingAction.ClarifyGoal
        )
        assertEquals(required, TeachingAction.values().toSet())
    }

    @Test
    fun turnPlanOutputEnumsAreBoundedWhitelists() {
        assertEquals(
            setOf(
                ResponseFormat.Plain, ResponseFormat.Steps, ResponseFormat.Code,
                ResponseFormat.Table, ResponseFormat.Checklist
            ),
            ResponseFormat.values().toSet()
        )
        assertEquals(
            setOf(
                EvidenceRequirement.None, EvidenceRequirement.LocalCheck,
                EvidenceRequirement.UserAnswer, EvidenceRequirement.ToolReceipt
            ),
            EvidenceRequirement.values().toSet()
        )
    }

    // ---- 概念学习状态归一化 ----

    @Test
    fun conceptLearningStateClampsBoundsAndDropsNegativeCounts() {
        val noisy = ConceptLearningState(
            status = ConceptStatus.Fragile,
            confidence = 1.7f,
            attemptCount = -5,
            successStreak = -2,
            failureStreak = -9,
            misconceptionTags = listOf("  跳步骤  ", "", "  ", "跳步骤", "混淆符号"),
            lastAction = TeachingAction.Hint
        )

        val clean = noisy.normalized()

        assertEquals(1f, clean.confidence)
        assertEquals(0, clean.attemptCount)
        assertEquals(0, clean.successStreak)
        assertEquals(0, clean.failureStreak)
        // 去空白、去重、trim
        assertEquals(listOf("跳步骤", "混淆符号"), clean.misconceptionTags)
    }

    @Test
    fun conceptLearningStateConfidenceLowIsClampedToZero() {
        val low = ConceptLearningState(confidence = -0.4f).normalized()
        assertEquals(0f, low.confidence)
    }

    @Test
    fun conceptLearningStateBoundsMisconceptionTagList() {
        val many = ConceptLearningState(
            misconceptionTags = (1..50).map { "tag$it" }
        ).normalized()
        assertTrue(
            "misconceptionTags 必须有上限",
            many.misconceptionTags.size <= GuidedLearningContracts.MISCONCEPTION_TAG_MAX
        )
        many.misconceptionTags.forEach { assertTrue(it.length <= GuidedLearningContracts.MISCONCEPTION_TAG_LEN) }
    }

    // ---- TurnPlan 归一化：长度、hint 区间、可选字段一致 ----

    @Test
    fun turnPlanTrimsAndLengthCapsTextFields() {
        val longText = "目标".repeat(200)
        val plan = TurnPlan(
            actionType = TeachingAction.Explain,
            learningObjective = "  $longText  ",
            conceptKey = "  函数单调性 ",
            expectedUserMove = longText
        ).normalized()

        assertTrue(plan.learningObjective.length <= GuidedLearningContracts.OBJECTIVE_MAX)
        assertTrue(plan.expectedUserMove.length <= GuidedLearningContracts.EXPECTED_MOVE_MAX)
        assertEquals("函数单调性", plan.conceptKey)
    }

    @Test
    fun turnPlanClampsHintLevelIntoRange() {
        assertEquals(3, TurnPlan(TeachingAction.Hint, hintLevel = 99).normalized().hintLevel)
        assertEquals(0, TurnPlan(TeachingAction.Hint, hintLevel = -4).normalized().hintLevel)
        assertEquals(2, TurnPlan(TeachingAction.Hint, hintLevel = 2).normalized().hintLevel)
    }

    @Test
    fun turnPlanStripsSecretLikeSubstringsFromText() {
        val plan = TurnPlan(
            actionType = TeachingAction.Explain,
            learningObjective = "see https://api.example.com/v1 with Authorization: Bearer sk-abcdef123456"
        ).normalized()

        val lower = plan.learningObjective.lowercase()
        assertFalse("URL 必须被剔除", lower.contains("http"))
        assertFalse("Authorization 片段必须被剔除", lower.contains("authorization"))
        assertFalse("Bearer 片段必须被剔除", lower.contains("bearer"))
        assertFalse("sk- 密钥片段必须被剔除", lower.contains("sk-"))
    }

    @Test
    fun turnPlanClearsSecondaryActionWhenItEqualsPrimary() {
        val plan = TurnPlan(
            actionType = TeachingAction.Hint,
            secondaryAction = TeachingAction.Hint
        ).normalized()
        assertNull(plan.secondaryAction)
    }

    @Test
    fun turnPlanAllowsDistinctSecondaryAction() {
        val plan = TurnPlan(
            actionType = TeachingAction.Diagnose,
            secondaryAction = TeachingAction.WorkedExample
        ).normalized()
        assertEquals(TeachingAction.WorkedExample, plan.secondaryAction)
    }

    // ---- GuidedLearningTurnInput 归一化 ----

    @Test
    fun turnInputNormalizesNestedConceptStatesAndIds() {
        val input = GuidedLearningTurnInput(
            sessionId = "  s-1  ",
            windowId = "  w-9 ",
            spaceId = "  sp-3 ",
            userIntentHint = UserIntent.AskHint,
            conceptStates = mapOf("  函数单调性  " to ConceptLearningState(confidence = 5f)),
            recentActions = listOf(TeachingAction.Hint, TeachingAction.Hint)
        ).normalized()

        assertEquals("s-1", input.sessionId)
        assertEquals("w-9", input.windowId)
        assertEquals("sp-3", input.spaceId)
        assertEquals(UserIntent.AskHint, input.userIntentHint)
        assertEquals(setOf("函数单调性"), input.conceptStates.keys)
        assertEquals(1f, input.conceptStates.getValue("函数单调性").confidence)
        assertEquals(listOf(TeachingAction.Hint, TeachingAction.Hint), input.recentActions)
    }

    @Test
    fun turnInputBindsConceptOrDefaultStateSafely() {
        val empty = GuidedLearningTurnInput()
        val state = empty.conceptStateFor("不存在的知识点")
        assertEquals(ConceptStatus.Unknown, state.status)
        // 未知概念返回默认状态，不抛异常
        assertNotEquals(ConceptLearningState(), state.copy(lastAction = TeachingAction.Hint))
    }

    @Test
    fun richSnapshotsBoundCollectionsAndRedactSensitiveText() {
        val input = GuidedLearningTurnInput(
            sessionTemplate = SessionTemplateSnapshot(
                subject = "  数学 https://example.test  ",
                learningGoal = "Bearer sk-should-not-survive",
                learnerRole = "  高中生  ",
                dialogueStrategy = "  scaffold  ",
                correctionTiming = "  immediate  ",
                preferredResponseFormat = "  steps  "
            ),
            visibleContext = VisibleLearningContext(
                visibleMessageIds = (1..20).map { " message-$it " },
                currentConceptKeys = (1..10).map { " 概念$it " },
                unresolvedQuestionCount = -3
            ),
            learnerProfile = LearnerProfileSnapshot(
                declaredLevel = "  beginner ",
                preferredPace = "  slow ",
                preferredTone = "  calm ",
                knownStrengths = (1..12).map { " 优势$it " },
                knownGaps = (1..12).map { " 缺口$it " }
            )
        ).normalized()

        assertFalse(input.sessionTemplate.subject.contains("http"))
        assertFalse(input.sessionTemplate.learningGoal.contains("sk-"))
        assertEquals("高中生", input.sessionTemplate.learnerRole)
        assertEquals(12, input.visibleContext.visibleMessageIds.size)
        assertEquals(6, input.visibleContext.currentConceptKeys.size)
        assertEquals(0, input.visibleContext.unresolvedQuestionCount)
        assertEquals(8, input.learnerProfile.knownStrengths.size)
        assertEquals(8, input.learnerProfile.knownGaps.size)
    }

    @Test
    fun legacyRecentFieldsMigrateOnlyWhenNewSignalsAreUnset() {
        val migrated = GuidedLearningTurnInput(
            recentActions = listOf(TeachingAction.Explain, TeachingAction.Hint),
            recentHintsWithoutAnswer = 2
        ).normalized()
        assertEquals(TeachingAction.Hint, migrated.recentSignals.lastAction)
        assertEquals(2, migrated.recentSignals.askedForHintCount)

        val authoritative = GuidedLearningTurnInput(
            recentActions = listOf(TeachingAction.Hint, TeachingAction.Hint),
            recentHintsWithoutAnswer = 9,
            recentSignals = RecentTurnSignals(
                lastAction = TeachingAction.Explain,
                askedForHintCount = 1
            )
        ).normalized()
        assertEquals(TeachingAction.Explain, authoritative.recentSignals.lastAction)
        assertEquals(1, authoritative.recentSignals.askedForHintCount)
    }
}
