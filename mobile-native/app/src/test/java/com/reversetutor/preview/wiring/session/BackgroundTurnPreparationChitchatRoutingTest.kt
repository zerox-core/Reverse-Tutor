package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1g 闲聊兼容（意图分流）验收：闲聊类输入不进重装配管线。
 *
 * 分流规则：确定性分类器 GuidedLearningIntentClassifier 在装配前先跑；
 * OffTopic 且未带图片附件 => 轻装配（只读最近消息），其余意图（含
 * GoalChange、问题/作答/复习类）一律全装配；轻量入口未接线时 OffTopic
 * 回落全装配（旧构造行为不变）。
 */
class BackgroundTurnPreparationChitchatRoutingTest {

    private var heavyAssemblyCalls = 0
    private var lightweightAssemblyCalls = 0

    private val queuedInputs = mutableListOf<BackgroundGenerationInput>()

    private fun routingCoordinator(
        wireLightweight: Boolean = true
    ) = BackgroundTurnPreparationCoordinator(
        isSessionDeleted = { false },
        assembleContext = { _, _, _ ->
            heavyAssemblyCalls++
            ConversationContextContract.empty("space-1", "session-1")
        },
        assembleLightweightContext = if (wireLightweight) {
            { _, _ ->
                lightweightAssemblyCalls++
                ConversationContextContract(
                    spaceId = "space-1",
                    sessionId = "session-1",
                    prerequisiteGaps = emptyList(),
                    relatedMemory = emptyList(),
                    sourceEvidence = emptyList(),
                    historicalErrors = emptyList(),
                    pendingReviewKnowledgePoints = emptyList(),
                    recentMessages = (10 downTo 1).map { i ->
                        ContextMessage("msg-light-$i", "user", "闲聊上下文 $i", i.toLong())
                    },
                    warnings = emptyList()
                )
            }
        } else {
            null
        },
        enqueueJob = { input, now ->
            queuedInputs.add(input)
            BackgroundGenerationJob(
                id = "job-${queuedInputs.size}",
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
    fun offtopic_turn_routes_to_lightweight_assembly_and_skips_heavy_ports() = runBlocking {
        val coordinator = routingCoordinator()

        val result = coordinator.prepareAndEnqueue(request("讲个笑话"))

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals("闲聊必须走轻装配", 1, lightweightAssemblyCalls)
        assertEquals("闲聊不得进重装配管线", 0, heavyAssemblyCalls)

        val evidence = queuedInputs.single().contextEvidence
        assertTrue(
            "轻量回合只应有 Message 类证据，实际 kinds=${evidence.map { it.kind }}",
            evidence.all { it.kind == "Message" }
        )
        assertEquals("轻量证据只带最近 2 条消息", 2, evidence.size)
        assertEquals(
            listOf("msg-light-10", "msg-light-9"),
            evidence.map { it.sourceMessageId }
        )
    }

    @Test
    fun learning_question_keeps_heavy_assembly() = runBlocking {
        val coordinator = routingCoordinator()

        val result = coordinator.prepareAndEnqueue(request("什么是二分查找？"))

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(1, heavyAssemblyCalls)
        assertEquals(0, lightweightAssemblyCalls)
    }

    @Test
    fun goal_change_keeps_heavy_assembly() = runBlocking {
        val coordinator = routingCoordinator()

        val result = coordinator.prepareAndEnqueue(request("我不学这个了，改学化学"))

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(1, heavyAssemblyCalls)
        assertEquals(0, lightweightAssemblyCalls)
    }

    @Test
    fun offtopic_with_image_attachment_keeps_heavy_assembly() = runBlocking {
        val coordinator = routingCoordinator()

        val result = coordinator.prepareAndEnqueue(
            request("讲个笑话").copy(
                imageAttachments = listOf(
                    MessageAttachment(
                        id = "attachment-1",
                        spaceId = "space-1",
                        messageId = "msg-1",
                        name = "photo.jpg",
                        mimeType = "image/jpeg",
                        uri = "file:///data/local/tmp/photo.jpg"
                    )
                )
            )
        )

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals("带图片附件的闲聊回合一律重装配", 1, heavyAssemblyCalls)
        assertEquals(0, lightweightAssemblyCalls)
    }

    @Test
    fun offtopic_falls_back_to_heavy_assembly_when_lightweight_not_wired() = runBlocking {
        val coordinator = routingCoordinator(wireLightweight = false)

        val result = coordinator.prepareAndEnqueue(request("讲个笑话"))

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals("未接线时 OffTopic 回落全装配", 1, heavyAssemblyCalls)
        assertEquals(0, lightweightAssemblyCalls)
    }
}
