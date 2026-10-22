package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchException
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自适应网络搜索提供方：对齐 DSH 搜索理念，实现「用户自定义 Key 优先 + 官方/开源空客安全兜底」。
 *
 * 优先级逻辑：
 * 1. 优先用户自定义 Key：若当前激活的 Profile 配置了有效的 ApiKey，
 *    向其对应 BaseURL 发起与 DSH 一致的 Anthropic 协议搜索请求（带 web_search_20250305 工具）。
 * 2. 官方保底通道（未来接入）：若用户未配置且内置了官方保底凭据，走官方托管中继。
 * 3. 开源空客兜底：若均无配置或发生不可恢复异常，返回空列表（内核自动渲染 available=false 事实），
 *    绝不抛出崩溃异常打断教学主线。
 */
class AdaptiveWebSearchProvider(
    private val profileProvider: suspend () -> LlmProfile?,
    private val apiKeyResolver: suspend (String?) -> String?,
    private val transport: ProviderHttpTransport,
    private val builtinOfficialKeyProvider: (() -> String?)? = null,
    private val builtinOfficialBaseUrl: String? = null
) : RTWebSearchProvider {

    override val providerName: String = "adaptive-search"

    override fun search(query: String, maxResults: Int): List<JSONObject> = kotlinx.coroutines.runBlocking {
        val q = query.trim()
        if (q.isEmpty()) return@runBlocking emptyList<JSONObject>()

        // 1. 优先提取用户自定义 Profile
        val userProfile = profileProvider()
        val userApiKey = userProfile?.secretRef?.let { apiKeyResolver(it) }?.trim().orEmpty()
        val userBaseUrl = userProfile?.baseUrl?.trim().orEmpty()

        val (targetBaseUrl, targetApiKey) = when {
            userApiKey.isNotEmpty() -> {
                // 用户配置优先
                val base = if (userBaseUrl.isNotEmpty()) userBaseUrl else "https://api.deepseek.com"
                base to userApiKey
            }
            else -> {
                // 用户未配置 -> 尝试官方保底
                val officialKey = builtinOfficialKeyProvider?.invoke()?.trim().orEmpty()
                if (officialKey.isNotEmpty()) {
                    (builtinOfficialBaseUrl ?: "https://api.deepseek.com") to officialKey
                } else {
                    // 开源空客模式：无可用 Key，直接返回空（触发内核 available=false 事实）
                    return@runBlocking emptyList<JSONObject>()
                }
            }
        }

        try {
            executeSearchRequest(targetBaseUrl, targetApiKey, q, maxResults)
        } catch (e: Exception) {
            // 记录异常并抛出 RTWebSearchException，让内核 CoreTools 走标准的结构化降级逻辑
            throw RTWebSearchException("搜索请求执行失败: ${e.message}")
        }
    }

    private suspend fun executeSearchRequest(
        baseUrl: String,
        apiKey: String,
        query: String,
        maxResults: Int
    ): List<JSONObject> {
        // 标准化 Anthropic 兼容端点：保证结尾带 /messages
        val cleanBase = baseUrl.trimEnd('/')
        val endpoint = if (cleanBase.endsWith("/messages")) {
            cleanBase
        } else if (cleanBase.endsWith("/v1")) {
            "$cleanBase/messages"
        } else {
            "$cleanBase/anthropic/v1/messages"
        }

        val payload = JSONObject().apply {
            put("model", "deepseek-v4-flash")
            put("max_tokens", 1024)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Perform a web search for the query: $query")
                })
            })
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "web_search_20250305")
                    put("name", "web_search")
                })
            })
        }

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        val request = com.reversetutor.core.llm.ProviderHttpRequest(
            url = endpoint,
            headers = headers,
            jsonBody = payload.toString(),
            timeoutMillis = 15000,
            streaming = false
        )

        val result = transport.execute(request)
        val response = when (result) {
            is com.reversetutor.core.llm.ProviderHttpResult.Response -> result
            is com.reversetutor.core.llm.ProviderHttpResult.Timeout -> {
                throw RTWebSearchException("搜索请求超时")
            }
            else -> {
                throw RTWebSearchException("搜索请求网络失败")
            }
        }

        if (response.statusCode !in 200..299) {
            throw RTWebSearchException("搜索端点响应异常: HTTP ${response.statusCode}")
        }

        return parseSearchResponse(response.body, maxResults)
    }

    /**
     * 解析 Anthropic / DeepSeek 原生搜索响应，提取 [{title, snippet, url}]
     */
    internal fun parseSearchResponse(responseBody: String, maxResults: Int): List<JSONObject> {
        val hits = mutableListOf<JSONObject>()
        val root = try {
            JSONObject(responseBody)
        } catch (_: Exception) {
            return emptyList()
        }

        val content = root.optJSONArray("content") ?: return emptyList()

        // 提取 URL -> 引用片段 (cited_text)
        val citationsMap = mutableMapOf<String, String>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            val citations = block.optJSONArray("citations") ?: continue
            for (j in 0 until citations.length()) {
                val cite = citations.optJSONObject(j) ?: continue
                val u = cite.optString("url")
                val text = cite.optString("cited_text")
                if (u.isNotEmpty() && text.isNotEmpty() && !citationsMap.containsKey(u)) {
                    citationsMap[u] = text
                }
            }
        }

        // 提取 web_search_result 块
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "web_search_tool_result") {
                val resultsArr = block.optJSONArray("results") ?: continue
                for (k in 0 until resultsArr.length()) {
                    val r = resultsArr.optJSONObject(k) ?: continue
                    val url = r.optString("url")
                    val title = r.optString("title").ifEmpty { url }
                    val snippet = citationsMap[url] ?: r.optString("snippet", "")
                    if (url.isNotEmpty() || title.isNotEmpty()) {
                        hits.add(JSONObject().apply {
                            put("title", title)
                            put("url", url)
                            put("snippet", snippet)
                        })
                    }
                    if (hits.size >= maxResults) break
                }
            }
        }

        return hits
    }
}
