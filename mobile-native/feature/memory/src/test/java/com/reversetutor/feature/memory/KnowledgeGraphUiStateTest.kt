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
import org.junit.Assert.assertNotEquals
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
        assertEquals("当前会话信息过少，再多聊会天吧", state.title)
        assertEquals("", state.summary)
        assertTrue(state.visibleEdges.isEmpty())
    }

    @Test
    fun errorGraphKeepsRetryablePresentationSeparateFromEmpty() {
        val state = KnowledgeGraphUiState.error(
            scope = GraphScope.Global,
            message = "本地图谱暂时不可用"
        )

        assertEquals(GraphRenderStatus.Error, state.status)
        assertEquals("图谱加载失败", state.title)
        assertEquals("本地图谱暂时不可用", state.summary)
        assertTrue(state.allNodes.isEmpty())
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
        assertEquals(listOf("node-b"), state.lockedNodes.map { it.id })
        assertEquals("需审核", state.nodes.single().statusLabel)
        assertEquals(
            listOf("通过", "归档", "隐藏"),
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

    @Test
    fun readyGraphSummaryNamesNativeInteractionAndListFallback() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("node-a", "Alpha"),
                node("node-b", "Beta")
            ),
            edges = listOf(edge("edge-valid", "node-a", "node-b"))
        )

        assertTrue(state.summary.contains("拖动"))
        assertTrue(state.summary.contains("缩放"))
        assertTrue(state.summary.contains("选择"))
        assertTrue(state.summary.contains("节点列表"))
    }

    @Test
    fun hierarchyAnchorsFollowDirectedDepthInsteadOfCircularOrder() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("root", "Root", kind = GraphNodeKind.Session),
                node("child", "Child"),
                node("leaf", "Leaf")
            ),
            edges = listOf(
                edge("root-child", "root", "child"),
                edge("child-leaf", "child", "leaf")
            ),
            scope = GraphScope.Global
        )

        val root = state.nodes.first { it.id == "root" }
        val child = state.nodes.first { it.id == "child" }
        val leaf = state.nodes.first { it.id == "leaf" }

        assertEquals(listOf(0, 1, 2), listOf(root.depth, child.depth, leaf.depth))
        assertTrue(root.anchorY < child.anchorY)
        assertTrue(child.anchorY < leaf.anchorY)
        assertNotEquals(root.y, child.y)
        assertNotEquals(child.y, leaf.y)
    }

    @Test
    fun physicsLayoutIsDeterministicBoundedAndRetainsInertiaState() {
        val nodes = listOf(
            node("root", "Root", kind = GraphNodeKind.Session),
            node("a", "A"),
            node("b", "B"),
            node("c", "C"),
            node("d", "D")
        )
        val edges = listOf(
            edge("root-a", "root", "a"),
            edge("root-b", "root", "b"),
            edge("root-c", "root", "c"),
            edge("root-d", "root", "d")
        )

        val first = KnowledgeGraphUiState.from(nodes, edges, scope = GraphScope.Global)
        val second = KnowledgeGraphUiState.from(nodes, edges, scope = GraphScope.Global)

        assertEquals(first.nodes.map { it.x to it.y }, second.nodes.map { it.x to it.y })
        first.nodes.forEach { layoutNode ->
            assertTrue(layoutNode.x in 0.07f..0.93f)
            assertTrue(layoutNode.y in 0.07f..0.93f)
            assertTrue(kotlin.math.abs(layoutNode.velocityX) <= 0.032f)
            assertTrue(kotlin.math.abs(layoutNode.velocityY) <= 0.032f)
        }
        assertEquals(first.nodes.size, first.nodes.map { it.x to it.y }.distinct().size)
        first.nodes.forEachIndexed { index, left ->
            first.nodes.drop(index + 1).forEach { right ->
                val overlapsHorizontally = kotlin.math.abs(left.x - right.x) < left.cardHalfWidth + right.cardHalfWidth
                val overlapsVertically = kotlin.math.abs(left.y - right.y) < left.cardHalfHeight + right.cardHalfHeight
                assertFalse("layout cards overlap: ${left.id}/${right.id}", overlapsHorizontally && overlapsVertically)
            }
        }
    }

    @Test
    fun semanticZoomUsesCirclesOnlyForGlobalOverview() {
        assertEquals(GraphSemanticMode.OverviewCircles, graphSemanticMode(GraphScope.Global, 1f))
        assertEquals(GraphSemanticMode.DetailCards, graphSemanticMode(GraphScope.Global, 1.5f))
        assertEquals(GraphSemanticMode.DetailCards, graphSemanticMode(GraphScope.Session, 0.72f))
    }

    @Test
    fun globalOverviewUsesTopologyToKeepClusterLeavesCompact() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("root", "Root", kind = GraphNodeKind.Session),
                node("hub", "Hub"),
                node("leaf-a", "Leaf A"),
                node("leaf-b", "Leaf B"),
                node("leaf-c", "Leaf C")
            ),
            edges = listOf(
                edge("root-hub", "root", "hub"),
                edge("hub-a", "hub", "leaf-a"),
                edge("hub-b", "hub", "leaf-b"),
                edge("hub-c", "hub", "leaf-c")
            ),
            scope = GraphScope.Global
        )

        val root = state.nodes.first { it.id == "root" }
        val hub = state.nodes.first { it.id == "hub" }
        val leaf = state.nodes.first { it.id == "leaf-a" }

        assertTrue(root.radius > hub.radius)
        assertTrue(hub.radius > leaf.radius)
        assertEquals(0.014f, leaf.radius)
    }

    @Test
    fun globalOverviewLabelsFlipBeforeCrossingTheViewportEdge() {
        assertEquals(
            GraphOverviewLabelPlacement.Inside,
            graphOverviewLabelPlacement(
                wantsInside = true,
                centerX = 195f,
                radius = 24f,
                labelWidth = 40f,
                viewportWidth = 390f,
                margin = 8f,
                gap = 5f
            )
        )
        assertEquals(
            GraphOverviewLabelPlacement.Right,
            graphOverviewLabelPlacement(
                wantsInside = false,
                centerX = 28f,
                radius = 6f,
                labelWidth = 70f,
                viewportWidth = 390f,
                margin = 8f,
                gap = 5f
            )
        )
        assertEquals(
            GraphOverviewLabelPlacement.Left,
            graphOverviewLabelPlacement(
                wantsInside = false,
                centerX = 362f,
                radius = 6f,
                labelWidth = 70f,
                viewportWidth = 390f,
                margin = 8f,
                gap = 5f
            )
        )
    }

    @Test
    fun sessionCardLayoutKeepsNodesInsideTheFormalViewportLanes() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("root", "Python", kind = GraphNodeKind.Session),
                node("student", "Student", kind = GraphNodeKind.Person),
                node("goal", "Goal", kind = GraphNodeKind.Requirement),
                node("material", "Material", kind = GraphNodeKind.Source),
                node("concept-a", "Concept A"),
                node("concept-b", "Concept B")
            ),
            edges = listOf(
                edge("root-student", "root", "student"),
                edge("root-goal", "root", "goal"),
                edge("root-material", "root", "material"),
                edge("root-a", "root", "concept-a"),
                edge("root-b", "root", "concept-b")
            ),
            scope = GraphScope.Session
        )

        state.allNodes.forEach { node ->
            assertTrue("session node escaped horizontally: ${node.id}", node.x in 0.17f..0.85f)
        }
    }

    @Test
    fun lockedNodesCanBeShownWithoutRebuildingRepositoryState() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                node("active", "Active"),
                node("locked", "Locked", status = GraphNodeStatus.Hidden)
            ),
            edges = listOf(edge("active-locked", "active", "locked")),
            scope = GraphScope.Session
        )

        assertEquals(listOf("active"), state.renderSnapshot(showLockedNodes = false).nodes.map { it.id })
        assertEquals(setOf("active", "locked"), state.renderSnapshot(showLockedNodes = true).nodes.map { it.id }.toSet())
        assertTrue(state.renderSnapshot(showLockedNodes = false).edges.isEmpty())
        assertEquals(listOf("active-locked"), state.renderSnapshot(showLockedNodes = true).edges.map { it.id })
        assertEquals("locked", state.withSelection("locked").selectedNode?.id)
    }

    private fun node(
        id: String,
        label: String,
        status: GraphNodeStatus = GraphNodeStatus.Active,
        kind: GraphNodeKind = GraphNodeKind.Concept
    ): GraphNode = GraphNode(
        id = id,
        spaceId = "space-1",
        label = label,
        kind = kind,
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
