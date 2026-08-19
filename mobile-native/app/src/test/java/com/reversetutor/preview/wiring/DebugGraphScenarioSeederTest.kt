package com.reversetutor.preview.wiring

import com.reversetutor.core.data.graph.GraphEdgeInput
import com.reversetutor.core.model.Message
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugGraphScenarioSeederTest {
    @Test
    fun firstRunWritesInDependencyOrderAndSecondRunSkips() = runTest {
        val sink = RecordingDebugGraphScenarioSink()
        val seeder = DebugGraphScenarioSeeder(sink)

        assertEquals(DebugGraphSeedResult.Seeded, seeder.ensureSeeded(1_000L))
        assertEquals(listOf("sessions", "messages", "anchors", "nodes", "edges"), sink.calls)

        sink.hasMarker = true
        assertEquals(DebugGraphSeedResult.AlreadySeeded, seeder.ensureSeeded(2_000L))
        assertEquals(1, sink.calls.count { it == "sessions" })
    }

    @Test
    fun failedSinkDoesNotReportSeeded() = runTest {
        val sink = RecordingDebugGraphScenarioSink(failAt = "edges")

        assertEquals(DebugGraphSeedResult.Failed, DebugGraphScenarioSeeder(sink).ensureSeeded(1_000L))
        assertTrue(sink.calls.contains("edges"))
    }
}

private class RecordingDebugGraphScenarioSink(
    private val failAt: String? = null
) : DebugGraphScenarioSink {
    val calls = mutableListOf<String>()
    var hasMarker = false

    override suspend fun markerExists(markerId: String): Boolean = hasMarker

    override suspend fun writeSessions(sessions: List<DebugSessionSeed>, nowEpochMillis: Long): Boolean =
        record("sessions")

    override suspend fun writeMessages(messages: List<Message>, nowEpochMillis: Long): Boolean =
        record("messages")

    override suspend fun writeAnchors(anchors: List<DebugAnchorSeed>, nowEpochMillis: Long): Boolean =
        record("anchors")

    override suspend fun writeNodes(nodes: List<DebugGraphNodeSeed>, nowEpochMillis: Long): Boolean =
        record("nodes")

    override suspend fun writeEdges(edges: List<GraphEdgeInput>, nowEpochMillis: Long): Boolean =
        record("edges")

    private fun record(name: String): Boolean {
        calls += name
        return failAt != name
    }
}
