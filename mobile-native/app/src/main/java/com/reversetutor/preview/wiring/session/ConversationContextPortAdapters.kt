package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.learning.LearningLedgerRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ErrorContextPort
import com.reversetutor.core.domain.ErrorReferenceContract
import com.reversetutor.core.domain.GraphContextPort
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.MasteryFactContextPort
import com.reversetutor.core.domain.MasteryLedgerProjection
import com.reversetutor.core.domain.MemoryContextPort
import com.reversetutor.core.domain.MemoryReferenceContract
import com.reversetutor.core.domain.MessageContextPort
import com.reversetutor.core.domain.SourceContextPort
import com.reversetutor.core.domain.SourceReferenceContract
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.GraphScope
import com.reversetutor.core.model.GraphSnapshotResult
import com.reversetutor.core.model.MemoryItemKind

/**
 * Adapts existing repository read capabilities to the non-frozen conversation
 * context source ports. Each adapter translates frozen repository results into
 * the safe domain contracts defined in `core:domain`.
 *
 * Scoping caveats (see tasks/native-p2-007-api-fact-map.md §5): the frozen
 * `MemoryRepository` and `SourceRepository` are space-scoped, not
 * session-scoped. The adapters faithfully return the space's data and do not
 * fabricate session-level filtering. The assembler's bounded read + partial
 * failure semantics turn empty data into empty categories (never failures).
 */

class MessageContextPortAdapter(
    private val messageRepository: MessageRepository
) : MessageContextPort {
    override suspend fun listRecentMessages(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<ContextMessage> =
        messageRepository.listMessages(sessionId)
            .takeLast(limit)
            .map { ContextMessage(it.id, it.role.name.lowercase(), it.text, it.createdAtEpochMillis) }
}

class MemoryContextPortAdapter(
    private val memoryRepository: MemoryRepository
) : MemoryContextPort {
    override suspend fun listMemoryReferences(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<MemoryReferenceContract> =
        memoryRepository.snapshot(spaceId).items
            .filter { it.kind != MemoryItemKind.Error }
            .sortedByDescending { it.createdAtEpochMillis }
            .take(limit)
            .map { MemoryReferenceContract(it.id, it.title, 0f, it.createdAtEpochMillis) }
}

class ErrorContextPortAdapter(
    private val memoryRepository: MemoryRepository
) : ErrorContextPort {
    override suspend fun listHistoricalErrors(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<ErrorReferenceContract> =
        memoryRepository.snapshot(spaceId).errors
            .filterNot { it.resolved }
            .sortedByDescending { it.createdAtEpochMillis }
            .take(limit)
            .map { ErrorReferenceContract(it.id, it.code ?: it.title, it.title, it.createdAtEpochMillis) }
}

class GraphContextPortAdapter(
    private val graphRepository: GraphRepository,
    private val learningLedgerRepository: LearningLedgerRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : GraphContextPort {

    private suspend fun sessionNodes(sessionId: String): List<com.reversetutor.core.model.GraphNode> =
        when (val result = graphRepository.snapshot(GraphScope.Session(sessionId))) {
            is GraphSnapshotResult.Ready -> result.snapshot.nodes
            is GraphSnapshotResult.Empty -> emptyList()
            is GraphSnapshotResult.Error -> emptyList()
        }

    override suspend fun listPrerequisiteGaps(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<String> =
        sessionNodes(sessionId)
            .filter { it.kind == GraphNodeKind.Requirement || it.status == GraphNodeStatus.NeedsReview }
            .map { it.label }
            .distinct()
            .take(limit)

    override suspend fun listPendingReviewPoints(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<String> {
        // Old-parity due-review selection (legacy `list_due_reviews`): the
        // deterministic mastery ladder decides which knowledge points are due
        // at read time; no persisted schedule and no new write side. Due
        // points come first (earliest-due ordering), then graph NeedsReview
        // labels, deduplicated and bounded by [limit].
        val due = learningLedgerRepository
            ?.let { repo ->
                MasteryLedgerProjection(snapshotLimit = DueProjectionSnapshotLimit)
                    .projectDue(repo.listLearningFacts(spaceId), nowEpochMillis())
            }
            .orEmpty()
        val graphLabels = sessionNodes(sessionId)
            .filter { it.status == GraphNodeStatus.NeedsReview }
            .map { it.label }
        return (due + graphLabels)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
    }

    private companion object {
        // Wider than the assembler's top-10 evidence cap so low-score but
        // due knowledge points are not starved by the score sort before the
        // due filter runs; the caller still bounds the final list.
        const val DueProjectionSnapshotLimit = 50
    }
}

class SourceContextPortAdapter(
    private val sourceRepository: SourceRepository
) : SourceContextPort {
    override suspend fun listSourceEvidence(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<SourceReferenceContract> =
        sourceRepository.listSourcesWithChunks(spaceId)
            // Imports and reprocessing stamp a new local creation time. Reading
            // newest-first makes a just-added chat/source document available to
            // the next turn without altering an already snapshotted job.
            .sortedByDescending { it.source.createdAtEpochMillis }
            .take(limit)
            .map { entry ->
                SourceReferenceContract(
                    id = entry.source.id,
                    title = entry.source.title,
                    excerpt = entry.chunks.firstOrNull()?.text ?: "",
                    sourceType = entry.source.type.name,
                    relevanceScore = 0f,
                    // Local version token: import/reprocessing stamps a new
                    // createdAt, so the next turn binds the newer revision while
                    // already-saved snapshots keep the revision they captured.
                    sourceRevision = "rev-${entry.source.id}-${entry.source.createdAtEpochMillis}"
                )
            }
}

class MasteryFactContextPortAdapter(
    private val learningLedgerRepository: LearningLedgerRepository
) : MasteryFactContextPort {
    override suspend fun listMasteryFacts(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<LearningFactReceipt> =
        learningLedgerRepository.listLearningFacts(spaceId)
            // Space-scoped like Memory/Source adapters: the append-only ledger
            // is keyed by space, and the deterministic mastery fold isolates
            // scores per knowledge point. Newest receipts first so the bounded
            // read keeps the most recent evidence.
            .sortedByDescending { it.occurredAtEpochMillis }
            .take(limit)
}
