package com.reversetutor.core.data.migration

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.ImportBatchEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.SpaceKind
import com.reversetutor.core.protocol.ProtocolDocumentType
import com.reversetutor.core.protocol.ProtocolImportDocument
import com.reversetutor.core.protocol.ProtocolImportLlmProfileRecord
import com.reversetutor.core.protocol.ProtocolImportMessageRecord
import com.reversetutor.core.protocol.ProtocolImportModelBindingRecord
import com.reversetutor.core.protocol.ProtocolImportProviderConnectionRecord
import com.reversetutor.core.protocol.ProtocolImportReader
import com.reversetutor.core.protocol.ProtocolImportSessionRecord
import com.reversetutor.core.protocol.ProtocolModule
import java.security.MessageDigest
import java.util.UUID

class NativeImportRepository(
    private val store: NativeImportStore,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun dryRun(
        json: String,
        sourceFileName: String,
        nowEpochMillis: Long,
        mode: NativeImportMode = NativeImportMode.Append
    ): NativeImportResult =
        plan(json, sourceFileName, mode, nowEpochMillis).toResult(
            status = NativeImportStatus.DryRun,
            completedAtEpochMillis = null
        )

    suspend fun importJson(
        json: String,
        sourceFileName: String,
        nowEpochMillis: Long,
        mode: NativeImportMode = NativeImportMode.Append,
        overwriteConfirmed: Boolean = false,
        batchId: String = "import-${UUID.randomUUID()}"
    ): NativeImportResult {
        var plan = plan(json, sourceFileName, mode, nowEpochMillis, batchId)
        if (mode == NativeImportMode.Overwrite && !overwriteConfirmed) {
            plan = plan.copy(
                errors = (plan.errors + "Overwrite import requires destructive confirmation.").distinct()
            )
            return plan.toResult(
                status = NativeImportStatus.Failed,
                completedAtEpochMillis = null
            )
        }
        if (!plan.canWrite) {
            return plan.toResult(
                status = NativeImportStatus.Failed,
                completedAtEpochMillis = null
            )
        }

        val status = if (plan.errors.isEmpty()) NativeImportStatus.Completed else NativeImportStatus.Partial
        val result = plan.toResult(
            status = status,
            completedAtEpochMillis = nowEpochMillis
        )
        store.writeImport(plan.toWriteSet(result))
        return result
    }

    private fun plan(
        json: String,
        sourceFileName: String,
        mode: NativeImportMode,
        nowEpochMillis: Long,
        batchId: String = "dry-run"
    ): NativeImportPlan {
        val readResult = ProtocolImportReader.read(json)
        val document = readResult.document
        if (document == null) {
            return NativeImportPlan(
                batchId = batchId,
                sourceFileName = sourceFileName,
                sourceSchema = readResult.validation.schema ?: "unknown",
                mode = mode,
                spaceId = defaultSpaceId,
                nowEpochMillis = nowEpochMillis,
                warnings = readResult.warnings,
                errors = readResult.validation.errors,
                wholeFileInvalid = true
            )
        }

        val warnings = readResult.warnings.toMutableList()
        val errors = mutableListOf<String>()
        val spaceId = when (mode) {
            NativeImportMode.Append,
            NativeImportMode.Overwrite -> defaultSpaceId
            NativeImportMode.NewSpace -> document.toStableImportSpaceId(sourceFileName)
        }
        val records = document.toRecords(
            batchId = batchId,
            spaceId = spaceId,
            nowEpochMillis = nowEpochMillis,
            idPrefix = if (mode == NativeImportMode.NewSpace) "$spaceId-" else "",
            errors = errors
        )
        if (!records.hasWritableRecords) {
            warnings += "No writable records were detected."
        }

        return NativeImportPlan(
            batchId = batchId,
            sourceFileName = sourceFileName,
            sourceSchema = document.sourceSchema,
            mode = mode,
            spaceId = spaceId,
            spaceName = document.toSpaceName(sourceFileName, mode),
            nowEpochMillis = nowEpochMillis,
            records = records,
            warnings = warnings.distinct(),
            errors = errors.distinct(),
            wholeFileInvalid = false,
            documentType = document.documentType
        )
    }
}

