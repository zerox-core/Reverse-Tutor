package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTurnPreparationCoordinatorTest {

    private val queuedInputs = mutableListOf<BackgroundGenerationInput>()
    private var directGenerationCalls = 0

    private val coordinator = BackgroundTurnPreparationCoordinator(
        isSessionDeleted = { false },
        assembleContext = { _, _ -> ConversationContextContract.empty("space-1", "session-1") },
        enqueueJob = { input, now ->
            queuedInputs.add(input)
            BackgroundGenerationJob(
                id = "job-\${queuedInputs.size}",
                spaceId = input.spaceId,
                sessionId = input.sessionId,
                userMessageId = input.userMessageId,
                userText = input.userText,
                token = input.token,
                modelBindingId = null,
                status = BackgroundJobStatus.Queued,
                createdAtEpochMillis = now,
                startedAtEpochMillis = null,
                completedAtEpochMillis = null,
                errorMessage = null,
                capabilities = null,
                quoteExcerpt = null,
                imageAttachments = input.imageAttachments,
                contextEvidence = input.contextEvidence,
                sessionPolicy = input.sessionPolicy
            )
        },
        nowEpochMillis = { 1000L }
    )

    private val deletedCoordinator = BackgroundTurnPreparationCoordinator(
        isSessionDeleted = { true },
        assembleContext = { _, _ -> ConversationContextContract.empty("space-1", "session-1") },
        enqueueJob = { input, _ ->
            queuedInputs.add(input)
            error("should not enqueue")
        },
        nowEpochMillis = { 1000L }
    )

    private fun request(text: String) = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = text,
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList<MessageAttachment>(),
        sessionSnapshot = null
    )

    @Test
    fun preparation_enqueues_once_without_direct_generation() = runBlocking {
        val result = coordinator.prepareAndEnqueue(request("\u89e3\u91ca\u4e00\u4e0b"))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(1, queuedInputs.size)
        assertEquals(0, directGenerationCalls)
    }

    @Test
    fun blank_or_deleted_never_enqueues() = runBlocking {
        assertEquals(
            BackgroundTurnPreparationResult.BlankInput,
            coordinator.prepareAndEnqueue(request(" "))
        )
        assertEquals(
            BackgroundTurnPreparationResult.SessionUnavailable,
            deletedCoordinator.prepareAndEnqueue(request("\u95ee\u9898"))
        )
        assertTrue(queuedInputs.isEmpty())
    }
}
