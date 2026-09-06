package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import java.net.URI

data class LlmCapabilities(
    val supportsVision: Boolean = false,
    val supportsJsonMode: Boolean = false
)

data class LlmProfileDraft(
    val name: String,
    val provider: LlmProviderKind?,
    val model: String,
    val baseUrl: String?,
    val apiKey: String?,
    val capabilities: LlmCapabilities = LlmCapabilities()
)

data class LlmProviderPreset(
    val id: String,
    val label: String,
    val provider: LlmProviderKind,
    val baseUrl: String?,
    val model: String,
    val capabilities: LlmCapabilities
) {
    companion object {
        val defaults: List<LlmProviderPreset> = listOf(
            LlmProviderPreset(
                id = "openai-compatible",
                label = "OpenAI-compatible",
                provider = LlmProviderKind.OpenAiCompatible,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                capabilities = LlmCapabilities(supportsVision = true, supportsJsonMode = true)
            ),
            LlmProviderPreset(
                id = "anthropic-compatible",
                label = "Anthropic-compatible",
                provider = LlmProviderKind.AnthropicCompatible,
                baseUrl = "https://api.anthropic.com/v1",
                model = "claude-3-5-haiku-latest",
                capabilities = LlmCapabilities(supportsVision = true)
            ),
            LlmProviderPreset(
                id = "custom-local",
                label = "Custom/local",
                provider = LlmProviderKind.Custom,
                baseUrl = "http://localhost:11434",
                model = "local-model",
                capabilities = LlmCapabilities()
            )
        )
    }
}

object LlmProfileCapabilityResolver {
    fun infer(profile: LlmProfile): LlmCapabilities {
        val model = profile.model.lowercase()
        val supportsVision = when (profile.provider) {
            LlmProviderKind.OpenAiCompatible -> model.contains("gpt-4o") ||
                model.contains("gpt-4.1") ||
                model.contains("gpt-5") ||
                model.contains("vision") ||
                model.startsWith("qwen3.7-flash")
            LlmProviderKind.AnthropicCompatible -> model.contains("claude-3") ||
                model.contains("claude-4")
            LlmProviderKind.Gemini -> true
            LlmProviderKind.FreeGlm -> model.contains("glm-4v") ||
                model.contains("vision") ||
                model.contains("vl")
            LlmProviderKind.DeepSeek,
            LlmProviderKind.Local,
            LlmProviderKind.Custom -> model.contains("vision") || model.contains("vl")
        }
        val supportsJsonMode = when (profile.provider) {
            LlmProviderKind.OpenAiCompatible -> true
            LlmProviderKind.Gemini -> true
            else -> false
        }
        return LlmCapabilities(
            supportsVision = supportsVision,
            supportsJsonMode = supportsJsonMode
        )
    }
}

data class LlmValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

object LlmProfileValidator {
    fun validate(draft: LlmProfileDraft): LlmValidationResult {
        val errors = mutableListOf<String>()
        val trimmedName = draft.name.trim()
        val trimmedModel = draft.model.trim()
        val trimmedBaseUrl = draft.baseUrl?.trim().orEmpty()

        if (trimmedName.isBlank()) {
            errors += "Profile name is required."
        }
        if (draft.provider == null) {
            errors += "Provider type is required."
        }
        if (trimmedModel.isBlank()) {
            errors += "Model name is required."
        }
        if (trimmedBaseUrl.isNotBlank() && !trimmedBaseUrl.isHttpUrl()) {
            errors += "Base URL must be a valid http or https URL."
        }
        if (listOf(trimmedName, trimmedModel, trimmedBaseUrl).any { it.containsSecretMaterial() }) {
            errors += "Profile metadata must not contain raw secret material."
        }

        return LlmValidationResult(errors)
    }
}

data class ExportableLlmProfile(
    val id: String,
    val spaceId: String,
    val name: String,
    val provider: LlmProviderKind,
    val model: String,
    val baseUrl: String?,
    val enabled: Boolean,
    val capabilities: LlmCapabilities,
    val hasSecret: Boolean,
    val secret: String?
)

object LlmProfileExportPolicy {
    fun redacted(
        profile: LlmProfile,
        capabilities: LlmCapabilities
    ): ExportableLlmProfile {
        val hasSecret = profile.secretRef != null
        return ExportableLlmProfile(
            id = profile.id,
            spaceId = profile.spaceId,
            name = profile.name,
            provider = profile.provider,
            model = profile.model,
            baseUrl = profile.baseUrl,
            enabled = profile.enabled,
            capabilities = capabilities,
            hasSecret = hasSecret,
            secret = if (hasSecret) "redacted" else null
        )
    }
}

sealed interface LlmConnectionResult {
    data class Success(val message: String) : LlmConnectionResult
    data class Failure(val message: String, val retryable: Boolean = true) : LlmConnectionResult
}

interface LlmConnectionTester {
    fun testConnection(profile: LlmProfile): LlmConnectionResult
}

class MockLlmConnectionTester(
    private val outcomes: Map<String, LlmConnectionResult> = emptyMap()
) : LlmConnectionTester {
    var realProviderCallCount: Int = 0
        private set

    override fun testConnection(profile: LlmProfile): LlmConnectionResult =
        outcomes[profile.id] ?: LlmConnectionResult.Success("Mock connection ready")
}

private fun String.isHttpUrl(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}

private fun String.containsSecretMaterial(): Boolean {
    if (isBlank()) return false
    val secretPatterns = listOf(
        Regex("""sk-[A-Za-z0-9_-]{3,}""", RegexOption.IGNORE_CASE),
        Regex("""api[_ -]?key""", RegexOption.IGNORE_CASE),
        Regex("""secret""", RegexOption.IGNORE_CASE)
    )
    return secretPatterns.any { it.containsMatchIn(this) }
}
