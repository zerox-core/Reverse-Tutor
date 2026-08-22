package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the stable UI-event → interaction mapping used by the session assistant
 * panel. The mapping must not expose Repository callbacks, event payloads, or
 * domain objects — only the [SessionAssistantInteraction] intent.
 */
class SessionAssistantInteractionTest {

    @Test
    fun retryMapsToRetry() {
        assertEquals(
            SessionAssistantInteraction.RETRY,
            ConversationUiEventType.RETRY.toPanelInteractionForTest()
        )
    }

    @Test
    fun openContextMapsToOpenContext() {
        assertEquals(
            SessionAssistantInteraction.OPEN_CONTEXT,
            ConversationUiEventType.OPEN_CONTEXT.toPanelInteractionForTest()
        )
    }

    @Test
    fun openSourceMapsToOpenSource() {
        assertEquals(
            SessionAssistantInteraction.OPEN_SOURCE,
            ConversationUiEventType.OPEN_SOURCE.toPanelInteractionForTest()
        )
    }

    @Test
    fun showEvaluationMapsToShowEvaluation() {
        assertEquals(
            SessionAssistantInteraction.SHOW_EVALUATION,
            ConversationUiEventType.SHOW_EVALUATION.toPanelInteractionForTest()
        )
    }

    @Test
    fun dismissMapsToDismiss() {
        assertEquals(
            SessionAssistantInteraction.DISMISS,
            ConversationUiEventType.DISMISS.toPanelInteractionForTest()
        )
    }
}
