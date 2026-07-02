package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.MemoryItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class GraphRenderStatus(
    val label: String
) {
    Loading("Loading"),
    Empty("Empty"),
    Ready("Native"),
    Invalid("Needs review"),
    Large("Large graph")
}

enum class GraphScope(
    val label: String
) {
    Session("Session graph"),
    Global("Global graph")
}

enum class GraphNodeReviewAction(
    val label: String,
    val targetStatus: GraphNodeStatus
) {
    MarkNeedsReview("Mark needs review", GraphNodeStatus.NeedsReview),
    Approve("Approve", GraphNodeStatus.Approved),
    Restore("Restore active", GraphNodeStatus.Active),
    Archive("Archive", GraphNodeStatus.Archived),
    Hide("Hide", GraphNodeStatus.Hidden)
}

data class KnowledgeGraphUiState(
    val scope: GraphScope,
    val status: GraphRenderStatus,
    val title: String,
    val summary: String,
    val nodes: List<GraphLayoutNode>,
    val visibleEdges: List<GraphLayoutEdge>,
    val invalidEdgeCount: Int,
    val selectedNodeId: String? = null
) {
    val selectedNode: GraphLayoutNode?
        get() = nodes.firstOrNull { it.id == selectedNodeId }

    val edgeCount: Int
        get() = visibleEdges.size + invalidEdgeCount

    fun withSelection(nodeId: String?): KnowledgeGraphUiState =
        copy(selectedNodeId = nodeId?.takeIf { id -> nodes.any { it.id == id } })

    fun hitTest(
        x: Float,
        y: Float
    ): GraphLayoutNode? =
        nodes
            .asReversed()
            .firstOrNull { node ->
                val dx = node.x - x
                val dy = node.y - y
                val distance = sqrt(dx * dx + dy * dy)
                distance <= node.hitRadius
            }

    fun relatedEdges(nodeId: String): List<GraphLayoutEdge> =
        visibleEdges.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }

    companion object {
        fun loading(scope: GraphScope = GraphScope.Session): KnowledgeGraphUiState =
            KnowledgeGraphUiState(
                scope = scope,
                status = GraphRenderStatus.Loading,
                title = "Loading graph",
                summary = "Native graph data is loading.",
                nodes = emptyList(),
                visibleEdges = emptyList(),
                invalidEdgeCount = 0
            )

        fun from(
            nodes: List<GraphNode>,
            edges: List<GraphEdge>,
            selectedNodeId: String? = null,
            scope: GraphScope = GraphScope.Session,
            memoryItems: List<MemoryItem> = emptyList()
        ): KnowledgeGraphUiState {
            val evidenceByMemoryId = memoryItems.associateBy { it.id }
            val layoutNodes = layout(nodes, evidenceByMemoryId)
            val nodeIds = layoutNodes.map { it.id }.toSet()
            val invalidEdgeCount = edges.count { it.fromNodeId !in nodeIds || it.toNodeId !in nodeIds }
            val visibleEdges = edges
                .filter { it.fromNodeId in nodeIds && it.toNodeId in nodeIds }
                .sortedWith(compareBy<GraphEdge> { it.createdAtEpochMillis }.thenBy { it.id })
                .map { edge ->
                    GraphLayoutEdge(
                        id = edge.id,
                        fromNodeId = edge.fromNodeId,
                        toNodeId = edge.toNodeId,
                        relation = edge.relation,
                        sourceMemoryId = edge.sourceMemoryId
                    )
                }
            val status = when {
                layoutNodes.isEmpty() -> GraphRenderStatus.Empty
                layoutNodes.size > largeGraphNodeThreshold -> GraphRenderStatus.Large
                invalidEdgeCount > 0 -> GraphRenderStatus.Invalid
                else -> GraphRenderStatus.Ready
            }
            return KnowledgeGraphUiState(
                scope = scope,
                status = status,
                title = status.titleFor(scope),
                summary = status.summaryFor(
                    scope = scope,
                    nodes = layoutNodes.size,
                    visibleEdges = visibleEdges.size,
                    invalidEdges = invalidEdgeCount
                ),
                nodes = layoutNodes,
                visibleEdges = visibleEdges,
                invalidEdgeCount = invalidEdgeCount,
                selectedNodeId = selectedNodeId?.takeIf { it in nodeIds }
            )
        }

        private const val largeGraphNodeThreshold = 60

        private fun layout(
            nodes: List<GraphNode>,
            evidenceByMemoryId: Map<String, MemoryItem>
        ): List<GraphLayoutNode> {
            val sorted = nodes
                .filter { it.status != GraphNodeStatus.Hidden }
                .sortedWith(compareBy<GraphNode> { it.kind.name }.thenBy { it.label }.thenBy { it.id })
            if (sorted.isEmpty()) return emptyList()
            if (sorted.size == 1) {
                return listOf(
                    sorted.single().toLayoutNode(
                        x = 0.5f,
                        y = 0.5f,
                        radius = 0.07f,
                        evidence = sorted.single().sourceMemoryId?.let(evidenceByMemoryId::get)
                    )
                )
            }
            val radius = if (sorted.size > 20) 0.42f else 0.36f
            return sorted.mapIndexed { index, node ->
                val angle = -PI / 2.0 + (2.0 * PI * index.toDouble() / sorted.size.toDouble())
                node.toLayoutNode(
                    x = (0.5 + radius * cos(angle)).toFloat(),
                    y = (0.5 + radius * sin(angle)).toFloat(),
                    radius = if (sorted.size > 40) 0.035f else 0.055f,
                    evidence = node.sourceMemoryId?.let(evidenceByMemoryId::get)
                )
            }
        }

        private fun GraphNode.toLayoutNode(
            x: Float,
            y: Float,
            radius: Float,
            evidence: MemoryItem?
        ): GraphLayoutNode =
            GraphLayoutNode(
                id = id,
                label = label,
                kind = kind,
                status = status,
                x = x.coerceIn(0.08f, 0.92f),
                y = y.coerceIn(0.08f, 0.92f),
                radius = radius,
                sourceMemoryId = sourceMemoryId,
                sourceMessageId = evidence?.sourceMessageId,
                sourceId = evidence?.sourceId,
                evidenceTitle = evidence?.title,
                evidenceBody = evidence?.body
            )

        private fun GraphRenderStatus.titleFor(scope: GraphScope): String =
            when (this) {
                GraphRenderStatus.Loading -> "Loading graph"
                GraphRenderStatus.Empty -> "No graph nodes yet"
                GraphRenderStatus.Ready -> scope.label
                GraphRenderStatus.Invalid -> "Graph needs review"
                GraphRenderStatus.Large -> "Large graph"
            }

        private fun GraphRenderStatus.summaryFor(
            scope: GraphScope,
            nodes: Int,
            visibleEdges: Int,
            invalidEdges: Int
        ): String =
            when (this) {
                GraphRenderStatus.Loading -> "Native graph data is loading."
                GraphRenderStatus.Empty -> "Native Canvas graph is ready, but no nodes are stored for this space yet."
                GraphRenderStatus.Ready -> "${scope.label}: $nodes nodes and $visibleEdges relations rendered with native Canvas."
                GraphRenderStatus.Invalid -> "${scope.label}: $nodes nodes and $visibleEdges valid relations rendered; $invalidEdges relation needs repair before graph parity."
                GraphRenderStatus.Large -> "${scope.label}: $nodes nodes are available. Native rendering stays active while detailed clustering remains a follow-up."
            }
    }
}

