package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract

/**
 * Immutable, visual-agnostic conversation contract for the session assistant
 * side-panel. This is the ONLY type the UI layer should consume for a single
 * conversation turn.
 *
 * Design rules:
 * - No visual types (Bubble, Dialog, Drawer, Card) — the contract is
 *   presentation-agnostic and can be rendered by any UI container.
 * - No sensitive data: the contract must never contain `sk-`, Authorization,
 *   Bearer, Provider URL, SecretStore references, DAO/Entity, or raw
 *   exception text.
 * - Stable event IDs allow retry/open-context/open-source actions without
 *   re-deriving domain state.
 */

// ---------------------------------------------------------------------------
// Message contract
// ---------------------------------------------------------------------------

data class ConversationMessageContract(
    val messageId: String,
    val role: String,
    val text: String,
    val timestampEpochMillis: Long
)

// ---------------------------------------------------------------------------
// Generation UI contract
// ---------------------------------------------------------------------------

enum class GenerationState {
    IDLE, LOADING, READY, ERROR, NO_MODEL, UNSUPPORTED, BLANK
}

data class GenerationUiContract(
    val state: GenerationState,
    val assistantMessageId: String? = null,
    val assistantText: String? = null,
    val safeError: String? = null
)

// ---------------------------------------------------------------------------
// Next step contract
// ---------------------------------------------------------------------------

data class NextStepContract(
    val hint: String,
    val knowledgePoint: String,
    val actionType: String
)

// ---------------------------------------------------------------------------
// UI event contract
// ---------------------------------------------------------------------------

enum class ConversationUiEventType {
    RETRY, OPEN_CONTEXT, OPEN_SOURCE, SHOW_EVALUATION, DISMISS
}

data class ConversationUiEvent(
    val eventId: String,
    val type: ConversationUiEventType,
    val payload: String? = null
)

// ---------------------------------------------------------------------------
// Main conversation contract
// ---------------------------------------------------------------------------

data class SessionConversationContract(
    val sessionId: String,
    val turnId: String?,
    val messages: List<ConversationMessageContract>,
    val generation: GenerationUiContract,
    val evaluation: SessionEvaluationContract?,
    val action: SessionActionContract?,
    val processSummary: String?,
    val currentKnowledgePoint: String?,
    val nextStep: NextStepContract?,
    val context: ConversationContextContract,
    val events: List<ConversationUiEvent>
) {
    companion object {
        fun empty(sessionId: String): SessionConversationContract =
            SessionConversationContract(
                sessionId = sessionId,
                turnId = null,
                messages = emptyList(),
                generation = GenerationUiContract(state = GenerationState.IDLE),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = ConversationContextContract.empty("", sessionId),
                events = emptyList()
            )
    }
}
