package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.MessageAttachment

@JvmInline
value class LlmGenerationToken(val value: String)

data class LlmGenerationRequest(
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val profileId: String,
    val provider: LlmProviderKind,
    val model: String,
    val baseUrl: String?,
    val capabilities: LlmCapabilities,
    val token: LlmGenerationToken,
    val secretRef: String? = null,
    val streaming: Boolean = true,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList()
)

data class LlmContextEvidence(
    val id: String,
    val title: String,
    val body: String,
    val kind: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
) {
    fun normalized(): LlmContextEvidence? {
        val normalizedId = id.trim()
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        if (normalizedId.isEmpty() || normalizedTitle.isEmpty() || normalizedBody.isEmpty()) return null
        return copy(
            id = normalizedId,
            title = normalizedTitle,
            body = normalizedBody,
            kind = kind.trim().ifEmpty { "Evidence" },
            sourceMessageId = sourceMessageId?.trim()?.ifEmpty { null },
            sourceId = sourceId?.trim()?.ifEmpty { null }
        )
    }
}

sealed interface LlmGenerationPlan {
    data class Ready(val request: LlmGenerationRequest) : LlmGenerationPlan
    data class Blocked(val reason: LlmGenerationBlockReason) : LlmGenerationPlan
}

enum class LlmGenerationBlockReason {
    NoModelConfigured,
    UnsupportedVision,
    BlankPrompt
}

object LlmGenerationPlanner {
    fun plan(
        sessionId: String,
        userMessageId: String,
        userText: String,
        profile: LlmProfile?,
        capabilities: LlmCapabilities,
        token: LlmGenerationToken,
        quoteExcerpt: String? = null,
        imageAttachments: List<MessageAttachment> = emptyList(),
        contextEvidence: List<LlmContextEvidence> = emptyList()
    ): LlmGenerationPlan {
        val normalizedText = userText.trim()
        val normalizedImageAttachments = imageAttachments.filter { it.isImageAttachment() }
        val normalizedEvidence = contextEvidence.mapNotNull { it.normalized() }.take(MaxContextEvidence)
        if (profile == null) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.NoModelConfigured)
        }
        if (normalizedText.isEmpty() && normalizedImageAttachments.isEmpty()) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.BlankPrompt)
        }
        if (normalizedImageAttachments.isNotEmpty() && !capabilities.supportsVision) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.UnsupportedVision)
        }

        return LlmGenerationPlan.Ready(
            LlmGenerationRequest(
                sessionId = sessionId,
                userMessageId = userMessageId,
                userText = normalizedText.ifEmpty { DefaultImagePrompt },
                profileId = profile.id,
                provider = profile.provider,
                model = profile.model,
                baseUrl = profile.baseUrl,
                capabilities = capabilities,
                token = token,
                secretRef = profile.secretRef,
                quoteExcerpt = quoteExcerpt?.trim()?.ifEmpty { null },
                imageAttachments = normalizedImageAttachments,
                contextEvidence = normalizedEvidence
            )
        )
    }
}

private const val DefaultImagePrompt = "Describe the attached image."
private const val MaxContextEvidence = 6

sealed interface LlmGenerationResult {
    val visibleText: String

    data class Success(val text: String) : LlmGenerationResult {
        override val visibleText: String = text.trim()
    }

    data class Streamed(val chunks: List<String>) : LlmGenerationResult {
        override val visibleText: String = chunks.joinToString(separator = "").trim()
    }

    data class Failure(
        val message: String,
        val retryable: Boolean = true
    ) : LlmGenerationResult {
        override val visibleText: String = ""
    }

    object Timeout : LlmGenerationResult {
        override val visibleText: String = ""
        const val message: String = "Timeout"
    }
}

interface LlmGenerationRuntime {
    fun generate(request: LlmGenerationRequest): LlmGenerationResult
}

class FakeLlmGenerationRuntime(
    private val outcomes: Map<LlmGenerationToken, LlmGenerationResult> = emptyMap(),
    private val defaultResult: LlmGenerationResult = LlmGenerationResult.Success("Mock generation ready")
) : LlmGenerationRuntime {
    var realProviderCallCount: Int = 0
        private set

    override fun generate(request: LlmGenerationRequest): LlmGenerationResult =
        outcomes[request.token] ?: defaultResult
}

