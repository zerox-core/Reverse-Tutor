package com.reversetutor.core.remote

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 局域网自恢复传输层。
 *
 * 开发机 WLAN IP 会随 DHCP 漂移，硬编码 baseUrl 会随之失效。本类在请求
 * 失败（网络不可达 / 超时）时按序探测候选地址——当前地址、编译期种子、
 * 上次成功缓存、模拟器回环（10.0.2.2）、UDP 广播发现结果——用短超时
 * GET /api/v1/health 找到可用地址后重写请求 URL 重试，并缓存结果；
 * 恢复成功后的后续请求直接改写到活动地址，不再先失败一次。
 */
class DiscoveryOnlineHttpTransport(
    private val delegate: OnlineHttpTransport,
    private val seedBaseUrl: String,
    private val probeTransport: OnlineHttpTransport = UrlConnectionOnlineHttpTransport(3_000),
    private val discover: suspend () -> List<String> = { UdpLanDiscovery.discoverBaseUrls() },
    private val loadCachedBaseUrl: () -> String? = { null },
    private val saveCachedBaseUrl: (String) -> Unit = {}
) : OnlineHttpTransport {

    @Volatile
    private var activeBaseUrl: String = seedBaseUrl.trimEnd('/')

    override suspend fun execute(request: OnlineHttpRequest): OnlineHttpResponse {
        val effective = if (baseOf(request.url) != activeBaseUrl) {
            request.copy(url = rewriteTo(request.url, activeBaseUrl))
        } else {
            request
        }
        return try {
            delegate.execute(effective)
        } catch (error: OnlineTransportException) {
            if (!error.retryable) throw error
            val recovered = recoverBaseUrl() ?: throw error
            delegate.execute(effective.copy(url = rewriteTo(effective.url, recovered)))
        }
    }

    private suspend fun recoverBaseUrl(): String? {
        val candidates = buildList {
            add(activeBaseUrl)
            add(seedBaseUrl)
            loadCachedBaseUrl()?.let { add(it) }
            add(EMULATOR_FALLBACK_BASE_URL)
            addAll(discover())
        }.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
        for (candidate in candidates) {
            if (probe(candidate)) {
                activeBaseUrl = candidate
                saveCachedBaseUrl(candidate)
                return candidate
            }
        }
        return null
    }

    private suspend fun probe(baseUrl: String): Boolean = try {
        probeTransport.execute(
            OnlineHttpRequest(method = "GET", url = "$baseUrl/api/v1/health")
        ).statusCode in 200..299
    } catch (_: OnlineTransportException) {
        false
    }

    private fun rewriteTo(url: String, baseUrl: String): String =
        baseUrl.trimEnd('/') + pathOf(url)

    internal companion object {
        const val EMULATOR_FALLBACK_BASE_URL = "http://10.0.2.2:8100"

        fun baseOf(url: String): String {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return url
            val pathStart = url.indexOf('/', schemeEnd + 3)
            return if (pathStart < 0) url else url.substring(0, pathStart)
        }

        fun pathOf(url: String): String {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return url
            val pathStart = url.indexOf('/', schemeEnd + 3)
            return if (pathStart < 0) "/" else url.substring(pathStart)
        }
    }
}

/** UDP 广播发现：向局域网询问在线服务地址（服务端 DiscoveryResponder 应答）。 */
object UdpLanDiscovery {
    const val DISCOVERY_PORT = 8137
    const val MAGIC = "REVERSE_TUTOR_DISCOVER_V1"
    private const val RECEIVE_WINDOW_MILLIS = 1_500L
    private const val SOCKET_TIMEOUT_MILLIS = 250

    suspend fun discoverBaseUrls(): List<String> = withContext(Dispatchers.IO) {
        val found = linkedSetOf<String>()
        val socket = try {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = SOCKET_TIMEOUT_MILLIS
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        try {
            val probe = MAGIC.toByteArray(Charsets.UTF_8)
            try {
                socket.send(
                    DatagramPacket(
                        probe,
                        probe.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT
                    )
                )
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            val deadline = System.currentTimeMillis() + RECEIVE_WINDOW_MILLIS
            while (System.currentTimeMillis() < deadline) {
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) {
                    continue
                }
                val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                found.addAll(parseBaseUrls(payload))
            }
        } finally {
            socket.close()
        }
        found.toList()
    }

    internal fun parseBaseUrls(payload: String): List<String> {
        if (!payload.contains(MAGIC)) return emptyList()
        return Regex("\"(https?://[^\"]+)\"")
            .findAll(payload)
            .map { it.groupValues[1].trimEnd('/') }
            .distinct()
            .toList()
    }
}
