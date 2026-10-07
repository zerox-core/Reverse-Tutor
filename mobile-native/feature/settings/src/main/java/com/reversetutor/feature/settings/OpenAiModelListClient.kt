package com.reversetutor.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一键获取模型列表（2026-09-24 拍板）：用户在设置页配好上游 baseUrl + key 后，
 * 调上游 OpenAI 兼容的 GET /models 拉取该 key 可见的全部模型 id，
 * 作为勾选编辑的候选来源。只读发现接口，不产生对话消耗。
 */
suspend fun fetchOpenAiModelIds(
    baseUrl: String,
    apiKey: String
): Result<List<String>> = withContext(Dispatchers.IO) {
    runCatching {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("http")) { "Base URL 需以 http(s) 开头" }
        val connection = URL("$trimmed/models").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
            require(code in 200..299) { "上游返回 HTTP $code：${body.take(160)}" }
            val data = JSONObject(body).optJSONArray("data")
            buildList {
                for (i in 0 until (data?.length() ?: 0)) {
                    data?.optJSONObject(i)?.optString("id")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}