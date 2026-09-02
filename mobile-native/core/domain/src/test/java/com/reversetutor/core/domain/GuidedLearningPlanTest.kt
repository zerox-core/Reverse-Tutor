package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedLearningPlanTest {

    @Test
    fun persistentChallengeCarriesBoundedCorrectionPlan() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            misconception = "忽略定义域",
            knowledgePoint = "函数单调性",
            settings = SessionStrategySettings(
                correctionPersistence = CorrectionPersistenceWire.PERSISTENT
            )
        )
        val output = SessionTurnPolicy.normalize(input)

        val plan = GuidedLearningPlanFactory.from(input, output)

        assertEquals(SessionModeWire.STUDY, plan.mode)
        assertEquals(ActionTypeWire.CHALLENGE, plan.actionType)
        assertEquals(StudentRoleWire.CONFUSED_STUDENT, plan.studentRole)
        assertEquals(2, plan.correctionLevel)
        assertEquals(MasteryEvidenceTypeWire.NONE, plan.requiredEvidenceType)
        assertEquals("none", plan.documentIntent)
    }

    @Test
    fun recapRequestsLearningNoteWithoutChangingEvidenceRequirement() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.RECAP,
            evidenceType = MasteryEvidenceTypeWire.CORRECTION,
            evidenceStatus = MasteryEvidenceStatusWire.PASSED,
            settings = SessionStrategySettings(
                correctionTiming = CorrectionTimingWire.SUMMARY_ONLY
            )
        )
        val output = SessionTurnPolicy.normalize(input)

        val plan = GuidedLearningPlanFactory.from(input, output)

        assertEquals(ActionTypeWire.RECAP, plan.actionType)
        assertEquals("learning_note", plan.documentIntent)
        assertEquals(MasteryEvidenceTypeWire.CORRECTION, plan.requiredEvidenceType)
    }
}
