package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphCanvasInteractionStateTest {
    @Test
    fun reducer_keeps_only_existing_node_overrides() {
        val original = GraphCanvasInteractionState(
            nodePositionOverrides = mapOf(
                "keep" to GraphPoint(.2f, .3f),
                "gone" to GraphPoint(.4f, .5f)
            )
        )

        val result = reduceGraphCanvasInteraction(
            original,
            GraphCanvasInteractionEvent.ReconcileNodes(setOf("keep"))
        )

        assertEquals(mapOf("keep" to GraphPoint(.2f, .3f)), result.nodePositionOverrides)
    }

    @Test
    fun reducer_ignores_blank_node_position_updates() {
        val result = reduceGraphCanvasInteraction(
            GraphCanvasInteractionState(),
            GraphCanvasInteractionEvent.NodeMoved("   ", GraphPoint(.2f, .3f))
        )

        assertEquals(emptyMap<String, GraphPoint>(), result.nodePositionOverrides)
    }

    @Test
    fun evidence_capability_prefers_chat_then_source_and_requires_a_target() {
        assertEquals(
            GraphCanvasCapability.OpenChatEvidence("message-7"),
            graphEvidenceCapability(sourceMessageId = "message-7", sourceId = "source-8")
        )
        assertEquals(
            GraphCanvasCapability.OpenSourceEvidence("source-8"),
            graphEvidenceCapability(sourceMessageId = null, sourceId = "source-8")
        )
        assertNull(graphEvidenceCapability(sourceMessageId = null, sourceId = null))
    }

    @Test
    fun legend_summarizes_only_present_node_kinds_in_stable_order() {
        val concept = node(id = "concept", kind = GraphNodeKind.Concept)
        val source = node(id = "source", kind = GraphNodeKind.Source)

        assertEquals(
            listOf(
                GraphLegendItem(GraphNodeKind.Concept, "概念", 2),
                GraphLegendItem(GraphNodeKind.Source, "资料", 1)
            ),
            graphLegendItems(listOf(source, concept, concept.copy(id = "concept-two")))
        )
    }

    private fun node(id: String, kind: GraphNodeKind) = GraphLayoutNode(
        id = id,
        label = id,
        kind = kind,
        status = GraphNodeStatus.Active,
        x = .5f,
        y = .5f,
        radius = .03f
    )
}
