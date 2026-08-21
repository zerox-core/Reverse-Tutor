package com.reversetutor.core.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Session turn policy contracts — wire-safe, visual-agnostic input/output types
 * and bounded normalization for the migrated study/goal/companion decision loop.
 *
 * These types live in [core:domain] (non-frozen). They intentionally avoid
 * frozen [core:model] enums: every mode/action/role/evidence value is a stable
 * wire string so the strategy layer can evolve without touching the frozen
 * domain model or protocol wire values.
 *
 * The policy layer is pure Kotlin: it never touches Android, Room, a Repository,
 * an LLM, or the clock. All mutable defaults are backward compatible.
 */

// ---------------------------------------------------------------------------
// Wire value constants
// ---------------------------------------------------------------------------

object SessionModeWire {
    const val STUDY = "study"
    const val GOAL = "goal"
    const val COMPANION = "companion"
    val ALL = setOf(STUDY, GOAL, COMPANION)
}

object EntryStatusWire {
    const val HAS_ENTRY = "has_entry"
    const val NO_ENTRY = "no_entry"
    const val RECALL_DECAY = "recall_decay"
    val ALL = setOf(HAS_ENTRY, NO_ENTRY, RECALL_DECAY)
}

object UserEmotionWire {
    const val FRESH = "fresh"
    const val NEUTRAL = "neutral"
    const val ENGAGED = "engaged"
    const val TIRED = "tired"
    const val FRUSTRATED = "frustrated"
    const val REFUSING = "refusing"
    val ALL = setOf(FRESH, NEUTRAL, ENGAGED, TIRED, FRUSTRATED, REFUSING)
}

object StudentRoleWire {
    const val PROBING_STUDENT = "probing_student"
    const val CLUE_STUDENT = "clue_student"
    const val SCAFFOLD_STUDENT = "scaffold_student"
    const val CONFUSED_STUDENT = "confused_student"
    const val EXAMINER = "examiner"
    const val REVIEW_STUDENT = "review_student"
    const val GOAL_PARTNER = "goal_partner"
    const val COMPANION = "companion"
    val ALL = setOf(
        PROBING_STUDENT, CLUE_STUDENT, SCAFFOLD_STUDENT, CONFUSED_STUDENT,
        EXAMINER, REVIEW_STUDENT, GOAL_PARTNER, COMPANION
    )
}

object MasteryEvidenceTypeWire {
    const val NONE = "none"
    const val EXPLANATION = "explanation"
    const val RETRIEVAL = "retrieval"
    const val TRANSFER = "transfer"
    const val DELAYED_RETRIEVAL = "delayed_retrieval"
    const val CORRECTION = "correction"
    val ALL = setOf(NONE, EXPLANATION, RETRIEVAL, TRANSFER, DELAYED_RETRIEVAL, CORRECTION)
}

object MasteryEvidenceStatusWire {
    const val NONE = "none"
    const val PASSED = "passed"
    const val PARTIAL = "partial"
    const val FAILED = "failed"
    val ALL = setOf(NONE, PASSED, PARTIAL, FAILED)
}

object ActionTypeWire {
    // study
    const val ASK = "ask"
    const val PROBE = "probe"
    const val CHALLENGE = "challenge"
    const val CLUE = "clue"
    const val SCAFFOLD_EXAMPLE = "scaffold_example"
    const val SMALL_LECTURE = "small_lecture"
    const val EXAMINER_VERIFY = "examiner_verify"
    const val EMOTE = "emote"
    const val PERSUADE = "persuade"
    const val NEXT = "next"
    const val RECAP = "recap"
    // goal
    const val DECOMPOSE = "decompose"
    const val ADVANCE = "advance"
    const val VERIFY_DONE = "verify_done"
    const val UNBLOCK = "unblock"
    // companion
    const val EMPATHIZE = "empathize"
    const val OBSERVE = "observe"
    const val SOFT_GUIDE = "soft_guide"

    val STUDY_ALL = setOf(
        ASK, PROBE, CHALLENGE, CLUE, SCAFFOLD_EXAMPLE, SMALL_LECTURE,
        EXAMINER_VERIFY, EMOTE, PERSUADE, NEXT, RECAP
    )
    val GOAL_ALL = setOf(DECOMPOSE, ADVANCE, VERIFY_DONE, UNBLOCK)
    val COMPANION_ALL = setOf(EMPATHIZE, OBSERVE, SOFT_GUIDE)
}

