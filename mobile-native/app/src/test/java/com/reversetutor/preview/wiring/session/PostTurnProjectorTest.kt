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
    fun repeatedJobCompletionProjectsExactlyOnce() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink)
        assertTrue(projector.project("job-1", outcome()))
        assertFalse(projector.project("job-1", outcome()))
        assertEquals(1, sink.records.size)
    }

    @Test
    fun distinctJobsProjectIndependently() = runBlocking {
        val sink = RecordingSink()
        val projector = PostTurnProjector(sink)
        assertTrue(projector.project("job-1", outcome()))
        assertTrue(projector.project("job-2", outcome()))
        assertEquals(2, sink.records.size)
    }

    private class RecordingSink : TurnProjectionSink {
        val records = mutableListOf<StructuredTurnOutcome>()
        override suspend fun record(outcome: StructuredTurnOutcome) {
            records += outcome
        }
    }
}
