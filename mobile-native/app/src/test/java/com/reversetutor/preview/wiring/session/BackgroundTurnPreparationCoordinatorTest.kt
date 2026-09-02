package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.NewSessionConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun prepared_generation_contains_only_the_window_visible_message_ids() = runBlocking {
        queuedInputs.clear()
        val hidden = ContextMessage("root-after-child-1", "user", "root-after-child-1", 30L)
        val visibleContext = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = emptyList(),
            recentMessages = listOf(
                ContextMessage("root-before", "user", "root-before", 10L),
                ContextMessage("child-2-local", "user", "child-2-local", 60L)
            ),
            warnings = emptyList()
        )
        val topologyAwareCoordinator = BackgroundTurnPreparationCoordinator(
            isSessionDeleted = { false },
            assembleContext = { _, _ -> visibleContext },
            enqueueJob = { input, now ->
                queuedInputs.add(input)
                BackgroundGenerationJob(
                    id = "job-visible",
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

        val result = topologyAwareCoordinator.prepareAndEnqueue(request("explain"))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val visibleIds = queuedInputs.single().contextEvidence
            .filter { it.kind == "Message" }
            .mapNotNull { it.sourceMessageId }
        assertEquals(listOf("root-before", "child-2-local"), visibleIds)
        assertFalse(visibleIds.contains(hidden.messageId))
    }

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

    @Test
    fun preparation_carries_bounded_template_context_into_generation_evidence() = runBlocking {
        queuedInputs.clear()
        val result = coordinator.prepareAndEnqueue(
            request("解释单调性").copy(
                sessionSnapshot = NewSessionConfiguration(
                    learnerRole = "谨慎的追问型学生",
                    learnerProfile = "容易漏步骤，喜欢反例",
                    goal = "掌握函数单调性",
                    plan = "先诊断，再例题",
                    dialogueStrategy = "每次追问一个为什么",
                    speakingTone = "自然"
                )
            )
        )

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        val templateEvidence = queuedInputs.single().contextEvidence.filter { it.kind == "Template" }
        assertEquals(1, templateEvidence.size)
        val body = templateEvidence.single().body
        assertTrue(body.contains("谨慎的追问型学生"))
        assertTrue(body.contains("掌握函数单调性"))
        assertTrue(body.contains("每次追问一个为什么"))
    }
}
