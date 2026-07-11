package com.reversetutor.core.data.migration

import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.protocol.ProtocolDocumentType
import com.reversetutor.core.protocol.ProtocolValidationResult
import com.reversetutor.core.protocol.export.ExportFieldValue
import com.reversetutor.core.protocol.export.ExportGraphEdgeRecord
import com.reversetutor.core.protocol.export.ExportGraphNodeRecord
import com.reversetutor.core.protocol.export.ExportGraphSnapshotPayload
import com.reversetutor.core.protocol.export.ExportLlmProfileRecord
import com.reversetutor.core.protocol.export.ExportMessageRecord
import com.reversetutor.core.protocol.export.ExportModelBindingRecord
import com.reversetutor.core.protocol.export.ExportProviderConnectionRecord
import com.reversetutor.core.protocol.export.ExportSecretStatus
import com.reversetutor.core.protocol.export.ExportSessionRecord
import com.reversetutor.core.protocol.export.ProtocolExportPayloadBuilder
import com.reversetutor.core.protocol.export.ProtocolExportPayloadValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NativeExportRepository(
    private val store: NativeExportStore,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun currentSession(
        sessionId: String,
        createdAt: String,
        targetFileName: String = "reverse-tutor-session-$sessionId.json"
    ): NativeExportResult {
        val session = store.getSession(sessionId)
            ?: return NativeExportResult.failed(
                kind = NativeExportKind.CurrentSession,
                targetFileName = targetFileName,
                error = "Session $sessionId was not found."
            )
        val settings = store.getSessionSettings(session.id)
        val messages = store.listMessages(session.id)
        val snapshot = NativeExportSnapshot(
            sessions = listOf(session.toExportRecord(settings)),
            messages = messages.map { it.toExportRecord(includeSessionId = false) }
        )
        val payload = ProtocolExportPayloadBuilder.currentSession(
            createdAt = createdAt,
            session = snapshot.sessions.single(),
            messages = snapshot.messages
        )
        return NativeExportResult.fromPayload(
            kind = NativeExportKind.CurrentSession,
            targetFileName = targetFileName,
            snapshot = snapshot,
            json = payload.json,
            documentType = payload.documentType
        )
    }

    suspend fun fullBackup(
        createdAt: String,
        spaceId: String = defaultSpaceId,
        targetFileName: String = "reverse-tutor-full-backup.json"
    ): NativeExportResult {
        val sessions = store.listSessions(spaceId)
        val settingsBySessionId = sessions.associate { session ->
            session.id to store.getSessionSettings(session.id)
        }
        val messages = sessions.flatMap { store.listMessages(it.id) }
        val graph = ExportGraphSnapshotPayload(
            createdAt = createdAt,
            nodes = store.listGraphNodes(spaceId).map { it.toExportRecord() },
            edges = store.listGraphEdges(spaceId).map { it.toExportRecord() }
        )
        val snapshot = NativeExportSnapshot(
            sessions = sessions.map { it.toExportRecord(settingsBySessionId[it.id]) },
            messages = messages.map { it.toExportRecord(includeSessionId = true) },
            llmProfiles = store.listLlmProfiles(spaceId).map { it.toExportRecord() },
            providerConnections = store.listProviderConnections(spaceId).map { it.toExportRecord() },
            modelBindings = store.listModelBindings(spaceId).map { it.toExportRecord() },
            graph = graph
        )
        val payload = ProtocolExportPayloadBuilder.fullBackup(
            createdAt = createdAt,
            sessions = snapshot.sessions,
            llmProfiles = snapshot.llmProfiles,
            graph = graph,
            providerConnections = snapshot.providerConnections,
            modelBindings = snapshot.modelBindings
        )
        return NativeExportResult.fromPayload(
            kind = NativeExportKind.FullBackup,
            targetFileName = targetFileName,
            snapshot = snapshot,
            json = payload.json,
            documentType = payload.documentType
        )
    }

    suspend fun graphSnapshot(
        createdAt: String,
        spaceId: String = defaultSpaceId,
        targetFileName: String = "reverse-tutor-graph-snapshot.json"
    ): NativeExportResult {
        val snapshot = NativeExportSnapshot(
            graph = ExportGraphSnapshotPayload(
                createdAt = createdAt,
                nodes = store.listGraphNodes(spaceId).map { it.toExportRecord() },
                edges = store.listGraphEdges(spaceId).map { it.toExportRecord() }
            )
        )
        val payload = ProtocolExportPayloadBuilder.graphSnapshot(snapshot.graph!!)
        return NativeExportResult.fromPayload(
            kind = NativeExportKind.GraphSnapshot,
            targetFileName = targetFileName,
            snapshot = snapshot,
            json = payload.json,
            documentType = payload.documentType
        )
    }
}

