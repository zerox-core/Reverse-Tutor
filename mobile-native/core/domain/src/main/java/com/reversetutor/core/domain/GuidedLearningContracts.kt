package com.reversetutor.core.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Guided-learning turn contracts (NEWMP-V1-002 Task 2.1).
 *
 * Pure Kotlin decision-kernel value types shared by the intent reader, the
 * [TeachingActionSelector] and the downstream plan builder. They deliberately
 * avoid any platform, storage or generation-runtime dependency — the model
 * only ever *expresses* a plan; these types are what the domain algorithm
 * reads and produces.
 *
 * Every collection/text field is bounded and safe-normalized through
 * [normalized] so an untrusted upstream value can never carry an oversized
 * string or a secret-like fragment into a turn plan.
 */

enum class UserIntent {
    AnswerAttempt,
    AskQuestion,
    AskHint,
    AskExample,
    Reflect,
    GoalChange,
    OffTopic,
    ToolRequest,
    Ambiguous
}

enum class ConceptStatus {
    Unknown,
    Exploring,
    Fragile,
    Stable,
    Mastered
}

enum class TeachingAction {
    Diagnose,
    SocraticQuestion,
    Hint,
    Explain,
    WorkedExample,
    CounterExample,
    Practice,
    Reflect,
    Summarize,
    ClarifyGoal
}

enum class ResponseFormat {
    Plain,
    Steps,
    Code,
    Table,
    Checklist
}

enum class EvidenceRequirement {
    None,
    LocalCheck,
    UserAnswer,
    ToolReceipt
}

/**
 * Bounded per-concept learning state. Only evidence-driven transitions (handled
 * downstream by the verifier / ledger projector) may move [status]; the selector
 * reads this snapshot, it never writes it.
 */
data class ConceptLearningState(
    val status: ConceptStatus = ConceptStatus.Unknown,
    val confidence: Float = 0f,
    val attemptCount: Int = 0,
    val successStreak: Int = 0,
    val failureStreak: Int = 0,
    val misconceptionTags: List<String> = emptyList(),
    val lastAction: TeachingAction? = null
) {
    fun normalized(): ConceptLearningState = ConceptLearningState(
        status = status,
        confidence = GuidedLearningContracts.clampConfidence(confidence),
        attemptCount = max(0, attemptCount),
        successStreak = max(0, successStreak),
        failureStreak = max(0, failureStreak),
        misconceptionTags = misconceptionTags.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { it.take(GuidedLearningContracts.MISCONCEPTION_TAG_LEN) }
            .take(GuidedLearningContracts.MISCONCEPTION_TAG_MAX)
            .toList(),
        lastAction = lastAction
    )
}

/**
 * The bounded, deterministic outcome of a single turn decision. This is what the
 * domain hands to the LLM as structured context; the LLM cannot add or widen
 * these fields. [normalized] enforces text caps, the `0..3` hint range, and the
 * "at most one, never-equals-primary secondary action" rule.
 */
data class TurnPlan(
    val actionType: TeachingAction,
    val secondaryAction: TeachingAction? = null,
    val learningObjective: String = "",
    val conceptKey: String = GuidedLearningContracts.UNKNOWN_CONCEPT,
    val expectedUserMove: String = "",
    val responseFormat: ResponseFormat = ResponseFormat.Plain,
    val hintLevel: Int = GuidedLearningContracts.HINT_LEVEL_MIN,
    val evidenceRequirement: EvidenceRequirement = EvidenceRequirement.None,
    val nextActionOnSuccess: TeachingAction? = null,
    val nextActionOnFailure: TeachingAction? = null
) {
    fun normalized(): TurnPlan = TurnPlan(
        actionType = actionType,
        secondaryAction = secondaryAction?.takeIf { it != actionType },
        learningObjective = GuidedLearningContracts.boundText(
            learningObjective, GuidedLearningContracts.OBJECTIVE_MAX
        ),
        conceptKey = GuidedLearningContracts.normalizeConceptKey(conceptKey),
        expectedUserMove = GuidedLearningContracts.boundText(
            expectedUserMove, GuidedLearningContracts.EXPECTED_MOVE_MAX
        ),
        responseFormat = responseFormat,
        hintLevel = GuidedLearningContracts.clampHintLevel(hintLevel),
        evidenceRequirement = evidenceRequirement,
        nextActionOnSuccess = nextActionOnSuccess,
        nextActionOnFailure = nextActionOnFailure
    )
}

