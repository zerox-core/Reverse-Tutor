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
        assertEquals("未选择会话", state.sessionTitle)
        assertTrue(state.overviewLines.any { it.contains("native Canvas", ignoreCase = true) })
        assertFalse(state.overviewLines.any { it.contains("legacy graph parity", ignoreCase = true) })
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
        assertEquals("还没有图谱节点", graph.title)
        assertEquals("待启用", settings.statusLabel)
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

        assertEquals("1 个锚点", anchors.statusLabel)
        assertTrue(anchors.body.contains("Formula anchor"))
        assertTrue(anchors.body.contains("资料：source-1"))
        assertEquals("1 篇随笔", notes.statusLabel)
        assertTrue(notes.body.contains("Sign confusion"))
        assertEquals("1 个未解决", errors.statusLabel)
        assertTrue(errors.body.contains("Exponent mistake"))
        assertTrue(state.overviewLines.any { it.contains("锚点：1") })
    }

    @Test
    fun memorySnapshotNamesEvidenceReferencesAndRecoveryActions() {
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
                )
            )
        )

        val anchors = state.sections.first { it.section == ContextHubSection.Anchors }

        assertTrue(anchors.body.contains("证据引用"))
        assertTrue(anchors.body.contains("聊天消息：message-1"))
        assertTrue(anchors.body.contains("资料：source-1"))
        assertTrue(anchors.nextActions.any { it.contains("打开关联资料证据") })
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
        assertTrue(graph.body.contains("2 个节点"))
        assertTrue(state.overviewLines.any { it.contains("图谱节点：2") })
        assertTrue(state.overviewLines.any { it.contains("图谱关系：1") })
    }
}
