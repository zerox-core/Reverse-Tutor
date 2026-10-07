package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.CheckRule
import com.reversetutor.core.domain.CheckVerification
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.MasteryEvidenceTypeWire
import com.reversetutor.core.domain.SourceGroundedCheckPlan
import com.reversetutor.core.domain.SourceGroundedCheckPolicy

/**
 * NEWMP-V1-002 plan Task 4 · source-grounded local learning-evidence verifier.
 *
 * This is a *separate* boundary from the legacy candidate verifier
 * ([LocalLearningEvidenceVerifier]) so the existing worker wiring
 * (`PostTurnProjector.project(jobId, outcome)`) stays untouched. The new
 * source-grounded path decides a saved [SourceGroundedCheckPlan] against a
 * candidate answer using only the deterministic
 * [SourceGroundedCheckPolicy] — it never reads transcript into evidence fields
 * and never trusts a model self-assessment.
 *
 * [LocalLearningEvidenceInput] is the whole input: the durable job id, the
 * already-normalized saved plan, the learner's candidate answer, and the current
 * source revision that must still match the plan's revision.
 */
data class LocalLearningEvidenceInput(
    val jobId: String,
    val plan: SourceGroundedCheckPlan,
    val candidateAnswer: String,
    /// Live revisions per handle (V1-004 Task 1): a missing or drifted handle makes the
    /// whole plan Unverified.
    val currentSourceRevisions: Map<String, String>
)

/**
 * Local-only source-grounded evidence boundary. Returning `null` (or a result
 * that fails [LocalLearningEvidenceVerification.isAccepted]) means "this layer
 * cannot prove a learning fact", the correct outcome for open-ended rubrics and
 * any revision drift.
 */
fun interface SourceGroundedEvidenceVerifier {
    suspend fun verifySourceGrounded(
        input: LocalLearningEvidenceInput
    ): LocalLearningEvidenceVerification?
}

/**
 * Decides a saved [plan] with [SourceGroundedCheckPolicy]. Mapping is fixed and
 * verifier-owned: passed -> correctness/depth 1.0, partial -> 0.5/0.5, failed ->
 * 0.0/0.0, and any `Unverified` (rubric or version drift) returns `null` so the
 * projector writes nothing. Evidence fields are bounded wire enum tokens only.
 */
class SourceGroundedLocalVerifier : SourceGroundedEvidenceVerifier {
    override suspend fun verifySourceGrounded(
        input: LocalLearningEvidenceInput
    ): LocalLearningEvidenceVerification? {
        val decision = SourceGroundedCheckPolicy.validateAnswer(
            plan = input.plan,
            candidateAnswer = input.candidateAnswer,
            currentSourceRevisions = input.currentSourceRevisions
        )
        val status = when (decision) {
            CheckVerification.VerifiedPassed -> MasteryEvidenceStatusWire.PASSED
            CheckVerification.VerifiedPartial -> MasteryEvidenceStatusWire.PARTIAL
            CheckVerification.VerifiedFailed -> MasteryEvidenceStatusWire.FAILED
            CheckVerification.Unverified -> return null
        }
        val (correctness, depth) = when (decision) {
            CheckVerification.VerifiedPassed -> 1f to 1f
            CheckVerification.VerifiedPartial -> 0.5f to 0.5f
            else -> 0f to 0f
        }
        return LocalLearningEvidenceVerification(
            evidenceType = evidenceTypeFor(input.plan),
            evidenceStatus = status,
            correctness = correctness,
            depth = depth
        )
    }

    private fun evidenceTypeFor(plan: SourceGroundedCheckPlan): String = when (plan.rule) {
        is CheckRule.ExactText, is CheckRule.NumericTolerance -> MasteryEvidenceTypeWire.RETRIEVAL
        is CheckRule.RequiredConcepts -> MasteryEvidenceTypeWire.EXPLANATION
        is CheckRule.Rubric -> MasteryEvidenceTypeWire.NONE
    }
}

/**
 * Fail-closed default used until a real plan channel is wired: it never yields
 * evidence, so ordinary chat and model self-assessment cannot reach the ledger.
 */
object NoSourceGroundedEvidenceVerifier : SourceGroundedEvidenceVerifier {
    override suspend fun verifySourceGrounded(
        input: LocalLearningEvidenceInput
    ): LocalLearningEvidenceVerification? = null
}