interface NativeExportStore {
    suspend fun getSession(id: String): SessionEntity?
    suspend fun getSessionSettings(sessionId: String): SessionSettingsEntity?
    suspend fun listSessions(spaceId: String): List<SessionEntity>
    suspend fun listMessages(sessionId: String): List<MessageEntity>
    suspend fun listLlmProfiles(spaceId: String): List<LlmProfileEntity>
    suspend fun listProviderConnections(spaceId: String): List<ProviderConnectionEntity> = emptyList()
    suspend fun listModelBindings(spaceId: String): List<ModelBindingEntity> = emptyList()
    suspend fun listGraphNodes(spaceId: String): List<GraphNodeEntity>
    suspend fun listGraphEdges(spaceId: String): List<GraphEdgeEntity>
}

class RoomNativeExportStore(
    private val database: ReverseTutorDatabase
) : NativeExportStore {
    override suspend fun getSession(id: String): SessionEntity? =
        database.sessionDao().getById(id)

    override suspend fun getSessionSettings(sessionId: String): SessionSettingsEntity? =
        database.sessionSettingsDao().getBySessionId(sessionId)

    override suspend fun listSessions(spaceId: String): List<SessionEntity> =
        database.sessionDao().listBySpace(spaceId)

    override suspend fun listMessages(sessionId: String): List<MessageEntity> =
        database.messageDao().listBySession(sessionId)

    override suspend fun listLlmProfiles(spaceId: String): List<LlmProfileEntity> =
        database.llmProfileDao().listBySpace(spaceId)

    override suspend fun listProviderConnections(spaceId: String): List<ProviderConnectionEntity> =
        database.modelConnectionDao().listConnections(spaceId)

    override suspend fun listModelBindings(spaceId: String): List<ModelBindingEntity> =
        database.modelConnectionDao().listBindings(spaceId)

    override suspend fun listGraphNodes(spaceId: String): List<GraphNodeEntity> =
        database.graphDao().listNodesBySpace(spaceId)

    override suspend fun listGraphEdges(spaceId: String): List<GraphEdgeEntity> =
        database.graphDao().listEdgesBySpace(spaceId)
}

enum class NativeExportKind {
    CurrentSession,
    GraphSnapshot,
    FullBackup
}

data class NativeExportSnapshot(
    val sessions: List<ExportSessionRecord> = emptyList(),
    val messages: List<ExportMessageRecord> = emptyList(),
    val llmProfiles: List<ExportLlmProfileRecord> = emptyList(),
    val providerConnections: List<ExportProviderConnectionRecord> = emptyList(),
    val modelBindings: List<ExportModelBindingRecord> = emptyList(),
    val graph: ExportGraphSnapshotPayload? = null
)

data class NativeExportResult(
    val kind: NativeExportKind,
    val targetFileName: String,
    val documentType: ProtocolDocumentType?,
    val json: String?,
    val snapshot: NativeExportSnapshot,
    val validation: ProtocolValidationResult?,
    val warnings: List<String>,
    val errors: List<String>
) {
    val isValid: Boolean =
        errors.isEmpty() && validation?.isValid == true

    companion object {
        fun failed(
            kind: NativeExportKind,
            targetFileName: String,
            error: String
        ): NativeExportResult =
            NativeExportResult(
                kind = kind,
                targetFileName = targetFileName,
                documentType = null,
                json = null,
                snapshot = NativeExportSnapshot(),
                validation = null,
                warnings = emptyList(),
                errors = listOf(error)
            )

        fun fromPayload(
            kind: NativeExportKind,
            targetFileName: String,
            snapshot: NativeExportSnapshot,
            json: String,
            documentType: ProtocolDocumentType
        ): NativeExportResult {
            val validation = ProtocolExportPayloadValidator.validate(json)
            return NativeExportResult(
                kind = kind,
                targetFileName = targetFileName,
                documentType = documentType,
                json = json,
                snapshot = snapshot,
                validation = validation,
                warnings = validation.warnings,
                errors = validation.errors
            )
        }
    }
}

