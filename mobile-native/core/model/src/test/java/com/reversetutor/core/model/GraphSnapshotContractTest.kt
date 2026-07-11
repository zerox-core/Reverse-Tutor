package com.reversetutor.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphSnapshotContractTest {
    @Test
    fun graphScopesAndResultsExposeStableMachineReadableContracts() {
        val global = GraphScope.Global("space-1")
        val session = GraphScope.Session("session-1")
        val empty = GraphSnapshotResult.Empty(GraphEmptyReason.NoExtractedNodes)
        val ready = GraphSnapshotResult.Ready(
            GraphSnapshot(nodes = emptyList(), edges = emptyList())
        )
        val error = GraphSnapshotResult.Error(
            DomainError(
                code = DomainErrorCode.NotFound,
                retryable = false,
                safeMessage = "Graph scope was not found."
            )
        )

        assertEquals("space-1", global.spaceId)
        assertEquals("session-1", session.sessionId)
        assertEquals(GraphEmptyReason.NoExtractedNodes, empty.reason)
        assertTrue(ready.snapshot.nodes.isEmpty())
        assertEquals(DomainErrorCode.NotFound, error.error.code)
    }

    @Test
    fun graphSnapshotTracksInvalidEdgesWithoutAddingUiCopy() {
        val snapshot = GraphSnapshot(
            nodes = emptyList(),
            edges = emptyList(),
            invalidEdgeCount = 2
        )

        assertEquals(2, snapshot.invalidEdgeCount)
        assertEquals(listOf("NoExtractedNodes"), GraphEmptyReason.entries.map { it.name })
    }
}
