package com.reversetutor.core.model

data class LlmProfile(
    val id: String,
    val spaceId: String,
    val name: String,
    val provider: LlmProviderKind,
    val model: String,
    val secretRef: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val baseUrl: String? = null,
    val enabled: Boolean = true
)

enum class LlmProviderKind {
    OpenAiCompatible,
    AnthropicCompatible,
    Gemini,
    DeepSeek,
    FreeGlm,
    Local,
    Custom
}
