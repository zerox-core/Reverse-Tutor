package com.reversetutor.feature.chat

import com.reversetutor.core.model.BackgroundJobStatus

/**
 * Compose-free state/event bridge for wiring [SessionConversationContract]
 * from [BackgroundTurnPreparationResult.Queued] to the chat host.
 *
 * This file contains pure functions only — no Compose types, no Repository,
 * no DAO, no Entity, no Database, no SecretStore, no protocol DTO.
 */

/**
 * Update a queued (LOADING) [SessionConversationContract] to a safe terminal
 * state after the background job reaches a terminal [BackgroundJobStatus].
 *
 * Safety rules:
 * - Never fakes [GenerationUiContract.assistantMessageId]; sets it to null so
 *   [SessionConversationContract.inlineHintAnchor] uses [SessionAssistantHintAnchor.TimelineEnd].
 * - Never exposes raw [errorMessage]; maps to a fixed white-listed Chinese label.
 * - Preserves evaluation, action, context and events from the queued snapshot.
 * - Adds a RETRY event when generation fails (if not already present).
 */
fun SessionConversationContract.withTerminalGeneration(
    status: BackgroundJobStatus,
    errorMessage: String?
): SessionConversationContract {
    val newState = when (status) {
        BackgroundJobStatus.Completed -> GenerationState.READY
        BackgroundJobStatus.Failed ->
            if (errorMessage == "No model configured") GenerationState.NO_MODEL
            else GenerationState.ERROR
        BackgroundJobStatus.Cancelled,
        BackgroundJobStatus.Discarded -> GenerationState.IDLE
        BackgroundJobStatus.Queued,
        BackgroundJobStatus.Running -> GenerationState.LOADING
    }
    val safeError = when {
        newState == GenerationState.ERROR && errorMessage == "No model configured" -> null
        newState == GenerationState.ERROR -> "生成失败"
        else -> null
    }
    val newEvents = if (newState == GenerationState.ERROR && turnId != null) {
        val hasRetry = events.any { it.type == ConversationUiEventType.RETRY }
        if (!hasRetry) {
            events + ConversationUiEvent(
                eventId = "retry_$turnId",
                type = ConversationUiEventType.RETRY
            )
        } else {
            events
        }
    } else {
        events
    }
    return copy(
        generation = GenerationUiContract(
            state = newState,
            assistantMessageId = null,
            assistantText = null,
            safeError = safeError
        ),
        events = newEvents
    )
}