/** A bounded copy of the template facts that are relevant to one learning turn. */
data class SessionTemplateSnapshot(
    val subject: String = "",
    val learningGoal: String = "",
    val learnerRole: String = "",
    val dialogueStrategy: String = "",
    val correctionTiming: String = "",
    val preferredResponseFormat: String = ""
) {
    fun normalized(): SessionTemplateSnapshot = copy(
        subject = GuidedLearningContracts.boundText(subject, GuidedLearningContracts.TEMPLATE_TEXT_MAX),
        learningGoal = GuidedLearningContracts.boundText(learningGoal, GuidedLearningContracts.TEMPLATE_TEXT_MAX),
        learnerRole = GuidedLearningContracts.boundText(learnerRole, GuidedLearningContracts.TEMPLATE_TEXT_MAX),
        dialogueStrategy = GuidedLearningContracts.boundText(dialogueStrategy, GuidedLearningContracts.TEMPLATE_TEXT_MAX),
        correctionTiming = GuidedLearningContracts.boundText(correctionTiming, GuidedLearningContracts.TEMPLATE_TEXT_MAX),
        preferredResponseFormat = GuidedLearningContracts.boundText(
            preferredResponseFormat, GuidedLearningContracts.TEMPLATE_TEXT_MAX
        )
    )
}

/**
 * Privacy-safe observation of the current window. Message identity is enough
 * for correlation; transcript text is intentionally not part of this value.
 */
data class VisibleLearningContext(
    val visibleMessageIds: List<String> = emptyList(),
    val recentUserIntent: UserIntent = UserIntent.Ambiguous,
    val currentConceptKeys: List<String> = emptyList(),
    val unresolvedQuestionCount: Int = 0,
    val latestAssistantAction: TeachingAction? = null
) {
    fun normalized(): VisibleLearningContext = copy(
        visibleMessageIds = GuidedLearningContracts.boundedDistinctText(
            visibleMessageIds,
            GuidedLearningContracts.VISIBLE_MESSAGE_IDS_MAX,
            GuidedLearningContracts.ID_MAX
        ),
        currentConceptKeys = currentConceptKeys.asSequence()
            .map(GuidedLearningContracts::normalizeConceptKey)
            .filter { it != GuidedLearningContracts.UNKNOWN_CONCEPT }
            .distinct()
            .take(GuidedLearningContracts.VISIBLE_CONCEPT_KEYS_MAX)
            .toList(),
        unresolvedQuestionCount = max(0, unresolvedQuestionCount)
    )
}

/** Bounded, structured learner preferences and known capability summaries. */
data class LearnerProfileSnapshot(
    val declaredLevel: String = "",
    val preferredPace: String = "",
    val preferredTone: String = "",
    val knownStrengths: List<String> = emptyList(),
    val knownGaps: List<String> = emptyList()
) {
    fun normalized(): LearnerProfileSnapshot = copy(
        declaredLevel = GuidedLearningContracts.boundText(declaredLevel, GuidedLearningContracts.PROFILE_TEXT_MAX),
        preferredPace = GuidedLearningContracts.boundText(preferredPace, GuidedLearningContracts.PROFILE_TEXT_MAX),
        preferredTone = GuidedLearningContracts.boundText(preferredTone, GuidedLearningContracts.PROFILE_TEXT_MAX),
        knownStrengths = GuidedLearningContracts.boundedDistinctText(
            knownStrengths,
            GuidedLearningContracts.PROFILE_FACTS_MAX,
            GuidedLearningContracts.PROFILE_FACT_TEXT_MAX
        ),
        knownGaps = GuidedLearningContracts.boundedDistinctText(
            knownGaps,
            GuidedLearningContracts.PROFILE_FACTS_MAX,
            GuidedLearningContracts.PROFILE_FACT_TEXT_MAX
        )
    )
}

