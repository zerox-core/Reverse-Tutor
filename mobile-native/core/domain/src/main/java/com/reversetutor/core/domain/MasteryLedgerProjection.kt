package com.reversetutor.core.domain

import kotlin.math.round

/**
 * Deterministic read-model that replays the append-only learning ledger
 * ([LearningFactReceipt]) into per-knowledge-point mastery snapshots.
 *
 * Parity target: the legacy engine's `upsert_mastery` fold:
 * - evidence gate: a score move requires a known evidence type other than
 *   "none" and result "passed"/"partial";
 * - EMA: score = 0.65 * old + 0.35 * target; partial evidence multiplies the
 *   target by 0.75;
 * - failed verification rolls back 8 points only when the old score > 50 and
 *   resets the review interval to 1 day;
 * - review ladder [1, 3, 7, 14] capped at the last rung;
 * - every receipt counts as an attempt, gated or not;
 * - per-step rounding to 2 decimals, clamped to [0, 100].
 *
 * The fold is a pure function of the receipt list (ordered by occurrence
 * time, then source turn id), so the same ledger always yields the same
 * snapshots. This is a read model only: no persistence, no new Room tables,
 * no frozen-layer writes.
 */
data class MasterySnapshot(
    val knowledgePoint: String,
    val score: Float,
    val reviewIntervalDays: Int,
    val nextReviewAtEpochMillis: Long,
    val attempts: Int,
    val band: String
)

class MasteryLedgerProjection(
    private val snapshotLimit: Int = DefaultSnapshotLimit
) {

    /**
     * Replays [facts] into bounded mastery snapshots, sorted by score
     * descending (then knowledge point) and capped at [snapshotLimit].
     * Blank knowledge points and unknown receipts never produce snapshots.
     */
    fun project(facts: List<LearningFactReceipt>): List<MasterySnapshot> {
        if (snapshotLimit <= 0) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<LearningFactReceipt>>()
        facts.asSequence()
            .filter { it.knowledgePoint.isNotBlank() }
            .sortedWith(compareBy({ it.occurredAtEpochMillis }, { it.sourceTurnId }))
            .forEach { fact ->
                grouped.getOrPut(fact.knowledgePoint) { mutableListOf() }.add(fact)
            }
        return grouped.values
            .asSequence()
            .map { fold(it) }
            .sortedWith(compareByDescending<MasterySnapshot> { it.score }.thenBy { it.knowledgePoint })
            .take(snapshotLimit)
            .toList()
    }

    /**
     * Returns knowledge points whose next review time is due at
     * [nowEpochMillis], earliest-due first. Pure projection over the same
     * ledger; the write side (scheduling, notifications) stays out of scope.
     */
    fun projectDue(facts: List<LearningFactReceipt>, nowEpochMillis: Long): List<String> =
        project(facts)
            .filter { it.nextReviewAtEpochMillis <= nowEpochMillis }
            .sortedWith(compareBy<MasterySnapshot> { it.nextReviewAtEpochMillis }.thenBy { it.knowledgePoint })
            .map { it.knowledgePoint }

    // --- Fold -------------------------------------------------------------

    private fun fold(facts: List<LearningFactReceipt>): MasterySnapshot {
        val knowledgePoint = facts.first().knowledgePoint
        var score = 0f
        var interval = 0
        var nextReviewAt = 0L
        facts.forEach { fact ->
            val target = evidenceTarget(fact.evidenceType)
            when {
                target != null && fact.result == ResultPassed -> {
                    score = ema(score, target)
                    interval = nextInterval(interval)
                    nextReviewAt = fact.occurredAtEpochMillis + interval * MillisPerDay
                }
                target != null && fact.result == ResultPartial -> {
                    score = ema(score, target * PartialEvidenceFactor)
                    interval = nextInterval(interval)
                    nextReviewAt = fact.occurredAtEpochMillis + interval * MillisPerDay
                }
                fact.result == ResultFailed -> {
                    // Failed verification is evidence, but not evidence for
                    // progress: roll back only above the threshold.
                    val rollback = if (score > FailedRollbackThreshold) FailedRollbackPoints else 0f
                    score = round2(score - rollback).coerceAtLeast(0f)
                    interval = 1
                    nextReviewAt = fact.occurredAtEpochMillis + MillisPerDay
                }
                else -> {
                    // Gated out (no usable evidence or status): attempts still
                    // count, score and schedule stay untouched.
                }
            }
        }
        return MasterySnapshot(
            knowledgePoint = knowledgePoint,
            score = score,
            reviewIntervalDays = interval,
            nextReviewAtEpochMillis = nextReviewAt,
            attempts = facts.size,
            band = band(score)
        )
    }

    private fun ema(oldScore: Float, target: Float): Float =
        round2((1f - EmaAlpha) * oldScore + EmaAlpha * target).coerceIn(0f, MaxScore)

    /** First ladder rung above [current], capped at the last rung. */
    private fun nextInterval(current: Int): Int {
        val ladder = DefaultReviewLadder
        for (days in ladder) {
            if (current < days) return days
        }
        return ladder.last()
    }

    /** Legacy score table lookup; "none" and unknown types gate out (null). */
    private fun evidenceTarget(evidenceType: String): Float? =
        if (evidenceType == EvidenceNone) null else EvidenceTargetScores[evidenceType]

    companion object {
        const val EmaAlpha = 0.35f
        const val PartialEvidenceFactor = 0.75f
        const val FailedRollbackThreshold = 50f
        const val FailedRollbackPoints = 8f
        const val MaxScore = 100f
        const val MillisPerDay = 86_400_000L
        const val DefaultSnapshotLimit = 10
        const val EvidenceNone = "none"
        const val ResultPassed = "passed"
        const val ResultPartial = "partial"
        const val ResultFailed = "failed"

        val DefaultReviewLadder: List<Int> = listOf(1, 3, 7, 14)

        val EvidenceTargetScores: Map<String, Float> = mapOf(
            "none" to 0f,
            "explanation" to 35f,
            "retrieval" to 55f,
            "transfer" to 72f,
            "delayed_retrieval" to 82f,
            "correction" to 90f
        )

        /**
         * Stable band codes (UI text mapping stays in the UI layer):
         * <10 untouched, <30 intuitive_entry, <50 guided_example,
         * <70 basic_application, <85 variant_handling, >=85 transferable.
         */
        fun band(score: Float): String = when {
            score < 10f -> "untouched"
            score < 30f -> "intuitive_entry"
            score < 50f -> "guided_example"
            score < 70f -> "basic_application"
            score < 85f -> "variant_handling"
            else -> "transferable"
        }

        private fun round2(value: Float): Float = round(value * 100f) / 100f
    }
}
