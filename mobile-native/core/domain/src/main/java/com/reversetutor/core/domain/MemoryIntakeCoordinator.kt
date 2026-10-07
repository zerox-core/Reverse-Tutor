package com.reversetutor.core.domain

/**
 * Memory-intake coordinator (NEWMP-V2-001): turns per-turn proposed memory
 * observations into bounded, domain-safe candidates.
 *
 * Contract: [MemoryIntakeContractTest]; task card: docs/NEWMP-V2-001-memory-intake.md.
 *
 * Rules:
 * - a window may only emit candidates its kind allows
 *   ([CompanionMemoryEvolutionPolicy.canEmit]) — task/child windows can never
 *   emit companion-domain content;
 * - companion-domain candidates must carry a partition;
 * - candidates per turn are bounded ([MAX_CANDIDATES_PER_TURN]);
 * - observations carry normalized values plus provenance only — raw
 *   conversation text never enters this layer
 *   ([MemoryObservation.ALLOWED_PERSISTED_FIELDS]).
 */
class MemoryIntakeCoordinator(
    private val evolutionPolicy: CompanionMemoryEvolutionPolicy = CompanionMemoryEvolutionPolicy
) {
    companion object {
        const val MAX_CANDIDATES_PER_TURN: Int = 3
    }

    /**
     * Filters and bounds the observations proposed after one conversation turn.
     * Order is preserved; rejected candidates are dropped silently here — the
     * caller may log rejections at a higher layer if needed.
     */
    fun candidatesFromTurn(
        window: WindowRef,
        proposed: List<MemoryObservation>
    ): List<MemoryObservation> =
        proposed.asSequence()
            .filter { it.domain != MemoryDomain.COMPANION || it.partition != null }
            .filter { evolutionPolicy.canEmit(it, window) }
            .take(MAX_CANDIDATES_PER_TURN)
            .toList()
}
