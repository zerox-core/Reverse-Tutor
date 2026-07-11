package com.reversetutor.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.DomainError
import com.reversetutor.core.model.DomainErrorCode
import com.reversetutor.core.model.DomainUserAction
import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.SyncConflict
import com.reversetutor.core.model.SyncConflictResolution
import com.reversetutor.core.model.SyncConflictState
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOperation
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.WeeklySummary
import com.reversetutor.core.model.WidgetLayoutPreference
import com.reversetutor.core.model.WidgetSize

@Entity(tableName = "provider_connections", indices = [Index("spaceId"), Index("protocol")])
data class ProviderConnectionEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val name: String,
    val protocol: String,
    val providerName: String? = null,
    val baseUrl: String? = null,
    val secretRef: String? = null,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "model_bindings",
    foreignKeys = [
        ForeignKey(
            entity = ProviderConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("spaceId"),
        Index("connectionId"),
        Index("availability"),
        Index(value = ["spaceId", "isDefault"])
    ]
)
data class ModelBindingEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String,
    val availability: String = ModelAvailability.Untested.name,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val lastCheckedAtEpochMillis: Long? = null,
    val lastUsedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "context_snapshots",
    indices = [Index("spaceId"), Index("sessionId"), Index(value = ["turnId", "version"], unique = true)]
)
data class ContextSnapshotEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val sessionId: String,
    val turnId: String,
    val version: Long,
    val messageIdsPayload: String,
    val parentTurnId: String? = null,
    val maxSequence: Long = 0L,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "turn_runs",
    foreignKeys = [
        ForeignKey(
            entity = ModelBindingEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelBindingId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("spaceId"),
        Index("sessionId"),
        Index("state"),
        Index("modelBindingId"),
        Index(value = ["turnId", "attempt"], unique = true),
        Index(value = ["sessionId", "sequence"])
    ]
)
data class TurnRunEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val turnId: String,
    val sessionId: String,
    val userMessageId: String,
    val sequence: Long,
    val contextVersion: Long,
    val modelBindingId: String,
    val parentTurnId: String? = null,
    val contextSnapshotId: String? = null,
    val attempt: Int,
    val state: String,
    val createdAtEpochMillis: Long,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val resultMessageId: String? = null,
    val errorCode: String? = null,
    val errorRetryable: Boolean? = null,
    val errorSafeMessage: String? = null,
    val errorUserAction: String? = null
)

@Entity(
    tableName = "study_plan_tasks",
    indices = [Index("spaceId"), Index("state"), Index("sourceSessionId"), Index("updatedAtEpochMillis")]
)
data class StudyPlanTaskEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val title: String,
    val detail: String? = null,
    val state: String,
    val dueAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val sourceSessionId: String? = null,
    val sourceMessageId: String? = null,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "weekly_summaries",
    indices = [
        Index("spaceId"),
        Index(value = ["spaceId", "weekStartEpochMillis", "sourceRevision", "generatorVersion"], unique = true)
    ]
)
data class WeeklySummaryEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val weekStartEpochMillis: Long,
    val weekEndEpochMillis: Long,
    val sourceRevision: Long,
    val generatorVersion: String,
    val summary: String,
    val generatedAtEpochMillis: Long,
    val stale: Boolean
)

@Entity(
    tableName = "token_usage_records",
    indices = [
        Index("spaceId"),
        Index("modelBindingId"),
        Index(value = ["turnId", "attempt"], unique = true),
        Index("createdAtEpochMillis")
    ]
)
data class TokenUsageRecordEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val turnId: String,
    val attempt: Int,
    val modelBindingId: String? = null,
    val providerUsageId: String? = null,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
    val reasoningTokens: Long,
    val totalTokens: Long,
    val estimated: Boolean,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "widget_layout_preferences",
    primaryKeys = ["spaceId", "widgetId"],
    indices = [Index(value = ["spaceId", "order"])]
)
data class WidgetLayoutPreferenceEntity(
    val spaceId: String,
    val widgetId: String,
    val order: Int,
    val hidden: Boolean,
    val size: String,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "search_documents",
    indices = [
        Index("spaceId"),
        Index("entityType"),
        Index("sessionId"),
        Index(value = ["entityType", "entityId"], unique = true)
    ]
)
data class SearchDocumentEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val entityType: String,
    val entityId: String,
    val sessionId: String? = null,
    val parentEntityId: String? = null,
    val title: String,
    val body: String,
    val normalizedText: String,
    val updatedAtEpochMillis: Long,
    val rebuildRequired: Boolean = false
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index("spaceId"),
        Index("entityType"),
        Index("nextAttemptAtEpochMillis"),
        Index("status"),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val entityId: String,
    val entityType: String,
    val ownerId: String,
    val deviceId: String,
    val revision: Long,
    val idempotencyKey: String,
    val ownership: String,
    val operation: String,
    val payload: String? = null,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val retryCount: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0L,
    val status: String = "Pending",
    val lastError: String? = null
)

