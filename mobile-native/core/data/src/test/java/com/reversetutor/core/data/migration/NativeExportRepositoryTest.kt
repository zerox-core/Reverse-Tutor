package com.reversetutor.core.data.migration

import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.protocol.ProtocolDocumentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeExportRepositoryTest {
    @Test
    fun currentSessionBuildsDeterministicProtocolPayloadFromStoredSessionAndMessages() = runBlocking {
        val store = FakeNativeExportStore(
            sessions = listOf(
                SessionEntity(
                    id = "session-1",
                    spaceId = "default-space",
                    title = "Limits review",
                    createdAtEpochMillis = 1_788_153_600_000L,
                    updatedAtEpochMillis = 1_788_153_660_000L,
                    pinned = true,
                    llmProfileId = "profile-1"
                )
            ),
            settings = listOf(
                SessionSettingsEntity(
                    id = "settings-session-1",
                    spaceId = "default-space",
                    sessionId = "session-1",
                    llmProfileId = "profile-1",
                    systemPrompt = "Use Socratic hints."
                )
            ),
            messages = listOf(
                MessageEntity(
                    id = "message-1",
                    spaceId = "default-space",
                    sessionId = "session-1",
                    role = "User",
                    text = "Explain limits intuitively",
                    createdAtEpochMillis = 1_788_153_601_000L
                ),
                MessageEntity(
                    id = "message-2",
                    spaceId = "default-space",
                    sessionId = "session-1",
                    role = "Assistant",
                    text = "Think of values getting closer.",
                    createdAtEpochMillis = 1_788_153_602_000L,
                    parentMessageId = "message-1"
                )
            )
        )
        val repository = NativeExportRepository(store)

        val result = repository.currentSession(
            sessionId = "session-1",
            createdAt = fixtureCreatedAt,
            targetFileName = "session.json"
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(ProtocolDocumentType.SessionExport, result.documentType)
        assertEquals("session.json", result.targetFileName)
        assertEquals("session-1", result.snapshot.sessions.single().id)
        assertEquals(listOf("message-1", "message-2"), result.snapshot.messages.map { it.id })
        assertEquals(
            result.json,
            repository.currentSession(
                sessionId = "session-1",
                createdAt = fixtureCreatedAt,
                targetFileName = "session.json"
            ).json
        )
    }

    @Test
    fun fullBackupBuildsProtocolPayloadWithoutSecretReferencesOrApiKeys() = runBlocking {
        val store = FakeNativeExportStore(
            sessions = listOf(
                SessionEntity(
                    id = "session-1",
                    spaceId = "default-space",
                    title = "Calculus",
                    createdAtEpochMillis = 1_788_153_600_000L,
                    updatedAtEpochMillis = 1_788_153_660_000L
                )
            ),
            messages = listOf(
                MessageEntity(
                    id = "message-1",
                    spaceId = "default-space",
                    sessionId = "session-1",
                    role = "User",
                    text = "This remains in the snapshot.",
                    createdAtEpochMillis = 1_788_153_601_000L
                )
            ),
            llmProfiles = listOf(
                LlmProfileEntity(
                    id = "profile-1",
                    spaceId = "default-space",
                    name = "OpenAI compatible",
                    provider = "OpenAiCompatible",
                    model = "fixture-model",
                    secretRef = "llm-secret-profile-1",
                    createdAtEpochMillis = 1_788_153_500_000L,
                    updatedAtEpochMillis = 1_788_153_550_000L,
                    baseUrl = "https://example.invalid/v1",
                    enabled = true
                )
            ),
            graphNodes = listOf(
                GraphNodeEntity(
                    id = "node-1",
                    spaceId = "default-space",
                    label = "Derivative",
                    kind = "concept",
                    createdAtEpochMillis = 1_788_153_610_000L,
                    status = "Active"
                )
            ),
            graphEdges = listOf(
                GraphEdgeEntity(
                    id = "edge-1",
                    spaceId = "default-space",
                    fromNodeId = "node-1",
                    toNodeId = "node-1",
                    relation = "self",
                    createdAtEpochMillis = 1_788_153_620_000L
                )
            )
        )
        val repository = NativeExportRepository(store)

        val result = repository.fullBackup(
            createdAt = fixtureCreatedAt,
            spaceId = "default-space",
            targetFileName = "backup.json"
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(ProtocolDocumentType.FullBackup, result.documentType)
        assertEquals("backup.json", result.targetFileName)
        assertEquals(1, result.snapshot.messages.size)
        assertNotNull(result.snapshot.graph)
        val json = result.json.orEmpty()
        assertFalse(json.contains("secretRef"))
        assertFalse(json.contains("llm-secret-profile-1"))
        assertFalse(json.contains("apiKey"))
        assertFalse(json.contains("sk-"))
        assertTrue(json.contains("\"secret_status\":\"excluded\""))
        assertEquals(
            json,
            repository.fullBackup(
                createdAt = fixtureCreatedAt,
                targetFileName = "backup.json"
            ).json
        )
    }

    @Test
    fun graphSnapshotBuildsStandaloneProtocolPayload() = runBlocking {
        val store = FakeNativeExportStore(
            graphNodes = listOf(
                GraphNodeEntity(
                    id = "node-1",
                    spaceId = "default-space",
                    label = "Gradient",
                    kind = "concept",
                    createdAtEpochMillis = 1_788_153_610_000L,
                    status = "Active"
                )
            ),
            graphEdges = listOf(
                GraphEdgeEntity(
                    id = "edge-1",
                    spaceId = "default-space",
                    fromNodeId = "node-1",
                    toNodeId = "node-1",
                    relation = "self",
                    createdAtEpochMillis = 1_788_153_620_000L
                )
            )
        )
        val repository = NativeExportRepository(store)

        val result = repository.graphSnapshot(
            createdAt = fixtureCreatedAt,
            spaceId = "default-space",
            targetFileName = "graph.json"
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(NativeExportKind.GraphSnapshot, result.kind)
        assertEquals(ProtocolDocumentType.GraphSnapshot, result.documentType)
        assertEquals("graph.json", result.targetFileName)
        assertEquals(1, result.snapshot.graph?.nodes?.size)
        assertEquals(1, result.snapshot.graph?.edges?.size)
        assertTrue(result.json.orEmpty().contains("\"type\":\"graph_snapshot\""))
    }

    private companion object {
        const val fixtureCreatedAt = "2026-07-01T00:00:00Z"
    }
}

private class FakeNativeExportStore(
    sessions: List<SessionEntity> = emptyList(),
    settings: List<SessionSettingsEntity> = emptyList(),
    messages: List<MessageEntity> = emptyList(),
    llmProfiles: List<LlmProfileEntity> = emptyList(),
    graphNodes: List<GraphNodeEntity> = emptyList(),
    graphEdges: List<GraphEdgeEntity> = emptyList()
) : NativeExportStore {
    private val sessionsById = sessions.associateBy { it.id }
    private val settingsBySessionId = settings.associateBy { it.sessionId }
    private val messagesBySessionId = messages.groupBy { it.sessionId }
    private val profiles = llmProfiles
    private val nodes = graphNodes
    private val edges = graphEdges

    override suspend fun getSession(id: String): SessionEntity? =
        sessionsById[id]

    override suspend fun getSessionSettings(sessionId: String): SessionSettingsEntity? =
        settingsBySessionId[sessionId]

    override suspend fun listSessions(spaceId: String): List<SessionEntity> =
        sessionsById.values
            .filter { it.spaceId == spaceId && !it.archived }
            .sortedWith(compareByDescending<SessionEntity> { it.pinned }.thenByDescending { it.updatedAtEpochMillis })

    override suspend fun listMessages(sessionId: String): List<MessageEntity> =
        messagesBySessionId[sessionId].orEmpty().sortedBy { it.createdAtEpochMillis }

    override suspend fun listLlmProfiles(spaceId: String): List<LlmProfileEntity> =
        profiles.filter { it.spaceId == spaceId }.sortedByDescending { it.updatedAtEpochMillis }

    override suspend fun listGraphNodes(spaceId: String): List<GraphNodeEntity> =
        nodes.filter { it.spaceId == spaceId }.sortedBy { it.label }

    override suspend fun listGraphEdges(spaceId: String): List<GraphEdgeEntity> =
        edges.filter { it.spaceId == spaceId }.sortedBy { it.createdAtEpochMillis }
}
