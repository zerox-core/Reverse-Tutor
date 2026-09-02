package com.reversetutor.core.domain

/**
 * Renderer-neutral learning guidance snapshot derived from the normalized
 * turn policy. It carries no Android, persistence, or generation types.
 */
data class GuidedLearningPlan(
    val mode: String,
    val actionType: String,
    val studentRole: String,
    val knowledgePoint: String,
    val difficulty: Float,
    val correctionLevel: Int = 0,
    val requiredEvidenceType: String = MasteryEvidenceTypeWire.NONE,
    val documentIntent: String = "none"
)

object GuidedLearningPlanFactory {
    fun from(input: SessionPolicyInput, output: SessionPolicyOutput): GuidedLearningPlan {
        val mode = SessionTurnContracts.normalizeMode(input.mode)
        return GuidedLearningPlan(
            mode = mode,
            actionType = output.action.type,
            studentRole = output.action.studentRole,
            knowledgePoint = output.action.knowledgePoint,
            difficulty = output.action.difficulty,
            correctionLevel = when (output.action.type) {
                ActionTypeWire.CHALLENGE -> when (
                    SessionTurnContracts.normalizeCorrectionPersistence(
                        input.settings.correctionPersistence
                    )
                ) {
                    CorrectionPersistenceWire.PERSISTENT -> 2
                    else -> 1
                }
                else -> 0
            },
            requiredEvidenceType = if (mode == SessionModeWire.STUDY) {
                output.evaluation.evidence.type
            } else {
                MasteryEvidenceTypeWire.NONE
            },
            documentIntent = if (
                output.action.type in setOf(ActionTypeWire.RECAP, ActionTypeWire.SMALL_LECTURE)
            ) {
                "learning_note"
            } else {
                "none"
            }
        )
    }
}
