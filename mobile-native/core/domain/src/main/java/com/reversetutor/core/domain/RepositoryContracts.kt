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
import com.reversetutor.core.model.CreateWorldTreeDraftCommand
import com.reversetutor.core.model.WorldTreeDraft
import com.reversetutor.core.model.WorldTreeSection
import kotlinx.coroutines.flow.Flow

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

interface WorldTreeRepository {
    fun observeDraft(draftId: String): Flow<WorldTreeDraft?>
    suspend fun getDraft(draftId: String): WorldTreeDraft?
    suspend fun createDraft(command: CreateWorldTreeDraftCommand): WorldTreeDraft
    suspend fun updateTitle(draftId: String, title: String, nowEpochMillis: Long): WorldTreeDraft
    suspend fun upsertSection(
        draftId: String,
        section: WorldTreeSection,
        nowEpochMillis: Long
    ): WorldTreeDraft
    suspend fun reorderSections(
        draftId: String,
        sectionIds: List<String>,
        nowEpochMillis: Long
    ): WorldTreeDraft
    suspend fun removeCustomSection(
        draftId: String,
        sectionId: String,
        nowEpochMillis: Long
    ): WorldTreeDraft
    suspend fun replaceSourceLinks(
        draftId: String,
        sourceIds: List<String>,
        nowEpochMillis: Long
    ): WorldTreeDraft
    suspend fun attachToSession(
        draftId: String,
        sessionId: String,
        nowEpochMillis: Long
    ): WorldTreeDraft
    suspend fun archive(draftId: String, nowEpochMillis: Long)
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
    suspend fun list(cursor: String? = null, limit: Int = 20): OnlineData<OnlineActivityPage>
    suspend fun detail(activityId: String): OnlineData<ActivitySummary>
    suspend fun leaderboard(
        activityId: String,
        cursor: String? = null,
        limit: Int = 50
    ): OnlineData<ActivityLeaderboardPage>
    suspend fun join(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation>
    suspend fun updateProgress(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String,
        progress: Long
    ): OnlineData<ActivityParticipation>
    suspend fun leave(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation>
}

data class ActivitySummary(
    val id: String,
    val title: String,
    val revision: Long,
    val startsAtEpochMillis: Long? = null,
    val endsAtEpochMillis: Long? = null,
    val description: String = "",
    val requiresOnlineConfirmation: Boolean = false,
    val allowsDeferredProgress: Boolean = false,
    val state: String = "offline",
    val sessionTemplateId: String? = null
)

data class OnlineActivityPage(
    val items: List<ActivitySummary>,
    val nextCursor: String?,
    val updatedAtEpochMillis: Long
)

data class ActivityParticipation(
    val activityId: String,
    val userId: String,
    val joined: Boolean,
    val progress: Long,
    val revision: Long,
    val state: String,
    val idempotencyKey: String
)

data class ActivityLeaderboardEntry(
    val rank: Long,
    val displayName: String,
    val avatarUrl: String?,
    val progress: Long,
    val isCurrentUser: Boolean
)

data class ActivityLeaderboardPage(
    val items: List<ActivityLeaderboardEntry>,
    val nextCursor: String?,
    val updatedAtEpochMillis: Long
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
