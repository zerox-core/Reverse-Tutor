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
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
    val guidedTurnPlan: LlmGuidedTurnPlan? = null
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

/**
 * Wire-only bounded guided-learning teaching plan (NEWMP-V1-002 Task 2.4).
 *
 * Like [LlmSessionPolicyContext] it deliberately has no `core:domain`
 * dependency: the application layer maps the domain `TurnPlan` into this
 * snapshot before generation. It constrains *expression only* — action
 * vocabulary, objective, expected learner move, format and hint depth. It can
 * never carry mastery, evidence verdicts or learning-fact mutations, and an
 * unknown action rejects the whole snapshot so nothing model-suggested slips
 * into the prompt. [normalized] bounds text and strips secret-like content.
 */
data class LlmGuidedTurnPlan(
    val actionType: String,
    val secondaryAction: String = "",
    val learningObjective: String = "",
    val conceptKey: String = "",
    val expectedUserMove: String = "",
    val responseFormat: String = "plain",
    val hintLevel: Int = 0,
    val evidenceRequirement: String = "none"
) {
    companion object {
        val AllowedActions = setOf(
            "diagnose", "socratic_question", "hint", "explain", "worked_example",
            "counter_example", "practice", "reflect", "summarize", "clarify_goal"
        )
        val AllowedFormats = setOf("plain", "steps", "code", "table", "checklist")
        val AllowedEvidence = setOf("none", "local_check", "user_answer", "tool_receipt")
        const val OBJECTIVE_MAX = 120
        const val EXPECTED_MOVE_MAX = 160

        private val sensitivePatterns = listOf(
            Regex("(?i)sk-[a-z0-9_-]{2,}"),
            Regex("(?i)authorization\\s*[:=]\\s*\\S*"),
            Regex("(?i)bearer\\s+[a-z0-9._-]+"),
            Regex("(?i)https?://[^\\s]+")
        )

        internal fun boundText(value: String?, maxLength: Int): String {
            var text = (value ?: "").trim()
            sensitivePatterns.forEach { pattern -> text = text.replace(pattern, "[redacted]") }
            return text.replace(Regex("\\s+"), " ").trim().take(maxLength)
        }
    }

    fun normalized(): LlmGuidedTurnPlan? {
        val action = actionType.trim().lowercase()
        if (action !in AllowedActions) return null
        val secondary = secondaryAction.trim().lowercase()
        return copy(
            actionType = action,
            secondaryAction = secondary.takeIf { it in AllowedActions && it != action } ?: "",
            learningObjective = boundText(learningObjective, OBJECTIVE_MAX),
            conceptKey = boundText(conceptKey, 40).ifEmpty { "unknown" },
            expectedUserMove = boundText(expectedUserMove, EXPECTED_MOVE_MAX),
            responseFormat = responseFormat.trim().lowercase().takeIf { it in AllowedFormats } ?: "plain",
            hintLevel = hintLevel.coerceIn(0, 3),
            evidenceRequirement = evidenceRequirement.trim().lowercase().takeIf { it in AllowedEvidence } ?: "none"
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

sealed interface LlmRichContentBlock {
    data class Heading(val level: Int, val text: String) : LlmRichContentBlock
    data class Paragraph(val text: String) : LlmRichContentBlock
    data class BulletList(val items: List<String>) : LlmRichContentBlock
    data class NumberedList(val items: List<String>) : LlmRichContentBlock
    data class CodeBlock(val language: String?, val code: String) : LlmRichContentBlock
    data class Callout(val kind: String, val text: String) : LlmRichContentBlock
    data class SimpleTable(val columns: List<String>, val rows: List<List<String>>) : LlmRichContentBlock
}

data class LlmToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String
)

/**
 * Validated user-visible reply structure. References are opaque handles only;
 * tool calls remain unexecuted until the session-scoped registry authorizes
 * them. This contract never carries retrieval bodies or Provider diagnostics.
 */
data class LlmAssistantReplyEnvelope(
    val blocks: List<LlmRichContentBlock>,
    val evidenceReferenceIds: List<String> = emptyList(),
    val toolCalls: List<LlmToolCall> = emptyList(),
    val outcome: StructuredTurnOutcome = StructuredTurnOutcome.EMPTY
)

fun LlmAssistantReplyEnvelope.timelineText(): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is LlmRichContentBlock.Heading -> block.text
        is LlmRichContentBlock.Paragraph -> block.text
        is LlmRichContentBlock.BulletList -> block.items.joinToString("\n") { "• $it" }
        is LlmRichContentBlock.NumberedList -> block.items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
        is LlmRichContentBlock.CodeBlock -> block.code
        is LlmRichContentBlock.Callout -> block.text
        is LlmRichContentBlock.SimpleTable -> buildString {
            append(block.columns.joinToString(" | "))
            block.rows.forEach { row -> append("\n").append(row.joinToString(" | ")) }
        }
    }
}.trim()

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
        guidedTurnPlan: LlmGuidedTurnPlan? = null,
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
                assistantTurnEnvelope = normalizedEnvelope,
                guidedTurnPlan = guidedTurnPlan?.normalized()
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
        guidedLearningPlanPromptBlock()?.let { add(it) }
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

internal fun LlmGenerationRequest.guidedLearningPlanPromptBlock(): String? =
    guidedTurnPlan?.normalized()?.let { plan ->
        buildString {
            append("Guided learning plan:")
            append("\nAction: ").append(plan.actionType)
            if (plan.secondaryAction.isNotBlank()) {
                append("\nSecondary action: ").append(plan.secondaryAction)
            }
            append("\nKnowledge point: ").append(plan.conceptKey)
            if (plan.learningObjective.isNotBlank()) {
                append("\nObjective: ").append(plan.learningObjective)
            }
            if (plan.expectedUserMove.isNotBlank()) {
                append("\nExpected learner move: ").append(plan.expectedUserMove)
            }
            append("\nResponse format: ").append(plan.responseFormat)
            append("\nHint level: ").append(plan.hintLevel)
            append("\nEvidence requirement: ").append(plan.evidenceRequirement)
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
