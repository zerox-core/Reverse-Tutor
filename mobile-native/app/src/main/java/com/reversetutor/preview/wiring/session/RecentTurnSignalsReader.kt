package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.CompletedTurnPlanSnapshot
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.GuidedLearningContracts
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.MasteryEvidenceTypeWire
import com.reversetutor.core.domain.RecentTurnSignals
import com.reversetutor.core.domain.TeachingAction

/**
 * Rebuilds the selector's short-term signals from durable, structured records.
 *
 * Completed plans supply only the prior teaching action. Learning success and
 * failure are read solely from locally verified ledger receipts linked to those
 * jobs. Failed/cancelled Provider work has no completed-plan snapshot and can
 * therefore never become a learning failure here.
 */
internal class RecentTurnSignalsReader(
    private val loadCompletedPlans: suspend (String) -> List<CompletedTurnPlanSnapshot>,
    private val loadLearningFacts: suspend (String) -> List<LearningFactReceipt>
) {
    suspend fun read(windowId: String, conceptKey: String): RecentTurnSignals {
        val normalizedConceptKey = GuidedLearningContracts.normalizeConceptKey(conceptKey)
        val plans = loadCompletedPlans(windowId)
            .filter { it.turnPlan.conceptKey == normalizedConceptKey }
            .sortedBy { it.completedAtEpochMillis }
            .takeLast(MaxRecentTurns)
        if (plans.isEmpty()) return RecentTurnSignals()

        val receiptsByJobId = loadLearningFacts(windowId)
            .mapNotNull { receipt ->
                receipt.sourceTurnId
                    .removePrefix(BackgroundTurnPrefix)
                    .takeIf { receipt.sourceTurnId.startsWith(BackgroundTurnPrefix) }
                    ?.let { jobId -> jobId to receipt }
            }
            .toMap()

        val latest = plans.last()
        val latestReceipt = receiptsByJobId[latest.jobId]
        val (successes, failures) = trailingVerifiedResultCounts(plans, receiptsByJobId)

        return RecentTurnSignals(
            lastAction = latest.turnPlan.actionType,
            askedForHintCount = plans.asReversed()
                .takeWhile { it.turnPlan.actionType == TeachingAction.Hint }
                .count(),
            consecutiveSuccessCount = successes,
            consecutiveFailureCount = failures,
            // The verifier-backed receipt proves that a local evidence check
            // occurred. It does not infer a user answer from transcript text.
            lastEvidenceAvailable = latestReceipt.isLocallyVerified()
        ).normalized()
    }

    private fun trailingVerifiedResultCounts(
        plans: List<CompletedTurnPlanSnapshot>,
        receiptsByJobId: Map<String, LearningFactReceipt>
    ): Pair<Int, Int> {
        var successes = 0
        var failures = 0
        var mode: VerifiedResult? = null

        for (plan in plans.asReversed()) {
            when (receiptsByJobId[plan.jobId].verifiedResult()) {
                VerifiedResult.Success -> {
                    if (mode == VerifiedResult.Failure) break
                    mode = VerifiedResult.Success
                    successes++
                }
                VerifiedResult.Failure -> {
                    if (mode == VerifiedResult.Success) break
                    mode = VerifiedResult.Failure
                    failures++
                }
                null -> break
            }
        }
        return successes to failures
    }

    private fun LearningFactReceipt?.isLocallyVerified(): Boolean =
        this != null &&
            evidenceType in (MasteryEvidenceTypeWire.ALL - MasteryEvidenceTypeWire.NONE) &&
            result in (MasteryEvidenceStatusWire.ALL - MasteryEvidenceStatusWire.NONE)

    private fun LearningFactReceipt?.verifiedResult(): VerifiedResult? {
        val receipt = this ?: return null
        if (!receipt.isLocallyVerified()) return null
        return when (receipt.result) {
            MasteryEvidenceStatusWire.PASSED -> VerifiedResult.Success
            MasteryEvidenceStatusWire.FAILED -> VerifiedResult.Failure
            else -> null
        }
    }

    private enum class VerifiedResult { Success, Failure }

    private companion object {
        const val BackgroundTurnPrefix = "background:"
        const val MaxRecentTurns = 8
    }
}
