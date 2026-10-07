package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MasteryLedgerProjection]:
 * - deterministic replay of the append-only learning ledger,
 * - parity with the legacy engine `upsert_mastery` semantics
 *   (evidence gate, EMA alpha=0.35, partial x0.75 factor, failed rollback
 *   above 50, review ladder [1,3,7,14], band thresholds),
 * - per-knowledge-point isolation, ordering, and bounded output.
 */
class MasteryLedgerProjectionTest {

    private val dayMillis = 86_400_000L
    private val t0 = 1_700_000_000_000L

    private fun fact(
        kp: String,
        evidence: String,
        result: String,
        at: Long,
        turn: String = "turn-$at"
    ): LearningFactReceipt = LearningFactReceipt(
        knowledgePoint = kp,
        evidenceType = evidence,
        result = result,
        confidence = 0.8f,
        sourceWindowId = "window-1",
        sourceTurnId = turn,
        occurredAtEpochMillis = at
    )

    @Test
    fun passedExplanationSeedsScoreFromZero() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(fact("因式分解", "explanation", "passed", t0))
        )
        val snap = snapshots.single()
        assertEquals("因式分解", snap.knowledgePoint)
        // 0.35 * 35 = 12.25
        assertEquals(12.25f, snap.score, 0.001f)
        assertEquals(1, snap.reviewIntervalDays)
        assertEquals(t0 + dayMillis, snap.nextReviewAtEpochMillis)
        assertEquals(1, snap.attempts)
        assertEquals("intuitive_entry", snap.band)
    }

    @Test
    fun partialEvidenceReducesTarget() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(fact("因式分解", "retrieval", "partial", t0))
        )
        // 0.35 * (55 * 0.75) = 14.4375 -> 14.44
        assertEquals(14.44f, snapshots.single().score, 0.011f)
    }

    @Test
    fun emaFoldsAcrossSequentialFactsInTimeOrder() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("因式分解", "retrieval", "passed", t0),
                fact("因式分解", "explanation", "passed", t0 + 1_000L, turn = "turn-2")
            )
        )
        val snap = snapshots.single()
        // 19.25 -> 0.65 * 19.25 + 0.35 * 35 = 24.7625 -> 24.76
        assertEquals(24.76f, snap.score, 0.011f)
        assertEquals(3, snap.reviewIntervalDays)
        assertEquals(2, snap.attempts)
        assertEquals(t0 + 1_000L + 3 * dayMillis, snap.nextReviewAtEpochMillis)
    }

    @Test
    fun failedVerificationRollsBackOnlyAboveFifty() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("函数", "correction", "passed", t0),
                fact("函数", "correction", "passed", t0 + 1_000L, turn = "turn-2"),
                fact("函数", "correction", "failed", t0 + 2_000L, turn = "turn-3")
            )
        )
        val snap = snapshots.single()
        // 31.5 -> 51.975 -> rollback 8 -> ~43.98
        assertEquals(43.98f, snap.score, 0.021f)
        assertEquals(1, snap.reviewIntervalDays)
        assertEquals(t0 + 2_000L + dayMillis, snap.nextReviewAtEpochMillis)
    }

    @Test
    fun failedVerificationBelowFiftyLeavesScoreUnchanged() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("函数", "retrieval", "passed", t0),
                fact("函数", "retrieval", "failed", t0 + 1_000L, turn = "turn-2")
            )
        )
        // 19.25 -> failed with old <= 50 -> no rollback
        assertEquals(19.25f, snapshots.single().score, 0.001f)
        assertEquals(1, snapshots.single().reviewIntervalDays)
    }

    @Test
    fun ladderProgressionAndCap() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("数列", "explanation", "passed", t0),
                fact("数列", "retrieval", "passed", t0 + dayMillis, turn = "turn-2"),
                fact("数列", "transfer", "passed", t0 + 2 * dayMillis, turn = "turn-3"),
                fact("数列", "delayed_retrieval", "passed", t0 + 3 * dayMillis, turn = "turn-4")
            )
        )
        val snap = snapshots.single()
        // ladder 1 -> 3 -> 7 -> 14
        assertEquals(14, snap.reviewIntervalDays)
        assertEquals(4, snap.attempts)
        assertEquals(t0 + 3 * dayMillis + 14 * dayMillis, snap.nextReviewAtEpochMillis)
    }

    @Test
    fun ladderCapsAtLastRung() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("数列", "explanation", "passed", t0),
                fact("数列", "retrieval", "passed", t0 + dayMillis, turn = "turn-2"),
                fact("数列", "transfer", "passed", t0 + 2 * dayMillis, turn = "turn-3"),
                fact("数列", "delayed_retrieval", "passed", t0 + 3 * dayMillis, turn = "turn-4"),
                fact("数列", "correction", "passed", t0 + 4 * dayMillis, turn = "turn-5")
            )
        )
        // 1 -> 3 -> 7 -> 14 -> capped at 14
        assertEquals(14, snapshots.single().reviewIntervalDays)
    }

    @Test
    fun noneOrUnknownEvidenceDoesNotChangeScore() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("几何", "none", "passed", t0),
                fact("几何", "mystery_type", "passed", t0 + 1_000L, turn = "turn-2"),
                fact("几何", "explanation", "none", t0 + 2_000L, turn = "turn-3"),
                fact("几何", "explanation", "unknown_status", t0 + 3_000L, turn = "turn-4")
            )
        )
        val snap = snapshots.single()
        assertEquals(0f, snap.score, 0.0001f)
        // attempts still count every receipt, mirroring the legacy upsert.
        assertEquals(4, snap.attempts)
        assertEquals("untouched", snap.band)
    }

    @Test
    fun knowledgePointsAreIsolatedAndSortedByScoreDescending() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact("kp-a", "explanation", "passed", t0),
                fact("kp-b", "correction", "passed", t0 + 1_000L),
                fact("kp-a", "retrieval", "partial", t0 + 2_000L)
            )
        )
        // kp-a: 12.25 -> 22.4 ; kp-b: 31.5
        assertEquals(listOf("kp-b", "kp-a"), snapshots.map { it.knowledgePoint })
    }

    @Test
    fun blankKnowledgePointsAreFiltered() {
        val snapshots = MasteryLedgerProjection().project(
            listOf(
                fact(" ", "explanation", "passed", t0),
                fact("", "correction", "passed", t0 + 1_000L),
                fact("kp-a", "explanation", "passed", t0 + 2_000L)
            )
        )
        assertEquals(listOf("kp-a"), snapshots.map { it.knowledgePoint })
    }

    @Test
    fun foldIsDeterministicRegardlessOfInputOrder() {
        val facts = listOf(
            fact("kp-a", "explanation", "passed", t0),
            fact("kp-b", "retrieval", "passed", t0 + 1_000L),
            fact("kp-a", "correction", "passed", t0 + 5_000L)
        )
        val forward = MasteryLedgerProjection().project(facts)
        val reversed = MasteryLedgerProjection().project(facts.reversed())
        assertEquals(forward, reversed)
    }

    @Test
    fun projectDueReturnsOnlyOverduePointsSortedByNextReview() {
        val facts = listOf(
            fact("kp-a", "explanation", "passed", t0),
            fact("kp-b", "explanation", "passed", t0 + 5 * dayMillis)
        )
        // kp-a due at t0 + 1d ; kp-b due at t0 + 6d
        assertEquals(
            listOf("kp-a"),
            MasteryLedgerProjection().projectDue(facts, nowEpochMillis = t0 + 2 * dayMillis)
        )
        assertEquals(
            listOf("kp-a", "kp-b"),
            MasteryLedgerProjection().projectDue(facts, nowEpochMillis = t0 + 7 * dayMillis)
        )
        assertTrue(MasteryLedgerProjection().projectDue(facts, nowEpochMillis = t0).isEmpty())
    }

    @Test
    fun bandThresholdsMatchLegacyEngine() {
        assertEquals("untouched", MasteryLedgerProjection.band(0f))
        assertEquals("untouched", MasteryLedgerProjection.band(9.99f))
        assertEquals("intuitive_entry", MasteryLedgerProjection.band(10f))
        assertEquals("intuitive_entry", MasteryLedgerProjection.band(29.99f))
        assertEquals("guided_example", MasteryLedgerProjection.band(30f))
        assertEquals("guided_example", MasteryLedgerProjection.band(49.99f))
        assertEquals("basic_application", MasteryLedgerProjection.band(50f))
        assertEquals("basic_application", MasteryLedgerProjection.band(69.99f))
        assertEquals("variant_handling", MasteryLedgerProjection.band(70f))
        assertEquals("variant_handling", MasteryLedgerProjection.band(84.99f))
        assertEquals("transferable", MasteryLedgerProjection.band(85f))
        assertEquals("transferable", MasteryLedgerProjection.band(100f))
    }

    @Test
    fun snapshotLimitBoundsOutput() {
        val facts = listOf(
            fact("kp-a", "explanation", "passed", t0),
            fact("kp-b", "correction", "passed", t0 + 1_000L)
        )
        val snapshots = MasteryLedgerProjection(snapshotLimit = 1).project(facts)
        assertEquals(1, snapshots.size)
        assertEquals("kp-b", snapshots[0].knowledgePoint)
    }

    @Test
    fun emptyLedgerYieldsEmptyProjection() {
        assertTrue(MasteryLedgerProjection().project(emptyList()).isEmpty())
    }
}
