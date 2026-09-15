package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphSourceFidelityPresentationTest {
    @Test
    fun sourcePaletteMapsNativeKindsToReferenceColors() {
        assertEquals(0xFFC6E4F6L, graphKindFillArgb(GraphNodeKind.Concept))
        assertEquals(0xFFF3CBD8L, graphKindFillArgb(GraphNodeKind.Requirement))
        assertEquals(0xFFC3E6BEL, graphKindFillArgb(GraphNodeKind.Source))
        assertEquals(0xFFCCC0EAL, graphKindFillArgb(GraphNodeKind.Session))
        assertEquals(0xFFE8BC6CL, graphKindFillArgb(GraphNodeKind.Person))
        assertEquals(0xFF48BAAEL, graphKindFillArgb(GraphNodeKind.Other))
    }

    @Test
    fun sourceCanvasTokensMatchReferenceConfig() {
        assertEquals(0xFFEDF2F9L, GraphCanvasPresentation.BackgroundArgb)
        assertEquals(0xFF64748BL, GraphCanvasPresentation.EdgeArgb)
        assertEquals(0.16f, GraphCanvasPresentation.EdgeAlpha)
        assertEquals(0.70f, GraphCanvasPresentation.EdgeHighlightAlpha)
        assertEquals(0.25f, GraphCanvasPresentation.SelectionHaloAlpha)
        assertEquals(2.5f, GraphCanvasPresentation.NodeRadiusMin)
        assertEquals(18f, GraphCanvasPresentation.NodeRadiusMax)
        assertEquals(0.4f, GraphCanvasPresentation.SemanticZoomHubOnly)
        assertEquals(0.7f, GraphCanvasPresentation.SemanticZoomHubSecondary)
        assertEquals(0.9f, GraphCanvasPresentation.SemanticZoomAll)
        assertEquals(1.6f, GraphCanvasPresentation.SemanticZoomLabel)
    }

    @Test
    fun displayImportanceIsBoundedAndDoesNotChangeDomainStatus() {
        val node = GraphLayoutNode(
            id = "node",
            label = "Node",
            kind = GraphNodeKind.Concept,
            status = GraphNodeStatus.NeedsReview,
            x = 0.5f,
            y = 0.5f,
            radius = 0.03f
        )

        val importance = graphDisplayImportance(node, relatedEdgeCount = 3)

        assertTrue(importance in 0..100)
        assertEquals(GraphNodeStatus.NeedsReview, node.status)
    }

    @Test
    fun selectedNeighborhoodUsesReferenceOpacityHierarchy() {
        val node = GraphLayoutNode(
            id = "node",
            label = "Node",
            kind = GraphNodeKind.Concept,
            status = GraphNodeStatus.Active,
            x = 0.5f,
            y = 0.5f,
            radius = 0.03f
        )

        assertEquals(1f, graphNodeFillAlpha(node, selected = false, neighbor = false, hasSelection = false))
        assertEquals(1f, graphNodeFillAlpha(node, selected = true, neighbor = false, hasSelection = true))
        assertEquals(0.85f, graphNodeFillAlpha(node, selected = false, neighbor = true, hasSelection = true))
        assertEquals(0.25f, graphNodeFillAlpha(node, selected = false, neighbor = false, hasSelection = true))
    }

    @Test
    fun legend_layout_starts_below_graph_top_bar() {
        assertTrue(
            GraphCanvasOverlaySpec.LegendTopPadding.value >=
                GraphCanvasOverlaySpec.TopBarHeight.value
        )
    }

    @Test
    fun legend_has_no_rows_for_absent_node_kinds() {
        val source = GraphLayoutNode(
            id = "source",
            label = "资料",
            kind = GraphNodeKind.Source,
            status = GraphNodeStatus.Active,
            x = 0.5f,
            y = 0.5f,
            radius = 0.03f
        )

        assertEquals(
            listOf(GraphLegendItem(GraphNodeKind.Source, "资料", 1)),
            graphLegendItems(listOf(source))
        )
    }
}
