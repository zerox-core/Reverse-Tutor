package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.StructuredTurnOutcome
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.MasteryEvidenceTypeWire

/**
 * Proof emitted by a local verifier for one completed turn. The verifier owns
 * these fields; a model-produced [StructuredTurnOutcome] is only a candidate
 * and can never set learning evidence by itself.
 */
data class LocalLearningEvidenceVerification(
    val evidenceType: String,
    val evidenceStatus: String,
    val correctness: Float,
    val depth: Float
) {
    fun isAccepted(): Boolean =
        evidenceType in (MasteryEvidenceTypeWire.ALL - MasteryEvidenceTypeWire.NONE) &&
            evidenceStatus in (MasteryEvidenceStatusWire.ALL - MasteryEvidenceStatusWire.NONE)
}

/**
 * Local-only learning evidence boundary. A future deterministic checker may
 * inspect its own stored challenge state by [jobId], but raw chat or Provider
 * text is intentionally not exposed through this contract.
 */
fun interface LocalLearningEvidenceVerifier {
    suspend fun verify(
        jobId: String,
        candidate: StructuredTurnOutcome
    ): LocalLearningEvidenceVerification?
}

private val NoLocalLearningEvidenceVerifier = LocalLearningEvidenceVerifier { _, _ -> null }

/**
 * Port for writing P6-approved post-turn projection records.
 * The production wiring routes these to the window/learning/companion/heartbeat
 * repositories; it never receives an entity or DAO.
 */
fun interface TurnProjectionSink {
    suspend fun record(jobId: String, outcome: StructuredTurnOutcome)
}

/**
 * Idempotent post-turn projector.
 *
 * It consumes only a trusted job id plus a validated bounded [StructuredTurnOutcome]
 * and writes the approved learning receipt through [TurnProjectionSink]. It
 * no-ops for an empty or non-learning outcome, projects a given job id at most
 * once, and records only [StructuredTurnOutcome.normalized] fields, so a
 * repeated job completion cannot double-write and no unbounded text ever
 * reaches the sink. It never calls a Provider and never rewrites the assistant
 * record, and it never guesses mastery from chat text: without a real
 * structured result nothing is projected.
 */
class PostTurnProjector(
    private val sink: TurnProjectionSink,
    private val evidenceVerifier: LocalLearningEvidenceVerifier = NoLocalLearningEvidenceVerifier
) {
    private val projectedJobIds = mutableSetOf<String>()

    suspend fun project(jobId: String, outcome: StructuredTurnOutcome): Boolean {
        if (outcome == StructuredTurnOutcome.EMPTY) return false
        val normalized = outcome.normalized()
        val verification = evidenceVerifier.verify(jobId, normalized) ?: return false
        if (!verification.isAccepted()) return false
        val locallyVerified = normalized.copy(
            correctness = verification.correctness.coerceIn(0f, 1f),
            depth = verification.depth.coerceIn(0f, 1f),
            evidenceType = verification.evidenceType,
            evidenceStatus = verification.evidenceStatus
        ).normalized()
        if (!locallyVerified.isLearningOutcome()) return false
        if (!projectedJobIds.add(jobId)) return false
        sink.record(jobId, locallyVerified)
        return true
    }
}

/**
 * A learning receipt requires a window id, a non-blank knowledge point and real
 * evidence (type and status both not "none"). Companion/goal/observe turns and
 * EMPTY outcomes must never write mastery.
 */
private fun StructuredTurnOutcome.isLearningOutcome(): Boolean =
    !windowId.isNullOrBlank() &&
        knowledgePoint.isNotBlank() &&
        evidenceType != "none" &&
        evidenceStatus != "none"
