package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphSourceFidelityPresentationTest {
    @Test
    fun sourcePaletteMapsNativeKindsToReferenceColors() {
        assertEquals(0xFF3B82F6L, graphKindFillArgb(GraphNodeKind.Concept))
        assertEquals(0xFFFF6B61L, graphKindFillArgb(GraphNodeKind.Requirement))
        assertEquals(0xFF84B547L, graphKindFillArgb(GraphNodeKind.Source))
        assertEquals(0xFF8B5CF6L, graphKindFillArgb(GraphNodeKind.Session))
        assertEquals(0xFFF59E0BL, graphKindFillArgb(GraphNodeKind.Person))
        assertEquals(0xFF22B8CFL, graphKindFillArgb(GraphNodeKind.Other))
    }

    @Test
    fun sourceCanvasTokensMatchReferenceConfig() {
        assertEquals(0xFFF7F8FAL, GraphCanvasPresentation.BackgroundArgb)
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
}
