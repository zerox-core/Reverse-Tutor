package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.MessageAttachment

@JvmInline
value class LlmGenerationToken(val value: String)

data class LlmGenerationRequest(
    val sessionId: String,
    val userMessageId: String? = null,
    val userText: String? = null,
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
    val contextEvidence: List<LlmContextEvidence> = emptyList(),
    val sessionPolicy: LlmSessionPolicyContext? = null,
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null
)

/**
 * Optional, wire-only teaching strategy supplied by the application layer.
 * This type deliberately contains no `core:domain` dependency, so the LLM
 * planner can consume it without reversing the module dependency direction.
 */
data class LlmSessionPolicyContext(
    val actionType: String,
    val studentRole: String,
    val knowledgePoint: String,
    val difficulty: Float,
    val processSummary: String,
    val evaluationCorrectness: Float = 0f,
    val userEmotion: String = "neutral",
    val correctionTiming: String = "immediate"
) {
    fun normalized(): LlmSessionPolicyContext? {
        val normalizedAction = actionType.trim().lowercase().take(48)
        val normalizedRole = studentRole.trim().lowercase().take(64)
        if (normalizedAction.isEmpty() || normalizedRole.isEmpty()) return null
        return copy(
            actionType = normalizedAction,
            studentRole = normalizedRole,
            knowledgePoint = knowledgePoint.trim().ifEmpty { "Current method" }.take(120),
            difficulty = difficulty.coerceIn(0f, 1f),
            processSummary = processSummary.trim().ifEmpty { "Teaching turn" }.take(320),
            evaluationCorrectness = evaluationCorrectness.coerceIn(0f, 1f),
            userEmotion = userEmotion.trim().lowercase().ifEmpty { "neutral" }.take(48),
            correctionTiming = correctionTiming.trim().lowercase().ifEmpty { "immediate" }.take(48)
        )
    }
}

/** Immutable window/topology context snapshot carried into generation (P3). */
data class LlmWindowContext(
    val windowId: String,
    val rootId: String,
    val parentId: String? = null,
    val windowKind: String = "",
    val forkRevision: Long = 0L
) {
    fun normalized(): LlmWindowContext? {
        val id = windowId.trim()
        val root = rootId.trim()
        if (id.isEmpty() || root.isEmpty()) return null
        return copy(
            windowId = id,
            rootId = root,
            parentId = parentId?.trim()?.ifEmpty { null },
            windowKind = windowKind.trim().take(32),
            forkRevision = forkRevision
        )
    }
}

/** Bounded turn plan snapshot carried into generation (P3). Not a provider request. */
data class LlmTurnPlan(
    val intent: String = "",
    val actionType: String = "",
    val studentRole: String = "",
    val knowledgePoint: String = "",
    val difficulty: Float = 0.5f,
    val studyMethod: String = "",
    val toneConstraints: List<String> = emptyList(),
    val expiryEpochMillis: Long = 0L,
    val minCooldownMillis: Long = 0L
) {
    fun normalized(): LlmTurnPlan? {
        val ni = intent.trim().take(96)
        val na = actionType.trim().lowercase().take(48)
        val nr = studentRole.trim().lowercase().take(64)
        if (ni.isEmpty() || na.isEmpty() || nr.isEmpty()) return null
        return copy(
            intent = ni,
            actionType = na,
            studentRole = nr,
            knowledgePoint = knowledgePoint.trim().take(120),
            difficulty = difficulty.coerceIn(0f, 1f),
            studyMethod = studyMethod.trim().take(64),
            toneConstraints = toneConstraints.take(6).map { it.trim().take(160) }.filter { it.isNotEmpty() },
            expiryEpochMillis = expiryEpochMillis.coerceAtLeast(0L),
            minCooldownMillis = minCooldownMillis.coerceAtLeast(0L)
        )
    }
}

/** Immutable assistant-turn envelope (P3). Missing or malformed -> null (plain path). */
data class LlmAssistantTurnEnvelope(
    val window: LlmWindowContext,
    val turnPlan: LlmTurnPlan? = null,
    val initiativeSource: String? = null
) {
    fun normalized(): LlmAssistantTurnEnvelope? {
        val normalizedWindow = window.normalized() ?: return null
        return copy(
            window = normalizedWindow,
            turnPlan = turnPlan?.normalized(),
            initiativeSource = initiativeSource?.trim()?.ifEmpty { null }
        )
    }
}

/**
 * Validated bounded structured turn outcome (P3). It never carries raw
 * conversation or Provider text; a malformed envelope yields [StructuredTurnOutcome.EMPTY].
 */