interface NativeImportStore {
    suspend fun writeImport(writeSet: NativeImportWriteSet)
}

class RoomNativeImportStore(
    private val database: ReverseTutorDatabase
) : NativeImportStore {
    override suspend fun writeImport(writeSet: NativeImportWriteSet) {
        database.withTransaction {
            if (writeSet.replaceExistingSpace) {
                database.clearImportTargetSpace(writeSet.space.id)
            }
            database.spaceDao().upsert(writeSet.space)
            writeSet.sessions.forEach { database.sessionDao().upsert(it) }
            writeSet.sessionSettings.forEach { database.sessionSettingsDao().upsert(it) }
            writeSet.messages.forEach { database.messageDao().insert(it) }
            writeSet.llmProfiles.forEach { database.llmProfileDao().upsert(it) }
            writeSet.providerConnections.forEach { database.modelConnectionDao().upsertConnection(it) }
            writeSet.modelBindings.forEach { database.modelConnectionDao().upsertBinding(it) }
            database.importBatchDao().insert(writeSet.batch)
        }
    }
}

enum class NativeImportMode(val wireValue: String) {
    Append("append"),
    Overwrite("overwrite"),
    NewSpace("new_space");

    val label: String
        get() = when (this) {
            Append -> "Append"
            Overwrite -> "Overwrite"
            NewSpace -> "New space"
        }
}

enum class NativeImportStatus(val wireValue: String, val label: String) {
    DryRun("dry_run", "Dry run"),
    Completed("completed", "Completed"),
    Partial("partial", "Partial"),
    Failed("failed", "Failed")
}

data class NativeImportResult(
    val batchId: String,
    val sourceFileName: String,
    val sourceSchema: String,
    val documentType: ProtocolDocumentType?,
    val mode: NativeImportMode,
    val status: NativeImportStatus,
    val insertedCounts: Map<String, Int>,
    val skippedCounts: Map<String, Int>,
    val warnings: List<String>,
    val errors: List<String>,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?
) {
    val canWrite: Boolean = status == NativeImportStatus.DryRun && errors.isEmpty() && insertedCounts.values.any { it > 0 }

    fun toProtocolJson(): String =
        """{"schema":"${ProtocolModule.importResultSchema}","version":1,"type":"import_result","status":"${status.wireValue}","mode":"${mode.wireValue}","inserted_counts":${insertedCounts.toJsonObject()},"skipped_counts":${skippedCounts.toJsonObject()},"warnings":${warnings.toJsonArray()},"errors":${errors.toJsonArray()}}"""
}

data class NativeImportWriteSet(
    val batch: ImportBatchEntity,
    val space: SpaceEntity,
    val sessions: List<SessionEntity>,
    val sessionSettings: List<SessionSettingsEntity>,
    val messages: List<MessageEntity>,
    val llmProfiles: List<LlmProfileEntity>,
    val providerConnections: List<ProviderConnectionEntity>,
    val modelBindings: List<ModelBindingEntity>,
    val replaceExistingSpace: Boolean
)

