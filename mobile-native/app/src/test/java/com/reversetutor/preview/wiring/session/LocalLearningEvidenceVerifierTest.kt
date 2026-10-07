package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.CheckRule
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.SourceGroundedCheckPlan
import com.reversetutor.core.domain.SourceGroundedCheckPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 plan Task 4 · [SourceGroundedLocalVerifier].
 *
 * The verifier turns a saved source-grounded check plan plus a candidate answer
 * into verifier-owned evidence. It never trusts model self-assessment: only a
 * deterministic [SourceGroundedCheckPolicy] decision on a version-matched plan
 * yields accepted evidence; anything undecidable or version-mismatched yields
 * `null`, so the projector writes nothing.
 */
class LocalLearningEvidenceVerifierTest {

    private val handle = "source:src-1:rev-src-1-1000"

    private fun groundedRule(
        rule: CheckRule,
        answer: String = "有一个重根",
        revision: String = "rev-src-1-1000"
    ): SourceGroundedCheckPlan =
        SourceGroundedCheckPolicy.normalize(
            SourceGroundedCheckPlan(
                id = "check-1",
                sourceRevision = revision,
                sourceHandles = listOf(handle),
                prompt = "判别式为0说明什么？",
                expectedAnswer = answer,
                rule = rule,
                conceptKey = "判别式"
            ),
            allowedSourceHandles = setOf(handle)
        )!!

    @Test
    fun exactTextPassYieldsVerifiedPassedEvidence() = runBlocking {
        val plan = groundedRule(CheckRule.ExactText("有一个重根"))
        val evidence = SourceGroundedLocalVerifier().verifySourceGrounded(
            LocalLearningEvidenceInput(
                jobId = "job-1",
                plan = plan,
                candidateAnswer = " 有一个重根 ",
                currentSourceRevisions = plan.sourceHandles.associateWith { plan.sourceRevision }
            )
        )
        assertTrue(evidence?.isAccepted() == true)
        assertEquals(MasteryEvidenceStatusWire.PASSED, evidence?.evidenceStatus)
        assertEquals(1f, evidence?.correctness ?: -1f, 0.0001f)
    }

    @Test
    fun numericBoundaryUsesInclusiveTolerance() = runBlocking {
        val plan = groundedRule(CheckRule.NumericTolerance(expected = 4.0, tolerance = 0.5))
        val verifier = SourceGroundedLocalVerifier()
        val pass = verifier.verifySourceGrounded(
            LocalLearningEvidenceInput("j", plan, "4.5", plan.sourceHandles.associateWith { plan.sourceRevision })
        )
        assertEquals(MasteryEvidenceStatusWire.PASSED, pass?.evidenceStatus)
        val fail = verifier.verifySourceGrounded(
            LocalLearningEvidenceInput("j", plan, "4.51", plan.sourceHandles.associateWith { plan.sourceRevision })
        )
        assertEquals(MasteryEvidenceStatusWire.FAILED, fail?.evidenceStatus)
    }

    @Test
    fun requiredConceptsMapToPartial() = runBlocking {
        val plan = groundedRule(CheckRule.RequiredConcepts(listOf("重根", "判别式")))
        val partial = SourceGroundedLocalVerifier().verifySourceGrounded(
            LocalLearningEvidenceInput("j", plan, "这里判别式有点问题", plan.sourceHandles.associateWith { plan.sourceRevision })
        )
        assertEquals(MasteryEvidenceStatusWire.PARTIAL, partial?.evidenceStatus)
    }

    @Test
    fun rubricYieldsNoEvidence() = runBlocking {
        val plan = groundedRule(CheckRule.Rubric(listOf("解释完整")))
        assertNull(
            SourceGroundedLocalVerifier().verifySourceGrounded(
                LocalLearningEvidenceInput("j", plan, "我自认为讲清楚了", plan.sourceHandles.associateWith { plan.sourceRevision })
            )
        )
    }

    @Test
    fun revisionMismatchYieldsNoEvidence() = runBlocking {
        val plan = groundedRule(CheckRule.ExactText("有一个重根"))
        assertNull(
            SourceGroundedLocalVerifier().verifySourceGrounded(
                LocalLearningEvidenceInput("j", plan, "有一个重根", currentSourceRevisions = plan.sourceHandles.associateWith { "rev-src-1-2000" })
            )
        )
    }

    @Test
    fun verifierNeverEchoesCandidateTextIntoEvidence() = runBlocking {
        val plan = groundedRule(CheckRule.ExactText("有一个重根"))
        val evidence = SourceGroundedLocalVerifier().verifySourceGrounded(
            LocalLearningEvidenceInput(
                jobId = "job-9",
                plan = plan,
                candidateAnswer = "有一个重根，这是一大段老师原文，含 https://api.invalid/x",
                currentSourceRevisions = plan.sourceHandles.associateWith { plan.sourceRevision }
            )
        )!!
        assertFalse(evidence.evidenceType.contains("重根"))
        assertFalse(evidence.evidenceStatus.contains("http"))
        assertTrue(evidence.isAccepted())
    }

    // Task 4/6: projectCheck writes once and shares the dedup set with the legacy entry.
    @Test
    fun projectCheckDedupesAcrossEntryPoints() = runBlocking {
        val sink = mutableListOf<com.reversetutor.core.llm.StructuredTurnOutcome>()
        val projector = PostTurnProjector(
            sink = TurnProjectionSink { _, outcome -> sink += outcome }
        )
        val plan = groundedRule(CheckRule.ExactText("有一个重根"))
        val input = LocalLearningEvidenceInput("job-x", plan, "有一个重根", plan.sourceHandles.associateWith { plan.sourceRevision })
        val outcome = com.reversetutor.core.llm.StructuredTurnOutcome(
            windowId = "window-1", knowledgePoint = "判别式"
        )
        org.junit.Assert.assertTrue(projector.projectCheck(input, outcome, SourceGroundedLocalVerifier()))
        org.junit.Assert.assertFalse(projector.projectCheck(input, outcome, SourceGroundedLocalVerifier()))
        org.junit.Assert.assertFalse(projector.project("job-x", outcome))
        assertEquals(1, sink.size)
    }
}
