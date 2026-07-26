package com.reversetutor.preview.shell

import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.SourceChunk
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceRecord
import com.reversetutor.core.model.SourceType
import com.reversetutor.core.model.TutorSession
import com.reversetutor.feature.chat.ChatQueryCategory
import com.reversetutor.feature.chat.ChatQueryScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryChatReferenceQueryPortTest {
    @Test
    fun realPortMapsCurrentAndAllSessionMessageGraphAndSourceData() = runBlocking {
        val dataSource = FakeChatReferenceQueryDataSource()
        val port = RepositoryChatReferenceQueryPort(dataSource) { false }

        val current = port.query("函数", ChatQueryScope.CurrentSession, "session-a")
        assertEquals(
            listOf(ChatQueryCategory.Session, ChatQueryCategory.Graph, ChatQueryCategory.Source),
            current.results.map { it.category }
        )
        assertEquals(setOf("session-a"), current.results.mapNotNull { it.target.sessionId }.toSet())

        val all = port.query("函数", ChatQueryScope.AllSessions, "session-a")
        assertEquals(4, all.results.size)
        assertEquals(setOf("session-a", "session-b"), all.results.mapNotNull { it.target.sessionId }.toSet())
    }
}

private class FakeChatReferenceQueryDataSource : ChatReferenceQueryDataSource {
    private val sessions = listOf(session("session-a", "A"), session("session-b", "B"))

    override suspend fun listSessions(): List<TutorSession> = sessions

    override suspend fun listMessages(sessionId: String): List<MessageRecord> = listOf(
        MessageRecord(
            Message("message-$sessionId", "space", sessionId, MessageRole.User, "函数 message $sessionId", 1L),
            quote = null
        )
    )

    override suspend fun listGraphNodes(sessionId: String): List<GraphNode> =
        if (sessionId == "session-a") {
            listOf(GraphNode("node-a", "space", "函数 node", GraphNodeKind.Concept, 1L))
        } else {
            emptyList()
        }

    override suspend fun listSources(): List<SourceWithChunks> = listOf(
        SourceWithChunks(
            SourceRecord("source-a", "space", "函数.pdf", SourceType.Pdf, SourceParserStatus.FullyLocal, 1L),
            listOf(SourceChunk("chunk-a", "space", "source-a", 0, "函数 source", 2))
        )
    )

    override fun sourceIdsForSession(sessionId: String): Set<String> =
        if (sessionId == "session-a") setOf("source-a") else emptySet()

    private fun session(id: String, title: String) = TutorSession(
        id = id,
        spaceId = "space",
        title = title,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