private data class NativeImportPlan(
    val batchId: String,
    val sourceFileName: String,
    val sourceSchema: String,
    val mode: NativeImportMode,
    val spaceId: String,
    val spaceName: String = "Default",
    val nowEpochMillis: Long,
    val records: NativeImportRecords = NativeImportRecords(),
    val warnings: List<String>,
    val errors: List<String>,
    val wholeFileInvalid: Boolean,
    val documentType: ProtocolDocumentType? = null
) {
    val canWrite: Boolean =
        !wholeFileInvalid && records.hasWritableRecords

    fun toResult(
        status: NativeImportStatus,
        completedAtEpochMillis: Long?
    ): NativeImportResult =
        NativeImportResult(
            batchId = batchId,
            sourceFileName = sourceFileName,
            sourceSchema = sourceSchema,
            documentType = documentType,
            mode = mode,
            status = status,
            insertedCounts = records.insertedCounts(),
            skippedCounts = records.skippedCounts(),
            warnings = warnings,
            errors = errors,
            startedAtEpochMillis = nowEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis
        )

    fun toWriteSet(result: NativeImportResult): NativeImportWriteSet =
        NativeImportWriteSet(
            batch = ImportBatchEntity(
                id = batchId,
                spaceId = spaceId,
                sourceFileName = sourceFileName,
                sourceSchema = sourceSchema,
                mode = mode.wireValue,
                status = result.status.wireValue,
                startedAtEpochMillis = nowEpochMillis,
                completedAtEpochMillis = result.completedAtEpochMillis,
                insertedCountsJson = result.insertedCounts.toJsonObject(),
                skippedCountsJson = result.skippedCounts.toJsonObject(),
                warningsJson = warnings.toJsonArray(),
                errorsJson = errors.toJsonArray()
            ),
            space = SpaceEntity(
                id = spaceId,
                name = spaceName,
                kind = if (mode == NativeImportMode.NewSpace) SpaceKind.Imported.name else SpaceKind.Default.name,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis
            ),
            sessions = records.sessions,
            sessionSettings = records.sessionSettings,
            messages = records.messages,
            llmProfiles = records.llmProfiles,
            providerConnections = records.providerConnections,
            modelBindings = records.modelBindings,
            replaceExistingSpace = mode == NativeImportMode.Overwrite
        )
}

private data class NativeImportRecords(
    val sessions: List<SessionEntity> = emptyList(),
    val sessionSettings: List<SessionSettingsEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val llmProfiles: List<LlmProfileEntity> = emptyList(),
    val providerConnections: List<ProviderConnectionEntity> = emptyList(),
    val modelBindings: List<ModelBindingEntity> = emptyList(),
    val skippedSessions: Int = 0,
    val skippedMessages: Int = 0,
    val skippedProfiles: Int = 0
) {
    val hasWritableRecords: Boolean =
        sessions.isNotEmpty() || messages.isNotEmpty() || llmProfiles.isNotEmpty() ||
            providerConnections.isNotEmpty() || modelBindings.isNotEmpty()

    fun insertedCounts(): Map<String, Int> =
        linkedMapOf(
            "sessions" to sessions.size,
            "messages" to messages.size,
            "llm_profiles" to llmProfiles.size
        )

    fun skippedCounts(): Map<String, Int> =
        linkedMapOf(
            "sessions" to skippedSessions,
            "messages" to skippedMessages,
            "llm_profiles" to skippedProfiles
        )
}

