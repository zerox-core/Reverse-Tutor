package com.reversetutor.core.data.graph

import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.DomainError
import com.reversetutor.core.model.DomainErrorCode
import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphEmptyReason
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.GraphScope
import com.reversetutor.core.model.GraphSnapshot as DomainGraphSnapshot
import com.reversetutor.core.model.GraphSnapshotResult
import java.util.UUID

class GraphRepository(
    private val graphDao: GraphDao,
    private val sessionDao: SessionDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun snapshot(spaceId: String = defaultSpaceId): GraphSnapshot =
        GraphSnapshot(
            nodes = graphDao.listNodesBySpace(spaceId).map { it.toDomain() },
            edges = graphDao.listEdgesBySpace(spaceId).map { it.toDomain() }
        )

    suspend fun snapshot(scope: GraphScope): GraphSnapshotResult =
        try {
            when (scope) {
                is GraphScope.Global -> buildResult(
                    nodes = graphDao.listNodesBySpace(scope.spaceId).map { it.toDomain() },
                    edges = graphDao.listEdgesBySpace(scope.spaceId).map { it.toDomain() }
                )

                is GraphScope.Session -> {
                    val session = sessionDao.getById(scope.sessionId)
                    if (session == null) {
                        GraphSnapshotResult.Error(notFoundError())
                    } else {
                        val nodes = graphDao.listNodesBySession(scope.sessionId).map { it.toDomain() }
                        val nodeIds = nodes.mapTo(linkedSetOf()) { it.id }
                        val edges = graphDao.listEdgesBySpace(session.spaceId)
                            .map { it.toDomain() }
                            .filter { it.fromNodeId in nodeIds && it.toNodeId in nodeIds }
                        buildResult(nodes, edges)
                    }
                }
            }
        } catch (_: Exception) {
            GraphSnapshotResult.Error(storageError())
        }

    suspend fun saveNode(
        input: GraphNodeInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId
    ): GraphNode? {
        val normalized = input.normalized() ?: return null
        val node = GraphNode(
            id = normalized.id,
            spaceId = spaceId,
            label = normalized.label,
            kind = normalized.kind,
            createdAtEpochMillis = nowEpochMillis,
            status = normalized.status,
            sourceMemoryId = normalized.sourceMemoryId
        )
        graphDao.insertNode(node.toEntity())
        return node
    }

    suspend fun updateNode(
        input: GraphNodeEditInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId
    ): GraphNode? {
        val normalized = input.normalized() ?: return null
        val existing = snapshot(spaceId).nodes.firstOrNull { it.id == normalized.id } ?: return null
        val updated = existing.copy(
            label = normalized.label,
            kind = normalized.kind,
            status = normalized.status,
            sourceMemoryId = normalized.sourceMemoryId,
            createdAtEpochMillis = existing.createdAtEpochMillis
        )
        graphDao.insertNode(updated.toEntity())
        return updated
    }

    suspend fun saveEdge(
        input: GraphEdgeInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId
    ): GraphEdge? {
        val normalized = input.normalized() ?: return null
        val edge = GraphEdge(
            id = normalized.id,
            spaceId = spaceId,
            fromNodeId = normalized.fromNodeId,
            toNodeId = normalized.toNodeId,
            relation = normalized.relation,
            createdAtEpochMillis = nowEpochMillis,
            sourceMemoryId = normalized.sourceMemoryId
        )
        graphDao.insertEdge(edge.toEntity())
        return edge
    }

    private fun buildResult(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>
    ): GraphSnapshotResult {
        if (nodes.isEmpty()) {
            return GraphSnapshotResult.Empty(GraphEmptyReason.NoExtractedNodes)
        }
        val nodeIds = nodes.mapTo(hashSetOf()) { it.id }
        val validEdges = edges.filter {
            it.fromNodeId in nodeIds && it.toNodeId in nodeIds
        }
        return GraphSnapshotResult.Ready(
            DomainGraphSnapshot(
                nodes = nodes,
                edges = validEdges,
                invalidEdgeCount = edges.size - validEdges.size
            )
        )
    }

    private fun notFoundError(): DomainError = DomainError(
        code = DomainErrorCode.NotFound,
        retryable = false,
        safeMessage = "graph_scope_not_found"
    )

    private fun storageError(): DomainError = DomainError(
        code = DomainErrorCode.StorageUnavailable,
        retryable = true,
        safeMessage = "graph_storage_unavailable"
    )
}

data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNodeInput(
    val id: String = "node-${UUID.randomUUID()}",
    val label: String,
    val kind: GraphNodeKind = GraphNodeKind.Other,
    val status: GraphNodeStatus = GraphNodeStatus.Active,
    val sourceMemoryId: String? = null
) {
    fun normalized(): GraphNodeInput? {
        val normalizedId = id.trim()
        val normalizedLabel = label.trim()
        if (normalizedId.isEmpty() || normalizedLabel.isEmpty()) return null
        return copy(
            id = normalizedId,
            label = normalizedLabel,
            sourceMemoryId = sourceMemoryId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

data class GraphNodeEditInput(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val status: GraphNodeStatus,
    val sourceMemoryId: String? = null
) {
    fun normalized(): GraphNodeEditInput? {
        val normalizedId = id.trim()
        val normalizedLabel = label.trim()
        if (normalizedId.isEmpty() || normalizedLabel.isEmpty()) return null
        return copy(
            id = normalizedId,
            label = normalizedLabel,
            sourceMemoryId = sourceMemoryId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

data class GraphEdgeInput(
    val id: String = "edge-${UUID.randomUUID()}",
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val sourceMemoryId: String? = null
) {
    fun normalized(): GraphEdgeInput? {
        val normalizedId = id.trim()
        val normalizedFrom = fromNodeId.trim()
        val normalizedTo = toNodeId.trim()
        val normalizedRelation = relation.trim()
        if (normalizedId.isEmpty() || normalizedFrom.isEmpty() || normalizedTo.isEmpty() || normalizedRelation.isEmpty()) {
            return null
        }
        return copy(
            id = normalizedId,
            fromNodeId = normalizedFrom,
            toNodeId = normalizedTo,
            relation = normalizedRelation,
            sourceMemoryId = sourceMemoryId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
