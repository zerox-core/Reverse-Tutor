package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R88：LlmGuidedTurnPlan 新增路径字段的归一与 guided plan 提示块的
 * "Path move + Transition expression" 渲染。
 */
class ChapterTransitionPromptTest {

    private fun requestWith(plan: LlmGuidedTurnPlan) = LlmGenerationRequest(
        sessionId = "session-1",
        userMessageId = "user-1",
        userText = "对的",
        profileId = "profile-1",
        provider = LlmProviderKind.AnthropicCompatible,
        model = "claude-3-5-haiku-latest",
        baseUrl = null,
        capabilities = LlmCapabilities(),
        token = LlmGenerationToken("token-1"),
        guidedTurnPlan = plan
    )

    @Test
    fun normalizedAcceptsUppercaseMoveAndTrimsLabel() {
        val normalized = LlmGuidedTurnPlan(
            actionType = "practice",
            pathMove = "ADVANCE",
            pathLabel = "  电学  ",
            pathPosition = 1,
            pathSize = 5
        ).normalized()
        assertNotNull(normalized)
        assertEquals("advance", normalized!!.pathMove)
        assertEquals("电学", normalized.pathLabel)
        assertEquals(1, normalized.pathPosition)
        assertEquals(5, normalized.pathSize)
    }

    @Test
    fun normalizedDropsUnknownMoveToBlank() {
        val normalized = LlmGuidedTurnPlan(
            actionType = "practice",
            pathMove = "teleport"
        ).normalized()
        assertEquals("", normalized!!.pathMove)
    }

    @Test
    fun promptBlockRendersPathMoveAndTransitionDirective() {
        val block = requestWith(
            LlmGuidedTurnPlan(
                actionType = "practice",
                learningObjective = "电学入门",
                pathMove = "advance",
                pathLabel = "电学",
                pathPosition = 1,
                pathSize = 5
            )
        ).guidedLearningPlanPromptBlock()!!
        assertTrue(block.contains("Path move: advance → 电学 (2/5)"))
        assertTrue(block.contains("Transition expression: "))
    }

    @Test
    fun promptBlockWithoutPathMoveKeepsLegacyShape() {
        val block = requestWith(
            LlmGuidedTurnPlan(actionType = "practice", learningObjective = "电学入门")
        ).guidedLearningPlanPromptBlock()!!
        assertTrue(!block.contains("Path move"))
        assertTrue(!block.contains("Transition expression"))
    }

    @Test
    fun transitionDirectiveKeepsStudentVoicePerMove() {
        assertTrue(LlmStudentExpressionPolicy.transitionDirectiveFor("advance").isNotBlank())
        assertTrue(LlmStudentExpressionPolicy.transitionDirectiveFor("regress").isNotBlank())
        assertTrue(LlmStudentExpressionPolicy.transitionDirectiveFor("completed").isNotBlank())
        assertEquals("", LlmStudentExpressionPolicy.transitionDirectiveFor("stay"))
    }
}