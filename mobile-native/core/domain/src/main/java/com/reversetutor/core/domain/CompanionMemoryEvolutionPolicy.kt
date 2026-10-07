package com.reversetutor.core.domain

/**
 * Pure Hermes-like companion-memory curation policy.
 *
 * This policy never reads a clock, persistent storage, a data-access layer, or
 * an LLM. It decides whether a candidate observation should be ignored, retained
 * as short-lived context, reinforce the active value, supersede it, or conflict.
 *
 * It does NOT model a learning-forgetting curve. An automatic supersession of a
 * template/manual/inferred active value requires independent, temporally stable
 * support plus material advantage; a single user statement, joke, roleplay turn,
 * or model guess is insufficient. Learning-retention signals are never treated
 * as personality/relationship evolution evidence.
 */
object CompanionMemoryEvolutionPolicy {

    private const val MIN_INDEPENDENT_SOURCES = 2
    private const val STABLE_CONFIDENCE = 0.7f
    private const val SUPERSEDE_CONFIDENCE = 0.8f
    private const val MIN_TEMPORAL_SPAN_MILLIS = 6 * 60 * 60 * 1000L
    private const val SHORT_LIVED_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L

    private fun isLearningRetentionSignal(obs: MemoryObservation): Boolean =
        obs.sourceClass == MemoryObservationSourceClass.LEARNING_RETENTION ||
            obs.domain == MemoryDomain.GLOBAL_LEARNING

    private fun isFresh(obs: MemoryObservation, nowEpochMillis: Long): Boolean =
        obs.observedAtEpochMillis <= nowEpochMillis &&
            (nowEpochMillis - obs.observedAtEpochMillis) <= SHORT_LIVED_TTL_MILLIS

    private fun hasStableIndependentSupport(obsList: List<MemoryObservation>, nowEpochMillis: Long): Boolean {
        val confident = obsList.filter { it.confidence >= STABLE_CONFIDENCE }
        if (confident.size < MIN_INDEPENDENT_SOURCES) return false
        val sources = confident.map { it.sourceClass }.distinct()
        if (sources.size < MIN_INDEPENDENT_SOURCES) return false
        val times = confident.map { it.observedAtEpochMillis }
        val span = (times.max() - times.min()).coerceAtLeast(0L)
        return span >= MIN_TEMPORAL_SPAN_MILLIS
    }

    private fun isMateriallyAdvantaged(obsList: List<MemoryObservation>): Boolean {
        val distinctValues = obsList.map { it.normalizedValue }.distinct()
        return distinctValues.size == 1 && obsList.any { it.confidence >= SUPERSEDE_CONFIDENCE }
    }

    /**
     * Decide the evolution of [active] given the candidate [observations].
     *
     * [nowEpochMillis] is supplied by the caller (the policy never reads a clock).
     */
    fun decide(
        active: ActiveMemoryVersion,
        observations: List<MemoryObservation>,
        nowEpochMillis: Long
    ): MemoryEvolutionDecision {
        val evolutionCandidates = observations.filter {
            it.domain == MemoryDomain.COMPANION && it.partition != null && !isLearningRetentionSignal(it)
        }
        if (evolutionCandidates.isEmpty()) return MemoryEvolutionDecision.Ignore

        // Short-lived situation candidates never supersede a durable active value.
        if (active.partition != CompanionMemoryPartition.SHORT_LIVED_SITUATION &&
            evolutionCandidates.all { it.partition == CompanionMemoryPartition.SHORT_LIVED_SITUATION }
        ) {
            return if (evolutionCandidates.any { isFresh(it, nowEpochMillis) }) {
                MemoryEvolutionDecision.KeepShortLived
            } else {
                MemoryEvolutionDecision.Ignore
            }
        }

        val disagreeing = evolutionCandidates.filter { it.normalizedValue != active.value }
        if (disagreeing.isEmpty()) return MemoryEvolutionDecision.Reinforce

        if (hasStableIndependentSupport(disagreeing, nowEpochMillis) && isMateriallyAdvantaged(disagreeing)) {
            return MemoryEvolutionDecision.Supersede(
                value = disagreeing.first().normalizedValue,
                revision = active.revision + 1L
            )
        }
        return MemoryEvolutionDecision.Conflict
    }

    /**
     * Companion domain is readable only by the companion root. Global learning
     * facts are a shared projection; window-local memory belongs to its owner.
     */
    fun canReadDomain(domain: MemoryDomain, window: WindowRef): Boolean = when (domain) {
        MemoryDomain.COMPANION -> window.kind == WindowKind.COMPANION_ROOT
        MemoryDomain.GLOBAL_LEARNING -> true
        MemoryDomain.WINDOW_LOCAL -> true
    }

    /**
     * A task window may emit normalized global learning facts, but only the
     * companion root may emit companion-domain (personality/relationship)
     * observations.
     */
    fun canEmit(observation: MemoryObservation, window: WindowRef): Boolean = when (observation.domain) {
        MemoryDomain.COMPANION -> window.kind == WindowKind.COMPANION_ROOT
        MemoryDomain.GLOBAL_LEARNING -> true
        MemoryDomain.WINDOW_LOCAL -> true
    }
}
