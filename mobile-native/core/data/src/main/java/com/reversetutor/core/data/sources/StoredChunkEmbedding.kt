package com.reversetutor.core.data.sources

/**
 * 1e: a stored chunk embedding plus the identity of the model that produced
 * it. [modelKey] is null for rows written before database v19 (legacy data);
 * the retrieval side treats those as compatible only with user-configured
 * channels, never with the built-in fallback channel, because cross-model
 * cosine scores are meaningless even at equal dimensions.
 */
data class StoredChunkEmbedding(
    val vector: FloatArray,
    val modelKey: String?,
)
