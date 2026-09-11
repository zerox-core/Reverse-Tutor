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
    /** Provider-ready image bytes, resolved only at execution time. */
    val resolvedImages: List<LlmResolvedImage> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList(),
    val sessionPolicy: LlmSessionPolicyContext? = null,
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
    val guidedTurnPlan: LlmGuidedTurnPlan? = null,
    /** Process-local preview only; never persisted or sent to a provider. */
    val onStreamChunk: ((String) -> Unit)? = null
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
    val outcome: StructuredTurnOutcome = StructuredTurnOutcome.EMPTY,
    val checkPlan: LlmSourceGroundedCheckPlan? = null
)

/**
 * Provider-facing candidate for a source-grounded check. It is deliberately
 * wire-only; the application layer must map it through the domain policy
 * before it can influence a learning receipt.
 */
data class LlmSourceGroundedCheckPlan(
    val id: String,
    val sourceRevision: String,
    val sourceReferenceIds: List<String>,
    /**
     * NEWMP-V1-004 Task 1: optional explicit handle -> revision bindings. When
     * present it must cover every referenced handle; an empty map keeps the
     * legacy single-revision semantics for old candidates and payloads.
     */
    val sourceRevisions: Map<String, String> = emptyMap(),
    val prompt: String,
    val expectedAnswer: String,
    val rule: LlmSourceCheckRule,
    val conceptKey: String = ""
) {
    fun normalized(): LlmSourceGroundedCheckPlan? {
        val normalizedId = id.trim().take(80)
        val revision = sourceRevision.trim().take(120)
        val refs = sourceReferenceIds.map { it.trim().take(160) }
            .filter { it.isNotEmpty() }.distinct().take(6)
        val normalizedPrompt = prompt.trim().replace(Regex("\\s+"), " ").take(400)
        val expected = expectedAnswer.trim().replace(Regex("\\s+"), " ").take(200)
        val concept = conceptKey.trim().replace(Regex("\\s+"), " ").take(40)
        if (normalizedId.isEmpty() || revision.isEmpty() || refs.isEmpty() || normalizedPrompt.isEmpty()) return null
        if (listOf(normalizedId, revision, normalizedPrompt, expected, concept).any(::containsSensitive)) return null
        val normalizedRule = rule.normalized() ?: return null
        if (normalizedRule.fields().any(::containsSensitive)) return null
        val explicitRevisions = sourceRevisions
            .mapKeys { (handle, _) -> handle.trim().take(160) }
            .mapValues { (_, revision) -> revision.trim().take(120) }
        if (explicitRevisions.isNotEmpty() &&
            (explicitRevisions.values.any { it.isEmpty() || containsSensitive(it) } ||
                explicitRevisions.size != refs.size || refs.any { !explicitRevisions.containsKey(it) })
        ) {
            return null
        }
        return copy(
            id = normalizedId,
            sourceRevision = revision,
            sourceReferenceIds = refs,
            sourceRevisions = explicitRevisions,
            prompt = normalizedPrompt,
            expectedAnswer = expected,
            rule = normalizedRule,
            conceptKey = concept
        )
    }

    private companion object {
        val sensitive = listOf(
            Regex("(?i)sk-[a-z0-9_-]{2,}"),
            Regex("(?i)authorization\\s*[:=]"),
            Regex("(?i)bearer\\s+[a-z0-9._-]+"),
            Regex("(?i)https?://[^\\s]+")
        )
        fun containsSensitive(value: String): Boolean = sensitive.any { it.containsMatchIn(value) }
    }
}

sealed interface LlmSourceCheckRule {
    fun normalized(): LlmSourceCheckRule?
    fun fields(): List<String>

    data class ExactText(val normalizedAnswer: String) : LlmSourceCheckRule {
        override fun normalized(): LlmSourceCheckRule? = copy(normalizedAnswer = normalizedAnswer.trim().replace(Regex("\\s+"), " ").take(200))
            .takeIf { it.normalizedAnswer.isNotEmpty() }
        override fun fields(): List<String> = listOf(normalizedAnswer)
    }

