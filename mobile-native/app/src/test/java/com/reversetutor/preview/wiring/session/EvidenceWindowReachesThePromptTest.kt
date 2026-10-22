package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGenerationPlan
import com.reversetutor.core.llm.LlmGenerationPlanner
import com.reversetutor.core.llm.OpenAiCompatibleGenerationRuntime
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.NewSessionConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历史窗口**真正到达模型**的端到端验收（2026-10-18）。
 *
 * 起因：`SessionPolicyInputMapper` 的 8 条消息窗口只保证「装配层出口」，下游 `core/llm`
 * 还有一道 `take(MaxContextEvidence)` 会把证据重新钳短——模板证据占 1 条、摘要/缺口/
 * 复习各占 1 条之后，真实对话只剩 2 条，模型接不住「再写一个」指代什么。只断言
 * mapper 出口的用例给出的是假保证，所以这里复刻 Worker 的真实映射
 * （`BackgroundTurnPreparationCoordinator` → `LlmGenerationPlanner.plan` →
 * OpenAI 兼容 payload），断言**最终提示词文本**里到底有什么。
 */
class EvidenceWindowReachesThePromptTest {

    private val queuedInputs = mutableListOf<BackgroundGenerationInput>()

    private fun coordinator(context: ConversationContextContract) = BackgroundTurnPreparationCoordinator(
        isSessionDeleted = { false },
        assembleContext = { _, _, _ -> context },
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

    private fun request() = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = "我不太明白这里为什么要变号",
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList<MessageAttachment>(),
        sessionSnapshot = NewSessionConfiguration(title = "普通会话")
    )

    /** 复刻 Worker 从已落盘 job 到 `core:llm` 请求的映射，观测最终提示词。 */
    private fun promptFor(input: BackgroundGenerationInput): String {
        val plan = LlmGenerationPlanner.plan(
            sessionId = input.sessionId,
            userMessageId = input.userMessageId,
            userText = input.userText,
            profile = LlmProfile(
                id = "profile-1",
                spaceId = "space-1",
                name = "preview",
                provider = LlmProviderKind.OpenAiCompatible,
                model = "gemini-3.8-flash",
                secretRef = "secret-1",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                baseUrl = "https://api.example.test/v1",
                enabled = true
            ),
            capabilities = LlmCapabilities(),
            token = input.token,
            contextEvidence = input.contextEvidence,
            sessionPolicy = input.sessionPolicy,
            guidedTurnPlan = null,
            turnNoteBlock = input.turnNoteBlock
        )
        val ready = plan as LlmGenerationPlan.Ready
        val payload = OpenAiCompatibleGenerationRuntime().buildPayload(ready.request)
        val messages = payload.body["messages"] as List<*>
        return (messages.single() as Map<*, *>)["content"] as String
    }

    private fun richContext(messageCount: Int) = ConversationContextContract(
        spaceId = "space-1",
        sessionId = "session-1",
        prerequisiteGaps = listOf("先掌握等式的性质"),
        relatedMemory = emptyList(),
        sourceEvidence = emptyList(),
        historicalErrors = emptyList(),
        pendingReviewKnowledgePoints = listOf("系数化为 1"),
        // 契约按时间倒序给出（最新在前），与生产装配一致
        recentMessages = (1..messageCount).map { index ->
            ContextMessage(
                messageId = "message-$index",
                role = if (index % 2 == 0) "assistant" else "user",
                text = "第 $index 条对话正文",
                timestampEpochMillis = index.toLong()
            )
        }.sortedByDescending { it.timestampEpochMillis },
        warnings = emptyList(),
        earlyHistoryDigest = "早期对话摘要：解一元一次方程"
    )

    @Test
    fun eightMessageWindowAndTheAggregatesAllReachTheFinalPrompt() = runBlocking {
        queuedInputs.clear()
        val result = coordinator(richContext(messageCount = 8)).prepareAndEnqueue(request())
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val prompt = promptFor(queuedInputs.single())

        // 模板 1 + 摘要 1 + 缺口 1 + 复习 1 + 消息 8 = 12 条，正好用满上限：全部必须出现
        (1..8).forEach { index ->
            assertTrue(
                "第 $index 条对话必须进最终提示词（历史窗口不得被下游上限吞掉）",
                prompt.contains("第 $index 条对话正文")
            )
        }
        assertTrue("早期摘要证据不得被消息挤掉", prompt.contains("早期对话摘要"))
        assertTrue("前置知识缺口证据不得被挤掉", prompt.contains("先掌握等式的性质"))
        assertTrue("复习证据不得被挤掉", prompt.contains("系数化为 1"))
    }

    @Test
    fun messageWindowIsBoundedSoThePromptCannotGrowWithoutLimit() = runBlocking {
        queuedInputs.clear()
        val result = coordinator(richContext(messageCount = 20)).prepareAndEnqueue(request())
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val prompt = promptFor(queuedInputs.single())

        // 倒序契约下取最近 8 条 = 第 20..13 条；第 12 条及更早必须被截断（窗口有界）
        (13..20).forEach { index ->
            assertTrue("最近窗口内的第 $index 条必须进提示词", prompt.contains("第 $index 条对话正文"))
        }
        (1..12).forEach { index ->
            assertFalse(
                "超出窗口的第 $index 条不得进提示词（否则提示词无界增长）",
                prompt.contains("第 $index 条对话正文")
            )
        }
    }
}
