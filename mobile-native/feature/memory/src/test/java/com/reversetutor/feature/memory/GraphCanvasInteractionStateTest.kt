package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.MemoryItemKind
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

    @Test
    fun memory_item_enriches_global_graph_node_with_chat_evidence() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                GraphNode(
                    id = "graph-node",
                    spaceId = "default-space",
                    label = "证据节点",
                    kind = GraphNodeKind.Concept,
                    createdAtEpochMillis = 1L,
                    sourceMemoryId = "memory-anchor"
                )
            ),
            edges = emptyList(),
            memoryItems = listOf(
                MemoryItem(
                    id = "memory-anchor",
                    spaceId = "default-space",
                    kind = MemoryItemKind.Fact,
                    title = "证据",
                    body = "正文",
                    createdAtEpochMillis = 1L,
                    sourceMessageId = "message-9"
                )
            ),
            scope = GraphScope.Global
        )

        assertEquals("message-9", state.allNodes.single().sourceMessageId)
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
