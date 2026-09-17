package com.reversetutor.core.domain

/**
 * V2-002: window-level active values (Layer 3 of window memory) and their
 * evolution policy.
 *
 * A window keeps at most one active value per slot: the window's current
 * belief about that topic, with a revision, a weight, and a provenance
 * handle. This layer is the ONLY one eligible for global projection
 * (see GlobalProjectionPolicy).
 *
 * Window-local evolution is intentionally lighter than companion-memory
 * evolution: inside a single window, one confident user statement may
 * supersede the current value at once; behavior observations alone may
 * only reinforce or raise a conflict.
 */

/** The window's current belief about one slot. */
data class WindowActiveValue(
    val category: WindowObservationCategory,
    val slotKey: String,
    val value: String,
    val revision: Long,
    val weight: Double,
    val sourceClass: String,
    val provenanceHandle: String,
    val updatedAtEpochMillis: Long,
)

/** A Layer-1 window-local observation, ready for the evolution policy. */
data class WindowLocalObservation(
    val category: WindowObservationCategory,
    val slotKey: String,
    val value: String,
    val sourceClass: String,
    val confidence: Double,
    val salience: SignalSalience,
    val occurredAtEpochMillis: Long,
    val provenanceHandle: String,
)

enum class WindowEvolutionDecision { CREATE, REINFORCE, SUPERSEDE, CONFLICT, IGNORE }

/** Pure evolution policy for window-level active values. */
object WindowActiveValuePolicy {

    const val REINFORCE_WEIGHT_STEP: Double = 0.15
    const val MAX_WEIGHT: Double = 1.0
    const val SUPERSEDE_CONFIDENCE_THRESHOLD: Double = 0.8

    fun decide(current: WindowActiveValue?, observation: WindowLocalObservation): WindowEvolutionDecision {
        if (observation.salience == SignalSalience.LOW) return WindowEvolutionDecision.IGNORE
        if (current == null) return WindowEvolutionDecision.CREATE
        if (current.value == observation.value) return WindowEvolutionDecision.REINFORCE
        val confidentUserVoice =
            (observation.sourceClass == MemoryObservationSourceClass.USER_STATEMENT ||
                observation.sourceClass == MemoryObservationSourceClass.MANUAL) &&
                observation.confidence >= SUPERSEDE_CONFIDENCE_THRESHOLD
        return if (confidentUserVoice) WindowEvolutionDecision.SUPERSEDE else WindowEvolutionDecision.CONFLICT
    }

    /** Same value seen again: weight up (capped), revision up, latest provenance kept. */
    fun reinforce(current: WindowActiveValue, observation: WindowLocalObservation): WindowActiveValue =
        current.copy(
            revision = current.revision + 1,
            weight = (current.weight + REINFORCE_WEIGHT_STEP).coerceAtMost(MAX_WEIGHT),
            provenanceHandle = observation.provenanceHandle,
            updatedAtEpochMillis = observation.occurredAtEpochMillis,
        )

    /** Replace (or create) the active value from a decisive observation. */
    fun supersede(current: WindowActiveValue?, observation: WindowLocalObservation): WindowActiveValue =
        WindowActiveValue(
            category = observation.category,
            slotKey = observation.slotKey,
            value = observation.value,
            revision = (current?.revision ?: 0L) + 1,
            weight = observation.confidence.coerceAtMost(MAX_WEIGHT),
            sourceClass = observation.sourceClass,
            provenanceHandle = observation.provenanceHandle,
            updatedAtEpochMillis = observation.occurredAtEpochMillis,
        )
}