private fun SessionEntity.toExportRecord(settings: SessionSettingsEntity?): ExportSessionRecord {
    val extras = linkedMapOf<String, ExportFieldValue>(
        "space_id" to ExportFieldValue.StringValue(spaceId),
        "updated_at" to ExportFieldValue.StringValue(updatedAtEpochMillis.toIsoUtc()),
        "pinned" to ExportFieldValue.BooleanValue(pinned),
        "archived" to ExportFieldValue.BooleanValue(archived)
    )
    (modelBindingId ?: llmProfileId)?.let {
        extras["model_binding_id"] = ExportFieldValue.StringValue(it)
    }
    settings?.systemPrompt?.takeIf { it.isNotBlank() }?.let {
        extras["system_prompt"] = ExportFieldValue.StringValue(it)
    }
    return ExportSessionRecord(
        id = id,
        title = title,
        createdAt = createdAtEpochMillis.toIsoUtc(),
        extraFields = extras
    )
}

private fun MessageEntity.toExportRecord(includeSessionId: Boolean): ExportMessageRecord {
    val extras = linkedMapOf<String, ExportFieldValue>(
        "space_id" to ExportFieldValue.StringValue(spaceId)
    )
    if (includeSessionId) {
        extras["session_id"] = ExportFieldValue.StringValue(sessionId)
    }
    parentMessageId?.let { extras["parent_message_id"] = ExportFieldValue.StringValue(it) }
    return ExportMessageRecord(
        id = id,
        role = role.lowercase(),
        text = text,
        createdAt = createdAtEpochMillis.toIsoUtc(),
        extraFields = extras
    )
}

private fun LlmProfileEntity.toExportRecord(): ExportLlmProfileRecord =
    ExportLlmProfileRecord(
        id = id,
        name = name,
        provider = provider.toProtocolProvider(),
        apiType = provider.toProtocolProvider(),
        model = model,
        baseUrl = baseUrl,
        capabilities = mapOf("enabled" to enabled),
        secretStatus = if (secretRef.isNullOrBlank()) ExportSecretStatus.None else ExportSecretStatus.Excluded,
        secretRef = null,
        apiKey = null
    )

private fun ProviderConnectionEntity.toExportRecord(): ExportProviderConnectionRecord =
    ExportProviderConnectionRecord(
        id = id,
        name = name,
        protocol = protocol,
        providerName = providerName,
        baseUrl = baseUrl,
        secretStatus = if (secretRef.isNullOrBlank()) ExportSecretStatus.None else ExportSecretStatus.Excluded
    )

private fun ModelBindingEntity.toExportRecord(): ExportModelBindingRecord =
    ExportModelBindingRecord(
        id = id,
        connectionId = connectionId,
        modelId = modelId,
        displayName = displayName,
        enabled = enabled
    )

private fun GraphNodeEntity.toExportRecord(): ExportGraphNodeRecord =
    ExportGraphNodeRecord(
        id = id,
        kind = kind,
        title = label,
        extraFields = linkedMapOf(
            "status" to ExportFieldValue.StringValue(status),
            "created_at" to ExportFieldValue.StringValue(createdAtEpochMillis.toIsoUtc())
        )
    )

private fun GraphEdgeEntity.toExportRecord(): ExportGraphEdgeRecord =
    ExportGraphEdgeRecord(
        id = id,
        sourceNodeId = fromNodeId,
        targetNodeId = toNodeId,
        kind = relation,
        extraFields = linkedMapOf(
            "created_at" to ExportFieldValue.StringValue(createdAtEpochMillis.toIsoUtc())
        )
    )

private fun String.toProtocolProvider(): String =
    when (this) {
        "OpenAiCompatible" -> "openai_compatible"
        "AnthropicCompatible" -> "anthropic_compatible"
        "FreeGlm" -> "free_glm"
        else -> replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }

private fun Long.toIsoUtc(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(this))
}
