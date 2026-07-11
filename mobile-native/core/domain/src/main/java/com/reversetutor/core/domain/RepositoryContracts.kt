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
import com.reversetutor.core.model.WeeklySummary

interface SessionRepository {
    suspend fun sessionExists(sessionId: String): Boolean
}

interface ConversationRunRepository {
    suspend fun nextSequence(spaceId: String, sessionId: String): Long
    suspend fun saveRun(run: TurnRun): TurnRun
    suspend fun saveContextSnapshot(snapshot: ContextSnapshot): ContextSnapshot
    suspend fun findRun(runId: String): TurnRun?
    suspend fun findLatestRun(turnId: String): TurnRun?
    suspend fun findWaitingRuns(parentTurnId: String): List<TurnRun>
    suspend fun isSessionDeleted(sessionId: String): Boolean
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
    suspend fun readCursor(entityType: String): SyncCursor? = null
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
