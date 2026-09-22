package com.reversetutor.core.llm

data class ProviderHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val jsonBody: String,
    val timeoutMillis: Int,
    val streaming: Boolean,
    val method: String = "POST"
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

    /**
     * Streaming variant with an abort signal (expression-loop slice 3): the
     * transport checks [shouldAbort] between lines and stops reading early —
     * the reply watchdog uses it to cut a red-lined stream instead of
     * draining it. Default delegates to the two-arg version and ignores the
     * signal, so legacy transports keep their behavior.
     */
    suspend fun executeStreaming(
        request: ProviderHttpRequest,
        onLine: (String) -> Unit,
        shouldAbort: () -> Boolean
    ): ProviderHttpResult = executeStreaming(request, onLine)
}

fun interface LlmSecretResolver {
    suspend fun resolve(secretRef: String): String?
}
