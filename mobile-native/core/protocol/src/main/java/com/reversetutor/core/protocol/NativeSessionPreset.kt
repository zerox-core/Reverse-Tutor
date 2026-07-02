package com.reversetutor.core.protocol

data class NativeSessionPreset(
    val title: String,
    val role: String,
    val goal: String,
    val profile: String,
    val sourceHandoff: String = NativeSessionPresetValidator.deferredSourceHandoff
)

data class PresetValidationResult(
    val preset: NativeSessionPreset?,
    val errors: List<String>,
    val warnings: List<String> = emptyList()
) {
    val isValid: Boolean = preset != null && errors.isEmpty()
}

object NativeSessionPresetValidator {
    const val deferredSourceHandoff = "deferred"

    private val stringPairPattern = Regex(
        "\"([^\"\\\\]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
        RegexOption.DOT_MATCHES_ALL
    )
    private val keyNamePattern = Regex("\"([^\"\\\\]+)\"\\s*:")
    private val secretValuePatterns = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{3,}"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"),
        Regex("(?i)(api[_-]?key|secret|token|password)\\s*[:=]")
    )
    private val requiredFields = listOf("title", "role", "goal", "profile")

    fun validate(json: String): PresetValidationResult {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return PresetValidationResult(
                preset = null,
                errors = listOf("Preset JSON must be an object.")
            )
        }

        val fields = stringPairPattern.findAll(trimmed)
            .associate { match ->
                match.groupValues[1] to match.groupValues[2].unescapeJsonString()
            }
        val errors = mutableListOf<String>()

        val schema = fields["schema"]
        if (schema != ProtocolModule.presetSchema) {
            errors += "Preset schema must be ${ProtocolModule.presetSchema}."
        }

        keyNamePattern.findAll(trimmed).forEach { match ->
            if (match.groupValues[1].looksLikeSecretKey()) {
                errors += "Preset JSON must not include secret or key material."
            }
        }

        fields.forEach { (_, value) ->
            if (value.looksLikeSecretValue()) {
                errors += "Preset JSON must not include secret or key material."
            }
        }

        val missing = requiredFields.filter { fields[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            errors += "Missing required preset fields: ${missing.joinToString(", ")}."
        }

        if (errors.isNotEmpty()) {
            return PresetValidationResult(preset = null, errors = errors.distinct())
        }

        return PresetValidationResult(
            preset = NativeSessionPreset(
                title = fields.getValue("title").trim(),
                role = fields.getValue("role").trim(),
                goal = fields.getValue("goal").trim(),
                profile = fields.getValue("profile").trim(),
                sourceHandoff = fields["sourceHandoff"]?.trim()?.ifEmpty { deferredSourceHandoff }
                    ?: deferredSourceHandoff
            ),
            errors = emptyList()
        )
    }

    private fun String.looksLikeSecretKey(): Boolean {
        val normalized = lowercase().replace("-", "").replace("_", "")
        return normalized == "key" ||
            normalized.endsWith("key") ||
            normalized.contains("secret") ||
            normalized.contains("token") ||
            normalized.contains("password") ||
            normalized.contains("credential") ||
            normalized.contains("authorization")
    }

    private fun String.looksLikeSecretValue(): Boolean =
        secretValuePatterns.any { pattern -> pattern.containsMatchIn(this) }

    private fun String.unescapeJsonString(): String =
        replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
}
