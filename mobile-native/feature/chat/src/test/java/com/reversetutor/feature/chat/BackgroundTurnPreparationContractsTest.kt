package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageAttachment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract tests for [BackgroundTurnPreparationPort] and [BackgroundTurnPreparationResult].
 * Verifies that the Unavailable implementation never enqueues and returns the
 * correct result type.
 */
class BackgroundTurnPreparationContractsTest {

    private fun request() = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = "hello",
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList<MessageAttachment>(),
        sessionSnapshot = null
    )

    @Test
    fun unavailable_never_enqueues() = runBlocking {
        assertEquals(
            BackgroundTurnPreparationResult.Unavailable,
            BackgroundTurnPreparationPort.Unavailable.prepareAndEnqueue(request())
        )
    }
}
