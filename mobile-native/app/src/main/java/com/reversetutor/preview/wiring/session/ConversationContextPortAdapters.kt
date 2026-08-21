package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ErrorContextPort
import com.reversetutor.core.domain.ErrorReferenceContract
import com.reversetutor.core.domain.GraphContextPort
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
    private val graphRepository: GraphRepository
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
    ): List<String> =
        sessionNodes(sessionId)
            .filter { it.status == GraphNodeStatus.NeedsReview }
            .map { it.label }
            .distinct()
            .take(limit)
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
            .take(limit)
            .map { entry ->
                SourceReferenceContract(
                    id = entry.source.id,
                    title = entry.source.title,
                    excerpt = entry.chunks.firstOrNull()?.text ?: "",
                    sourceType = entry.source.type.name,
                    relevanceScore = 0f
                )
            }
}
