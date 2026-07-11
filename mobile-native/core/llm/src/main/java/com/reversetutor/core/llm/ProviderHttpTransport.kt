package com.reversetutor.core.llm

data class ProviderHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val jsonBody: String,
    val timeoutMillis: Int,
    val streaming: Boolean
)

sealed interface ProviderHttpResult {
    data class Response(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap()
    ) : ProviderHttpResult

    data object Timeout : ProviderHttpResult

    data object Failure : ProviderHttpResult
}

fun interface ProviderHttpTransport {
    suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult
}

fun interface LlmSecretResolver {
    suspend fun resolve(secretRef: String): String?
}