/** The single authoritative recent-turn signal set consumed by the selector. */
data class RecentTurnSignals(
    val lastIntent: UserIntent = UserIntent.Ambiguous,
    val lastAction: TeachingAction? = null,
    val answeredLastPrompt: Boolean = false,
    val askedForHintCount: Int = 0,
    val consecutiveSuccessCount: Int = 0,
    val consecutiveFailureCount: Int = 0,
    val lastEvidenceAvailable: Boolean = false
) {
    fun normalized(): RecentTurnSignals = copy(
        askedForHintCount = max(0, askedForHintCount),
        consecutiveSuccessCount = max(0, consecutiveSuccessCount),
        consecutiveFailureCount = max(0, consecutiveFailureCount)
    )
}

/**
 * The complete, safe input to one guided-learning turn. The selector consumes
 * this and produces a [TurnPlan]. `userIntentHint` is advisory only; the
 * selector treats the explicit per-turn `userIntent` (from `recentSignals` /
 * classifier) as authoritative when present.
 */
data class GuidedLearningTurnInput(
    val sessionId: String = "",
    val windowId: String = "",
    val spaceId: String = "",
    val conceptKey: String = GuidedLearningContracts.UNKNOWN_CONCEPT,
    val userIntentHint: UserIntent = UserIntent.Ambiguous,
    val sessionTemplate: SessionTemplateSnapshot = SessionTemplateSnapshot(),
    val visibleContext: VisibleLearningContext = VisibleLearningContext(),
    val learnerProfile: LearnerProfileSnapshot = LearnerProfileSnapshot(),
    val recentSignals: RecentTurnSignals = RecentTurnSignals(),
    @Deprecated("Use sessionTemplate.dialogueStrategy")
    val templateDialogueStrategy: String = "",
    @Deprecated("Use sessionTemplate.correctionTiming")
    val templateCorrectionPersistence: String = "",
    val conceptStates: Map<String, ConceptLearningState> = emptyMap(),
    @Deprecated("Use recentSignals.lastAction")
    val recentActions: List<TeachingAction> = emptyList(),
    @Deprecated("Use recentSignals.askedForHintCount")
    val recentHintsWithoutAnswer: Int = 0
) {
    /** Authoritative view of the requested state, or the safe default. */
    fun conceptStateFor(key: String): ConceptLearningState =
        conceptStates[key.trim()].normalizedOrUnknown()

    private fun ConceptLearningState?.normalizedOrUnknown(): ConceptLearningState =
        (this ?: ConceptLearningState()).normalized()

    fun normalized(): GuidedLearningTurnInput {
        val normalizedActions = recentActions.take(GuidedLearningContracts.RECENT_ACTIONS_MAX)
        val normalizedSignals = recentSignals.normalized()
        val resolvedSignals = if (normalizedSignals == RecentTurnSignals()) {
            normalizedSignals.copy(
                lastAction = normalizedActions.lastOrNull(),
                askedForHintCount = max(
                    max(0, recentHintsWithoutAnswer),
                    normalizedActions.takeLastWhile { it == TeachingAction.Hint }.size
                )
            )
        } else {
            normalizedSignals
        }
        val template = sessionTemplate.normalized()
        return copy(
            sessionId = GuidedLearningContracts.boundText(sessionId, GuidedLearningContracts.ID_MAX),
            windowId = GuidedLearningContracts.boundText(windowId, GuidedLearningContracts.ID_MAX),
            spaceId = GuidedLearningContracts.boundText(spaceId, GuidedLearningContracts.ID_MAX),
            conceptKey = GuidedLearningContracts.normalizeConceptKey(conceptKey),
            sessionTemplate = template.copy(
                dialogueStrategy = template.dialogueStrategy.ifBlank {
                    GuidedLearningContracts.boundText(
                        templateDialogueStrategy, GuidedLearningContracts.TEMPLATE_TEXT_MAX
                    )
                },
                correctionTiming = template.correctionTiming.ifBlank {
                    GuidedLearningContracts.boundText(
                        templateCorrectionPersistence, GuidedLearningContracts.TEMPLATE_TEXT_MAX
                    )
                }
            ),
            visibleContext = visibleContext.normalized(),
            learnerProfile = learnerProfile.normalized(),
            recentSignals = resolvedSignals,
            conceptStates = conceptStates.entries.asSequence()
                .associate { (key, value) ->
                    GuidedLearningContracts.normalizeConceptKey(key) to value.normalized()
                },
            recentActions = normalizedActions,
            recentHintsWithoutAnswer = max(0, recentHintsWithoutAnswer)
        )
    }
}

