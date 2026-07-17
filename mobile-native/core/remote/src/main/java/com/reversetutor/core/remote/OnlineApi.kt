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

interface OnlineApi : ActivityApi, ContentApi, SyncApi, InsightApi, ReleaseApi

sealed interface OnlineResult<out T> {
    data class Success<T>(val value: T) : OnlineResult<T>
    data class Failure(
        val code: String,
        val retryable: Boolean,
        val userAction: String = "none",
        val requestId: String? = null
    ) : OnlineResult<Nothing>
}

data class OnlineWriteIdentity(
    @Deprecated("Compatibility only; never serialized or used for authorization")
    val userId: String,
    val deviceId: String,
    val revision: Long,
    val idempotencyKey: String
) {
    constructor(
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ) : this("", deviceId, revision, idempotencyKey)
}

data class OnlineActivity(
    val id: String,
    val title: String,
    val revision: Long,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val description: String = "",
    val requiresOnlineConfirmation: Boolean = false,
    val allowsDeferredProgress: Boolean = false,
    val state: String = "offline",
    val sessionTemplateId: String? = null
)

data class ActivityProgress(
    val activityId: String,
    val userId: String,
    val joined: Boolean,
    val progress: Long,
    val revision: Long,
    val state: String,
    val idempotencyKey: String
)

data class SyncPushRequest(
    @Deprecated("Compatibility only; never serialized or used for authorization")
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
    @Deprecated("Compatibility only; never serialized or used for authorization")
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
    @Deprecated("Compatibility only; never serialized or used for authorization")
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
