package com.reversetutor.core.remote

import com.reversetutor.core.model.SyncEnvelope

interface OnlineApi {
    fun listActivities(): OnlineResult<List<OnlineActivity>>
    fun getActivity(activityId: String): OnlineResult<OnlineActivity>
    fun activityLeaderboard(activityId: String): OnlineResult<List<ActivityProgress>>
    fun joinActivity(activityId: String, write: OnlineWriteIdentity): OnlineResult<ActivityProgress>
    fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress>

    fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse>
    fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse>
    fun weeklyInsight(request: WeeklyInsightRequest): OnlineResult<WeeklyInsight>
    fun latestRelease(): OnlineResult<OnlineRelease>
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
    val items: List<SyncEnvelope>
)

data class SyncPushResponse(
    val acceptedEnvelopeIds: List<String>,
    val rejectedEnvelopeIds: List<String>
)

data class SyncPullRequest(
    val userId: String,
    val deviceId: String,
    val cursor: String? = null
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
