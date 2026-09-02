package com.reversetutor.core.data.agent

import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.data.local.dao.SessionAgentDao
import com.reversetutor.core.data.local.entity.AssistantReplyArtifactEntity

data class AssistantReplyArtifact(
    val assistantMessageId: String,
    val sessionId: String,
    val blocks: List<RichDocumentBlock>,
    val evidenceReferenceIds: List<String>,
    val toolResultCodes: List<String> = emptyList(),
    val createdAtEpochMillis: Long
)

interface AssistantReplyArtifactStore {
    suspend fun save(artifact: AssistantReplyArtifact)
    suspend fun read(sessionId: String, assistantMessageId: String): AssistantReplyArtifact?
}

class AssistantReplyArtifactRepository(private val store: AssistantReplyArtifactStore) {
    suspend fun save(
        assistantMessageId: String,
        sessionId: String,
        envelope: LlmAssistantReplyEnvelope,
        toolResultCodes: List<String> = emptyList(),
        nowEpochMillis: Long
    ) {
        val messageId = assistantMessageId.trim().take(160)
        val ownerSessionId = sessionId.trim().take(120)
        if (messageId.isEmpty() || ownerSessionId.isEmpty()) return
        store.save(
            AssistantReplyArtifact(
                assistantMessageId = messageId,
                sessionId = ownerSessionId,
                blocks = envelope.blocks.take(24).map { it.toDocumentBlock() },
                evidenceReferenceIds = envelope.evidenceReferenceIds.map { it.trim().take(120) }.filter { it.isNotEmpty() }.distinct().take(6),
                toolResultCodes = toolResultCodes.map { it.trim().take(80) }.filter { it.isNotEmpty() }.distinct().take(8),
                createdAtEpochMillis = nowEpochMillis
            )
        )
    }

    suspend fun read(sessionId: String, assistantMessageId: String): AssistantReplyArtifact? =
        store.read(sessionId.trim(), assistantMessageId.trim())
}

class InMemoryAssistantReplyArtifactStore : AssistantReplyArtifactStore {
    private val artifacts = linkedMapOf<String, AssistantReplyArtifact>()

    override suspend fun save(artifact: AssistantReplyArtifact) {
        artifacts[artifact.assistantMessageId] = artifact
    }

    override suspend fun read(sessionId: String, assistantMessageId: String): AssistantReplyArtifact? =
        artifacts[assistantMessageId]?.takeIf { it.sessionId == sessionId }
}

class RoomAssistantReplyArtifactStore(private val dao: SessionAgentDao) : AssistantReplyArtifactStore {
    override suspend fun save(artifact: AssistantReplyArtifact) {
        dao.upsertArtifact(
            AssistantReplyArtifactEntity(
                assistantMessageId = artifact.assistantMessageId,
                sessionId = artifact.sessionId,
                blocksPayload = AgentPayloadCodec.encodeBlocks(artifact.blocks),
                evidenceReferencesPayload = AgentPayloadCodec.encodeList(artifact.evidenceReferenceIds),
                toolResultsPayload = AgentPayloadCodec.encodeList(artifact.toolResultCodes),
                createdAtEpochMillis = artifact.createdAtEpochMillis
            )
        )
    }

    override suspend fun read(sessionId: String, assistantMessageId: String): AssistantReplyArtifact? =
        dao.getArtifact(sessionId, assistantMessageId)?.let { entity ->
            AssistantReplyArtifact(
                assistantMessageId = entity.assistantMessageId,
                sessionId = entity.sessionId,
                blocks = AgentPayloadCodec.decodeBlocks(entity.blocksPayload),
                evidenceReferenceIds = AgentPayloadCodec.decodeList(entity.evidenceReferencesPayload),
                toolResultCodes = AgentPayloadCodec.decodeList(entity.toolResultsPayload),
                createdAtEpochMillis = entity.createdAtEpochMillis
            )
        }
}