/**
 * Shared bounds, safe-text sanitization and enum normalization for the contracts
 * above. Mirrors the wire-safe normalization style already used by
 * [SessionTurnContracts] but for the guided-learning value types.
 */
object GuidedLearningContracts {

    const val OBJECTIVE_MAX = 120
    const val EXPECTED_MOVE_MAX = 160
    const val CONCEPT_KEY_MAX = 40
    const val ID_MAX = 120
    const val MISCONCEPTION_TAG_MAX = 8
    const val MISCONCEPTION_TAG_LEN = 24
    const val RECENT_ACTIONS_MAX = 8
    const val TEMPLATE_TEXT_MAX = 120
    const val PROFILE_TEXT_MAX = 80
    const val PROFILE_FACT_TEXT_MAX = 48
    const val PROFILE_FACTS_MAX = 8
    const val VISIBLE_MESSAGE_IDS_MAX = 12
    const val VISIBLE_CONCEPT_KEYS_MAX = 6
    const val HINT_LEVEL_MIN = 0
    const val HINT_LEVEL_MAX = 3
    const val UNKNOWN_CONCEPT = "unknown"

    private val sensitiveTextPatterns = listOf(
        Regex("(?i)sk-[a-z0-9_-]{2,}"),
        Regex("(?i)authorization\\s*[:=]\\s*\\S*"),
        Regex("(?i)bearer\\s+[a-z0-9._-]+"),
        Regex("(?i)https?://[^\\s]+")
    )

    fun clampConfidence(value: Float): Float = max(0f, min(1f, value))

    fun clampHintLevel(value: Int): Int =
        max(HINT_LEVEL_MIN, min(HINT_LEVEL_MAX, value))

    /** trim → redact secret-like substrings → collapse whitespace → cap length. */
    fun boundText(value: String?, maxLength: Int): String {
        var text = (value ?: "").trim()
        sensitiveTextPatterns.forEach { pattern -> text = text.replace(pattern, "[redacted]") }
        return text.replace(Regex("\\s+"), " ").trim().take(maxLength)
    }

    fun normalizeConceptKey(value: String?): String =
        (value ?: "").trim().take(CONCEPT_KEY_MAX).ifEmpty { UNKNOWN_CONCEPT }

    fun boundedDistinctText(values: List<String>, maxItems: Int, maxLength: Int): List<String> =
        values.asSequence()
            .map { boundText(it, maxLength) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(maxItems)
            .toList()

    fun normalizeIntent(value: String?): UserIntent {
        val key = (value ?: "").trim().lowercase().replace('-', '_')
        return UserIntent.values().firstOrNull { it.name.lowercase() == key }
            ?: when (key) {
                "answer_attempt", "answer", "attempt" -> UserIntent.AnswerAttempt
                "ask_question", "question" -> UserIntent.AskQuestion
                "ask_hint", "hint" -> UserIntent.AskHint
                "ask_example", "example" -> UserIntent.AskExample
                "goal_change", "goal" -> UserIntent.GoalChange
                "off_topic", "chit_chat" -> UserIntent.OffTopic
                "tool_request", "tool" -> UserIntent.ToolRequest
                else -> UserIntent.Ambiguous
            }
    }

    fun normalizeStatus(value: String?): ConceptStatus {
        val key = (value ?: "").trim().lowercase()
        return ConceptStatus.values().firstOrNull { it.name.lowercase() == key }
            ?: ConceptStatus.Unknown
    }

    /** Unknown action text maps to null (never a spurious primary action). */
    fun normalizeAction(value: String?): TeachingAction? {
        val key = (value ?: "").trim().lowercase()
        if (key.isEmpty() || key == "none") return null
        return TeachingAction.values().firstOrNull { it.name.lowercase() == key }
    }
}