data class GraphLayoutNode(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val status: GraphNodeStatus,
    val x: Float,
    val y: Float,
    val radius: Float,
    val sourceMemoryId: String? = null,
    val sourceMessageId: String? = null,
    val sourceId: String? = null,
    val evidenceTitle: String? = null,
    val evidenceBody: String? = null
) {
    val hitRadius: Float
        get() = (radius * 1.35f).coerceAtLeast(0.07f)

    val kindLabel: String
        get() = kind.name

    val statusLabel: String
        get() = when (status) {
            GraphNodeStatus.Active -> "Active"
            GraphNodeStatus.NeedsReview -> "Needs review"
            GraphNodeStatus.Approved -> "Approved"
            GraphNodeStatus.Hidden -> "Hidden"
            GraphNodeStatus.Archived -> "Archived"
        }

    val reviewActions: List<GraphNodeReviewAction>
        get() = when (status) {
            GraphNodeStatus.Active -> listOf(
                GraphNodeReviewAction.MarkNeedsReview,
                GraphNodeReviewAction.Approve,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.NeedsReview -> listOf(
                GraphNodeReviewAction.Approve,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Approved -> listOf(
                GraphNodeReviewAction.MarkNeedsReview,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Archived -> listOf(
                GraphNodeReviewAction.Restore,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Hidden -> listOf(GraphNodeReviewAction.Restore)
        }

    val reviewCards: List<GraphReviewCard>
        get() = buildList {
            if (!evidenceTitle.isNullOrBlank() || !evidenceBody.isNullOrBlank()) {
                add(
                    GraphReviewCard(
                        title = evidenceTitle ?: label,
                        body = evidenceBody ?: "Evidence is linked but has no readable summary.",
                        sourceMessageId = sourceMessageId,
                        sourceId = sourceId
                    )
                )
            }
            if (status == GraphNodeStatus.NeedsReview) {
                add(
                    GraphReviewCard(
                        title = "Review required",
                        body = "Confirm the node label, relationships, and linked evidence before approving this graph node.",
                        sourceMessageId = sourceMessageId,
                        sourceId = sourceId
                    )
                )
            }
        }
}

data class GraphReviewCard(
    val title: String,
    val body: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

data class GraphLayoutEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val sourceMemoryId: String? = null
)
