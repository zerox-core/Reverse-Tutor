package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SessionPolicyOutput
import com.reversetutor.core.model.BackgroundJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SessionConversationContract.withTerminalGeneration] — the pure
 * bridge that updates a queued (LOADING) contract to a safe terminal state
 * after the background job reaches a terminal status.
 *
 * Verifies:
 * - LOADING → READY on Completed (assistantMessageId NOT faked)
 * - LOADING → ERROR on Failed (raw error never exposed; safe white-listed text)
 * - LOADING → NO_MODEL on Failed with "No model configured"
 * - LOADING → IDLE on Cancelled/Discarded
 * - Evaluation/action/context preserved from the queued snapshot
 * - RETRY event added when generation fails
 * - assistantMessageId is always null (trusted id comes from records, not faked)
 */
class SessionAssistantHostBridgeTest {

    private fun queuedContract(turnId: String = "turn-1"): SessionConversationContract {
        val policy = SessionPolicyOutput(
            evaluation = SessionEvaluationContract(
                correctness = 0.8f,
                depth = 0.6f,
                entryStatus = "has_entry"
            ),
            action = SessionActionContract(
                type = "probe",
                studentRole = "learner",
                knowledgePoint = "因式分解",
                difficulty = 0.5f,
                note = null
            ),
            processSummary = "正在探查理解",
            correctionTiming = null,
            normalizationWarnings = emptyList()
        )
        return SessionConversationFacade().mapQueued(
            sessionId = "session-1",
            turnId = turnId,
            policy = policy,
            context = ConversationContextContract.empty("space-1", "session-1"),
            messages = emptyList()
        )
    }

    @Test
    fun completed_transitions_to_ready_without_faking_assistant_id() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        assertEquals(GenerationState.READY, result.generation.state)
        assertNull("assistantMessageId must not be faked", result.generation.assistantMessageId)
        assertNull(result.generation.assistantText)
        assertNull(result.generation.safeError)
    }

    @Test
    fun failed_transitions_to_error_with_safe_message() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Failed,
            errorMessage = "Connection refused: https://api.example.com/v1/chat (Authorization: Bearer sk-xxx)"
        )
        assertEquals(GenerationState.ERROR, result.generation.state)
        assertEquals("生成失败", result.generation.safeError)
        assertFalse(
            "Raw error must not be exposed",
            result.generation.safeError!!.contains("api.example.com") ||
                result.generation.safeError!!.contains("Bearer") ||
                result.generation.safeError!!.contains("sk-")
        )
    }

    @Test
    fun failed_no_model_transitions_to_no_model() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Failed,
            errorMessage = "No model configured"
        )
        assertEquals(GenerationState.NO_MODEL, result.generation.state)
        assertNull(result.generation.safeError)
    }

    @Test
    fun cancelled_transitions_to_idle() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Cancelled,
            errorMessage = null
        )
        assertEquals(GenerationState.IDLE, result.generation.state)
    }

    @Test
    fun discarded_transitions_to_idle() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Discarded,
            errorMessage = null
        )
        assertEquals(GenerationState.IDLE, result.generation.state)
    }

    @Test
    fun terminal_contract_preserves_evaluation_and_action_from_queued_snapshot() {
        val queued = queuedContract()
        val result = queued.withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        assertEquals(queued.evaluation, result.evaluation)
        assertEquals(queued.action, result.action)
        assertEquals(queued.processSummary, result.processSummary)
        assertEquals(queued.currentKnowledgePoint, result.currentKnowledgePoint)
        assertEquals(queued.nextStep, result.nextStep)
        assertEquals(queued.context, result.context)
    }

    @Test
    fun failed_state_adds_retry_event() {
        val queued = queuedContract(turnId = "turn-retry")
        val result = queued.withTerminalGeneration(
            BackgroundJobStatus.Failed,
            errorMessage = "some error"
        )
        val hasRetry = result.events.any { it.type == ConversationUiEventType.RETRY }
        assertTrue("RETRY event must be present when generation fails", hasRetry)
    }

    @Test
    fun completed_does_not_add_retry_event() {
        val queued = queuedContract()
        val result = queued.withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        val hasRetry = result.events.any { it.type == ConversationUiEventType.RETRY }
        assertFalse("RETRY event must not be present on success", hasRetry)
    }

    @Test
    fun open_context_event_preserved_from_queued() {
        val queued = queuedContract(turnId = "turn-ctx")
        val result = queued.withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        val hasOpenContext = result.events.any { it.type == ConversationUiEventType.OPEN_CONTEXT }
        assertTrue("OPEN_CONTEXT event must be preserved", hasOpenContext)
    }

    @Test
    fun ready_contract_uses_timeline_end_anchor_when_no_assistant_id() {
        val result = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        val anchor = result.inlineHintAnchor()
        assertNotNull("Hint must be visible when contract has learning info", anchor)
        assertEquals(
            "TimelineEnd anchor when no assistantMessageId",
            SessionAssistantHintAnchor.TimelineEnd,
            anchor
        )
    }

    @Test
    fun contract_with_assistant_id_uses_after_message_anchor() {
        val terminal = queuedContract().withTerminalGeneration(
            BackgroundJobStatus.Completed,
            errorMessage = null
        )
        val withAssistantId = terminal.copy(
            generation = terminal.generation.copy(
                assistantMessageId = "msg-assistant-1",
                assistantText = "好的，让我们从基础开始"
            )
        )
        val anchor = withAssistantId.inlineHintAnchor()
        assertEquals(
            "AfterAssistantMessage anchor when assistantMessageId is set",
            SessionAssistantHintAnchor.AfterAssistantMessage("msg-assistant-1"),
            anchor
        )
    }
}
