package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.MemoryItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGraphUiStateTest {
    @Test
    fun emptyGraphShowsEmptyState() {
        val state = KnowledgeGraphUiState.from(
            nodes = emptyList(),
            edges = emptyList()
        )

        assertEquals(GraphRenderStatus.Empty, state.status)
        assertEquals("No graph nodes yet", state.title)
        assertTrue(state.visibleEdges.isEmpty())
    }

    @Test
    fun graphLayoutFiltersInvalidEdgesAndSupportsHitTesting() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("node-a", "Alpha"),
                node("node-b", "Beta"),
                node("node-c", "Gamma")
            ),
            edges = listOf(
                edge("edge-valid", "node-a", "node-b"),
                edge("edge-invalid", "node-a", "missing")
            ),
            selectedNodeId = "node-b"
        )

        assertEquals(GraphRenderStatus.Invalid, state.status)
        assertEquals(3, state.nodes.size)
        assertEquals(listOf("edge-valid"), state.visibleEdges.map { it.id })
        assertEquals(1, state.invalidEdgeCount)
        assertEquals("Beta", state.selectedNode?.label)

        val beta = state.nodes.first { it.id == "node-b" }
        assertEquals("node-b", state.hitTest(beta.x, beta.y)?.id)
        assertNull(state.hitTest(0.02f, 0.02f))
    }

    @Test
    fun largeGraphUsesLargeStateWithoutDroppingNodes() {
        val nodes = (1..61).map { index -> node("node-$index", "Node $index") }

        val state = KnowledgeGraphUiState.from(nodes = nodes, edges = emptyList())

        assertEquals(GraphRenderStatus.Large, state.status)
        assertEquals(61, state.nodes.size)
        assertFalse(state.summary.contains("WebView"))
    }

    @Test
    fun reviewStatusProvidesNodeActionsAndHiddenNodesStayOutOfLayout() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("node-a", "Alpha", status = GraphNodeStatus.NeedsReview),
                node("node-b", "Beta", status = GraphNodeStatus.Hidden)
            ),
            edges = emptyList()
        )

        assertEquals(listOf("node-a"), state.nodes.map { it.id })
        assertEquals("Needs review", state.nodes.single().statusLabel)
        assertEquals(
            listOf("Approve", "Archive", "Hide"),
            state.nodes.single().reviewActions.map { it.label }
        )
    }

    @Test
    fun graphNodesResolveMemoryEvidenceForReviewCardsAndJumpTargets() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                GraphNode(
                    id = "node-source",
                    spaceId = "space-1",
                    label = "Source claim",
                    kind = GraphNodeKind.Source,
                    createdAtEpochMillis = 100L,
                    sourceMemoryId = "memory-anchor-1"
                )
            ),
            edges = emptyList(),
            memoryItems = listOf(
                MemoryItem(
                    id = "memory-anchor-1",
                    spaceId = "space-1",
                    kind = MemoryItemKind.Requirement,
                    title = "Textbook section",
                    body = "Difference of squares from imported source.",
                    createdAtEpochMillis = 90L,
                    sourceMessageId = "message-1",
                    sourceId = "source-1"
                )
            )
        )

        val node = state.nodes.single()

        assertEquals("message-1", node.sourceMessageId)
        assertEquals("source-1", node.sourceId)
        assertEquals("Textbook section", node.reviewCards.single().title)
        assertTrue(node.reviewCards.single().body.contains("Difference of squares"))
    }

    private fun node(
        id: String,
        label: String,
        status: GraphNodeStatus = GraphNodeStatus.Active
    ): GraphNode = GraphNode(
        id = id,
        spaceId = "space-1",
        label = label,
        kind = GraphNodeKind.Concept,
        createdAtEpochMillis = 100L,
        status = status
    )

    private fun edge(
        id: String,
        from: String,
        to: String
    ): GraphEdge = GraphEdge(
        id = id,
        spaceId = "space-1",
        fromNodeId = from,
        toNodeId = to,
        relation = "relates_to",
        createdAtEpochMillis = 110L
    )
}
