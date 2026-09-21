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
    fun converseAssemblesNonStreamingRequestWithContractPrompt() = runBlocking {
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

        val request = runtime.requests.single()
        assertFalse(request.streaming)
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
