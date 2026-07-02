package com.reversetutor.core.data.graph

import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphRepositoryTest {
    @Test
    fun savesAndListsGraphSnapshotBySpace() = runBlocking {
        val dao = FakeGraphDao()
        val repository = GraphRepository(dao, defaultSpaceId = "space-1")

        repository.saveNode(
            GraphNodeInput(
                id = "node-b",
                label = "Beta",
                kind = GraphNodeKind.Concept,
                sourceMemoryId = "memory-note-1"
            ),
            nowEpochMillis = 100L
        )
        repository.saveNode(
            GraphNodeInput(
                id = "node-a",
                label = "Alpha",
                kind = GraphNodeKind.Requirement
            ),
            nowEpochMillis = 90L
        )
        repository.saveEdge(
            GraphEdgeInput(
                id = "edge-1",
                fromNodeId = "node-a",
                toNodeId = "node-b",
                relation = "supports",
                sourceMemoryId = "memory-note-1"
            ),
            nowEpochMillis = 110L
        )

        val snapshot = repository.snapshot()

        assertEquals(listOf("Alpha", "Beta"), snapshot.nodes.map { it.label })
        assertEquals(listOf("supports"), snapshot.edges.map { it.relation })
        assertEquals("memory-note-1", snapshot.nodes.last().sourceMemoryId)
        assertEquals("memory-note-1", snapshot.edges.single().sourceMemoryId)
    }

    @Test
    fun rejectsBlankNodeAndEdgeInputs() = runBlocking {
        val dao = FakeGraphDao()
        val repository = GraphRepository(dao, defaultSpaceId = "space-1")

        assertNull(
            repository.saveNode(
                GraphNodeInput(id = "  ", label = "Alpha"),
                nowEpochMillis = 100L
            )
        )
        assertNull(
            repository.saveNode(
                GraphNodeInput(id = "node-a", label = "  "),
                nowEpochMillis = 100L
            )
        )
        assertNull(
            repository.saveEdge(
                GraphEdgeInput(
                    id = "edge-a",
                    fromNodeId = "node-a",
                    toNodeId = "node-b",
                    relation = " "
                ),
                nowEpochMillis = 110L
            )
        )

        assertEquals(0, repository.snapshot().nodes.size)
        assertEquals(0, repository.snapshot().edges.size)
    }

    @Test
    fun snapshotIsScopedBySpace() = runBlocking {
        val dao = FakeGraphDao()
        val repository = GraphRepository(dao, defaultSpaceId = "space-1")

        repository.saveNode(
            GraphNodeInput(id = "node-a", label = "Alpha"),
            nowEpochMillis = 100L,
            spaceId = "space-1"
        )
        repository.saveNode(
            GraphNodeInput(id = "node-b", label = "Beta"),
            nowEpochMillis = 100L,
            spaceId = "space-2"
        )

        assertEquals(listOf("Alpha"), repository.snapshot(spaceId = "space-1").nodes.map { it.label })
        assertEquals(listOf("Beta"), repository.snapshot(spaceId = "space-2").nodes.map { it.label })
    }

    @Test
    fun updateNodeEditsExistingNodeWithoutCreatingMissingNode() = runBlocking {
        val dao = FakeGraphDao()
        val repository = GraphRepository(dao, defaultSpaceId = "space-1")
        repository.saveNode(
            GraphNodeInput(
                id = "node-a",
                label = "Alpha",
                kind = GraphNodeKind.Concept
            ),
            nowEpochMillis = 100L
        )

        val updated = repository.updateNode(
            GraphNodeEditInput(
                id = "node-a",
                label = "Alpha reviewed",
                kind = GraphNodeKind.Requirement,
                status = GraphNodeStatus.NeedsReview,
                sourceMemoryId = "memory-alpha"
            ),
            nowEpochMillis = 200L
        )
        val missing = repository.updateNode(
            GraphNodeEditInput(
                id = "missing",
                label = "Missing",
                kind = GraphNodeKind.Other,
                status = GraphNodeStatus.Active
            ),
            nowEpochMillis = 210L
        )

        assertEquals("Alpha reviewed", updated?.label)
        assertEquals(GraphNodeStatus.NeedsReview, updated?.status)
        assertEquals(100L, updated?.createdAtEpochMillis)
        assertNull(missing)
        assertEquals(listOf("node-a"), repository.snapshot().nodes.map { it.id })
    }
}

private class FakeGraphDao : GraphDao {
    private val nodes = linkedMapOf<String, GraphNodeEntity>()
    private val edges = linkedMapOf<String, GraphEdgeEntity>()

    override suspend fun insertNode(node: GraphNodeEntity) {
        nodes[node.id] = node
    }

    override suspend fun insertEdge(edge: GraphEdgeEntity) {
        edges[edge.id] = edge
    }

    override suspend fun listNodesBySpace(spaceId: String): List<GraphNodeEntity> =
        nodes.values.filter { it.spaceId == spaceId }.sortedBy { it.label }

    override suspend fun listEdgesBySpace(spaceId: String): List<GraphEdgeEntity> =
        edges.values.filter { it.spaceId == spaceId }.sortedBy { it.createdAtEpochMillis }
}