data class StructuredTurnOutcome(
    val windowId: String? = null,
    val actionType: String = "",
    val studentRole: String = "",
    val knowledgePoint: String = "",
    val correctness: Float = 0f,
    val depth: Float = 0f,
    val evidenceType: String = "none",
    val evidenceStatus: String = "none",
    val processSummary: String = "",
    val initiativeSource: String? = null
) {
    companion object {
        val EMPTY = StructuredTurnOutcome()
    }

    fun normalized(): StructuredTurnOutcome = copy(
        correctness = correctness.coerceIn(0f, 1f),
        depth = depth.coerceIn(0f, 1f),
        evidenceType = evidenceType.trim().lowercase().take(32).ifEmpty { "none" },
        evidenceStatus = evidenceStatus.trim().lowercase().take(24).ifEmpty { "none" },
        actionType = actionType.trim().take(48),
        studentRole = studentRole.trim().take(64),
        knowledgePoint = knowledgePoint.trim().take(120),
        processSummary = processSummary.trim().take(320)
    )
}

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
        userMessageId: String?,
        userText: String?,
        profile: LlmProfile?,
        capabilities: LlmCapabilities,
        token: LlmGenerationToken,
        quoteExcerpt: String? = null,
        imageAttachments: List<MessageAttachment> = emptyList(),
        contextEvidence: List<LlmContextEvidence> = emptyList(),
        sessionPolicy: LlmSessionPolicyContext? = null,
        assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
        allowPlanDrivenOpening: Boolean = false
    ): LlmGenerationPlan {
        val normalizedText = userText.orEmpty().trim()
        val normalizedImageAttachments = imageAttachments.filter { it.isImageAttachment() }
        val normalizedEvidence = contextEvidence.mapNotNull { it.normalized() }.take(MaxContextEvidence)
        val normalizedSessionPolicy = sessionPolicy?.normalized()
        val normalizedEnvelope = assistantTurnEnvelope?.normalized()
        // A plan-driven opening (P3 appendix A) needs no user text: the immutable
        // envelope + TurnPlan drive generation. Never fabricate a placeholder user text.
        val planDrivenOpening = allowPlanDrivenOpening && normalizedEnvelope?.turnPlan != null
        if (profile == null) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.NoModelConfigured)
        }
        if (normalizedText.isEmpty() && normalizedImageAttachments.isEmpty() && !planDrivenOpening) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.BlankPrompt)
        }
        if (normalizedImageAttachments.isNotEmpty() && !capabilities.supportsVision) {
            return LlmGenerationPlan.Blocked(LlmGenerationBlockReason.UnsupportedVision)
        }

        return LlmGenerationPlan.Ready(
            LlmGenerationRequest(
                sessionId = sessionId,
                userMessageId = userMessageId?.trim()?.ifEmpty { null },
                userText = when {
                    normalizedText.isNotEmpty() -> normalizedText
                    planDrivenOpening -> null
                    else -> DefaultImagePrompt
                },
                profileId = profile.id,
                provider = profile.provider,
                model = profile.model,
                baseUrl = profile.baseUrl,
                capabilities = capabilities,
                token = token,
                secretRef = profile.secretRef,
                quoteExcerpt = quoteExcerpt?.trim()?.ifEmpty { null },
                imageAttachments = normalizedImageAttachments,
                contextEvidence = normalizedEvidence,
                sessionPolicy = normalizedSessionPolicy,
                assistantTurnEnvelope = normalizedEnvelope
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
    suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult
}

class FakeLlmGenerationRuntime(
    private val outcomes: Map<LlmGenerationToken, LlmGenerationResult> = emptyMap(),
    private val defaultResult: LlmGenerationResult = LlmGenerationResult.Success("Mock generation ready")
) : LlmGenerationRuntime {
    var realProviderCallCount: Int = 0
        private set

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult =
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

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult =
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

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult =
        LlmGenerationResult.Failure("Live Anthropic-compatible calls are disabled in preview")
}

private fun LlmGenerationRequest.contextualUserText(): String {
    val contextLines = buildList {
        turnPlanPromptBlock()?.let { add(it) }
        sessionPolicyPromptBlock()?.let { add(it) }
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
    if (contextLines.isEmpty()) return userText.orEmpty()
    return (contextLines + listOfNotNull(userText?.takeIf { it.isNotBlank() }))
        .joinToString(separator = "\n\n")
}

private fun LlmGenerationRequest.turnPlanPromptBlock(): String? =
    assistantTurnEnvelope?.turnPlan?.normalized()?.let { plan ->
        buildString {
            append("Initiative plan:")
            append("\nIntent: ").append(plan.intent)
            append("\nAction: ").append(plan.actionType)
            append("\nStudent role: ").append(plan.studentRole)
            append("\nKnowledge point: ").append(plan.knowledgePoint.ifBlank { "Current context" })
            append("\nDifficulty: ").append(plan.difficulty)
            if (plan.studyMethod.isNotBlank()) append("\nStudy method: ").append(plan.studyMethod)
            if (plan.toneConstraints.isNotEmpty()) {
                append("\nTone constraints: ").append(plan.toneConstraints.joinToString(", "))
            }
        }
    }

internal fun LlmGenerationRequest.sessionPolicyPromptBlock(): String? =
    sessionPolicy?.normalized()?.let { policy ->
        buildString {
            append("Teaching policy:")
            append("\nAction: ").append(policy.actionType)
            append("\nStudent role: ").append(policy.studentRole)
            append("\nKnowledge point: ").append(policy.knowledgePoint)
            append("\nDifficulty: ").append(policy.difficulty)
            append("\nEvaluation correctness: ").append(policy.evaluationCorrectness)
            append("\nLearner emotion: ").append(policy.userEmotion)
            append("\nCorrection timing: ").append(policy.correctionTiming)
            append("\nTurn intent: ").append(policy.processSummary)
        }
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