object CorrectionTimingWire {
    const val IMMEDIATE = "immediate"
    const val SUMMARY_ONLY = "summary_only"
    val ALL = setOf(IMMEDIATE, SUMMARY_ONLY)
}

object CorrectionPersistenceWire {
    const val GENTLE = "gentle"
    const val BALANCED = "balanced"
    const val PERSISTENT = "persistent"
    val ALL = setOf(GENTLE, BALANCED, PERSISTENT)
}

// ---------------------------------------------------------------------------
// Strategy settings snapshot
// ---------------------------------------------------------------------------

data class SessionStrategySettings(
    val probingIntensity: Int = 3,
    val correctionTiming: String = CorrectionTimingWire.IMMEDIATE,
    val correctionPersistence: String = CorrectionPersistenceWire.BALANCED
)

// ---------------------------------------------------------------------------
// Mastery evidence contract
// ---------------------------------------------------------------------------

data class MasteryEvidenceContract(
    val type: String = MasteryEvidenceTypeWire.NONE,
    val status: String = MasteryEvidenceStatusWire.NONE,
    val errorType: String = "",
    val reason: String = ""
)

// ---------------------------------------------------------------------------
// Evaluation contract
// ---------------------------------------------------------------------------

data class SessionEvaluationContract(
    val correctness: Float = 0f,
    val depth: Float = 0f,
    val entryStatus: String = EntryStatusWire.HAS_ENTRY,
    val evidence: MasteryEvidenceContract = MasteryEvidenceContract(),
    val errorPattern: String = "",
    val misconception: String = "",
    val userEmotion: String = UserEmotionWire.NEUTRAL,
    val newRequirements: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// Action contract
// ---------------------------------------------------------------------------

data class SessionActionContract(
    val type: String = ActionTypeWire.ASK,
    val studentRole: String = StudentRoleWire.PROBING_STUDENT,
    val knowledgePoint: String = "",
    val difficulty: Float = 0.5f,
    val note: String = ""
)

// ---------------------------------------------------------------------------
// Policy input/output
// ---------------------------------------------------------------------------

/**
 * Pure input to [SessionTurnPolicy]. Carries the read-only mastery/error
 * snapshot needed to apply entry-status and correction rules, plus the raw
 * proposal fields that the strategy normalizes. No Android/Room/LLM types.
 */
data class SessionPolicyInput(
    val mode: String = SessionModeWire.STUDY,
    val userInput: String = "",
    val recentUserInputs: List<String> = emptyList(),
    val knowledgePoint: String = "",
    val correctness: Float = 0f,
    val depth: Float = 0f,
    val entryStatus: String = EntryStatusWire.HAS_ENTRY,
    val userEmotion: String = UserEmotionWire.NEUTRAL,
    val errorPattern: String = "",
    val misconception: String = "",
    val evidenceType: String = MasteryEvidenceTypeWire.NONE,
    val evidenceStatus: String = MasteryEvidenceStatusWire.NONE,
    val evidenceErrorType: String = "",
    val evidenceReason: String = "",
    val newRequirements: List<String> = emptyList(),
    val actionType: String = ActionTypeWire.ASK,
    val actionStudentRole: String = StudentRoleWire.PROBING_STUDENT,
    val actionDifficulty: Float = 0.5f,
    val actionNote: String = "",
    val forceProbe: Boolean = false,
    val isFirstTurn: Boolean = false,
    val settings: SessionStrategySettings = SessionStrategySettings(),
    val topMasteryLevel: Float = 0f,
    val hasMatchingMastery: Boolean = false,
    val hasActiveError: Boolean = false
)

data class SessionPolicyOutput(
    val evaluation: SessionEvaluationContract,
    val action: SessionActionContract,
    val processSummary: String,
    val normalizationWarnings: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// Bounded wire-value normalization (pure)
// ---------------------------------------------------------------------------

object SessionTurnContracts {

    const val ERROR_PATTERN_MAX = 12
    const val MISCONCEPTION_MAX = 20
    const val DEFAULT_KNOWLEDGE_POINT = "当前方法"
    const val DEFAULT_NOTE = "normalized"

    fun normalizeMode(value: String?): String =
        value?.trim()?.lowercase().let { if (it in SessionModeWire.ALL) it!! else SessionModeWire.STUDY }

    fun normalizeEntryStatus(value: String?): String =
        value?.trim()?.lowercase().let { if (it in EntryStatusWire.ALL) it!! else EntryStatusWire.HAS_ENTRY }

    fun normalizeUserEmotion(value: String?): String =
        value?.trim()?.lowercase().let { if (it in UserEmotionWire.ALL) it!! else UserEmotionWire.NEUTRAL }

    fun normalizeStudentRole(value: String?): String =
        value?.trim()?.lowercase().let { if (it in StudentRoleWire.ALL) it!! else StudentRoleWire.PROBING_STUDENT }

    fun normalizeEvidenceType(value: String?): String =
        value?.trim()?.lowercase().let { if (it in MasteryEvidenceTypeWire.ALL) it!! else MasteryEvidenceTypeWire.NONE }

    fun normalizeEvidenceStatus(value: String?): String =
        value?.trim()?.lowercase().let { if (it in MasteryEvidenceStatusWire.ALL) it!! else MasteryEvidenceStatusWire.NONE }

    fun normalizeProbingIntensity(value: Int?): Int {
        val v = value ?: 3
        return if (v < 1) 1 else if (v > 5) 5 else v
    }

    fun normalizeCorrectionTiming(value: String?): String =
        value?.trim()?.lowercase().let { if (it in CorrectionTimingWire.ALL) it!! else CorrectionTimingWire.IMMEDIATE }

    fun normalizeCorrectionPersistence(value: String?): String =
        value?.trim()?.lowercase().let { if (it in CorrectionPersistenceWire.ALL) it!! else CorrectionPersistenceWire.BALANCED }

    /** Clamp to [0.0, 1.0]. */
    fun clamp01(value: Float): Float = max(0f, min(1f, value))

    /** Bounded error pattern (<= [ERROR_PATTERN_MAX] chars). */
    fun normalizeErrorPattern(value: String?): String =
        (value ?: "").trim().take(ERROR_PATTERN_MAX)

    /** Bounded misconception (<= [MISCONCEPTION_MAX] chars). */
    fun normalizeMisconception(value: String?): String =
        (value ?: "").trim().take(MISCONCEPTION_MAX)

    /** Blank knowledge point falls back to the safe default. */
    fun normalizeKnowledgePoint(value: String?): String =
        (value ?: "").trim().ifEmpty { DEFAULT_KNOWLEDGE_POINT }

    fun normalizeNote(value: String?): String =
        (value ?: "").trim().ifEmpty { DEFAULT_NOTE }

    fun normalizeDifficulty(value: Float?): Float = clamp01(value ?: 0.5f)

    fun actionWhitelistForMode(mode: String): Set<String> = when (mode) {
        SessionModeWire.GOAL -> ActionTypeWire.GOAL_ALL
        SessionModeWire.COMPANION -> ActionTypeWire.COMPANION_ALL
        else -> ActionTypeWire.STUDY_ALL
    }

    /**
     * Map an action type to its canonical student role (mirrors engine
     * `_student_role_for_action`). Invalid/unknown actions map to
     * [StudentRoleWire.PROBING_STUDENT].
     */
    fun studentRoleForAction(actionType: String): String = when (actionType) {
        ActionTypeWire.CHALLENGE -> StudentRoleWire.CONFUSED_STUDENT
        ActionTypeWire.CLUE -> StudentRoleWire.CLUE_STUDENT
        ActionTypeWire.SCAFFOLD_EXAMPLE -> StudentRoleWire.SCAFFOLD_STUDENT
        ActionTypeWire.SMALL_LECTURE -> StudentRoleWire.PROBING_STUDENT
        ActionTypeWire.EXAMINER_VERIFY -> StudentRoleWire.EXAMINER
        ActionTypeWire.RECAP -> StudentRoleWire.REVIEW_STUDENT
        ActionTypeWire.NEXT -> StudentRoleWire.REVIEW_STUDENT
        ActionTypeWire.DECOMPOSE, ActionTypeWire.ADVANCE,
        ActionTypeWire.VERIFY_DONE, ActionTypeWire.UNBLOCK -> StudentRoleWire.GOAL_PARTNER
        ActionTypeWire.EMPATHIZE, ActionTypeWire.OBSERVE,
        ActionTypeWire.SOFT_GUIDE -> StudentRoleWire.COMPANION
        else -> StudentRoleWire.PROBING_STUDENT
    }
}
