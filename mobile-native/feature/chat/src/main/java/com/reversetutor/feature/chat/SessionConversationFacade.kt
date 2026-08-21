package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SessionTurnResult

/**
 * The single stable entry point for the chat UI to obtain a
 * [SessionConversationContract]. The Facade wraps the domain
 * [ConversationSessionCoordinator] and maps its result into a
 * visual-agnostic contract.
 *
 * The UI never touches a Repository, Coordinator, or policy directly.
 */
class SessionConversationFacade {

    /**
     * Map a [SessionTurnResult] to a [SessionConversationContract].
     * The mapping is pure and deterministic; no Repository or LLM calls.
     */
    fun mapResult(
        result: SessionTurnResult,
        sessionId: String,
        turnId: String?,
        messages: List<ConversationMessageContract> = emptyList()
    ): SessionConversationContract {
        val context = when (result) {
            is SessionTurnResult.Success -> result.context
            is SessionTurnResult.NoModel -> result.context
            is SessionTurnResult.ProviderError -> result.context
            is SessionTurnResult.StaleToken -> result.context
            is SessionTurnResult.SessionDeleted -> result.context
            is SessionTurnResult.UnsupportedInput -> result.context
            is SessionTurnResult.BlankInput -> result.context
            is SessionTurnResult.Discarded -> result.context
        }

        return when (result) {
            is SessionTurnResult.Success -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(
                    state = GenerationState.READY,
                    assistantMessageId = result.assistantMessageId,
                    assistantText = result.assistantText
                ),
                evaluation = result.evaluation,
                action = result.action,
                processSummary = result.processSummary,
                currentKnowledgePoint = result.action.knowledgePoint,
                nextStep = NextStepContract(
                    hint = result.processSummary,
                    knowledgePoint = result.action.knowledgePoint,
                    actionType = result.action.type
                ),
                context = result.context,
                events = listOf(
                    ConversationUiEvent(
                        eventId = "retry_$turnId",
                        type = ConversationUiEventType.RETRY
                    ),
                    ConversationUiEvent(
                        eventId = "open_ctx_$turnId",
                        type = ConversationUiEventType.OPEN_CONTEXT
                    )
                )
            )
            is SessionTurnResult.NoModel -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.NO_MODEL),
                evaluation = null,
                action = null,
                processSummary = result.processSummary,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = emptyList()
            )
            is SessionTurnResult.ProviderError -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(
                    state = GenerationState.ERROR,
                    safeError = result.safeError
                ),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = listOf(
                    ConversationUiEvent(
                        eventId = "retry_$turnId",
                        type = ConversationUiEventType.RETRY
                    )
                )
            )
            is SessionTurnResult.StaleToken -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.ERROR, safeError = "token expired"),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = listOf(
                    ConversationUiEvent(
                        eventId = "retry_$turnId",
                        type = ConversationUiEventType.RETRY
                    )
                )
            )
            is SessionTurnResult.SessionDeleted -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.ERROR, safeError = "session removed"),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = emptyList()
            )
            is SessionTurnResult.UnsupportedInput -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.UNSUPPORTED),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = emptyList()
            )
            is SessionTurnResult.BlankInput -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.BLANK),
                evaluation = null,
                action = null,
                processSummary = null,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = emptyList()
            )
            is SessionTurnResult.Discarded -> SessionConversationContract(
                sessionId = sessionId,
                turnId = turnId,
                messages = messages,
                generation = GenerationUiContract(state = GenerationState.IDLE),
                evaluation = null,
                action = null,
                processSummary = result.reason,
                currentKnowledgePoint = null,
                nextStep = null,
                context = result.context,
                events = emptyList()
            )
        }
    }
}