enum class LlmProviderProtocol {
    OpenAiCompatible,
    AnthropicCompatible,
    GeminiNative
}

data class LlmProviderPayload(
    val protocol: LlmProviderProtocol,
    val endpoint: String,
    val body: Map<String, Any?>
)

class OpenAiCompatibleGenerationRuntime : LlmGenerationRuntime {
    var realProviderCallCount: Int = 0
        private set

    fun buildPayload(request: LlmGenerationRequest): LlmProviderPayload =
        LlmProviderPayload(
            protocol = LlmProviderProtocol.OpenAiCompatible,
            endpoint = request.baseUrl.orEmpty().trimEnd('/') + "/chat/completions",
            body = mapOf(
                "model" to request.model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to request.openAiUserContent())
                ),
                "stream" to request.streaming
            )
        )

    override fun generate(request: LlmGenerationRequest): LlmGenerationResult =
        LlmGenerationResult.Failure("Live OpenAI-compatible calls are disabled in preview")
}

class AnthropicCompatibleGenerationRuntime : LlmGenerationRuntime {
    var realProviderCallCount: Int = 0
        private set

    fun buildPayload(request: LlmGenerationRequest): LlmProviderPayload =
        LlmProviderPayload(
            protocol = LlmProviderProtocol.AnthropicCompatible,
            endpoint = request.baseUrl.orEmpty().trimEnd('/') + "/messages",
            body = mapOf(
                "model" to request.model,
                "max_tokens" to 2048,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to request.anthropicUserContent())
                ),
                "stream" to request.streaming
            )
        )

    override fun generate(request: LlmGenerationRequest): LlmGenerationResult =
        LlmGenerationResult.Failure("Live Anthropic-compatible calls are disabled in preview")
}

private fun LlmGenerationRequest.contextualUserText(): String {
    val contextLines = buildList {
        if (!quoteExcerpt.isNullOrBlank()) {
            add("Quote: $quoteExcerpt")
        }
        if (contextEvidence.isNotEmpty()) {
            add(
                buildString {
                    append("Context evidence:")
                    contextEvidence.forEachIndexed { index, evidence ->
                        append("\n[")
                        append(index + 1)
                        append("] ")
                        append(evidence.kind)
                        append(" - ")
                        append(evidence.title)
                        append(": ")
                        append(evidence.body)
                    }
                }
            )
        }
    }
    if (contextLines.isEmpty()) return userText
    return (contextLines + userText).joinToString(separator = "\n\n")
}

private fun LlmGenerationRequest.openAiUserContent(): Any =
    if (imageAttachments.isEmpty()) {
        contextualUserText()
    } else {
        buildList<Map<String, Any?>> {
            add(mapOf("type" to "text", "text" to contextualUserText()))
            imageAttachments.forEach { attachment ->
                add(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to attachment.uri.orEmpty(),
                            "detail" to "auto"
                        ),
                        "metadata" to attachment.toPayloadMetadata()
                    )
                )
            }
        }
    }

private fun LlmGenerationRequest.anthropicUserContent(): Any =
    if (imageAttachments.isEmpty()) {
        contextualUserText()
    } else {
        buildList<Map<String, Any?>> {
            add(mapOf("type" to "text", "text" to contextualUserText()))
            imageAttachments.forEach { attachment ->
                add(
                    mapOf(
                        "type" to "image",
                        "source" to mapOf(
                            "type" to "base64",
                            "media_type" to (attachment.mimeType ?: "image/*"),
                            "data" to attachment.uri.orEmpty()
                        ),
                        "metadata" to attachment.toPayloadMetadata()
                    )
                )
            }
        }
    }

private fun MessageAttachment.isImageAttachment(): Boolean =
    mimeType?.startsWith("image/") == true || uri?.startsWith("content://") == true

private fun MessageAttachment.toPayloadMetadata(): Map<String, String?> =
    mapOf(
        "id" to id,
        "name" to name,
        "mimeType" to mimeType,
        "uri" to uri,
        "sourceId" to sourceId
    )
