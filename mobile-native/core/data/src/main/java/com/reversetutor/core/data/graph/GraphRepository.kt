package com.reversetutor.core.data.graph

import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import java.util.UUID

class GraphRepository(
    private val graphDao: GraphDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun snapshot(spaceId: String = defaultSpaceId): GraphSnapshot =
        GraphSnapshot(
            nodes = graphDao.listNodesBySpace(spaceId).map { it.toDomain() },
            edges = graphDao.listEdgesBySpace(spaceId).map { it.toDomain() }
        )

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
