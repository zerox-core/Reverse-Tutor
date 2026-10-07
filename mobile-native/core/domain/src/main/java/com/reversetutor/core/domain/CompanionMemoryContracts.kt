package com.reversetutor.core.domain

/**
 * Companion-memory, learning-ledger, and window-local observation contracts.
 *
 * These types live in [core:domain] (non-frozen) and are wire-safe, Android-free.
 * They intentionally hold no raw conversation text: an observation carries only a
 * normalized value plus a compact provenance handle. Global learning facts are
 * separate from companion observations so task windows can never emit companion
 * personality content into the ledger.
 */

/** The three memory domains, matching the topology. */
enum class MemoryDomain {
    COMPANION,
    WINDOW_LOCAL,
    GLOBAL_LEARNING
}

/** The five bounded companion-memory partitions (section 2.1 of the design). */
enum class CompanionMemoryPartition {
    CORE_SETTING,
    STABLE_PREFERENCE,
    SHARED_EXPERIENCE,
    CADENCE,
    SHORT_LIVED_SITUATION
}

/** How an active companion value was established. */
enum class MemoryOrigin {
    TEMPLATE,
    MANUAL,
    INFERRED
}

/** Source class of an observation (how evidence was produced). */
object MemoryObservationSourceClass {
    const val USER_STATEMENT = "user_statement"
    const val BEHAVIOR_OBSERVATION = "behavior_observation"
    const val TEMPLATE = "template"
    const val MANUAL = "manual"
    const val MODEL_GUESS = "model_guess"
    const val LEARNING_RETENTION = "learning_retention"
}

/**
 * A normalized memory observation. It does NOT carry raw message text.
 * [partition] is null for learning-ledger and window-local facts; it is always
 * set for companion-domain observations.
 */
data class MemoryObservation(
    val domain: MemoryDomain,
    val partition: CompanionMemoryPartition?,
    val normalizedValue: String,
    val sourceClass: String,
    val observedAtEpochMillis: Long,
    val confidence: Float,
    val provenanceHandle: String
) {
    companion object {
        /**
         * The only fields a persistence layer may store for this observation.
         * Raw conversation text must never be added here; the P6 layer must
         * persist exactly this set and nothing more.
         */
        val ALLOWED_PERSISTED_FIELDS: Set<String> = setOf(
            "domain", "partition", "normalizedValue", "sourceClass",
            "observedAtEpochMillis", "confidence", "provenanceHandle"
        )
    }
}

/** A currently-active companion memory value for one partition. */
data class ActiveMemoryVersion(
    val partition: CompanionMemoryPartition,
    val value: String,
    val origin: MemoryOrigin,
    val promotedAtEpochMillis: Long,
    val revision: Long = 1L
)

/** A normalized global learning fact receipt (never a raw transcript). */
data class LearningFactReceipt(
    val knowledgePoint: String,
    val evidenceType: String,
    val result: String,
    val confidence: Float,
    val sourceWindowId: String,
    val sourceTurnId: String,
    val occurredAtEpochMillis: Long
)

/** Result of a companion-memory evolution decision. */
sealed interface MemoryEvolutionDecision {
    data object Ignore : MemoryEvolutionDecision
    data object KeepShortLived : MemoryEvolutionDecision
    data object Reinforce : MemoryEvolutionDecision
    data class Supersede(val value: String, val revision: Long) : MemoryEvolutionDecision
    data object Conflict : MemoryEvolutionDecision
}
