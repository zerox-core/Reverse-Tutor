package com.reversetutor.core.llm

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RtProviderTransport"

class UrlConnectionProviderHttpTransport : ProviderHttpTransport {
    override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = request.method
                    connectTimeout = request.timeoutMillis
                    readTimeout = request.timeoutMillis
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = request.method.uppercase() != "GET"
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (request.jsonBody.isNotEmpty()) {
                        outputStream.bufferedWriter(Charsets.UTF_8).use {
                            it.write(request.jsonBody)
                        }
                    }
                }
                try {
                    val statusCode = connection.responseCode
                    val body = (if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (statusCode !in 200..299) {
                        Log.e(TAG, "execute http $statusCode url=${request.url} body=${body.take(400)}")
                    }
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
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "timeout url=${request.url}", e)
                ProviderHttpResult.Timeout
            } catch (e: IOException) {
                Log.e(TAG, "io failure url=${request.url}", e)
                ProviderHttpResult.Failure
            }
        }

    override suspend fun executeStreaming(
        request: ProviderHttpRequest,
        onLine: (String) -> Unit
    ): ProviderHttpResult = executeStreaming(request, onLine) { false }

    override suspend fun executeStreaming(
        request: ProviderHttpRequest,
        onLine: (String) -> Unit,
        shouldAbort: () -> Boolean
    ): ProviderHttpResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = request.timeoutMillis
                readTimeout = request.timeoutMillis
                instanceFollowRedirects = false
                useCaches = false
                doOutput = request.method.uppercase() != "GET"
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.jsonBody) }
            }
            try {
                val statusCode = connection.responseCode
                val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader ->
                        buildString {
                            // Slice 3: poll the abort signal between lines so a
                            // red-lined stream is cut early; the aborted line
                            // itself is never forwarded.
                            while (!shouldAbort()) {
                                val line = reader.readLine() ?: break
                                if (shouldAbort()) break
                                onLine(line)
                                append(line).append('\n')
                            }
                        }
                    }.orEmpty()
                if (statusCode !in 200..299) {
                    Log.e(TAG, "stream http $statusCode url=${request.url} body=${body.take(400)}")
                }
                ProviderHttpResult.Response(
                    statusCode = statusCode,
                    body = body,
                    headers = connection.headerFields.filterKeys { it != null }
                        .mapValues { (_, values) -> values.joinToString(",") }
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "stream timeout url=${request.url}", e)
            ProviderHttpResult.Timeout
        } catch (e: IOException) {
            Log.e(TAG, "stream io failure url=${request.url}", e)
            ProviderHttpResult.Failure
        }
    }
}
