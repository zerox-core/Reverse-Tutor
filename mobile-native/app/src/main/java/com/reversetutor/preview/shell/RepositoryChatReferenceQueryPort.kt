package com.reversetutor.preview.shell

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.GraphScope
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphSnapshotResult
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.feature.chat.ChatQueryCategory
import com.reversetutor.feature.chat.ChatQueryResponse
import com.reversetutor.feature.chat.ChatQueryResult
import com.reversetutor.feature.chat.ChatQueryScope
import com.reversetutor.feature.chat.ChatQueryTarget
import com.reversetutor.feature.chat.ChatReferenceQueryPort

class RepositoryChatReferenceQueryPort(
    private val dataSource: ChatReferenceQueryDataSource,
    private val offline: () -> Boolean = { false }
) : ChatReferenceQueryPort {
    constructor(
        sessionRepository: SessionRepository,
        messageRepository: MessageRepository,
        sourceRepository: SourceRepository,
        graphRepository: GraphRepository,
        sourceIdsForSession: (String) -> Set<String>,
        offline: () -> Boolean = { false }
    ) : this(
        dataSource = RepositoryChatReferenceQueryDataSource(
            sessionRepository,
            messageRepository,
            sourceRepository,
            graphRepository,
            sourceIdsForSession
        ),
        offline = offline
    )

    override suspend fun query(
        query: String,
        scope: ChatQueryScope,
        currentSessionId: String
    ): ChatQueryResponse {
        val term = query.trim()
        if (term.isEmpty()) return ChatQueryResponse(emptyList(), offline())
        val sessions = dataSource.listSessions()
            .filterNot { it.archived }
            .filter { scope == ChatQueryScope.AllSessions || it.id == currentSessionId }
        val messageResults = sessions.flatMap { session ->
            dataSource.listMessages(session.id)
                .filter { it.message.text.contains(term, ignoreCase = true) }
                .map { record ->
                    ChatQueryResult(
                        id = "message:${record.message.id}",
                        category = ChatQueryCategory.Session,
                        summary = record.message.text.take(140),
                        sourceIdentity = session.title,
                        target = ChatQueryTarget.Message(session.id, record.message.id)
                    )
                }
        }
        val graphResults = sessions.flatMap { session ->
            dataSource.listGraphNodes(session.id)
                .filter { it.label.contains(term, ignoreCase = true) }
                .map { node ->
                    ChatQueryResult(
                        id = "graph:${session.id}:${node.id}",
                        category = ChatQueryCategory.Graph,
                        summary = node.label,
                        sourceIdentity = "${session.title} · 会话图谱",
                        target = ChatQueryTarget.GraphNode(session.id, node.id)
                    )
                }
        }
        val allowedSourceIds = if (scope == ChatQueryScope.CurrentSession) {
            dataSource.sourceIdsForSession(currentSessionId)
        } else {
            null
        }
        val sourceResults = dataSource.listSources()
            .filter { source -> allowedSourceIds == null || source.source.id in allowedSourceIds }
            .filter { source ->
                source.source.title.contains(term, ignoreCase = true) ||
                    source.chunks.any { it.text.contains(term, ignoreCase = true) }
            }
            .map { source ->
                val sessionId = sessions.firstOrNull {
                    source.source.id in dataSource.sourceIdsForSession(it.id)
                }?.id
                ChatQueryResult(
                    id = "source:${source.source.id}",
                    category = ChatQueryCategory.Source,
                    summary = source.chunks.firstOrNull {
                        it.text.contains(term, ignoreCase = true)
                    }?.text?.take(140) ?: source.source.title,
                    sourceIdentity = source.source.title,
                    target = ChatQueryTarget.Source(sessionId, source.source.id)
                )
            }
        return ChatQueryResponse(
            results = (messageResults + graphResults + sourceResults).distinctBy { it.id },
            offline = offline()
        )
    }
}

interface ChatReferenceQueryDataSource {
    suspend fun listSessions(): List<TutorSession>
    suspend fun listMessages(sessionId: String): List<MessageRecord>
    suspend fun listGraphNodes(sessionId: String): List<GraphNode>
    suspend fun listSources(): List<SourceWithChunks>
    fun sourceIdsForSession(sessionId: String): Set<String>
}

private class RepositoryChatReferenceQueryDataSource(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val sourceRepository: SourceRepository,
    private val graphRepository: GraphRepository,
    private val sourceIds: (String) -> Set<String>
) : ChatReferenceQueryDataSource {
    override suspend fun listSessions(): List<TutorSession> = sessionRepository.listSessions()

    override suspend fun listMessages(sessionId: String): List<MessageRecord> =
        messageRepository.listMessageRecords(sessionId)

    override suspend fun listGraphNodes(sessionId: String): List<GraphNode> =
        when (val snapshot = graphRepository.snapshot(GraphScope.Session(sessionId))) {
            is GraphSnapshotResult.Ready -> snapshot.snapshot.nodes
            is GraphSnapshotResult.Empty,
            is GraphSnapshotResult.Error -> emptyList()
        }

    override suspend fun listSources(): List<SourceWithChunks> = sourceRepository.listSourcesWithChunks()

    override fun sourceIdsForSession(sessionId: String): Set<String> = sourceIds(sessionId)
}
