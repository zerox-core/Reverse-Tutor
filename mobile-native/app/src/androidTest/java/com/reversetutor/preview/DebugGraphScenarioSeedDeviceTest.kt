package com.reversetutor.preview

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.preview.wiring.DebugGraphScenarioSeeder
import com.reversetutor.preview.wiring.DebugGraphSeedResult
import com.reversetutor.preview.wiring.HybridAppGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugGraphScenarioSeedDeviceTest {
    @Test
    fun debugScenarioSeedsRoomAndIsIdempotent() = runBlocking {
        val graph = HybridAppGraph.create(ApplicationProvider.getApplicationContext())
        val seeder = DebugGraphScenarioSeeder(graph)

        val first = seeder.ensureSeeded(1_000L)
        val firstSnapshot = graph.graphRepository.snapshot()
        val firstSessions = graph.sessionRepository.listSessions()
        val firstMemoryCount = graph.memoryRepository.snapshot().items.size

        assertTrue(first == DebugGraphSeedResult.Seeded || first == DebugGraphSeedResult.AlreadySeeded)
        assertTrue(firstSessions.any { it.id == "debug-session-python" })
        assertTrue(firstSessions.any { it.id == "debug-session-review" })
        assertEquals(21, firstSnapshot.nodes.size)
        assertEquals(25, firstSnapshot.edges.size)
        assertEquals(8, firstMemoryCount)
        assertTrue(firstSnapshot.edges.all { edge ->
            firstSnapshot.nodes.any { it.id == edge.fromNodeId } &&
                firstSnapshot.nodes.any { it.id == edge.toNodeId }
        })

        val second = seeder.ensureSeeded(2_000L)
        val secondSnapshot = graph.graphRepository.snapshot()
        val secondSessions = graph.sessionRepository.listSessions()
        assertEquals(DebugGraphSeedResult.AlreadySeeded, second)
        assertEquals(firstSnapshot.nodes.size, secondSnapshot.nodes.size)
        assertEquals(firstSnapshot.edges.size, secondSnapshot.edges.size)
        assertEquals(firstSessions.size, secondSessions.size)
    }
}
