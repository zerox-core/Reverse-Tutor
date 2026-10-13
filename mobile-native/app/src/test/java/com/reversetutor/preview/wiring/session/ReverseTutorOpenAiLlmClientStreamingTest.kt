package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.ProviderHttpRequest
import com.reversetutor.core.llm.ProviderHttpResult
import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真流式（2026-10-13 用户拍板「看得到思考过程」）：ReverseTutorOpenAiLlmClient
 * chatStreaming 的 SSE 解析 / 增量回调 / tool_calls 分片聚合 / 失败降级测试。
 */
class ReverseTutorOpenAiLlmClientStreamingTest {

    /** 按脚本逐行回放 SSE 的假 transport，记录全部请求。 */
    private class ScriptedSseTransport(
        private val streamLines: List<String>? = null,
        private val oneShotBody: String = """{"choices":[{"message":{"content":"一次性回复","role":"assistant"},"finish_reason":"stop"}],"model":"qwen-test"}""",
        private val streamResult: ProviderHttpResult? = null
    ) : ProviderHttpTransport {
        val requests = mutableListOf<ProviderHttpRequest>()

        override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult {
            requests.add(request)
            return ProviderHttpResult.Response(statusCode = 200, body = oneShotBody)
        }

        override suspend fun executeStreaming(
            request: ProviderHttpRequest,
            onLine: (String) -> Unit,
            shouldAbort: () -> Boolean
        ): ProviderHttpResult {
            requests.add(request)
            streamLines?.forEach { onLine(it) }
            return streamResult ?: ProviderHttpResult.Response(statusCode = 200, body = "stream-end")
        }
    }

    private fun buildClient(transport: ProviderHttpTransport): ReverseTutorOpenAiLlmClient =
        ReverseTutorOpenAiLlmClient(
            transport = transport,
            profileProvider = {
                LlmProfile(
                    id = "p1",
                    spaceId = "space-1",
                    name = "测试渠道",
                    provider = LlmProviderKind.OpenAiCompatible,
                    model = "qwen-test",
                    secretRef = "secret-ref",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                    baseUrl = "https://hub.test/v1"
                )
            },
            apiKeyResolver = { "key-123" }
        )

    @Test
    fun chatStreamingEmitsRawDeltasInOrderAndAggregatesResponse() = runBlocking {
        val sse = listOf(
            """data: {"choices":[{"delta":{"reasoning_content":"我先"}}]}""",
            """data: {"choices":[{"delta":{"reasoning_content":"想想"}}]}""",
            """data: {"choices":[{"delta":{"content":"<thinking>定稿"}}]}""",
            """data: {"choices":[{"delta":{"content":"内心</thinking>"}}]}""",
            """data: {"choices":[{"delta":{"content":"\n好的老师"}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}],"model":"qwen-live"}""",
            """data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""",
            "data: [DONE]"
        )
        val transport = ScriptedSseTransport(streamLines = sse)
        val client = buildClient(transport)

        val deltas = mutableListOf<Pair<String, String>>()
        val resp = client.chatStreaming(
            messages = listOf(LlmMessage(role = "user", content = "题目")),
            tools = emptyList(),
            onThinkingDelta = { reasoningDelta, contentDelta ->
                deltas.add(reasoningDelta to contentDelta)
            }
        )

        // 原始增量按到达顺序双通道回调（不做累计快照）
        assertEquals(
            listOf(
                "我先" to "",
                "想想" to "",
                "" to "<thinking>定稿",
                "" to "内心</thinking>",
                "" to "\n好的老师"
            ),
            deltas
        )
        // 聚合响应与一次性 chat 同构（content 含完整信封，ToolLoop 下游闸逻辑不变）
        assertEquals("<thinking>定稿内心</thinking>\n好的老师", resp.content)
        assertEquals("stop", resp.finishReason)
        assertEquals("qwen-live", resp.model)
        assertEquals(15, resp.usage?.totalTokens)
        assertEquals("我先想想", resp.rawMessage?.optString("reasoning_content"))
        assertEquals("<thinking>定稿内心</thinking>\n好的老师", resp.rawMessage?.optString("content"))
    }

    @Test
    fun chatStreamingRequestCarriesSseHeadersAndStreamPayload() = runBlocking {
        val transport = ScriptedSseTransport(
            streamLines = listOf("data: [DONE]")
        )
        val client = buildClient(transport)

        client.chatStreaming(
            messages = listOf(LlmMessage(role = "user", content = "题目")),
            tools = emptyList(),
            onThinkingDelta = { _, _ -> }
        )

        val request = transport.requests.single()
        assertEquals("https://hub.test/v1/chat/completions", request.url)
        assertEquals("text/event-stream", request.headers["Accept"])
        // 防 HttpURLConnection 透明 gzip 把整段流缓冲成一次回调
        assertEquals("identity", request.headers["Accept-Encoding"])
        val payload = JSONObject(request.jsonBody)
        assertTrue("stream 应为 true", payload.optBoolean("stream"))
        assertTrue(
            "stream_options.include_usage 应开启",
            payload.optJSONObject("stream_options")?.optBoolean("include_usage") == true
        )
        assertTrue("思考流开关应下发", payload.optBoolean("enable_thinking"))
    }

    @Test
    fun chatStreamingAggregatesToolCallFragmentsByIndex() = runBlocking {
        val sse = listOf(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"search_materials","arguments":"{\"concept\":\"一元"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"一次方程\"}"}}]}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )
        val transport = ScriptedSseTransport(streamLines = sse)
        val client = buildClient(transport)

        val resp = client.chatStreaming(
            messages = listOf(LlmMessage(role = "user", content = "题目")),
            tools = emptyList(),
            onThinkingDelta = { _, _ -> }
        )

        assertEquals(1, resp.toolCalls.size)
        val call = resp.toolCalls.single()
        assertEquals("call_1", call.callId)
        assertEquals("search_materials", call.name)
        assertEquals("""{"concept":"一元一次方程"}""", call.arguments)
        assertEquals("tool_calls", resp.finishReason)
    }

    @Test
    fun chatStreamingFallsBackToOneShotChatOnStreamFailure() = runBlocking {
        val transport = ScriptedSseTransport(streamResult = ProviderHttpResult.Failure)
        val client = buildClient(transport)

        val deltas = mutableListOf<Pair<String, String>>()
        val resp = client.chatStreaming(
            messages = listOf(LlmMessage(role = "user", content = "题目")),
            tools = emptyList(),
            onThinkingDelta = { reasoningDelta, contentDelta ->
                deltas.add(reasoningDelta to contentDelta)
            }
        )

        assertEquals("一次性回复", resp.content)
        assertEquals("降级后不应有增量回调", 0, deltas.size)
        assertEquals("降级走一次性路径", 2, transport.requests.size)
        assertTrue(transport.requests[0].streaming)
        assertTrue(!transport.requests[1].streaming)
    }
}