    data class NumericTolerance(val expected: Double, val tolerance: Double) : LlmSourceCheckRule {
        override fun normalized(): LlmSourceCheckRule = copy(expected = expected.coerceIn(-1e9, 1e9), tolerance = tolerance.coerceIn(0.0, 1e6))
        override fun fields(): List<String> = emptyList()
    }

    data class RequiredConcepts(val terms: List<String>) : LlmSourceCheckRule {
        override fun normalized(): LlmSourceCheckRule? = copy(terms = terms.map { it.trim().replace(Regex("\\s+"), " ").take(48) }.filter { it.isNotEmpty() }.distinct().take(12))
            .takeIf { it.terms.isNotEmpty() }
        override fun fields(): List<String> = terms
    }

    data class Rubric(val criteria: List<String>) : LlmSourceCheckRule {
        override fun normalized(): LlmSourceCheckRule? = copy(criteria = criteria.map { it.trim().replace(Regex("\\s+"), " ").take(160) }.filter { it.isNotEmpty() }.distinct().take(8))
            .takeIf { it.criteria.isNotEmpty() }
        override fun fields(): List<String> = criteria
    }
}

const val MaxVisibleTimelineCharacters = 1_200

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
}.toVisibleTimelineText()

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

/**
 * Final boundary before assistant text becomes a persisted chat message. The
 * generation prompt may contain internal teaching controls, but those controls
 * never form part of the student-facing conversation. This deliberately keeps
 * Markdown-like prose and code intact while discarding only known control rows.
 */
fun String.toVisibleTimelineText(): String {
    if (looksLikeAssistantReplyEnvelopeJson()) return VisibleTimelineFallbackText
    return lineSequence()
        .filterNot { it.trim().matches(InternalVisibleControlLine) }
        .joinToString("\n")
        .trim()
        .take(MaxVisibleTimelineCharacters)
        .ifBlank { VisibleTimelineFallbackText }
}

internal const val VisibleTimelineFallbackText = "我还没整理好这一步，能再给我一点提示吗？"

/**
 * Detects machine-shaped assistant reply envelopes (raw JSON carrying the
 * internal blocks/outcome/checkPlan contract) before any of their contents can
 * reach the student-facing bubble. A whole-text shape check is intentional: it
 * only fires when the entire candidate text is a JSON object with envelope
 * keys, never for ordinary prose that merely mentions braces.
 */
private fun String.looksLikeAssistantReplyEnvelopeJson(): Boolean {
    val trimmed = trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
    return AssistantReplyEnvelopeJsonKeyHint.containsMatchIn(trimmed)
}

private val AssistantReplyEnvelopeJsonKeyHint =
    Regex("\"(blocks|version|outcome|checkPlan)\"\\s*:")

private val InternalVisibleControlLine = Regex(
    "(?i)^(teaching policy|guided learning plan|initiative plan|action|secondary action|student role|knowledge point|objective|expected teacher move|student expression|response format|hint level|evidence requirement|evaluation correctness|learner emotion|correction timing|turn intent|outcome|correctness|mastery|depth|evidence type|evidence status|process summary|checkplan|initiative source|window id)\\s*:\\s*.*$"
)

data class LlmResolvedImage(
    val mimeType: String,
    val base64Data: String
)

fun interface LlmImagePayloadResolver {
    suspend fun resolve(attachment: MessageAttachment): LlmResolvedImage?
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
        onStreamChunk: ((String) -> Unit)? = null,
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
                guidedTurnPlan = guidedTurnPlan?.normalized(),
                onStreamChunk = onStreamChunk
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

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        val result = outcomes[request.token] ?: defaultResult
        when (result) {
            is LlmGenerationResult.Streamed -> {
                result.chunks.forEach { chunk -> request.onStreamChunk?.invoke(chunk) }
            }
            is LlmGenerationResult.Success -> {
                if (result.text.isNotBlank()) request.onStreamChunk?.invoke(result.text)
            }
            else -> Unit
        }
        return result
    }
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
        reverseTutorStudentPromptBlock()?.let { add(it) }
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

