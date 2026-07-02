package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextHubModelsTest {
    @Test
    fun activeSessionStateExposesAllRequiredSurfaces() {
        val state = ContextHubUiState.fromActiveSession(
            sessionId = "session-1",
            sessionTitle = "Algebra sprint"
        )

        assertTrue(state.hasActiveSession)
        assertEquals("Algebra sprint", state.sessionTitle)
        assertEquals(
            ContextHubSection.entries.toList(),
            state.sections.map { it.section }
        )
    }

    @Test
    fun emptyStateDoesNotClaimGraphParity() {
        val state = ContextHubUiState.fromActiveSession(
            sessionId = null,
            sessionTitle = null
        )

        assertFalse(state.hasActiveSession)
        assertEquals("No active session", state.sessionTitle)
        assertTrue(state.overviewLines.any { it.contains("native Canvas", ignoreCase = true) })
        assertTrue(state.overviewLines.any { it.contains("no legacy graph parity", ignoreCase = true) })
    }

    @Test
    fun graphSectionUsesNativeEmptyStateAndSettingsRemainDeferred() {
        val state = ContextHubUiState.fromActiveSession(
            sessionId = "session-1",
            sessionTitle = "Algebra sprint"
        )

        val graph = state.sections.first { it.section == ContextHubSection.Graph }
        val settings = state.sections.first { it.section == ContextHubSection.SessionSettings }

        assertEquals(GraphRenderStatus.Empty.label, graph.statusLabel)
        assertEquals("No graph nodes yet", graph.title)
        assertEquals("Deferred", settings.statusLabel)
    }

    @Test
    fun memorySnapshotPopulatesAnchorsNotesAndErrors() {
        val state = ContextHubUiState.fromMemorySnapshot(
            sessionId = "session-1",
            sessionTitle = "Algebra sprint",
            snapshot = ContextMemorySnapshot(
                anchors = listOf(
                    ContextMemoryEntry(
                        id = "anchor-1",
                        title = "Formula anchor",
                        body = "Difference of squares",
                        sourceMessageId = "message-1",
                        sourceId = "source-1"
                    )
                ),
                notes = listOf(
                    ContextMemoryEntry(
                        id = "note-1",
                        title = "Sign confusion",
                        body = "Student reversed the minus sign.",
                        sourceMessageId = "message-2"
                    )
                ),
                errors = listOf(
                    ContextErrorEntry(
                        id = "error-1",
                        title = "Exponent mistake",
                        detail = "Student treated x^2 as 2x.",
                        sourceMessageId = "message-3",
                        resolved = false
                    )
                )
            )
        )

        val anchors = state.sections.first { it.section == ContextHubSection.Anchors }
        val notes = state.sections.first { it.section == ContextHubSection.Notes }
        val errors = state.sections.first { it.section == ContextHubSection.Errors }

        assertEquals("1 anchor", anchors.statusLabel)
        assertTrue(anchors.body.contains("Formula anchor"))
        assertTrue(anchors.body.contains("Source: source-1"))
        assertEquals("1 note", notes.statusLabel)
        assertTrue(notes.body.contains("Sign confusion"))
        assertEquals("1 open", errors.statusLabel)
        assertTrue(errors.body.contains("Exponent mistake"))
        assertTrue(state.overviewLines.any { it.contains("Anchors: 1") })
    }

    @Test
    fun memorySnapshotPopulatesGraphSectionFromNativeState() {
        val graphState = KnowledgeGraphUiState.from(
            nodes = listOf(
                GraphNode(
                    id = "node-a",
                    spaceId = "space-1",
                    label = "Alpha",
                    kind = GraphNodeKind.Concept,
                    createdAtEpochMillis = 100L
                ),
                GraphNode(
                    id = "node-b",
                    spaceId = "space-1",
                    label = "Beta",
                    kind = GraphNodeKind.Requirement,
                    createdAtEpochMillis = 110L
                )
            ),
            edges = listOf(
                GraphEdge(
                    id = "edge-1",
                    spaceId = "space-1",
                    fromNodeId = "node-a",
                    toNodeId = "node-b",
                    relation = "supports",
                    createdAtEpochMillis = 120L
                )
            )
        )

        val state = ContextHubUiState.fromMemorySnapshot(
            sessionId = "session-1",
            sessionTitle = "Algebra sprint",
            snapshot = ContextMemorySnapshot(),
            graphState = graphState
        )

        val graph = state.sections.first { it.section == ContextHubSection.Graph }

        assertEquals(GraphRenderStatus.Ready.label, graph.statusLabel)
        assertTrue(graph.body.contains("2 nodes"))
        assertTrue(state.overviewLines.any { it.contains("Graph nodes: 2") })
        assertTrue(state.overviewLines.any { it.contains("Graph edges: 1") })
    }
}
