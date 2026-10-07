package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind

/**
 * Presentation-only state for a graph canvas. Nodes and edges remain owned by
 * [KnowledgeGraphUiState]; this state cannot introduce topology.
 */
data class GraphCanvasInteractionState(
    val nodePositionOverrides: Map<String, GraphPoint> = emptyMap(),
    val viewport: GraphViewportState = GraphViewportState(),
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val helpOpen: Boolean = false
)

sealed interface GraphCanvasInteractionEvent {
    data class NodeMoved(val nodeId: String, val point: GraphPoint) : GraphCanvasInteractionEvent
    data class ViewportChanged(val viewport: GraphViewportState) : GraphCanvasInteractionEvent
    data class ReconcileNodes(val ids: Set<String>) : GraphCanvasInteractionEvent
    data class SearchOpenChanged(val open: Boolean) : GraphCanvasInteractionEvent
    data class SearchQueryChanged(val query: String) : GraphCanvasInteractionEvent
    data class HelpOpenChanged(val open: Boolean) : GraphCanvasInteractionEvent
}

sealed interface GraphCanvasCapability {
    data class OpenChatEvidence(val messageId: String) : GraphCanvasCapability
    data class OpenSourceEvidence(val sourceId: String) : GraphCanvasCapability
}

data class GraphLegendItem(
    val kind: GraphNodeKind,
    val label: String,
    val count: Int
)

fun reduceGraphCanvasInteraction(
    state: GraphCanvasInteractionState,
    event: GraphCanvasInteractionEvent
): GraphCanvasInteractionState = when (event) {
    is GraphCanvasInteractionEvent.NodeMoved -> {
        val nodeId = event.nodeId.trim()
        if (nodeId.isEmpty()) state
        else state.copy(nodePositionOverrides = state.nodePositionOverrides + (nodeId to event.point))
    }
    is GraphCanvasInteractionEvent.ViewportChanged -> state.copy(viewport = event.viewport)
    is GraphCanvasInteractionEvent.ReconcileNodes -> state.copy(
        nodePositionOverrides = state.nodePositionOverrides.filterKeys(event.ids::contains)
    )
    is GraphCanvasInteractionEvent.SearchOpenChanged -> state.copy(
        searchOpen = event.open,
        searchQuery = if (event.open) state.searchQuery else ""
    )
    is GraphCanvasInteractionEvent.SearchQueryChanged -> state.copy(searchQuery = event.query)
    is GraphCanvasInteractionEvent.HelpOpenChanged -> state.copy(helpOpen = event.open)
}

fun graphEvidenceCapability(
    sourceMessageId: String?,
    sourceId: String?
): GraphCanvasCapability? = sourceMessageId
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(GraphCanvasCapability::OpenChatEvidence)
    ?: sourceId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(GraphCanvasCapability::OpenSourceEvidence)

fun graphLegendItems(nodes: List<GraphLayoutNode>): List<GraphLegendItem> =
    GraphNodeKind.entries.mapNotNull { kind ->
        val count = nodes.count { it.kind == kind }
        count.takeIf { it > 0 }?.let {
            GraphLegendItem(kind = kind, label = kind.graphLegendLabel(), count = it)
        }
    }

private fun GraphNodeKind.graphLegendLabel(): String = when (this) {
    GraphNodeKind.Concept -> "概念"
    GraphNodeKind.Requirement -> "目标"
    GraphNodeKind.Source -> "资料"
    GraphNodeKind.Session -> "会话"
    GraphNodeKind.Person -> "角色"
    GraphNodeKind.Other -> "节点"
}
