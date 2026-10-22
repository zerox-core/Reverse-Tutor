package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGenerationPlan
import com.reversetutor.core.llm.LlmGenerationPlanner
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.OpenAiCompatibleGenerationRuntime
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.NewSessionConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真机反馈（2026-10-15）：通过「AI 创建草案」新建的会话里，AI 的内心独白
 * （`<thinking>…</thinking>`）消失了。
 *
 * 这类会话没有内置预设 id（`builtInPresetId == null`）、也没有会话模板快照，
 * 因此走普通 RAG/图谱链路（[BackgroundTurnPreparationCoordinator]）。
 * `core:llm` 只在请求带「可 normalize 的 sessionPolicy 或 guidedTurnPlan」时
 * 注入 `reverseTutorStudentPromptBlock()`（其中第 757 行就是 `<thinking>` 独白
 * 契约），所以这类会话必须仍然带着非空、可 normalize 的策略上下文出发。
 */
class UntemplatedSessionStudentContractTest {

    private val queuedInputs = mutableListOf<BackgroundGenerationInput>()

    private fun coordinator() = BackgroundTurnPreparationCoordinator(
        isSessionDeleted = { false },
        assembleContext = { _, _, _ -> ConversationContextContract.empty("space-1", "session-1") },
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

    private fun request(
        snapshot: NewSessionConfiguration?,
        userText: String = "我不太明白这里为什么要变号"
    ) = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = userText,
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList<MessageAttachment>(),
        sessionSnapshot = snapshot
    )

    /**
     * 复刻 Worker（`BackgroundGenerationRepository.generateJob`）从已落盘 job
     * 到 `core:llm` 请求的映射，观测最终发给模型的提示词。
     */
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

    private fun chatGenerationInput(input: BackgroundGenerationInput) = ChatGenerationInput(
        sessionId = input.sessionId,
        userMessageId = input.userMessageId,
        userText = input.userText,
        token = input.token,
        spaceId = input.spaceId,
        contextEvidence = input.contextEvidence,
        sessionPolicy = input.sessionPolicy,
        turnNoteBlock = input.turnNoteBlock
    )

    @Test
    fun untemplatedSessionStillCarriesANormalizableSessionPolicy() = runBlocking {
        queuedInputs.clear()

        val result = coordinator().prepareAndEnqueue(request(snapshot = null))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val policy = queuedInputs.single().sessionPolicy
        assertNotNull(
            "没有会话模板快照的普通会话也必须带上非空 sessionPolicy，否则 core:llm 的" +
                "「学生表达契约」块（含 <thinking> 内心独白约定）不会注入",
            policy
        )
        assertNotNull("sessionPolicy 必须能通过 core:llm 的 normalized() 门控", policy!!.normalized())

        // 同一份证据下，普通会话的最终提示词必须仍含独白信封契约
        assertNotNull(chatGenerationInput(queuedInputs.single()).sessionPolicy?.normalized())
    }

    @Test
    fun untemplatedSessionFinalPromptKeepsTheThinkingEnvelopeContract() = runBlocking {
        queuedInputs.clear()

        val result = coordinator().prepareAndEnqueue(request(snapshot = NewSessionConfiguration(title = "普通会话")))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val prompt = promptFor(queuedInputs.single())
        assertTrue(
            "AI 创建草案的会话（无内置预设 / 无模板快照）最终提示词必须仍含 <thinking> 独白契约，实际提示词：\n$prompt",
            prompt.contains("<thinking>")
        )
    }

    @Test
    fun untemplatedSessionKeepsTheContractEvenWhenTheTurnPlanIsNotWhitelisted() = runBlocking {
        queuedInputs.clear()

        // 即便 guidedTurnPlan 因动作不在白名单被 core:llm 拒绝，sessionPolicy 也必须兜住契约块。
        val result = coordinator().prepareAndEnqueue(request(snapshot = null))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val prompt = promptFor(queuedInputs.single())
        assertTrue(
            "guidedTurnPlan 不可用时 sessionPolicy 必须独立撑起契约块，实际提示词：\n$prompt",
            prompt.contains("<thinking>")
        )
    }

    /**
     * 独白契约在提示词最前面只说一次时，模型是否吐 `<thinking>` 信封极不稳定
     * （真机同一条提示词直连网关实测 0/6-1/6，另一轮 5/6-6/6）；把这条格式要求
     * 重申在提示词最尾部（用户话之后）后实测 6/6。因此尾部重申是产品行为的一部分，
     * 这里锁住它在最终提示词里的位置，防止以后被挪回便签块（便签块在用户话之前，
     * 实测无效：0/6）。
     */
    @Test
    fun untemplatedSessionRestatesTheThinkingEnvelopeAtThePromptTail() = runBlocking {
        queuedInputs.clear()

        val result = coordinator().prepareAndEnqueue(request(snapshot = NewSessionConfiguration(title = "普通会话")))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)

        val input = queuedInputs.single()
        val prompt = promptFor(input)

        assertTrue(
            "提示词必须以独占尾部位置的格式重申收尾（用户话之后），实际提示词：\n$prompt",
            prompt.trimEnd().endsWith(MONOLOGUE_TAIL_REQUIREMENT)
        )
        assertTrue(
            "尾部重申只能出现一次，实际提示词：\n$prompt",
            prompt.split(MONOLOGUE_TAIL_REQUIREMENT).size == 2
        )
        // 用户话本体（原文）仍完整保留在尾部重申之前
        assertTrue(
            "尾部重申不得吃掉用户原话，实际提示词：\n$prompt",
            prompt.contains(input.userText.substringBefore(MONOLOGUE_TAIL_REQUIREMENT).trim())
        )
        // 内部便签（用户话之前）不承担格式重申，避免第二处来源
        assertTrue(
            "格式重申不应混进便签块，实际便签：\n${input.turnNoteBlock}",
            input.turnNoteBlock.orEmpty().contains(MONOLOGUE_TAIL_REQUIREMENT).not()
        )
    }

    /**
     * 失败重试（feature/chat 的孤儿消息重试）会把 `job.userText` 原样送回
     * 本协调器，因此尾部重申必须幂等，且所有派生（策略、便签、检索查询）
     * 都要先剥掉它，回落到用户原话——否则重试轮的密度/聚焦信号会被
     * 一段内部格式要求污染。
     */
    @Test
    fun retryWithPersistedJobTextKeepsDerivationsOnTheRawUserText() = runBlocking {
        queuedInputs.clear()
        val coordinator = coordinator()

        val first = coordinator.prepareAndEnqueue(request(snapshot = null))
        assertTrue(first is BackgroundTurnPreparationResult.Queued)
        val firstInput = queuedInputs.single()
        assertTrue(
            "首次排队必须带上尾部重申：\n${firstInput.userText}",
            firstInput.userText.endsWith(MONOLOGUE_TAIL_REQUIREMENT)
        )

        queuedInputs.clear()
        // 复刻孤儿重试：把 job.userText（含重申）原样送回协调器
        val retry = coordinator.prepareAndEnqueue(request(snapshot = null, userText = firstInput.userText))
        assertTrue(retry is BackgroundTurnPreparationResult.Queued)
        val retried = queuedInputs.single()

        assertTrue(
            "重试轮不得二次追加尾部重申，实际：\n${retried.userText}",
            retried.userText.split(MONOLOGUE_TAIL_REQUIREMENT).size == 2
        )
        assertEquals("重试轮的策略派生必须回到用户原话", firstInput.sessionPolicy, retried.sessionPolicy)
        assertEquals("重试轮的便签派生必须回到用户原话", firstInput.turnNoteBlock, retried.turnNoteBlock)
    }
}
