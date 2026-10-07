package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolves the inline hint anchor purely from [SessionConversationContract]
 * already-published fields. No repository, ViewModel, or derivation is touched.
 *
 * The fixture derives generation state from whether a [safeError] is supplied:
 * READY when none, ERROR when one is present — matching the contract invariant
 * that a non-blank safe error only appears alongside an ERROR generation.
 */
class SessionAssistantReplyHintTest {

    @Test
    fun assistantReplyAnchorsAfterAssistantMessage() {
        val contract = contract(assistantMessageId = "assistant-42")
        assertEquals(
            SessionAssistantHintAnchor.AfterAssistantMessage("assistant-42"),
            contract.inlineHintAnchor()
        )
    }

    @Test
    fun failureWithoutReplyAnchorsAtTimelineEnd() {
        val contract = contract(
            assistantMessageId = null,
            safeError = "暂时无法生成回复"
        )
        assertEquals(
            SessionAssistantHintAnchor.TimelineEnd,
            contract.inlineHintAnchor()
        )
    }

    @Test
    fun emptyContractHasNoInlineHint() {
        val contract = SessionConversationContract.empty("s-empty")
        assertNull(contract.inlineHintAnchor())
        assertFalse(contract.hasInlineLearningHint())
    }

    @Test
    fun actionMakesHintEligible() {
        val contract = contract(
            action = SessionActionContract(type = "probe", knowledgePoint = "函数")
        )
        assertTrue(contract.hasInlineLearningHint())
    }

    // --- Helper ---

    private fun contract(
        sessionId: String = "session-1",
        turnId: String? = "turn-1",
        assistantMessageId: String? = null,
        assistantText: String? = null,
        safeError: String? = null,
        action: SessionActionContract? = null,
        context: ConversationContextContract = ConversationContextContract.empty("space-1", sessionId)
    ): SessionConversationContract {
        val state = if (safeError != null) GenerationState.ERROR else GenerationState.READY
        return SessionConversationContract(
            sessionId = sessionId,
            turnId = turnId,
            messages = emptyList(),
            generation = GenerationUiContract(
                state = state,
                assistantMessageId = assistantMessageId,
                assistantText = assistantText,
                safeError = safeError
            ),
            evaluation = null,
            action = action,
            processSummary = null,
            currentKnowledgePoint = action?.knowledgePoint,
            nextStep = action?.let {
                NextStepContract(hint = "hint", knowledgePoint = it.knowledgePoint, actionType = it.type)
            },
            context = context,
            events = emptyList()
        )
    }
}
