package com.reversetutor.preview.wiring

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.memory.AnchorInput
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.TutorSession

enum class DebugGraphSeedResult {
    Seeded,
    AlreadySeeded,
    Failed
}

interface DebugGraphScenarioSink {
    suspend fun markerExists(markerId: String): Boolean
    suspend fun writeSessions(sessions: List<DebugSessionSeed>, nowEpochMillis: Long): Boolean
    suspend fun writeMessages(messages: List<com.reversetutor.core.model.Message>, nowEpochMillis: Long): Boolean
    suspend fun writeAnchors(anchors: List<DebugAnchorSeed>, nowEpochMillis: Long): Boolean
    suspend fun writeNodes(nodes: List<DebugGraphNodeSeed>, nowEpochMillis: Long): Boolean
    suspend fun writeEdges(edges: List<com.reversetutor.core.data.graph.GraphEdgeInput>, nowEpochMillis: Long): Boolean
}

class DebugGraphScenarioSeeder(
    private val sink: DebugGraphScenarioSink,
    private val definition: DebugGraphScenarioSeed = DebugGraphScenarioDefinition.build()
) {
    constructor(graph: HybridAppGraph) : this(
        sink = RepositoryDebugGraphScenarioSink(
            sessionRepository = graph.sessionRepository,
            messageRepository = graph.messageRepository,
            memoryRepository = graph.memoryRepository,
            graphRepository = graph.graphRepository
        )
    )

    suspend fun ensureSeeded(nowEpochMillis: Long): DebugGraphSeedResult {
        return try {
            if (sink.markerExists(DebugGraphScenarioDefinition.MarkerId)) {
                DebugGraphSeedResult.AlreadySeeded
            } else if (!sink.writeSessions(definition.sessions, nowEpochMillis)) {
                DebugGraphSeedResult.Failed
            } else if (!sink.writeMessages(definition.messages, nowEpochMillis)) {
                DebugGraphSeedResult.Failed
            } else if (!sink.writeAnchors(definition.anchors, nowEpochMillis)) {
                DebugGraphSeedResult.Failed
            } else if (!sink.writeNodes(definition.nodes, nowEpochMillis)) {
                DebugGraphSeedResult.Failed
            } else if (!sink.writeEdges(definition.edges, nowEpochMillis)) {
                DebugGraphSeedResult.Failed
            } else {
                DebugGraphSeedResult.Seeded
            }
        } catch (_: Exception) {
            DebugGraphSeedResult.Failed
        }
    }
}

private class RepositoryDebugGraphScenarioSink(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository,
    private val graphRepository: GraphRepository
) : DebugGraphScenarioSink {
    override suspend fun markerExists(markerId: String): Boolean =
        graphRepository.snapshot(SessionRepository.defaultSpaceId).nodes.any { it.id == markerId }

    override suspend fun writeSessions(
        sessions: List<DebugSessionSeed>,
        nowEpochMillis: Long
    ): Boolean {
        sessions.forEachIndexed { index, seed ->
            sessionRepository.saveSession(
                TutorSession(
                    id = seed.id,
                    spaceId = SessionRepository.defaultSpaceId,
                    title = seed.title,
                    createdAtEpochMillis = nowEpochMillis + index,
                    updatedAtEpochMillis = nowEpochMillis + index
                )
            )
        }
        return true
    }

    override suspend fun writeMessages(
        messages: List<com.reversetutor.core.model.Message>,
        nowEpochMillis: Long
    ): Boolean {
        messages.forEachIndexed { index, message ->
            messageRepository.saveMessage(message.copy(createdAtEpochMillis = nowEpochMillis + index))
        }
        return true
    }

    override suspend fun writeAnchors(
        anchors: List<DebugAnchorSeed>,
        nowEpochMillis: Long
    ): Boolean {
        for (anchor in anchors) {
            val saved = memoryRepository.createAnchor(
                input = AnchorInput(
                    title = anchor.title,
                    body = anchor.body,
                    sourceMessageId = anchor.sourceMessageId
                ),
                nowEpochMillis = nowEpochMillis,
                spaceId = SessionRepository.defaultSpaceId,
                anchorId = anchor.id
            ) ?: return false
            check(saved.id == anchor.id)
        }
        return true
    }

    override suspend fun writeNodes(
        nodes: List<DebugGraphNodeSeed>,
        nowEpochMillis: Long
    ): Boolean {
        for (node in nodes) {
            graphRepository.saveNode(
                input = node.toInput(),
                nowEpochMillis = nowEpochMillis,
                spaceId = SessionRepository.defaultSpaceId
            ) ?: return false
        }
        return true
    }

    override suspend fun writeEdges(
        edges: List<com.reversetutor.core.data.graph.GraphEdgeInput>,
        nowEpochMillis: Long
    ): Boolean {
        for (edge in edges) {
            graphRepository.saveEdge(
                input = edge,
                nowEpochMillis = nowEpochMillis,
                spaceId = SessionRepository.defaultSpaceId
            ) ?: return false
        }
        return true
    }
}
