package com.reversetutor.preview.wiring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugGraphScenarioDefinitionTest {
    @Test
    fun definitionContainsFourSessionsAndCrossSessionGraph() {
        val seed = DebugGraphScenarioDefinition.build()

        assertEquals(4, seed.sessions.size)
        assertEquals(8, seed.messages.size)
        assertEquals(20, seed.nodes.count { !it.hidden })
        assertEquals(1, seed.nodes.count { it.hidden && it.id == DebugGraphScenarioDefinition.MarkerId })
        assertEquals(25, seed.edges.size)
        assertTrue(seed.edges.all { edge ->
            seed.nodes.any { it.id == edge.fromNodeId } && seed.nodes.any { it.id == edge.toNodeId }
        })
        assertTrue(seed.edges.any { it.fromNodeId == "debug-node-python" && it.toNodeId == "debug-node-global-graph" })
        assertTrue(seed.edges.any { it.fromNodeId == "debug-node-qwen" && it.toNodeId == "debug-node-provider" })
    }

    @Test
    fun everyEvidenceNodeHasMessageBackReference() {
        val seed = DebugGraphScenarioDefinition.build()
        val messageIds = seed.messages.mapTo(hashSetOf()) { it.id }

        assertTrue(seed.anchors.all { it.sourceMessageId in messageIds })
        assertTrue(seed.nodes.filter { it.sourceMemoryId != null }
            .all { node -> seed.anchors.any { "memory-${it.id}" == node.sourceMemoryId } })
    }
}
