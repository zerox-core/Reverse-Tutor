package com.reversetutor.core.model

data class ProviderConnection(
    val id: String,
    val spaceId: String,
    val name: String,
    val protocol: ModelProtocol,
    val providerName: String? = null,
    val baseUrl: String? = null,
    val secretRef: String? = null,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis
)

data class ModelBinding(
    val id: String,
    val spaceId: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String = modelId,
    val availability: ModelAvailability = ModelAvailability.Untested,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val lastCheckedAtEpochMillis: Long? = null,
    val lastUsedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis
)

enum class ModelProtocol {
    OpenAiCompatible,
    AnthropicCompatible,
    GeminiNative
}

enum class ModelAvailability {
    Untested,
    Available,
    InvalidUrl,
    InvalidCredential,
    ModelMissing,
    PermissionDenied,
    QuotaExceeded,
    RateLimited,
    ProviderUnavailable
}

data class ModelCapabilityState(
    val text: ModelCapabilitySupport = ModelCapabilitySupport.Unknown,
    val image: ModelCapabilitySupport = ModelCapabilitySupport.Unknown,
    val streaming: ModelCapabilitySupport = ModelCapabilitySupport.Unknown,
    val tools: ModelCapabilitySupport = ModelCapabilitySupport.Unknown
)

enum class ModelCapabilitySupport {
    Supported,
    Unsupported,
    Unknown
}
