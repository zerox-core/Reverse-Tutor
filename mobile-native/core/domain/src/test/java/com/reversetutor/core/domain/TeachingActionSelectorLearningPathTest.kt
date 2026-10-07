package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R86：选择器接入有序学习路径——路径步态决定 conceptKey，回退 / 完成有硬规则。 */
class TeachingActionSelectorLearningPathTest {

    private fun path(vararg labels: String): LearningPath =
        LearningPathPolicy.fromLabels(labels.toList())

    private fun input(
        intent: UserIntent,
        conceptKey: String,
        path: LearningPath,
        states: Map<String, ConceptLearningState> = emptyMap()
    ) = GuidedLearningTurnInput(
        sessionId = "s-1",
        windowId = "w-1",
        spaceId = "sp-1",
        conceptKey = conceptKey,
        userIntentHint = intent,
        conceptStates = states,
        learningPath = path
    )

    @Test
    fun pathStartRedirectsConceptKeyToPathHead() {
        val plan = TeachingActionSelector.select(
            input(UserIntent.AnswerAttempt, "把物理讲明白", path("密度", "浮力", "压强"))
        )
        assertEquals("密度", plan.conceptKey)
        assertEquals(PathMove.Start, plan.pathMove)
        assertEquals(0, plan.pathPosition)
        assertEquals(3, plan.pathSize)
    }

    @Test
    fun masteredCurrentAdvancesConceptKeyAlongPath() {
        val plan = TeachingActionSelector.select(
            input(
                UserIntent.AnswerAttempt, "密度", path("密度", "浮力", "压强"),
                mapOf("密度" to ConceptLearningState(status = ConceptStatus.Mastered, attemptCount = 3))
            )
        )
        assertEquals("浮力", plan.conceptKey)
        assertEquals(PathMove.Advance, plan.pathMove)
        assertEquals(1, plan.pathPosition)
        assertEquals(3, plan.pathSize)
    }

    @Test
    fun stayKeepsConceptKeyAndCarriesPosition() {
        val plan = TeachingActionSelector.select(
            input(
                UserIntent.AskQuestion, "浮力", path("密度", "浮力", "压强"),
                mapOf(
                    "密度" to ConceptLearningState(status = ConceptStatus.Mastered, attemptCount = 3),
                    "浮力" to ConceptLearningState(status = ConceptStatus.Exploring, attemptCount = 1)
                )
            )
        )
        assertEquals("浮力", plan.conceptKey)
        assertEquals(PathMove.Stay, plan.pathMove)
        assertEquals(1, plan.pathPosition)
        assertEquals(3, plan.pathSize)
    }

    @Test
    fun regressForcesDiagnoseOnFoundationNode() {
        val plan = TeachingActionSelector.select(
            input(
                UserIntent.AskExample, "压强", path("密度", "浮力", "压强"),
                mapOf(
                    "密度" to ConceptLearningState(status = ConceptStatus.Mastered, attemptCount = 3),
                    "浮力" to ConceptLearningState(status = ConceptStatus.Exploring, attemptCount = 2),
                    "压强" to ConceptLearningState(
                        status = ConceptStatus.Fragile, attemptCount = 4, failureStreak = 2
                    )
                )
            )
        )
        assertEquals(TeachingAction.Diagnose, plan.actionType)
        assertEquals("浮力", plan.conceptKey)
        assertEquals(PathMove.Regress, plan.pathMove)
        assertEquals(1, plan.pathPosition)
        assertEquals(3, plan.pathSize)
    }

    @Test
    fun completedPathForcesReflect() {
        val plan = TeachingActionSelector.select(
            input(
                UserIntent.AnswerAttempt, "浮力", path("密度", "浮力"),
                mapOf(
                    "密度" to ConceptLearningState(status = ConceptStatus.Stable, attemptCount = 2),
                    "浮力" to ConceptLearningState(status = ConceptStatus.Mastered, attemptCount = 4)
                )
            )
        )
        assertEquals(TeachingAction.Reflect, plan.actionType)
        assertEquals(PathMove.Completed, plan.pathMove)
        assertEquals(-1, plan.pathPosition)
        assertEquals(2, plan.pathSize)
    }

    @Test
    fun emptyPathKeepsLegacyBehaviorWithoutPathFields() {
        val plan = TeachingActionSelector.select(
            GuidedLearningTurnInput(
                sessionId = "s-1",
                conceptKey = "函数单调性",
                userIntentHint = UserIntent.AskExample
            )
        )
        assertEquals("函数单调性", plan.conceptKey)
        assertEquals(TeachingAction.Explain, plan.actionType)
        assertNull(plan.pathMove)
        assertEquals(-1, plan.pathPosition)
        assertEquals(0, plan.pathSize)
    }
}
