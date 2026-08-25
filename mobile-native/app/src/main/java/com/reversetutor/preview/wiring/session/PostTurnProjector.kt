package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.StructuredTurnOutcome

/**
 * Port for writing P6-approved post-turn projection records.
 * The production wiring routes these to the window/learning/companion/heartbeat
 * repositories; it never receives an entity or DAO.
 */
fun interface TurnProjectionSink {
    suspend fun record(outcome: StructuredTurnOutcome)
}

/**
 * Idempotent post-turn projector.
 *
 * It consumes only a trusted job id plus a validated bounded [StructuredTurnOutcome]
 * and writes the approved local delta / learning receipt / memory observation /
 * heartbeat state through [TurnProjectionSink]. It no-ops for an empty outcome
 * and projects a given job id at most once, so a repeated job completion cannot
 * double-write. It never calls a Provider and never rewrites the assistant record.
 */
class PostTurnProjector(
    private val sink: TurnProjectionSink
) {
    private val projectedJobIds = mutableSetOf<String>()

    suspend fun project(jobId: String, outcome: StructuredTurnOutcome): Boolean {
        if (outcome == StructuredTurnOutcome.EMPTY) return false
        if (!projectedJobIds.add(jobId)) return false
        sink.record(outcome)
        return true
    }
}