private fun ProtocolImportDocument.toRecords(
    batchId: String,
    spaceId: String,
    nowEpochMillis: Long,
    idPrefix: String,
    errors: MutableList<String>
): NativeImportRecords {
    val sessionEntities = mutableListOf<SessionEntity>()
    val settingsEntities = mutableListOf<SessionSettingsEntity>()
    val profileEntities = mutableListOf<LlmProfileEntity>()
    val connectionEntities = mutableListOf<ProviderConnectionEntity>()
    val bindingEntities = mutableListOf<ModelBindingEntity>()
    val messageEntities = mutableListOf<MessageEntity>()
    val sessionIdMap = mutableMapOf<String, String>()
    var skippedSessions = 0
    var skippedMessages = 0
    var skippedProfiles = 0

    sessions.forEachIndexed { index, session ->
        val entity = session.toEntityOrNull(
            spaceId = spaceId,
            batchId = batchId,
            nowEpochMillis = nowEpochMillis + index
        )?.withImportIdPrefix(idPrefix)
        if (entity == null) {
            skippedSessions += 1
            errors += "Skipped session with missing id or title."
        } else {
            sessionEntities += entity
            sessionIdMap[session.id.trim()] = entity.id
            if (!session.systemPrompt.isNullOrBlank()) {
                settingsEntities += SessionSettingsEntity(
                    id = "settings-${entity.id}",
                    spaceId = spaceId,
                    sessionId = entity.id,
                    modelBindingId = entity.modelBindingId,
                    systemPrompt = session.systemPrompt
                )
            }
        }
    }

    val knownSessionIds = sessionEntities.map { it.id }.toSet()
    messages.forEachIndexed { index, message ->
        val entity = message.toEntityOrNull(
            spaceId = spaceId,
            batchId = batchId,
            nowEpochMillis = nowEpochMillis + sessionEntities.size + index,
            idPrefix = idPrefix,
            sessionIdMap = sessionIdMap,
            knownSessionIds = knownSessionIds
        )
        if (entity == null) {
            skippedMessages += 1
            errors += "Skipped message ${message.id.ifBlank { "<missing>" }} because its session, role, or text is invalid."
        } else {
            messageEntities += entity
        }
    }

    llmProfiles.forEachIndexed { index, profile ->
        val entity = profile.toEntityOrNull(
            spaceId = spaceId,
            nowEpochMillis = nowEpochMillis + sessionEntities.size + messageEntities.size + index
        )?.withImportIdPrefix(idPrefix)
        if (entity == null) {
            skippedProfiles += 1
            errors += "Skipped LLM profile with missing id, name, provider, or model."
        } else {
            profileEntities += entity
            connectionEntities += entity.toProviderConnectionEntity()
            bindingEntities += entity.toModelBindingEntity()
        }
    }

    providerConnections.mapNotNullTo(connectionEntities) {
        it.toEntityOrNull(spaceId, nowEpochMillis)
            ?.withImportIdPrefix(idPrefix)
    }
    modelBindings.mapNotNullTo(bindingEntities) {
        it.toEntityOrNull(spaceId, nowEpochMillis)
            ?.withImportIdPrefix(idPrefix)
    }

    return NativeImportRecords(
        sessions = sessionEntities,
        sessionSettings = settingsEntities,
        messages = messageEntities,
        llmProfiles = profileEntities,
        providerConnections = connectionEntities.distinctBy { it.id },
        modelBindings = bindingEntities.distinctBy { it.id },
        skippedSessions = skippedSessions,
        skippedMessages = skippedMessages,
        skippedProfiles = skippedProfiles
    )
}

private fun ProtocolImportSessionRecord.toEntityOrNull(
    spaceId: String,
    batchId: String,
    nowEpochMillis: Long
): SessionEntity? {
    val normalizedId = id.trim()
    val normalizedTitle = title.trim()
    if (normalizedId.isEmpty() || normalizedTitle.isEmpty()) return null
    return SessionEntity(
        id = normalizedId,
        spaceId = spaceId,
        title = normalizedTitle,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
        modelBindingId = modelBindingId,
        sourceImportId = batchId
    )
}

private fun ProtocolImportMessageRecord.toEntityOrNull(
    spaceId: String,
    batchId: String,
    nowEpochMillis: Long,
    idPrefix: String,
    sessionIdMap: Map<String, String>,
    knownSessionIds: Set<String>
): MessageEntity? {
    val normalizedId = id.trim()
    val normalizedSessionId = sessionIdMap[sessionId?.trim().orEmpty()] ?: sessionId?.trim().orEmpty()
    val normalizedText = text.trim()
    val role = role.toMessageRole() ?: return null
    if (normalizedId.isEmpty() || normalizedSessionId.isEmpty() || normalizedText.isEmpty()) return null
    if (knownSessionIds.isNotEmpty() && normalizedSessionId !in knownSessionIds) return null
    return MessageEntity(
        id = normalizedId.withImportIdPrefix(idPrefix),
        spaceId = spaceId,
        sessionId = normalizedSessionId,
        role = role.name,
        text = normalizedText,
        createdAtEpochMillis = nowEpochMillis,
        sourceImportId = batchId
    )
}

private fun SessionEntity.withImportIdPrefix(idPrefix: String): SessionEntity =
    if (idPrefix.isEmpty()) {
        this
    } else {
        copy(
            id = id.withImportIdPrefix(idPrefix),
            llmProfileId = llmProfileId?.withImportIdPrefix(idPrefix),
            modelBindingId = modelBindingId?.withImportIdPrefix(idPrefix)
        )
    }