@Entity(
    tableName = "sync_cursors",
    indices = [Index("spaceId"), Index(value = ["spaceId", "entityType"], unique = true)]
)
data class SyncCursorEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val entityType: String,
    val cursor: String? = null,
    val revision: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "sync_conflicts",
    indices = [Index("spaceId"), Index("entityType"), Index("state"), Index("entityId")]
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val entityId: String,
    val entityType: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localPayload: String? = null,
    val remotePayload: String? = null,
    val state: String,
    val resolution: String? = null,
    val createdAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long? = null
)

@Entity(
    tableName = "entity_tombstones",
    indices = [Index("spaceId"), Index("entityType"), Index("entityId"), Index("deletedAtEpochMillis")]
)
data class EntityTombstoneEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val entityType: String,
    val entityId: String,
    val revision: Long,
    val deletedAtEpochMillis: Long,
    val idempotencyKey: String? = null
)

fun ProviderConnection.toEntity(): ProviderConnectionEntity = ProviderConnectionEntity(
    id = id,
    spaceId = spaceId,
    name = name,
    protocol = protocol.name,
    providerName = providerName,
    baseUrl = baseUrl,
    secretRef = secretRef,
    enabled = enabled,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ProviderConnectionEntity.toDomain(): ProviderConnection = ProviderConnection(
    id = id,
    spaceId = spaceId,
    name = name,
    protocol = enumValueOrDefault(protocol, ModelProtocol.OpenAiCompatible),
    providerName = providerName,
    baseUrl = baseUrl,
    secretRef = secretRef,
    enabled = enabled,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ModelBinding.toEntity(): ModelBindingEntity = ModelBindingEntity(
    id = id,
    spaceId = spaceId,
    connectionId = connectionId,
    modelId = modelId,
    displayName = displayName,
    availability = availability.name,
    isDefault = isDefault,
    enabled = enabled,
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ModelBindingEntity.toDomain(): ModelBinding = ModelBinding(
    id = id,
    spaceId = spaceId,
    connectionId = connectionId,
    modelId = modelId,
    displayName = displayName,
    availability = enumValueOrDefault(availability, ModelAvailability.Untested),
    isDefault = isDefault,
    enabled = enabled,
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ContextSnapshot.toEntity(): ContextSnapshotEntity = ContextSnapshotEntity(
    id = id,
    spaceId = spaceId,
    sessionId = sessionId,
    turnId = turnId,
    version = version,
    messageIdsPayload = messageIds.joinToString("\n") { it.escapePayload() },
    parentTurnId = parentTurnId,
    maxSequence = maxSequence,
    createdAtEpochMillis = createdAtEpochMillis
)

fun ContextSnapshotEntity.toDomain(): ContextSnapshot = ContextSnapshot(
    id = id,
    spaceId = spaceId,
    sessionId = sessionId,
    turnId = turnId,
    version = version,
    messageIds = messageIdsPayload.lineSequence().filter { it.isNotEmpty() }.map { it.unescapePayload() }.toList(),
    parentTurnId = parentTurnId,
    maxSequence = maxSequence,
    createdAtEpochMillis = createdAtEpochMillis
)

fun TurnRun.toEntity(): TurnRunEntity = TurnRunEntity(
    id = id,
    spaceId = spaceId,
    turnId = turnId,
    sessionId = sessionId,
    userMessageId = userMessageId,
    sequence = sequence,
    contextVersion = contextVersion,
    modelBindingId = modelBindingId,
    parentTurnId = parentTurnId,
    contextSnapshotId = contextSnapshotId,
    attempt = attempt,
    state = state.name,
    createdAtEpochMillis = createdAtEpochMillis,
    startedAtEpochMillis = startedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    resultMessageId = resultMessageId,
    errorCode = error?.code?.name,
    errorRetryable = error?.retryable,
    errorSafeMessage = error?.safeMessage,
    errorUserAction = error?.userAction?.name
)

fun TurnRunEntity.toDomain(): TurnRun = TurnRun(
    id = id,
    spaceId = spaceId,
    turnId = turnId,
    sessionId = sessionId,
    userMessageId = userMessageId,
    sequence = sequence,
    contextVersion = contextVersion,
    modelBindingId = modelBindingId,
    parentTurnId = parentTurnId,
    contextSnapshotId = contextSnapshotId,
    attempt = attempt,
    state = enumValueOrDefault(state, TurnRunState.Failed),
    createdAtEpochMillis = createdAtEpochMillis,
    startedAtEpochMillis = startedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    resultMessageId = resultMessageId,
    error = errorSafeMessage?.let {
        DomainError(
            code = enumValueOrDefault(errorCode, DomainErrorCode.Unknown),
            retryable = errorRetryable ?: false,
            safeMessage = it,
            userAction = enumValueOrDefault(errorUserAction, DomainUserAction.None)
        )
    }
)

fun StudyPlanTask.toEntity(): StudyPlanTaskEntity = StudyPlanTaskEntity(
    id, spaceId, title, detail, state.name, dueAtEpochMillis, completedAtEpochMillis,
    sourceSessionId, sourceMessageId, revision, createdAtEpochMillis, updatedAtEpochMillis
)

fun StudyPlanTaskEntity.toDomain(): StudyPlanTask = StudyPlanTask(
    id, spaceId, title, detail, enumValueOrDefault(state, StudyPlanTaskState.Proposed),
    dueAtEpochMillis, completedAtEpochMillis, sourceSessionId, sourceMessageId, revision,
    createdAtEpochMillis, updatedAtEpochMillis
)

fun WeeklySummary.toEntity(): WeeklySummaryEntity = WeeklySummaryEntity(
    id, spaceId, weekStartEpochMillis, weekEndEpochMillis, sourceRevision, generatorVersion,
    summary, generatedAtEpochMillis, stale
)

fun WeeklySummaryEntity.toDomain(): WeeklySummary = WeeklySummary(
    id, spaceId, weekStartEpochMillis, weekEndEpochMillis, sourceRevision, generatorVersion,
    summary, generatedAtEpochMillis, stale
)

fun TokenUsageRecord.toEntity(): TokenUsageRecordEntity = TokenUsageRecordEntity(
    id, spaceId, turnId, attempt, modelBindingId, providerUsageId, inputTokens, outputTokens,
    cachedTokens, reasoningTokens, totalTokens, estimated, createdAtEpochMillis
)

fun TokenUsageRecordEntity.toDomain(): TokenUsageRecord = TokenUsageRecord(
    id, spaceId, turnId, attempt, modelBindingId, providerUsageId, inputTokens, outputTokens,
    cachedTokens, reasoningTokens, totalTokens, estimated, createdAtEpochMillis
)

fun WidgetLayoutPreference.toEntity(): WidgetLayoutPreferenceEntity = WidgetLayoutPreferenceEntity(
    spaceId, widgetId, order, hidden, size.name, updatedAtEpochMillis
)

fun WidgetLayoutPreferenceEntity.toDomain(): WidgetLayoutPreference = WidgetLayoutPreference(
    spaceId, widgetId, order, hidden, enumValueOrDefault(size, WidgetSize.Compact), updatedAtEpochMillis
)

fun SyncEnvelope.toEntity(
    retryCount: Int = 0,
    nextAttemptAtEpochMillis: Long = 0L,
    status: String = "Pending",
    lastError: String? = null
): SyncOutboxEntity = SyncOutboxEntity(
    id, spaceId, entityId, entityType, ownerId, deviceId, revision, idempotencyKey,
    ownership.name, operation.name, payload, updatedAtEpochMillis, deletedAtEpochMillis,
    retryCount, nextAttemptAtEpochMillis, status, lastError
)

fun SyncOutboxEntity.toDomain(): SyncEnvelope = SyncEnvelope(
    id, spaceId, entityId, entityType, ownerId, deviceId, revision, idempotencyKey,
    enumValueOrDefault(ownership, SyncOwnership.Local),
    enumValueOrDefault(operation, SyncOperation.Upsert), payload, updatedAtEpochMillis, deletedAtEpochMillis
)

fun SyncCursor.toEntity(): SyncCursorEntity = SyncCursorEntity(
    id, spaceId, entityType, cursor, revision, updatedAtEpochMillis
)

fun SyncCursorEntity.toDomain(): SyncCursor = SyncCursor(
    id, spaceId, entityType, cursor, revision, updatedAtEpochMillis
)

fun SyncConflict.toEntity(): SyncConflictEntity = SyncConflictEntity(
    id, spaceId, entityId, entityType, localRevision, remoteRevision, localPayload, remotePayload,
    state.name, resolution?.name, createdAtEpochMillis, resolvedAtEpochMillis
)

fun SyncConflictEntity.toDomain(): SyncConflict = SyncConflict(
    id, spaceId, entityId, entityType, localRevision, remoteRevision, localPayload, remotePayload,
    enumValueOrDefault(state, SyncConflictState.Pending),
    resolution?.let { enumValueOrDefault(it, SyncConflictResolution.KeepLocal) },
    createdAtEpochMillis, resolvedAtEpochMillis
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private fun String.escapePayload(): String = replace("\\", "\\\\").replace("\n", "\\n")

private fun String.unescapePayload(): String = replace("\\n", "\n").replace("\\\\", "\\")
