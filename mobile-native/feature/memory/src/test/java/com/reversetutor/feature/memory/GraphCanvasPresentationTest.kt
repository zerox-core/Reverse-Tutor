package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphCanvasPresentationTest {
    @Test
    fun nodeStyleSeparatesKindAndStatusWithoutChangingDomainData() {
        val active = node(GraphNodeKind.Concept, GraphNodeStatus.Active)
        val hidden = active.copy(status = GraphNodeStatus.Hidden)

        assertNotEquals(
            graphNodeStyle(active, selected = false),
            graphNodeStyle(hidden, selected = false)
        )
        assertEquals(active.kind, graphNodeStyle(active, selected = false).semanticKind)
        assertEquals(active.status, graphNodeStyle(active, selected = false).semanticStatus)
    }

    @Test
    fun selectedStyleOnlyChangesPresentationProperties() {
        val node = node(GraphNodeKind.Concept, GraphNodeStatus.Active)

        val normal = graphNodeStyle(node, selected = false)
        val selected = graphNodeStyle(node, selected = true)

        assertTrue(selected.radiusMultiplier > normal.radiusMultiplier)
        assertTrue(selected.fillAlpha > normal.fillAlpha)
        assertEquals(normal.semanticKind, selected.semanticKind)
        assertEquals(normal.semanticStatus, selected.semanticStatus)
    }

    @Test
    fun canvasControlsUseAtLeastFortyEightDpTouchTarget() {
        assertTrue(GraphCanvasPresentation.ControlTouchTargetDp >= 48f)
    }

    private fun node(kind: GraphNodeKind, status: GraphNodeStatus) = GraphLayoutNode(
        id = "node",
        label = "Node",
        kind = kind,
        status = status,
        x = 0.5f,
        y = 0.5f,
        radius = 0.03f
    )
}

