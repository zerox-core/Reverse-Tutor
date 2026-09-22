package com.reversetutor.core.llm

/**
 * 内置 embedding 兜底渠道（1e 设计：用户渠道 -> 内置 bge-m3 -> 关键词回退）。
 * key 不进 APK：内置渠道走服务器转发（hub /v1/embeddings），匿名调用。
 */
object BuiltInEmbeddingChannel {
    const val BaseUrl: String = "https://hub.zeroxcore.tech/v1"
    const val Model: String = "BAAI/bge-m3"
}

enum class EmbeddingChannelKind {
    UserConfigured,
    BuiltIn,
}

/**
 * 一批向量及其来源身份。
 * modelKey 用于存储/检索时的模型归属匹配（跨模型向量同维度余弦为垃圾值）。
 */
data class EmbeddingVectorSet(
    val modelKey: String,
    val channelKind: EmbeddingChannelKind,
    val vectors: List<FloatArray>,
)
