package com.reversetutor.core.remote

data class OnlineHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class OnlineHttpResponse(
    val statusCode: Int,
    val body: String = ""
)

fun interface OnlineHttpTransport {
    suspend fun execute(request: OnlineHttpRequest): OnlineHttpResponse
}

fun interface OnlineAuthTokenProvider {
    suspend fun token(): String?
}

class OnlineTransportException(
    val errorCode: String = "network_failure",
    val retryable: Boolean = true,
    cause: Throwable? = null
) : RuntimeException(errorCode, cause)
