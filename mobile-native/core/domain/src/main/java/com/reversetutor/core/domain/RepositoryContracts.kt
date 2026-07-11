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
    fun sessionExists(sessionId: String): Boolean
}

interface ConversationRunRepository {
    fun nextSequence(spaceId: String, sessionId: String): Long
    fun saveRun(run: TurnRun): TurnRun
    fun saveContextSnapshot(snapshot: ContextSnapshot): ContextSnapshot
    fun findRun(runId: String): TurnRun?
    fun findLatestRun(turnId: String): TurnRun?
    fun findWaitingRuns(parentTurnId: String): List<TurnRun>
    fun isSessionDeleted(sessionId: String): Boolean
}

interface ModelConnectionRepository {
    fun findConnection(connectionId: String): ProviderConnection?
    fun findBinding(bindingId: String): ModelBinding?
    fun listBindings(connectionId: String): List<ModelBinding>
    fun saveBinding(binding: ModelBinding): ModelBinding
}

interface StudyPlanRepository {
    fun listTasks(spaceId: String): List<StudyPlanTask>
    fun saveTask(task: StudyPlanTask): StudyPlanTask
}

interface LearningInsightRepository {
    fun findWeeklySummary(
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        generatorVersion: String
    ): WeeklySummary?

    fun saveWeeklySummary(summary: WeeklySummary): WeeklySummary
}

interface TokenUsageRepository {
    fun saveUsage(record: TokenUsageRecord): TokenUsageRecord
}

interface GlobalSearchRepository {
    fun search(spaceId: String, query: String, limit: Int = 50): List<SearchTarget>
}

interface ActivityRepository {
    fun listCachedActivities(): List<ActivitySummary>
}

data class ActivitySummary(
    val id: String,
    val title: String,
    val revision: Long,
    val startsAtEpochMillis: Long? = null,
    val endsAtEpochMillis: Long? = null
)

interface SyncRepository {
    fun pendingEnvelopes(limit: Int = 100): List<SyncEnvelope>
    fun markSucceeded(envelopeId: String, remoteRevision: Long)
    fun markFailed(envelopeId: String, error: String, retryable: Boolean)
    fun readCursor(entityType: String): SyncCursor? = null
    fun saveCursor(cursor: SyncCursor): SyncCursor = cursor
}

interface UpdateRepository {
    fun latestRelease(): ReleaseMetadata?
}

data class ReleaseMetadata(
    val versionName: String,
    val versionCode: Long,
    val minimumSupportedVersionCode: Long,
    val downloadUrl: String? = null,
    val sha256: String? = null
)
