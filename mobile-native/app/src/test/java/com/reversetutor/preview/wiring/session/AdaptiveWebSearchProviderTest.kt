package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.ProviderHttpRequest
import com.reversetutor.core.llm.ProviderHttpResult
import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdaptiveWebSearchProviderTest {

    private class MockHttpTransport : ProviderHttpTransport {
        var lastRequest: ProviderHttpRequest? = null
        var responseStatusCode: Int = 200
        var responseBody: String = ""

        override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult {
            lastRequest = request
            return ProviderHttpResult.Response(
                statusCode = responseStatusCode,
                body = responseBody
            )
        }
    }

    @Test
    fun userApiKeyTakesPrecedenceAndDispatchesAnthropicSearchRequest() {
        val mockTransport = MockHttpTransport().apply {
            responseStatusCode = 200
            responseBody = JSONObject().apply {
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "web_search_tool_result")
                        put("results", JSONArray().apply {
                            put(JSONObject().apply {
                                put("title", "空间向量基本定理")
                                put("url", "https://example.com/vector")
                                put("snippet", "基底不共面定理是空间向量核心。")
                            })
                        })
                    })
                })
            }.toString()
        }

        val testProfile = LlmProfile(
            id = "profile-user",
            spaceId = "space-1",
            name = "My Custom Sub2API",
            provider = LlmProviderKind.DeepSeek,
            model = "deepseek-chat",
            secretRef = "sec-user-key",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
            baseUrl = "https://api.my-sub2api.com/v1",
            enabled = true
        )

        val provider = AdaptiveWebSearchProvider(
            profileProvider = { testProfile },
            apiKeyResolver = { ref -> if (ref == "sec-user-key") "sk-user-sub2api-12345" else null },
            transport = mockTransport,
            builtinOfficialKeyProvider = { "sk-official-fallback" },
            builtinOfficialBaseUrl = "https://official.api.com"
        )

        val hits = provider.search("空间向量公式", maxResults = 3)

        // 验证优先走了用户的 Key 和用户配置的 BaseURL
        val req = mockTransport.lastRequest
        assertNotNull(req)
        assertTrue(req!!.url.startsWith("https://api.my-sub2api.com/v1/messages"))
        assertEquals("Bearer sk-user-sub2api-12345", req.headers["Authorization"])
        assertEquals("sk-user-sub2api-12345", req.headers["x-api-key"])

        // 验证正确解析了返回结果
        assertEquals(1, hits.size)
        assertEquals("空间向量基本定理", hits[0].optString("title"))
        assertEquals("https://example.com/vector", hits[0].optString("url"))
        assertEquals("基底不共面定理是空间向量核心。", hits[0].optString("snippet"))
    }

    @Test
    fun openSourceAirbusModeReturnsEmptyWhenNoKeysConfigured() {
        val mockTransport = MockHttpTransport()

        val emptyProfile = LlmProfile(
            id = "profile-empty",
            spaceId = "space-1",
            name = "No Key Profile",
            provider = LlmProviderKind.DeepSeek,
            model = "deepseek-chat",
            secretRef = null,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
            baseUrl = "",
            enabled = true
        )

        val provider = AdaptiveWebSearchProvider(
            profileProvider = { emptyProfile },
            apiKeyResolver = { null },
            transport = mockTransport,
            builtinOfficialKeyProvider = null, // 开源空客：无官方Key
            builtinOfficialBaseUrl = null
        )

        val hits = provider.search("任意问题", maxResults = 3)

        // 开源空客模式：不应发送任何 HTTP 请求，安全返回空结果
        assertTrue(hits.isEmpty())
        assertEquals(null, mockTransport.lastRequest)
    }

    @Test
    fun httpErrorThrowsStructuredExceptionForCoreToolsRetry() {
        val mockTransport = MockHttpTransport().apply {
            responseStatusCode = 502
            responseBody = "Bad Gateway"
        }

        val testProfile = LlmProfile(
            id = "profile-err",
            spaceId = "space-1",
            name = "Error Profile",
            provider = LlmProviderKind.DeepSeek,
            model = "deepseek-chat",
            secretRef = "sec-key",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
            baseUrl = "https://api.my-sub2api.com",
            enabled = true
        )

        val provider = AdaptiveWebSearchProvider(
            profileProvider = { testProfile },
            apiKeyResolver = { "sk-test" },
            transport = mockTransport
        )

        try {
            provider.search("测试异常", maxResults = 3)
            fail("网络响应 502 时必须抛出 RTWebSearchException")
        } catch (e: RTWebSearchException) {
            assertTrue(e.message?.contains("502") == true)
        }
    }
}
