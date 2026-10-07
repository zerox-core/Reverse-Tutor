package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 assistant-turn envelope tests.
 *
 * Package C (task 4): immutable window/topology context + bounded turn plan +
 * safe structured outcome. A malformed envelope must yield null and fall back to
 * the existing plain-reply path; the outcome never carries raw transcript or
 * Provider details.
 */
class AssistantTurnEnvelopeTest {

    private fun profile() = LlmProfile(
        id = "p1",
        spaceId = "space-1",
        name = "Work",
        provider = LlmProviderKind.OpenAiCompatible,
        model = "gpt-4o-mini",
        secretRef = "secret-1",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        baseUrl = "https://api.example.test/v1",
        enabled = true
    )

    private fun envelope() = LlmAssistantTurnEnvelope(
        window = LlmWindowContext(windowId = "w1", rootId = "root-1", windowKind = "TASK_ROOT", forkRevision = 7L),
        turnPlan = LlmTurnPlan(
            intent = "check-in",
            actionType = "obs",
            studentRole = "companion",
            knowledgePoint = "goal",
            difficulty = 0.4f
        ),
        initiativeSource = "heartbeat"
    )

    @Test
    fun envelope_normalizes_and_bounds() {
        val normalized = envelope().normalized()!!
        assertEquals("w1", normalized.window.windowId)
        assertEquals(7L, normalized.window.forkRevision)
        assertEquals("check-in", normalized.turnPlan!!.intent)
        assertEquals(0.4f, normalized.turnPlan!!.difficulty)
        assertEquals("heartbeat", normalized.initiativeSource)
    }

    @Test
    fun malformed_envelope_normalizes_to_null() {
        val malformed = LlmAssistantTurnEnvelope(
            window = LlmWindowContext(windowId = "  ", rootId = ""),
            turnPlan = envelope().turnPlan
        )
        assertNull(malformed.normalized())
    }

    @Test
    fun turn_plan_normalizes_wire_values() {
        val plan = LlmTurnPlan(
            intent = "  advance ",
            actionType = " PROBE ",
            studentRole = " PROBING_STUDENT ",
            knowledgePoint = " factoring ",
            difficulty = 3.0f,
            toneConstraints = listOf("  be warm ", "", "no lectures")
        ).normalized()!!
        assertEquals("advance", plan.intent)
        assertEquals("probe", plan.actionType)
        assertEquals("probing_student", plan.studentRole)
        assertEquals(1.0f, plan.difficulty)
        assertEquals(listOf("be warm", "no lectures"), plan.toneConstraints)
    }

    @Test
    fun structured_outcome_is_empty_and_bounded() {
        val empty = StructuredTurnOutcome.EMPTY.normalized()
        assertEquals("none", empty.evidenceType)
        assertEquals(0f, empty.correctness)
        // No raw-text/provider fields are accepted by construction; the outcome is bounded.
        assertTrue(empty.processSummary.isBlank())
        val clamped = StructuredTurnOutcome(
            correctness = 1.7f,
            depth = -0.3f,
            evidenceType = "transcript",
            processSummary = "raw " + "x".repeat(500)
        ).normalized()
        assertEquals(1.0f, clamped.correctness)
        assertEquals(0f, clamped.depth)
        assertEquals(320, clamped.processSummary.length)
    }

    @Test
    fun planner_threads_normalized_envelope() {
        val plan = LlmGenerationPlanner.plan(
            sessionId = "s1",
            userMessageId = "u1",
            userText = "hi",
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("tok"),
            assistantTurnEnvelope = envelope()
        ) as LlmGenerationPlan.Ready
        assertNotNull(plan.request.assistantTurnEnvelope)
        assertEquals("w1", plan.request.assistantTurnEnvelope!!.window.windowId)
    }

    @Test
    fun planner_with_null_envelope_runs_existing_behavior() {
        val plan = LlmGenerationPlanner.plan(
            sessionId = "s1",
            userMessageId = "u1",
            userText = "hi",
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("tok")
        ) as LlmGenerationPlan.Ready
        assertNull(plan.request.assistantTurnEnvelope)
    }

    @Test
    fun blank_normal_turn_cannot_use_envelope_to_bypass_prompt_guard() {
        val plan = LlmGenerationPlanner.plan(
            sessionId = "s1",
            userMessageId = null,
            userText = null,
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("tok"),
            assistantTurnEnvelope = envelope()
        )
        assertEquals(
            LlmGenerationBlockReason.BlankPrompt,
            (plan as LlmGenerationPlan.Blocked).reason
        )
    }

    @Test
    fun initiative_plan_drives_a_non_empty_provider_prompt_without_user_text() {
        val plan = LlmGenerationPlanner.plan(
            sessionId = "s1",
            userMessageId = null,
            userText = null,
            profile = profile(),
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("tok"),
            assistantTurnEnvelope = envelope(),
            allowPlanDrivenOpening = true
        ) as LlmGenerationPlan.Ready

        assertNull(plan.request.userText)
        val payload = OpenAiCompatibleGenerationRuntime().buildPayload(plan.request)
        val content = (payload.body["messages"] as List<*>).first()
            .let { it as Map<*, *> }["content"] as String
        assertTrue(content.contains("Initiative plan:"))
        assertTrue(content.contains("check-in"))
    }
}