/**
 * The teaching algorithm remains an internal decision layer. This boundary
 * turns its output into the product's public role: the assistant is always the
 * student and the user is the teacher. It is emitted only for turns that carry
 * a session policy or guided plan, so legacy unplanned generation is unchanged.
 */
internal fun LlmGenerationRequest.reverseTutorStudentPromptBlock(): String? =
    if (guidedTurnPlan?.normalized() == null && sessionPolicy?.normalized() == null) {
        null
    } else {
        """
            反转教学·学生表达契约：
            - 用户是老师，你是学生 AI：表面在向老师请教，实际是通过提问让老师把知识讲出来（教即是学）。
            - 全程学生口吻：自然口语、可以带一点情绪；角色、画像、目标与语气以证据里的「会话模板」为准。
            - 教学策略隐身：不宣布计划、不给老师打分、不切换成讲课腔，也不替老师给出完整权威解法。
            - 举例子时说成你自己的尝试：带具体数字或函数、最多 5 步、每步一句话、用「→」衔接，并请老师确认或纠错。
            - 本轮只做一个教学动作：最多三小段或四短行；关键词用 **加粗** 突出。
            - 最多问老师一个问题，问完就停，绝不自问自答。
        """.trimIndent()
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
                append("\nExpected teacher move: ").append(plan.expectedUserMove)
            }
            append("\nStudent expression: ")
                .append(LlmStudentExpressionPolicy.directiveFor(plan.actionType))
            append("\nResponse format: ").append(plan.responseFormat)
            append("\nHint level: ").append(plan.hintLevel)
            append("\nEvidence requirement: ").append(plan.evidenceRequirement)
            if (plan.evidenceRequirement == "local_check") {
                append("\nIf a source check is possible, include an optional checkPlan object in the JSON reply.")
                append(" It must reference only the supplied source evidence ids and use one rule: exact_text, numeric_tolerance, required_concepts, or rubric.")
                append(" Never include URLs, credentials, diagnostics, or mastery claims.")
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
            append("\n表达要求: ").append(LlmStudentExpressionPolicy.sessionPolicyDirectiveFor(policy.actionType))
        }
    }

private fun LlmGenerationRequest.openAiUserContent(): Any =
    if (imageAttachments.isEmpty()) {
        contextualUserText()
    } else {
        buildList<Map<String, Any?>> {
            add(mapOf("type" to "text", "text" to contextualUserText()))
            if (resolvedImages.isNotEmpty()) resolvedImages.forEach { image ->
                add(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:${image.mimeType};base64,${image.base64Data}",
                            "detail" to "auto"
                        )
                    )
                )
            } else imageAttachments.forEach { attachment ->
                add(mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf("url" to attachment.uri.orEmpty(), "detail" to "auto")
                ))
            }
        }
    }

private fun LlmGenerationRequest.anthropicUserContent(): Any =
    if (imageAttachments.isEmpty()) {
        contextualUserText()
    } else {
        buildList<Map<String, Any?>> {
            add(mapOf("type" to "text", "text" to contextualUserText()))
            if (resolvedImages.isNotEmpty()) resolvedImages.forEach { image ->
                add(
                    mapOf(
                        "type" to "image",
                        "source" to mapOf(
                            "type" to "base64",
                            "media_type" to image.mimeType,
                            "data" to image.base64Data
                        )
                    )
                )
            } else imageAttachments.forEach { attachment ->
                add(mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to (attachment.mimeType ?: "image/*"),
                        "data" to attachment.uri.orEmpty()
                    )
                ))
            }
        }
    }


private fun MessageAttachment.isImageAttachment(): Boolean =
    mimeType?.startsWith("image/") == true || uri?.startsWith("content://") == true
