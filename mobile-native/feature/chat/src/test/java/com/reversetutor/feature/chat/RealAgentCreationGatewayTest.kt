package com.reversetutor.feature.chat

import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R-B 生产网关测试：RecordingRuntime 只记录请求、不发任何真实网络调用。
 * 覆盖：非流式请求装配、提示词含契约与本轮输入、契约解析、无模型 / 生成失败降级。
 */
class RealAgentCreationGatewayTest {

    private class RecordingRuntime(
        private val result: LlmGenerationResult = LlmGenerationResult.Success(VALID_JSON)
    ) : LlmGenerationRuntime {
        val requests = mutableListOf<LlmGenerationRequest>()

        override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
            requests += request
            return result
        }
    }

    private fun profile(enabled: Boolean = true, model: String = "test-model") = LlmProfile(
        id = "p1",
        spaceId = "space",
        name = "测试通道",
        provider = LlmProviderKind.OpenAiCompatible,
        model = model,
        secretRef = "secret-ref",
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        baseUrl = "https://example.invalid",
        enabled = enabled
    )

    private fun gateway(
        runtime: LlmGenerationRuntime,
        activeProfile: suspend () -> LlmProfile? = { profile() }
    ) = RealAgentCreationGateway(runtime = runtime, activeProfile = activeProfile)

    @Test
    fun converseAssemblesStreamingRequestWithContractPrompt() = runBlocking {
        val runtime = RecordingRuntime()
        val gw = gateway(runtime)
        val history = listOf(
            AgentCreationHistoryTurn(isUser = false, text = "想学什么？"),
            AgentCreationHistoryTurn(isUser = true, text = "我想把初中物理浮力讲明白")
        )

        val result = gw.converse(
            history = history,
            userText = "我想把初中物理浮力讲明白",
            currentDraft = NewSessionConfiguration(),
            docAnalysis = null,
            strategy = AgentCreationTurnStrategy(
                deterministicUnderstanding = 0,
                rounds = 0,
                targetFollowUpField = "学习目标"
            )
        )

        // R93：创建链路全量改流式（2026-09-26 用户拍板），请求必须带 streaming=true
        val request = runtime.requests.single()
        assertTrue(request.streaming)
        assertEquals("agent-creation", request.sessionId)
        assertEquals("test-model", request.model)
        val prompt = request.userText.orEmpty()
        // 契约与本轮输入都进了提示词；history 尾条与本轮输入重复时不重复装配
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("我想把初中物理浮力讲明白"))
        assertTrue(prompt.contains("学习目标"))
        assertEquals(1, Regex("我想把初中物理浮力讲明白").findAll(prompt).count())
        // 契约解析
        assertEquals(55, result.understanding)
        assertEquals("目标是什么？", result.followUpQuestion)
        assertEquals("浮力 · 讲学练", result.draft?.title)
    }

    @Test
    fun converseThrowsNoModelWhenProfileMissingOrDisabled() = runBlocking {
        val runtime = RecordingRuntime()
        val noProfile = gateway(runtime) { null }
        val disabled = gateway(runtime) { profile(enabled = false) }
        val blankModel = gateway(runtime) { profile(model = "  ") }

        for (gw in listOf(noProfile, disabled, blankModel)) {
            try {
                gw.converse(emptyList(), "你好", NewSessionConfiguration(), null, AgentCreationTurnStrategy())
                fail("expected AgentCreationNoModelException")
            } catch (_: AgentCreationNoModelException) {
            }
        }
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun converseWrapsFailureAsGenerationException() = runBlocking {
        val runtime = RecordingRuntime(LlmGenerationResult.Failure("provider down", retryable = true))
        val gw = gateway(runtime)
        try {
            gw.converse(emptyList(), "你好", NewSessionConfiguration(), null, AgentCreationTurnStrategy())
            fail("expected AgentCreationGenerationException")
        } catch (e: AgentCreationGenerationException) {
            assertEquals("provider down", e.message)
        }
    }

    @Test
    fun converseWrapsInvalidJsonAsGenerationException() = runBlocking {
        val runtime = RecordingRuntime(LlmGenerationResult.Success("这不是 JSON"))
        val gw = gateway(runtime)
        try {
            gw.converse(emptyList(), "你好", NewSessionConfiguration(), null, AgentCreationTurnStrategy())
            fail("expected AgentCreationGenerationException")
        } catch (_: AgentCreationGenerationException) {
        }
    }

    @Test
    fun converseParsesStreamedVisibleText() = runBlocking {
        val runtime = RecordingRuntime(LlmGenerationResult.Streamed(listOf(VALID_JSON.take(20), VALID_JSON.drop(20))))
        val gw = gateway(runtime)
        val result = gw.converse(emptyList(), "你好", NewSessionConfiguration(), null, AgentCreationTurnStrategy())
        assertEquals(55, result.understanding)
    }

    /** R93：把契约 JSON 切成小段模拟 SSE 逐段到达，回放进 onStreamChunk。 */
    private class ChunkedRuntime(private val chunks: List<String>) : LlmGenerationRuntime {
        override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
            chunks.forEach { chunk -> request.onStreamChunk?.invoke(chunk) }
            return LlmGenerationResult.Streamed(chunks)
        }
    }

    // R93：流式期间口语字段快照按序回调——首帧是 followUp 前缀（VALID_JSON 里它先出现），
    // 快照全量覆盖、单调生长，末帧 = followUp 全量 + 换行 + note 全量。
    @Test
    fun converseStreamingEmitsPartialSpokenSnapshots() = runBlocking {
        val runtime = ChunkedRuntime(VALID_JSON.chunked(7))
        val gw = gateway(runtime)
        val partials = mutableListOf<String>()

        val result = gw.converseStreaming(
            history = emptyList(),
            userText = "你好",
            currentDraft = NewSessionConfiguration(),
            docAnalysis = null,
            strategy = AgentCreationTurnStrategy(),
            onPartialSpoken = { partials += it }
        )

        assertEquals(55, result.understanding)
        assertEquals("目标是什么？", result.followUpQuestion)
        assertTrue(partials.isNotEmpty())
        assertTrue("目标是什么？".startsWith(partials.first()))
        assertTrue(partials.zipWithNext().all { (prev, next) -> next.length >= prev.length })
        assertEquals("目标是什么？\n记下了。", partials.last())
    }

    @Test
    fun analyzeDocumentReturnsScriptedPlaceholder() = runBlocking {
        val gw = gateway(RecordingRuntime())
        val analysis = gw.analyzeDocument("浮力讲义.pdf")
        assertEquals("浮力讲义", analysis.materialTitle)
        assertTrue(analysis.knowledgePoints.isNotEmpty())
        assertNotNull(analysis.suggestedPath.firstOrNull())
    }

    private companion object {
        val VALID_JSON = """
            {
              "understanding": 55,
              "followUpQuestion": "目标是什么？",
              "assistantNote": "记下了。",
              "requestDocument": false,
              "draft": { "title": "浮力 · 讲学练" }
            }
        """.trimIndent()
    }
}
