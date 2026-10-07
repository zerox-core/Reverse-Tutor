package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.CompletedTurnPlanSnapshot
import com.reversetutor.core.domain.EvidenceRequirement
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.MasteryEvidenceTypeWire
import com.reversetutor.core.domain.TeachingAction
import com.reversetutor.core.domain.TurnPlan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTurnSignalsReaderTest {

    @Test
    fun completed_hint_plans_rebuild_the_hint_streak_without_transcript_access() = runBlocking {
        val reader = reader(
            plans = listOf(
                plan("job-1", 10L, TeachingAction.Hint),
                plan("job-2", 20L, TeachingAction.Hint)
            )
        )

        val signals = reader.read("window-1", Concept)

        assertEquals(TeachingAction.Hint, signals.lastAction)
        assertEquals(2, signals.askedForHintCount)
        assertEquals(0, signals.consecutiveSuccessCount)
        assertEquals(0, signals.consecutiveFailureCount)
        assertFalse(signals.lastEvidenceAvailable)
    }

    @Test
    fun only_locally_verified_passed_receipts_create_a_success_streak() = runBlocking {
        val reader = reader(
            plans = listOf(
                plan("job-1", 10L, TeachingAction.Diagnose),
                plan("job-2", 20L, TeachingAction.SocraticQuestion)
            ),
            receipts = listOf(receipt("job-1", MasteryEvidenceStatusWire.PASSED), receipt("job-2", MasteryEvidenceStatusWire.PASSED))
        )

        val signals = reader.read("window-1", Concept)

        assertEquals(2, signals.consecutiveSuccessCount)
        assertEquals(0, signals.consecutiveFailureCount)
        assertTrue(signals.lastEvidenceAvailable)
    }

    @Test
    fun a_verified_failure_is_distinct_from_provider_or_task_failure() = runBlocking {
        val reader = reader(
            plans = listOf(plan("job-1", 10L, TeachingAction.Practice)),
            receipts = listOf(receipt("job-1", MasteryEvidenceStatusWire.FAILED))
        )

        val signals = reader.read("window-1", Concept)

        assertEquals(0, signals.consecutiveSuccessCount)
        assertEquals(1, signals.consecutiveFailureCount)
        assertTrue(signals.lastEvidenceAvailable)
    }

    @Test
    fun a_partial_or_missing_receipt_never_invents_a_success_or_failure() = runBlocking {
        val partialReader = reader(
            plans = listOf(plan("job-1", 10L, TeachingAction.Practice)),
            receipts = listOf(receipt("job-1", MasteryEvidenceStatusWire.PARTIAL))
        )
        val missingReader = reader(plans = listOf(plan("job-2", 20L, TeachingAction.Practice)))

        val partial = partialReader.read("window-1", Concept)
        val missing = missingReader.read("window-1", Concept)

        assertEquals(0, partial.consecutiveSuccessCount)
        assertEquals(0, partial.consecutiveFailureCount)
        assertTrue(partial.lastEvidenceAvailable)
        assertEquals(0, missing.consecutiveSuccessCount)
        assertEquals(0, missing.consecutiveFailureCount)
        assertFalse(missing.lastEvidenceAvailable)
    }

    @Test
    fun another_concepts_history_cannot_change_this_concepts_plan() = runBlocking {
        val reader = reader(
            plans = listOf(
                plan("other-job", 10L, TeachingAction.Hint, "other-concept"),
                plan("current-job", 20L, TeachingAction.Explain, Concept)
            ),
            receipts = listOf(receipt("other-job", MasteryEvidenceStatusWire.FAILED))
        )

        val signals = reader.read("window-1", Concept)

        assertEquals(TeachingAction.Explain, signals.lastAction)
        assertEquals(0, signals.askedForHintCount)
        assertEquals(0, signals.consecutiveFailureCount)
    }

    private fun reader(
        plans: List<CompletedTurnPlanSnapshot>,
        receipts: List<LearningFactReceipt> = emptyList()
    ) = RecentTurnSignalsReader(
        loadCompletedPlans = { plans },
        loadLearningFacts = { receipts }
    )

    private fun plan(
        jobId: String,
        completedAtEpochMillis: Long,
        action: TeachingAction,
        conceptKey: String = Concept
    ) = CompletedTurnPlanSnapshot(
        jobId = jobId,
        completedAtEpochMillis = completedAtEpochMillis,
        turnPlan = TurnPlan(
            actionType = action,
            conceptKey = conceptKey,
            evidenceRequirement = EvidenceRequirement.UserAnswer
        )
    )

    private fun receipt(jobId: String, result: String) = LearningFactReceipt(
        knowledgePoint = Concept,
        evidenceType = MasteryEvidenceTypeWire.RETRIEVAL,
        result = result,
        confidence = 0.8f,
        sourceWindowId = "window-1",
        sourceTurnId = "background:$jobId",
        occurredAtEpochMillis = 100L
    )

    private companion object {
        const val Concept = "quadratic-discriminant"
    }

    // Task 5: the window identity is propagated to both loaders; other windows' data never loads.
    @Test
    fun window_identity_is_propagated_to_both_loaders() = runBlocking {
        val requestedWindows = mutableListOf<String>()
        val reader = RecentTurnSignalsReader(
            loadCompletedPlans = { window ->
                requestedWindows += "plans:" + window
                listOf(plan("job-w", 10L, TeachingAction.Hint))
            },
            loadLearningFacts = { window ->
                requestedWindows += "facts:" + window
                emptyList()
            }
        )
        reader.read("window-7", Concept)
        reader.read("window-8", Concept)
        assertEquals(
            listOf("plans:window-7", "facts:window-7", "plans:window-8", "facts:window-8"),
            requestedWindows
        )
    }

    // Task 5: an interleaved success/failure run stops at the first mismatch.
    @Test
    fun interleaved_verified_runs_stop_at_the_first_mismatch() = runBlocking {
        val reader = reader(
            plans = listOf(
                plan("job-a", 10L, TeachingAction.Practice),
                plan("job-b", 20L, TeachingAction.Practice),
                plan("job-c", 30L, TeachingAction.Practice)
            ),
            receipts = listOf(
                receipt("job-a", MasteryEvidenceStatusWire.PASSED),
                receipt("job-b", MasteryEvidenceStatusWire.FAILED),
                receipt("job-c", MasteryEvidenceStatusWire.PASSED)
            )
        )
        val signals = reader.read("window-1", Concept)
        assertEquals(1, signals.consecutiveSuccessCount)
        assertEquals(0, signals.consecutiveFailureCount)
        assertTrue(signals.lastEvidenceAvailable)
    }
}
