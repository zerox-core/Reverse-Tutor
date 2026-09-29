package com.reversetutor.core.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DiscoveryOnlineHttpTransportTest {
    @Test
    fun fallsBackToDiscoveredBaseUrlCachesItAndRewritesUpfront() = runBlocking {
        val delegate = RecordingTransport { request ->
            if (request.url.startsWith("http://192.168.0.102:8100")) {
                throw OnlineTransportException("network_failure", retryable = true)
            }
            OnlineHttpResponse(200, "{}")
        }
        val saved = mutableListOf<String>()
        val transport = DiscoveryOnlineHttpTransport(
            delegate = delegate,
            seedBaseUrl = "http://192.168.0.102:8100",
            probeTransport = RecordingTransport { request ->
                if (request.url == "http://192.168.0.100:8100/api/v1/health") {
                    OnlineHttpResponse(200, "{}")
                } else {
                    throw OnlineTransportException("network_failure", retryable = true)
                }
            },
            discover = { listOf("http://192.168.0.100:8100") },
            loadCachedBaseUrl = { null },
            saveCachedBaseUrl = { saved += it }
        )

        val response = transport.execute(
            OnlineHttpRequest("GET", "http://192.168.0.102:8100/api/v1/activities")
        )

        assertEquals(200, response.statusCode)
        assertEquals(
            "http://192.168.0.100:8100/api/v1/activities",
            delegate.requests.last().url
        )
        assertEquals(listOf("http://192.168.0.100:8100"), saved)

        // 恢复后的请求直接改写到活动地址，不再先失败一次
        val second = transport.execute(
            OnlineHttpRequest("GET", "http://192.168.0.102:8100/api/v1/activities")
        )
        assertEquals(200, second.statusCode)
        assertEquals(
            "http://192.168.0.100:8100/api/v1/activities",
            delegate.requests.last().url
        )
    }

    @Test
    fun cachedBaseUrlBeatsSeedWhenSeedIsUnreachable() = runBlocking {
        val delegate = RecordingTransport { request ->
            if (request.url.startsWith("http://192.168.0.102:8100")) {
                throw OnlineTransportException("network_failure", retryable = true)
            }
            OnlineHttpResponse(200, "{}")
        }
        val transport = DiscoveryOnlineHttpTransport(
            delegate = delegate,
            seedBaseUrl = "http://192.168.0.102:8100",
            probeTransport = RecordingTransport { request ->
                if (request.url == "http://192.168.0.100:8100/api/v1/health") {
                    OnlineHttpResponse(200, "{}")
                } else {
                    throw OnlineTransportException("network_failure", retryable = true)
                }
            },
            discover = { emptyList() },
            loadCachedBaseUrl = { "http://192.168.0.100:8100" }
        )

        val response = transport.execute(
            OnlineHttpRequest("GET", "http://192.168.0.102:8100/api/v1/auth/anonymous")
        )

        assertEquals(200, response.statusCode)
        assertEquals(
            "http://192.168.0.100:8100/api/v1/auth/anonymous",
            delegate.requests.last().url
        )
    }

    @Test
    fun propagatesFailureWhenNoCandidateIsReachable() = runBlocking {
        val delegate = RecordingTransport {
            throw OnlineTransportException("network_failure", retryable = true)
        }
        val transport = DiscoveryOnlineHttpTransport(
            delegate = delegate,
            seedBaseUrl = "http://192.168.0.102:8100",
            probeTransport = RecordingTransport {
                throw OnlineTransportException("network_failure", retryable = true)
            },
            discover = { emptyList() },
            loadCachedBaseUrl = { null }
        )

        try {
            transport.execute(
                OnlineHttpRequest("GET", "http://192.168.0.102:8100/api/v1/health")
            )
            fail("expected OnlineTransportException")
        } catch (error: OnlineTransportException) {
            assertEquals("network_failure", error.errorCode)
        }
    }

    @Test
    fun prefixedSeedBaseUrlPassesThroughWithoutDoubling() = runBlocking {
        val delegate = RecordingTransport { OnlineHttpResponse(200, "{}") }
        val transport = DiscoveryOnlineHttpTransport(
            delegate = delegate,
            seedBaseUrl = "https://reverse-tutor.example.cn/online-api",
            probeTransport = RecordingTransport {
                throw OnlineTransportException("network_failure", retryable = true)
            },
            discover = { emptyList() },
            loadCachedBaseUrl = { null }
        )

        val response = transport.execute(
            OnlineHttpRequest("GET", "https://reverse-tutor.example.cn/online-api/api/v1/activities?limit=20")
        )

        assertEquals(200, response.statusCode)
        assertEquals(
            "https://reverse-tutor.example.cn/online-api/api/v1/activities?limit=20",
            delegate.requests.last().url
        )
    }

    @Test
    fun recoveryFromPrefixedSeedToLanCandidateStripsPrefix() = runBlocking {
        val delegate = RecordingTransport { request ->
            if (request.url.startsWith("https://reverse-tutor.example.cn")) {
                throw OnlineTransportException("network_failure", retryable = true)
            }
            OnlineHttpResponse(200, "{}")
        }
        val transport = DiscoveryOnlineHttpTransport(
            delegate = delegate,
            seedBaseUrl = "https://reverse-tutor.example.cn/online-api",
            probeTransport = RecordingTransport { request ->
                if (request.url == "http://192.168.0.100:8100/api/v1/health") {
                    OnlineHttpResponse(200, "{}")
                } else {
                    throw OnlineTransportException("network_failure", retryable = true)
                }
            },
            discover = { listOf("http://192.168.0.100:8100") },
            loadCachedBaseUrl = { null }
        )

        val response = transport.execute(
            OnlineHttpRequest("GET", "https://reverse-tutor.example.cn/online-api/api/v1/content/feed?limit=4")
        )

        assertEquals(200, response.statusCode)
        assertEquals(
            "http://192.168.0.100:8100/api/v1/content/feed?limit=4",
            delegate.requests.last().url
        )
    }
    @Test
    fun parseBaseUrlsExtractsUrlsFromDiscoveryPayload() {
        val payload =
            """{"service":"reverse-tutor-online","magic":"REVERSE_TUTOR_DISCOVER_V1","baseUrls":["http://192.168.0.100:8100","http://198.18.0.1:8100"]}"""

        assertEquals(
            listOf("http://192.168.0.100:8100", "http://198.18.0.1:8100"),
            UdpLanDiscovery.parseBaseUrls(payload)
        )
        assertTrue(UdpLanDiscovery.parseBaseUrls("""{"magic":"other"}""").isEmpty())
    }
}

private class RecordingTransport(
    private val handler: (OnlineHttpRequest) -> OnlineHttpResponse
) : OnlineHttpTransport {
    val requests = mutableListOf<OnlineHttpRequest>()

    override suspend fun execute(request: OnlineHttpRequest): OnlineHttpResponse {
        requests += request
        return handler(request)
    }
}
