package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.StructuredTurnOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTurnProjectorTest {

    private fun outcome() = StructuredTurnOutcome(
        windowId = "w1",
        actionType = "probe",
        studentRole = "probing_student",
        knowledgePoint = "factoring",
        evidenceType = "explanation",
        evidenceStatus = "passed"
    )

    @Test
    fun emptyOutcomeNoOps() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink)
        assertFalse(projector.project("job-1", StructuredTurnOutcome.EMPTY))
        assertEquals(0, sink.records.size)
    }

    @Test
    fun modelCandidateWithoutLocalVerificationDoesNotWriteMastery() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink)

        assertFalse(projector.project("candidate-job", outcome()))
        assertEquals(0, sink.records.size)
    }

    @Test
    fun repeatedJobCompletionProjectsExactlyOnce() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink, AcceptingVerifier)
        assertTrue(projector.project("job-1", outcome()))
        assertFalse(projector.project("job-1", outcome()))
        assertEquals(1, sink.records.size)
    }

    @Test
    fun distinctJobsProjectIndependently() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink, AcceptingVerifier)
        assertTrue(projector.project("job-1", outcome()))
        assertTrue(projector.project("job-2", outcome()))
        assertEquals(2, sink.records.size)
    }

    @Test
    fun valid_study_outcome_is_recorded_once_and_is_bounded() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val projector = PostTurnProjector(
            sink = TurnProjectionSink { _, outcome -> received += outcome },
            evidenceVerifier = AcceptingVerifier
        )
        val outcome = StructuredTurnOutcome(
            windowId = "session-1",
            actionType = "probe",
            studentRole = "probing_student",
            knowledgePoint = "函数单调性",
            correctness = 0.8f,
            depth = 0.7f,
            evidenceType = "explanation",
            evidenceStatus = "passed",
            processSummary = "完成一次可复述解释"
        )

        assertTrue(projector.project("job-1", outcome))
        assertFalse(projector.project("job-1", outcome))
        assertEquals(1, received.size)
        assertTrue(received.single().knowledgePoint.length <= 120)
        assertTrue(received.single().processSummary.length <= 320)
    }

    @Test
    fun empty_or_non_learning_outcome_does_not_write_mastery() = runBlocking {
        var writes = 0
        val projector = PostTurnProjector(TurnProjectionSink { _, _ -> writes++ })

        assertFalse(projector.project("empty", StructuredTurnOutcome.EMPTY))
        assertFalse(
            projector.project(
                "none",
                StructuredTurnOutcome(
                    windowId = "session-1",
                    actionType = "observe",
                    studentRole = "companion",
                    evidenceType = "none",
                    evidenceStatus = "none"
                )
            )
        )
        assertEquals(0, writes)
    }

    @Test
    fun non_learning_outcome_without_evidence_does_not_write_mastery() = runBlocking {
        var writes = 0
        val projector = PostTurnProjector(TurnProjectionSink { _, _ -> writes++ })

        assertFalse(
            projector.project(
                "no-evidence",
                StructuredTurnOutcome(
                    windowId = "session-1",
                    actionType = "probe",
                    studentRole = "probing_student",
                    knowledgePoint = "factoring",
                    evidenceType = "none",
                    evidenceStatus = "none"
                )
            )
        )
        assertFalse(
            projector.project(
                "no-window",
                StructuredTurnOutcome(
                    actionType = "probe",
                    studentRole = "probing_student",
                    knowledgePoint = "factoring",
                    evidenceType = "explanation",
                    evidenceStatus = "passed"
                )
            )
        )
        assertFalse(
            projector.project(
                "no-knowledge-point",
                StructuredTurnOutcome(
                    windowId = "session-1",
                    actionType = "probe",
                    studentRole = "probing_student",
                    knowledgePoint = "",
                    evidenceType = "explanation",
                    evidenceStatus = "passed"
                )
            )
        )
        assertEquals(0, writes)
    }

    private class RecordingSink : TurnProjectionSink {
        val records = mutableListOf<StructuredTurnOutcome>()
        override suspend fun record(jobId: String, outcome: StructuredTurnOutcome) {
            records += outcome
        }
    }

    private object AcceptingVerifier : LocalLearningEvidenceVerifier {
        override suspend fun verify(
            jobId: String,
            candidate: StructuredTurnOutcome
        ): LocalLearningEvidenceVerification = LocalLearningEvidenceVerification(
            evidenceType = "explanation",
            evidenceStatus = "passed",
            correctness = 0.8f,
            depth = 0.7f
        )
    }
}
