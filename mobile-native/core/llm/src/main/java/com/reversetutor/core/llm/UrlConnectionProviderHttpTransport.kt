package com.reversetutor.core.llm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UrlConnectionProviderHttpTransport : ProviderHttpTransport {
    override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = request.timeoutMillis
                    readTimeout = request.timeoutMillis
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = true
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    outputStream.bufferedWriter(Charsets.UTF_8).use {
                        it.write(request.jsonBody)
                    }
                }
                try {
                    val statusCode = connection.responseCode
                    val body = (if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    ProviderHttpResult.Response(
                        statusCode = statusCode,
                        body = body,
                        headers = connection.headerFields
                            .filterKeys { it != null }
                            .mapValues { (_, values) -> values.joinToString(",") }
                    )
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                ProviderHttpResult.Timeout
            } catch (_: IOException) {
                ProviderHttpResult.Failure
            }
        }
}