private fun LlmProfileEntity.withImportIdPrefix(idPrefix: String): LlmProfileEntity =
    if (idPrefix.isEmpty()) this else copy(id = id.withImportIdPrefix(idPrefix))

private fun ProviderConnectionEntity.withImportIdPrefix(idPrefix: String): ProviderConnectionEntity =
    if (idPrefix.isEmpty()) this else copy(id = id.withImportIdPrefix(idPrefix))

private fun ModelBindingEntity.withImportIdPrefix(idPrefix: String): ModelBindingEntity =
    if (idPrefix.isEmpty()) {
        this
    } else {
        copy(
            id = id.withImportIdPrefix(idPrefix),
            connectionId = connectionId.withImportIdPrefix(idPrefix)
        )
    }

private fun String.withImportIdPrefix(idPrefix: String): String =
    if (idPrefix.isEmpty()) this else "$idPrefix$this"

private fun ProtocolImportDocument.toStableImportSpaceId(sourceFileName: String): String {
    val identity = buildList {
        add(sourceSchema)
        add(documentType.wireType)
        add(sourceFileName.trim().lowercase())
        sessions.mapTo(this) { "session:${it.id.trim()}" }
        messages.mapTo(this) { "message:${it.id.trim()}:${it.sessionId?.trim().orEmpty()}" }
        llmProfiles.mapTo(this) { "profile:${it.id.trim()}" }
        providerConnections.mapTo(this) { "connection:${it.id.trim()}" }
        modelBindings.mapTo(this) { "binding:${it.id.trim()}:${it.connectionId.trim()}" }
    }.joinToString("|")
    return "import-space-${identity.sha256Prefix(16)}"
}

private fun ProtocolImportDocument.toSpaceName(sourceFileName: String, mode: NativeImportMode): String {
    if (mode != NativeImportMode.NewSpace) return "Default"
    val base = sessions.firstOrNull()?.title?.trim()
        ?: sourceFileName.trim().ifEmpty { documentType.wireType }
    return "Import: ${base.take(52)}"
}

private fun String.sha256Prefix(length: Int): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
        .take(length)

private fun ReverseTutorDatabase.clearImportTargetSpace(spaceId: String) {
    val database = openHelper.writableDatabase
    val args = arrayOf<Any>(spaceId)
    listOf(
        "DELETE FROM sync_outbox WHERE spaceId = ?",
        "DELETE FROM sync_cursors WHERE spaceId = ?",
        "DELETE FROM sync_conflicts WHERE spaceId = ?",
        "DELETE FROM entity_tombstones WHERE spaceId = ?",
        "DELETE FROM search_documents WHERE spaceId = ?",
        "DELETE FROM widget_layout_preferences WHERE spaceId = ?",
        "DELETE FROM token_usage_records WHERE spaceId = ?",
        "DELETE FROM weekly_summaries WHERE spaceId = ?",
        "DELETE FROM study_plan_tasks WHERE spaceId = ?",
        "DELETE FROM turn_runs WHERE spaceId = ?",
        "DELETE FROM context_snapshots WHERE spaceId = ?",
        "DELETE FROM message_quotes WHERE spaceId = ?",
        "DELETE FROM message_attachments WHERE spaceId = ?",
        "DELETE FROM messages WHERE spaceId = ?",
        "DELETE FROM session_settings WHERE spaceId = ?",
        "DELETE FROM sessions WHERE spaceId = ?",
        "DELETE FROM model_bindings WHERE spaceId = ?",
        "DELETE FROM provider_connections WHERE spaceId = ?",
        "DELETE FROM llm_profiles WHERE spaceId = ?",
        "DELETE FROM background_jobs WHERE spaceId = ?",
        "DELETE FROM source_chunks WHERE spaceId = ?",
        "DELETE FROM sources WHERE spaceId = ?",
        "DELETE FROM graph_edges WHERE spaceId = ?",
        "DELETE FROM graph_nodes WHERE spaceId = ?",
        "DELETE FROM memory_items WHERE spaceId = ?",
        "DELETE FROM anchors WHERE spaceId = ?",
        "DELETE FROM notes WHERE spaceId = ?",
        "DELETE FROM error_logs WHERE spaceId = ?"
    ).forEach { sql ->
        database.execSQL(sql, args)
    }
}

