package com.reversetutor.core.data.graph

import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.model.DomainErrorCode
import com.reversetutor.core.model.GraphEmptyReason
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.GraphScope
import com.reversetutor.core.model.GraphSnapshotResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphRepositoryTest {
    @Test
    fun savesAndListsGraphSnapshotBySpace() = runBlocking {
        val dao = FakeGraphDao()
        val repository = GraphRepository(dao, FakeSessionDao(), defaultSpaceId = "space-1")

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
        val repository = GraphRepository(dao, FakeSessionDao(), defaultSpaceId = "space-1")

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
        val repository = GraphRepository(dao, FakeSessionDao(), defaultSpaceId = "space-1")

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
        val repository = GraphRepository(dao, FakeSessionDao(), defaultSpaceId = "space-1")
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

    @Test
    fun globalScopeReturnsReadySnapshotAndCountsInvalidEdges() = runBlocking {
        val dao = FakeGraphDao().apply {
            nodes["node-a"] = node("node-a", "space-1", "Alpha")
            nodes["node-b"] = node("node-b", "space-1", "Beta")
            nodes["other"] = node("other", "space-2", "Other")
            edges["valid"] = edge("valid", "space-1", "node-a", "node-b")
            edges["invalid"] = edge("invalid", "space-1", "node-a", "missing")
        }
        val repository = GraphRepository(dao, FakeSessionDao())

        val result = repository.snapshot(GraphScope.Global("space-1"))

        assertTrue(result is GraphSnapshotResult.Ready)
        val snapshot = (result as GraphSnapshotResult.Ready).snapshot
        assertEquals(listOf("node-a", "node-b"), snapshot.nodes.map { it.id })
        assertEquals(listOf("valid"), snapshot.edges.map { it.id })
        assertEquals(1, snapshot.invalidEdgeCount)
    }

    @Test
    fun sessionScopeFiltersNodesAndEdgesToThatSession() = runBlocking {
        val dao = FakeGraphDao().apply {
            nodes["node-a"] = node("node-a", "space-1", "Alpha")
            nodes["node-b"] = node("node-b", "space-1", "Beta")
            nodes["node-c"] = node("node-c", "space-1", "Gamma")
            sessionNodeIds["session-1"] = setOf("node-a", "node-b")
            edges["inside"] = edge("inside", "space-1", "node-a", "node-b")
            edges["outside"] = edge("outside", "space-1", "node-a", "node-c")
        }
        val sessionDao = FakeSessionDao().apply {
            sessions["session-1"] = session("session-1", "space-1")
        }
        val repository = GraphRepository(dao, sessionDao)

        val result = repository.snapshot(GraphScope.Session("session-1"))

        assertTrue(result is GraphSnapshotResult.Ready)
        val snapshot = (result as GraphSnapshotResult.Ready).snapshot
        assertEquals(listOf("node-a", "node-b"), snapshot.nodes.map { it.id })
        assertEquals(listOf("inside"), snapshot.edges.map { it.id })
        assertEquals(0, snapshot.invalidEdgeCount)
    }

    @Test
    fun emptyAndMissingScopesReturnMachineReadableResults() = runBlocking {
        val repository = GraphRepository(FakeGraphDao(), FakeSessionDao())

        assertEquals(
            GraphSnapshotResult.Empty(GraphEmptyReason.NoExtractedNodes),
            repository.snapshot(GraphScope.Global("space-1"))
        )

        val missing = repository.snapshot(GraphScope.Session("missing"))
        assertTrue(missing is GraphSnapshotResult.Error)
        assertEquals(
            DomainErrorCode.NotFound,
            (missing as GraphSnapshotResult.Error).error.code
        )
    }

    @Test
    fun daoFailureReturnsStorageUnavailable() = runBlocking {
        val dao = FakeGraphDao().apply { failReads = true }
        val repository = GraphRepository(dao, FakeSessionDao())

        val result = repository.snapshot(GraphScope.Global("space-1"))

        assertTrue(result is GraphSnapshotResult.Error)
        assertEquals(
            DomainErrorCode.StorageUnavailable,
            (result as GraphSnapshotResult.Error).error.code
        )
    }
}

private class FakeGraphDao : GraphDao {
    val nodes = linkedMapOf<String, GraphNodeEntity>()
    val edges = linkedMapOf<String, GraphEdgeEntity>()
    val sessionNodeIds = mutableMapOf<String, Set<String>>()
    var failReads: Boolean = false

    override suspend fun insertNode(node: GraphNodeEntity) {
        nodes[node.id] = node
    }

    override suspend fun insertEdge(edge: GraphEdgeEntity) {
        edges[edge.id] = edge
    }

    override suspend fun listNodesBySpace(spaceId: String): List<GraphNodeEntity> {
        if (failReads) error("graph read failed")
        return nodes.values.filter { it.spaceId == spaceId }.sortedBy { it.label }
    }

    override suspend fun listNodesBySession(sessionId: String): List<GraphNodeEntity> {
        if (failReads) error("graph read failed")
        val ids = sessionNodeIds[sessionId].orEmpty()
        return nodes.values.filter { it.id in ids }.sortedBy { it.label }
    }

    override suspend fun listEdgesBySpace(spaceId: String): List<GraphEdgeEntity> {
        if (failReads) error("graph read failed")
        return edges.values.filter { it.spaceId == spaceId }.sortedBy { it.createdAtEpochMillis }
    }
}

private class FakeSessionDao : SessionDao {
    val sessions = linkedMapOf<String, SessionEntity>()

    override suspend fun upsert(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun getById(id: String): SessionEntity? = sessions[id]

    override suspend fun listBySpace(spaceId: String): List<SessionEntity> =
        sessions.values.filter { it.spaceId == spaceId }

    override suspend fun rename(id: String, title: String, updatedAtEpochMillis: Long): Int = 0

    override suspend fun setPinned(id: String, pinned: Boolean, updatedAtEpochMillis: Long): Int = 0

    override suspend fun archive(id: String, updatedAtEpochMillis: Long): Int = 0

    override suspend fun updateSessionModelBinding(sessionId: String, modelBindingId: String): Int = 0

    override suspend fun updateSessionSettingsModelBinding(sessionId: String, modelBindingId: String): Int = 0
}

private fun node(
    id: String,
    spaceId: String,
    label: String
): GraphNodeEntity = GraphNodeEntity(
    id = id,
    spaceId = spaceId,
    label = label,
    kind = GraphNodeKind.Concept.name,
    createdAtEpochMillis = 100L
)

private fun edge(
    id: String,
    spaceId: String,
    fromNodeId: String,
    toNodeId: String
): GraphEdgeEntity = GraphEdgeEntity(
    id = id,
    spaceId = spaceId,
    fromNodeId = fromNodeId,
    toNodeId = toNodeId,
    relation = "related",
    createdAtEpochMillis = 100L
)

private fun session(id: String, spaceId: String): SessionEntity = SessionEntity(
    id = id,
    spaceId = spaceId,
    title = id,
    createdAtEpochMillis = 100L,
    updatedAtEpochMillis = 100L
)
