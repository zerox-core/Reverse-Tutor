package com.reversetutor.core.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-024 follow-up: embedding model auto-detection contracts.
 *
 * Pins: the GET /models request shape, candidate ranking, in-memory caching
 * for both positive and definitive-negative results, and that transient
 * failures return null without polluting the cache.
 */
class EmbeddingModelDiscoveryTest {

    private class RecordingTransport(vararg responses: ProviderHttpResult) : ProviderHttpTransport {
        val requests = mutableListOf<ProviderHttpRequest>()
        private val queue = responses.toMutableList()
        override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult {
            requests += request
            return queue.removeFirstOrNull() ?: ProviderHttpResult.Failure
        }
    }

    private val resolver = LlmSecretResolver { "secret-1" }

    private fun models(vararg ids: String): String {
        val data = ids.joinToString(",") { "{\"id\":\"$it\"}" }
        return "{\"data\":[$data]}"
    }

    @Test
    fun prefersKnownEmbeddingModelIdsOverGenericMatches() {
        val discovery = EmbeddingModelDiscovery(RecordingTransport(), resolver)
        val body = models("qwen-max", "BAAI/bge-m3", "text-embedding-v3", "embedding-3")
        assertEquals("text-embedding-v3", discovery.pickEmbeddingModel(body))
    }

    @Test
    fun fallsBackToAnyEmbeddingLookingId() {
        val discovery = EmbeddingModelDiscovery(RecordingTransport(), resolver)
        assertEquals(
            "some-embed-model",
            discovery.pickEmbeddingModel(models("chat-model", "some-embed-model"))
        )
        assertEquals(
            "BAAI/bge-m3",
            discovery.pickEmbeddingModel(models("chat-model", "BAAI/bge-m3"))
        )
    }

    @Test
    fun bodyWithoutEmbeddingModelsReturnsNull() {
        val discovery = EmbeddingModelDiscovery(RecordingTransport(), resolver)
        assertNull(discovery.pickEmbeddingModel(models("qwen-max", "chat-model")))
        assertNull(discovery.pickEmbeddingModel("{\"data\":[]}"))
        assertNull(discovery.pickEmbeddingModel("not json"))
    }

    @Test
    fun discoverSendsGetToModelsEndpoint() = runBlocking {
        val transport = RecordingTransport(
            ProviderHttpResult.Response(200, models("text-embedding-v3"))
        )
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        val model = discovery.discover("ref-1", "https://api.example.com/v1/")

        assertEquals("text-embedding-v3", model)
        val request = transport.requests.single()
        assertEquals("https://api.example.com/v1/models", request.url)
        assertEquals("GET", request.method)
        assertEquals("Bearer secret-1", request.headers["Authorization"])
    }

    @Test
    fun cachesResolvedModelAcrossCalls() = runBlocking {
        val transport = RecordingTransport(
            ProviderHttpResult.Response(200, models("text-embedding-v3"))
        )
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        assertEquals("text-embedding-v3", discovery.discover("ref-1", "https://api.example.com/v1"))
        assertEquals("text-embedding-v3", discovery.discover("ref-1", "https://api.example.com/v1"))

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun definitiveNegativeIsCached() = runBlocking {
        val transport = RecordingTransport(
            ProviderHttpResult.Response(200, models("qwen-max"))
        )
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        assertNull(discovery.discover("ref-1", "https://api.example.com/v1"))
        assertNull(discovery.discover("ref-1", "https://api.example.com/v1"))

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun transientFailuresAreNotCached() = runBlocking {
        val transport = RecordingTransport(
            ProviderHttpResult.Timeout,
            ProviderHttpResult.Response(200, models("text-embedding-v3"))
        )
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        assertNull(discovery.discover("ref-1", "https://api.example.com/v1"))
        assertEquals("text-embedding-v3", discovery.discover("ref-1", "https://api.example.com/v1"))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun blankInputsFailWithoutARequest() = runBlocking {
        val transport = RecordingTransport()
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        assertNull(discovery.discover("", "https://api.example.com/v1"))
        assertNull(discovery.discover("ref-1", "  "))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun unsafeEndpointFailsWithoutARequest() = runBlocking {
        val transport = RecordingTransport()
        val discovery = EmbeddingModelDiscovery(transport, resolver)

        assertNull(discovery.discover("ref-1", "file:///etc/passwd"))
        assertTrue(transport.requests.isEmpty())
    }
}
