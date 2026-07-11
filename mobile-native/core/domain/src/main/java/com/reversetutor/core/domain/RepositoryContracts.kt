package com.reversetutor.core.domain

import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.WeeklySummary
import com.reversetutor.core.model.WidgetLayoutPreference

interface SessionRepository {
    suspend fun sessionExists(sessionId: String): Boolean
    suspend fun setModelBinding(sessionId: String, modelBindingId: String): Boolean
}

interface ConversationRunRepository {
    suspend fun createRunWithSnapshot(command: PersistTurnRunCommand): TurnRun
    suspend fun findRun(runId: String): TurnRun?
    suspend fun findLatestRun(turnId: String): TurnRun?
    suspend fun isSessionDeleted(sessionId: String): Boolean
    suspend fun retryLatestAttempt(command: PersistTurnRetryCommand): TurnRun?
    suspend fun completeCurrentAttempt(command: PersistTurnCompletionCommand): PersistTurnCompletion
}

data class PersistTurnRunCommand(
    val runId: String,
    val snapshotId: String,
    val spaceId: String,
    val sessionId: String,
    val turnId: String,
    val userMessageId: String,
    val modelBindingId: String,
    val contextVersion: Long,
    val contextMessageIds: List<String>,
    val parentTurnId: String?,
    val initialState: TurnRunState,
    val createdAtEpochMillis: Long
)

data class PersistTurnRetryCommand(
    val runId: String,
    val replacementRunId: String,
    val initialState: TurnRunState,
    val createdAtEpochMillis: Long
)

data class PersistTurnCompletionCommand(
    val runId: String,
    val attempt: Int,
    val resultMessageId: String,
    val completedAtEpochMillis: Long
)

sealed interface PersistTurnCompletion {
    data class Accepted(
        val completedRun: TurnRun,
        val releasedRuns: List<TurnRun>
    ) : PersistTurnCompletion

    data object NotFound : PersistTurnCompletion
    data object StaleAttempt : PersistTurnCompletion
    data object SessionDeleted : PersistTurnCompletion
    data object AlreadyTerminal : PersistTurnCompletion
}

interface ModelConnectionRepository {
    suspend fun findConnection(connectionId: String): ProviderConnection?
    suspend fun findBinding(bindingId: String): ModelBinding?
    suspend fun listBindings(connectionId: String): List<ModelBinding>
    suspend fun saveBinding(binding: ModelBinding): ModelBinding
}

interface StudyPlanRepository {
    suspend fun listTasks(spaceId: String): List<StudyPlanTask>
    suspend fun saveTask(task: StudyPlanTask): StudyPlanTask
}

interface LearningInsightRepository {
    suspend fun findWeeklySummary(
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        generatorVersion: String
    ): WeeklySummary?

    suspend fun saveWeeklySummary(summary: WeeklySummary): WeeklySummary
}

interface TokenUsageRepository {
    suspend fun saveUsage(record: TokenUsageRecord): TokenUsageRecord
}

interface WidgetLayoutRepository {
    suspend fun load(spaceId: String): List<WidgetLayoutPreference>
    suspend fun saveLayout(spaceId: String, preferences: List<WidgetLayoutPreference>)
    suspend fun reset(spaceId: String)
}

interface GlobalSearchRepository {
    suspend fun search(
        spaceId: String,
        query: String,
        limit: Int = 50
    ): List<SearchTarget>
}

interface ActivityRepository {
    suspend fun listCachedActivities(): List<ActivitySummary>
}

data class ActivitySummary(
    val id: String,
    val title: String,
    val revision: Long,
    val startsAtEpochMillis: Long? = null,
    val endsAtEpochMillis: Long? = null
)

interface SyncRepository {
    suspend fun pendingEnvelopes(limit: Int = 100): List<SyncEnvelope>
    suspend fun markSucceeded(envelopeId: String, remoteRevision: Long)
    suspend fun markFailed(envelopeId: String, error: String, retryable: Boolean)
    suspend fun readCursor(spaceId: String, entityType: String): SyncCursor? = null
    suspend fun saveCursor(cursor: SyncCursor): SyncCursor = cursor
}

interface UpdateRepository {
    suspend fun latestRelease(): ReleaseMetadata?
}

data class ReleaseMetadata(
    val versionName: String,
    val versionCode: Long,
    val minimumSupportedVersionCode: Long,
    val downloadUrl: String? = null,
    val sha256: String? = null
)
