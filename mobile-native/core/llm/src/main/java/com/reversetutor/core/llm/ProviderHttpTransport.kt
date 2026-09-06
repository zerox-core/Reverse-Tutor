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

    /** Optional line callback for streaming responses; legacy transports keep one-shot behavior. */
    suspend fun executeStreaming(
        request: ProviderHttpRequest,
        onLine: (String) -> Unit
    ): ProviderHttpResult = execute(request)
}

fun interface LlmSecretResolver {
    suspend fun resolve(secretRef: String): String?
}