private fun ProtocolImportLlmProfileRecord.toEntityOrNull(
    spaceId: String,
    nowEpochMillis: Long
): LlmProfileEntity? {
    val normalizedId = id.trim()
    val normalizedName = name.trim()
    val providerKind = provider.toProviderKindName() ?: return null
    val normalizedModel = model.trim()
    if (normalizedId.isEmpty() || normalizedName.isEmpty() || normalizedModel.isEmpty()) return null
    return LlmProfileEntity(
        id = normalizedId,
        spaceId = spaceId,
        name = normalizedName,
        provider = providerKind,
        model = normalizedModel,
        secretRef = null,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
        baseUrl = baseUrl?.trim()?.ifEmpty { null },
        enabled = false
    )
}

private fun LlmProfileEntity.toProviderConnectionEntity(): ProviderConnectionEntity =
    ProviderConnectionEntity(
        id = "connection-$id",
        spaceId = spaceId,
        name = name,
        protocol = provider.toModelProtocol(),
        providerName = provider,
        baseUrl = baseUrl,
        secretRef = null,
        enabled = enabled,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

private fun LlmProfileEntity.toModelBindingEntity(): ModelBindingEntity =
    ModelBindingEntity(
        id = id,
        spaceId = spaceId,
        connectionId = "connection-$id",
        modelId = model,
        displayName = name,
        isDefault = enabled,
        enabled = enabled,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

private fun ProtocolImportProviderConnectionRecord.toEntityOrNull(
    spaceId: String,
    nowEpochMillis: Long
): ProviderConnectionEntity? {
    if (id.isBlank() || name.isBlank() || protocol.isBlank()) return null
    return ProviderConnectionEntity(
        id = id,
        spaceId = spaceId,
        name = name,
        protocol = protocol.toModelProtocol(),
        providerName = providerName,
        baseUrl = baseUrl,
        secretRef = null,
        enabled = true,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis
    )
}

private fun ProtocolImportModelBindingRecord.toEntityOrNull(
    spaceId: String,
    nowEpochMillis: Long
): ModelBindingEntity? {
    if (id.isBlank() || connectionId.isBlank() || modelId.isBlank()) return null
    return ModelBindingEntity(
        id = id,
        spaceId = spaceId,
        connectionId = connectionId,
        modelId = modelId,
        displayName = displayName?.ifBlank { modelId } ?: modelId,
        enabled = enabled,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis
    )
}

private fun String.toModelProtocol(): String =
    when (trim().lowercase()) {
        "anthropiccompatible", "anthropic_compatible", "anthropic-compatible" -> "AnthropicCompatible"
        "gemininative", "gemini_native", "gemini-native" -> "GeminiNative"
        else -> "OpenAiCompatible"
    }

private fun String.toMessageRole(): MessageRole? =
    when (trim().lowercase()) {
        "user" -> MessageRole.User
        "assistant" -> MessageRole.Assistant
        "system" -> MessageRole.System
        "tool" -> MessageRole.Tool
        else -> null
    }

private fun String.toProviderKindName(): String? =
    when (trim().lowercase()) {
        "openai", "openai-compatible", "openai_compatible", "openai-compatible-api" -> "OpenAiCompatible"
        "anthropic", "anthropic-compatible", "anthropic_compatible" -> "AnthropicCompatible"
        "local", "custom", "custom-local" -> "Local"
        else -> "Custom"
    }

private fun Map<String, Int>.toJsonObject(): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${key.escapeJson()}\":$value"
    }

private fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { "\"${it.escapeJson()}\"" }

private fun String.escapeJson(): String = buildString {
    this@escapeJson.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
