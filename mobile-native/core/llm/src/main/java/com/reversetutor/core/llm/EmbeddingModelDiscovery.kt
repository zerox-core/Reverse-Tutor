package com.reversetutor.core.llm

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * NEWMP-V1-024 follow-up: auto-detects the channel's embedding model name.
 *
 * Users never see (or know) which embedding model their configured channel
 * serves, so the app asks the channel itself: GET {baseUrl}/models lists the
 * model ids an OpenAI-compatible endpoint offers, and the best embedding
 * candidate is used for all embedding calls. The resolved name is cached in
 * memory per endpoint for the process lifetime; a definitive negative (the
 * endpoint answered but has no embedding model) is cached too so it costs one
 * extra request in total, not one per turn. Transient failures (timeout,
 * network error, auth trouble, non-2xx) are never cached, so a recovered
 * channel is picked up on the next call.
 *
 * Every failure mode returns null; callers keep their hardcoded default and
 * the keyword retrieval fallback stays the final safety net.
 */
class EmbeddingModelDiscovery(
    private val transport: ProviderHttpTransport,
    private val secretResolver: LlmSecretResolver,
    private val timeoutMillis: Int = DefaultDiscoveryTimeoutMillis
) {
    /** Resolved model names keyed by endpoint base URL. */
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun discover(secretRef: String?, baseUrl: String?): String? {
        val endpointBase = baseUrl?.trim()?.trimEnd('/').orEmpty()
        if (endpointBase.isEmpty()) return null
        cache[endpointBase]?.let { cached ->
            return if (cached == NoEmbeddingModelMarker) null else cached
        }
        return when (val outcome = queryModelList(secretRef, endpointBase)) {
            is ModelListOutcome.Resolved -> {
                cache[endpointBase] = outcome.model
                outcome.model
            }
            ModelListOutcome.NoEmbeddingModel -> {
                cache[endpointBase] = NoEmbeddingModelMarker
                null
            }
            ModelListOutcome.TransientFailure -> null
        }
    }

    private suspend fun queryModelList(secretRef: String?, endpointBase: String): ModelListOutcome {
        val trimmedRef = secretRef?.trim().orEmpty()
        if (trimmedRef.isEmpty()) return ModelListOutcome.TransientFailure
        val endpoint = "$endpointBase/models"
        if (!endpoint.isSafeHttpEndpoint()) return ModelListOutcome.TransientFailure
        val secret = runCatching { secretResolver.resolve(trimmedRef) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return ModelListOutcome.TransientFailure
        val request = ProviderHttpRequest(
            url = endpoint,
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $secret"
            ),
            jsonBody = "",
            timeoutMillis = timeoutMillis,
            streaming = false,
            method = "GET"
        )
        val result = runCatching { transport.execute(request) }.getOrNull()
            ?: return ModelListOutcome.TransientFailure
        val response = result as? ProviderHttpResult.Response
            ?: return ModelListOutcome.TransientFailure
        if (response.statusCode !in 200..299) return ModelListOutcome.TransientFailure
        val model = pickEmbeddingModel(response.body)
            ?: return ModelListOutcome.NoEmbeddingModel
        return ModelListOutcome.Resolved(model)
    }

    /** OpenAI-compatible `GET /models` shape: {"data":[{"id":"..."},...]}. */
    internal fun pickEmbeddingModel(body: String): String? {
        val root = ProviderJson.parse(body) as? Map<*, *> ?: return null
        val data = root["data"] as? List<*> ?: return null
        val ids = data.mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
        if (ids.isEmpty()) return null
        for (preferred in PreferredEmbeddingModels) {
            ids.firstOrNull { it == preferred }?.let { return it }
        }
        return ids.firstOrNull { id ->
            val lower = id.lowercase()
            lower.contains("embed") || lower.contains("bge")
        }
    }

    private fun String.isSafeHttpEndpoint(): Boolean {
        val uri = runCatching { URI(this) }.getOrNull() ?: return false
        return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }

    private sealed interface ModelListOutcome {
        data class Resolved(val model: String) : ModelListOutcome
        data object NoEmbeddingModel : ModelListOutcome
        data object TransientFailure : ModelListOutcome
    }

    private companion object {
        /** ConcurrentHashMap cannot hold nulls; this marker means "asked, none available". */
        const val NoEmbeddingModelMarker = "\u0000no-embedding-model"

        const val DefaultDiscoveryTimeoutMillis = 15_000

        /** Most common embedding model ids, best first. */
        val PreferredEmbeddingModels = listOf(
            "text-embedding-v3",
            "text-embedding-v4",
            "text-embedding-3-large",
            "text-embedding-3-small",
            "embedding-3",
            "embedding-2"
        )
    }
}
