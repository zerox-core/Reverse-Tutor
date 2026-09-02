package com.reversetutor.core.data.learning

import com.reversetutor.core.data.local.dao.LearningLedgerDao
import com.reversetutor.core.data.local.entity.LearningFactReceiptEntity
import com.reversetutor.core.data.local.entity.ScopeSignalEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.LearningScopeGuard
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.WindowRef
import java.util.UUID

/**
 * Global learning ledger + minimal scope-signal repository. Exposes only
 * domain-safe `core:domain` contracts (never entities or DAOs).
 *
 * The ledger is append-only and holds normalized learning facts only; companion
 * memory is never routed here (it is a separate type surfaced by the companion
 * repository). Scope signals store only minimal provenance (category, count,
 * source turn, occurrence time), never raw user text, and only for windows that
 * actually run the learning scope guard.
 */
class LearningLedgerRepository(
    private val dao: LearningLedgerDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {

    suspend fun appendLearningFact(receipt: LearningFactReceipt, spaceId: String = defaultSpaceId): LearningFactReceipt {
        dao.insertReceipt(receipt.toEntity(spaceId))
        return receipt
    }

    /**
     * Durable post-turn projection guard. The same background job may be
     * replayed after process death, but it must never create a second learning
     * fact for the same window and turn id.
     */
    suspend fun appendLearningFactIfAbsent(
        receipt: LearningFactReceipt,
        spaceId: String = defaultSpaceId
    ): Boolean {
        if (dao.hasFactForTurn(receipt.sourceWindowId, receipt.sourceTurnId)) return false
        return runCatching {
            dao.insertReceipt(receipt.toEntity(spaceId))
            true
        }.getOrDefault(false)
    }

    suspend fun listLearningFacts(spaceId: String = defaultSpaceId): List<LearningFactReceipt> =
        dao.listFactsBySpace(spaceId).map { it.toDomain() }

    suspend fun listLearningFactsForWindow(windowId: String): List<LearningFactReceipt> =
        dao.listFactsByWindow(windowId).map { it.toDomain() }

    /** Records a minimal scope signal only if the window actually runs the scope guard. */
    suspend fun appendScopeSignal(
        signal: ScopeSignal,
        window: WindowRef,
        envelope: LearningIntentEnvelope?,
        spaceId: String = defaultSpaceId
    ): Boolean {
        if (!LearningScopeGuard.forWindow(window, envelope)) return false
        dao.insertScopeSignal(signal.toEntity(window.id, spaceId))
        return true
    }

    suspend fun listScopeSignals(windowId: String): List<ScopeSignal> =
        dao.listScopeSignals(windowId).map { it.toDomain() }
}

private fun LearningFactReceipt.toEntity(spaceId: String) = LearningFactReceiptEntity(
    id = "fact-${UUID.randomUUID()}",
    spaceId = spaceId,
    knowledgePoint = knowledgePoint,
    evidenceType = evidenceType,
    result = result,
    confidence = confidence,
    sourceWindowId = sourceWindowId,
    sourceTurnId = sourceTurnId,
    occurredAtEpochMillis = occurredAtEpochMillis
)

private fun LearningFactReceiptEntity.toDomain() = LearningFactReceipt(
    knowledgePoint = knowledgePoint,
    evidenceType = evidenceType,
    result = result,
    confidence = confidence,
    sourceWindowId = sourceWindowId,
    sourceTurnId = sourceTurnId,
    occurredAtEpochMillis = occurredAtEpochMillis
)

private fun ScopeSignal.toEntity(windowId: String, spaceId: String) = ScopeSignalEntity(
    id = "scope-${UUID.randomUUID()}",
    windowId = windowId,
    spaceId = spaceId,
    category = category,
    count = count,
    sourceTurnId = sourceTurnId,
    occurredAtEpochMillis = observedAtEpochMillis
)

private fun ScopeSignalEntity.toDomain() = ScopeSignal(
    category = category,
    count = count,
    sourceTurnId = sourceTurnId,
    observedAtEpochMillis = occurredAtEpochMillis
)
