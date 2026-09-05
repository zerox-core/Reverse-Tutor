package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 Task 2.4 red tests: a bounded guided-learning [TurnPlan] may
 * enter the generation request as optional structured context, but it must
 * never rewrite the user message, never survive with an unknown action, and
 * never carry oversized or secret-like fragments into the provider prompt.
 */
class GuidedLearningTurnPlanContextTest {

    private fun profile() = LlmProfile(
        id = "profile-1",
        spaceId = "space-1",
        name = "Qwen test",
        provider = LlmProviderKind.OpenAiCompatible,
        model = "qwen-test",
        secretRef = "secret-ref-1",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        baseUrl = "https://provider.invalid/v1",
        enabled = true
    )

    private fun plan(
        actionType: String = "diagnose",
        secondaryAction: String = "",
        learningObjective: String = "Locate the exact gap in monotonicity reasoning",
        conceptKey: String = "函数单调性",
        expectedUserMove: String = "Answer the smallest diagnostic question",
        responseFormat: String = "steps",
        hintLevel: Int = 1,
        evidenceRequirement: String = "user_answer"
    ) = LlmGuidedTurnPlan(
        actionType = actionType,
        secondaryAction = secondaryAction,
        learningObjective = learningObjective,
        conceptKey = conceptKey,
        expectedUserMove = expectedUserMove,
        responseFormat = responseFormat,
        hintLevel = hintLevel,
        evidenceRequirement = evidenceRequirement
    )

    private fun openAiContentOf(request: LlmGenerationRequest): String {
        val payload = OpenAiCompatibleGenerationRuntime().buildPayload(request)
        val message = (payload.body.getValue("messages") as List<*>).first() as Map<*, *>
        return message["content"] as String
    }

    // 1. TurnPlan enters the structured request and reaches the provider prompt
    //    while the original user message stays byte-identical.
    @Test
    fun guidedPlanEntersRequestAndPromptWithoutRewritingUserText() {
        val originalUserText = "我先把定义域写错了会怎样？"
        val planResult = LlmGenerationPlanner.plan(
            sessionId = "s-1",
            userMessageId = "u-1",
            userText = originalUserText,
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("t-1"),
            guidedTurnPlan = plan(secondaryAction = "worked_example")
        )

        val request = (planResult as LlmGenerationPlan.Ready).request
        assertEquals(originalUserText, request.userText)
        assertEquals("diagnose", request.guidedTurnPlan?.actionType)
        assertEquals("worked_example", request.guidedTurnPlan?.secondaryAction)

        val content = openAiContentOf(request)
        assertTrue(content.contains("Guided learning plan:"))
        assertTrue(content.contains("Action: diagnose"))
        assertTrue(content.contains("Secondary action: worked_example"))
        assertTrue(content.contains("The user is the teacher. You are the student AI."))
        assertTrue(content.contains("Student expression: State one precise point"))
        assertTrue(content.contains("Expected teacher move:"))
        // user message is appended unchanged at the end
        assertTrue(content.endsWith(originalUserText))
    }

    // 2. Requests without a plan keep the exact old shape (default compatibility).
    @Test
    fun absentGuidedPlanKeepsLegacyRequestBehavior() {
        val legacy = LlmGenerationPlanner.plan(
            sessionId = "s-1",
            userMessageId = "u-1",
            userText = "hello",
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("t-legacy")
        )
        val request = (legacy as LlmGenerationPlan.Ready).request
        assertNull(request.guidedTurnPlan)
        assertEquals("hello", openAiContentOf(request))
    }

    // 5. Unknown actions never enter the model context.
    @Test
    fun unknownActionPlanIsRejectedEntirely() {
        val result = LlmGenerationPlanner.plan(
            sessionId = "s-1",
            userMessageId = "u-1",
            userText = "hello",
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("t-2"),
            guidedTurnPlan = plan(actionType = "delete_every_learning_fact")
        )
        val request = (result as LlmGenerationPlan.Ready).request
        assertNull(request.guidedTurnPlan)
        assertFalse(openAiContentOf(request).contains("Guided learning plan:"))
    }

    @Test
    fun unknownSecondaryActionIsDroppedButPrimarySurvives() {
        val normalized = plan(secondaryAction = "hallucinated_move").normalized()
        assertEquals("diagnose", normalized?.actionType)
        // wire keeps secondaryAction a non-null String; an unknown value is
        // dropped to blank so the prompt block omits it entirely.
        assertTrue(normalized?.secondaryAction?.isBlank() == true)
    }

    // 4. Oversized text is capped and secret-like substrings are stripped.
    @Test
    fun oversizedAndSensitiveFieldsAreBoundedAndRedacted() {
        val noisy = plan(
            learningObjective = "目标".repeat(300),
            expectedUserMove = "go see https://evil.invalid/x with Authorization: Bearer sk-abcdef123456 now"
        )
        val result = LlmGenerationPlanner.plan(
            sessionId = "s-1",
            userMessageId = "u-1",
            userText = "hello",
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("t-3"),
            guidedTurnPlan = noisy
        )
        val carried = (result as LlmGenerationPlan.Ready).request.guidedTurnPlan!!
        assertTrue(carried.learningObjective.length <= 120)
        assertTrue(carried.expectedUserMove.length <= 160)
        val lower = carried.learningObjective.lowercase() + carried.expectedUserMove.lowercase()
        assertFalse(lower.contains("http"))
        assertFalse(lower.contains("authorization"))
        assertFalse(lower.contains("bearer"))
        assertFalse(lower.contains("sk-"))

        val content = openAiContentOf((result as LlmGenerationPlan.Ready).request)
        assertFalse(content.contains("sk-abcdef123456"))
        assertFalse(content.contains("evil.invalid"))
    }

    @Test
    fun hintLevelAndFormatWhitelistsAreEnforced() {
        val wild = plan(hintLevel = 99, responseFormat = "Executable", evidenceRequirement = "silence")
        val normalized = wild.normalized()!!
        assertEquals(3, normalized.hintLevel)
        assertEquals("plain", normalized.responseFormat)
        assertEquals("none", normalized.evidenceRequirement)
    }
}
