package com.reversetutor.core.remote

import com.reversetutor.core.model.SyncEnvelope

object OnlineSyncEntityTypes {
    val Allowed: Set<String> = setOf(
        "activity_progress",
        "study_plan",
        "sync_summary",
        "user_setting"
    )

    fun isAllowed(entityType: String): Boolean = entityType in Allowed
}

interface OnlineApi {
    suspend fun listActivities(): OnlineResult<List<OnlineActivity>>
    suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity>
    suspend fun activityLeaderboard(activityId: String): OnlineResult<List<ActivityProgress>>
    suspend fun joinActivity(activityId: String, write: OnlineWriteIdentity): OnlineResult<ActivityProgress>
    suspend fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress>

    suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse>
    suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse>
    suspend fun weeklyInsight(request: WeeklyInsightRequest): OnlineResult<WeeklyInsight>
    suspend fun latestRelease(): OnlineResult<OnlineRelease>
}

sealed interface OnlineResult<out T> {
    data class Success<T>(val value: T) : OnlineResult<T>
    data class Failure(val code: String, val retryable: Boolean) : OnlineResult<Nothing>
}

data class OnlineWriteIdentity(
    val userId: String,
    val deviceId: String,
    val revision: Long,
    val idempotencyKey: String
)

data class OnlineActivity(
    val id: String,
    val title: String,
    val revision: Long,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long
)

data class ActivityProgress(
    val activityId: String,
    val userId: String,
    val progress: Long,
    val revision: Long
)

data class SyncPushRequest(
    val userId: String,
    val deviceId: String,
    val items: List<SyncEnvelope>,
    val cursor: String? = null
)

data class SyncPushResponse(
    val cursor: String?,
    val items: List<SyncPushItemResult>
) {
    val acceptedEnvelopeIds: List<String>
        get() = items.filter { it.accepted }.map { it.envelopeId }

    val rejectedEnvelopeIds: List<String>
        get() = items.filterNot { it.accepted }.map { it.envelopeId }
}

data class SyncPushItemResult(
    val envelopeId: String,
    val entityId: String,
    val accepted: Boolean,
    val remoteRevision: Long? = null,
    val errorCode: String? = null,
    val retryable: Boolean = false
)

data class SyncPullRequest(
    val userId: String,
    val deviceId: String,
    val cursor: String? = null,
    val spaceId: String = ""
)

data class SyncPullResponse(
    val cursor: String?,
    val items: List<SyncEnvelope>
)

data class WeeklyInsightRequest(
    val userId: String,
    val deviceId: String,
    val spaceId: String,
    val weekStartEpochMillis: Long,
    val sourceRevision: Long,
    val statistics: Map<String, Long>
)

data class WeeklyInsight(
    val spaceId: String,
    val weekStartEpochMillis: Long,
    val sourceRevision: Long,
    val summary: String
)

data class OnlineRelease(
    val versionName: String,
    val versionCode: Long,
    val minimumSupportedVersionCode: Long,
    val downloadUrl: String? = null,
    val sha256: String? = null
)
