package com.reversetutor.core.remote

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UrlConnectionOnlineHttpTransport(
    private val timeoutMillis: Int = 30_000
) : OnlineHttpTransport {
    override suspend fun execute(request: OnlineHttpRequest): OnlineHttpResponse =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = request.method
                    connectTimeout = timeoutMillis
                    readTimeout = timeoutMillis
                    instanceFollowRedirects = false
                    useCaches = false
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    request.body?.let { body ->
                        doOutput = true
                        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                    }
                }
                try {
                    val statusCode = connection.responseCode
                    val body = (if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val headers = connection.headerFields.entries
                        .filter { it.key != null }
                        .associate { (name, values) ->
                            name.lowercase(Locale.ROOT) to values.joinToString(",")
                        }
                    OnlineHttpResponse(statusCode = statusCode, body = body, headers = headers)
                } finally {
                    connection.disconnect()
                }
            } catch (error: SocketTimeoutException) {
                throw OnlineTransportException("timeout", retryable = true, cause = error)
            } catch (error: IOException) {
                throw OnlineTransportException("network_failure", retryable = true, cause = error)
            }
        }
}
