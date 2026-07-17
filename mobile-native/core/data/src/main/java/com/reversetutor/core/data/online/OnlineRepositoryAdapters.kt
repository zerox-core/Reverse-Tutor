package com.reversetutor.core.data.online

import com.reversetutor.core.domain.ActivityRepository
import com.reversetutor.core.domain.ActivityLeaderboardEntry
import com.reversetutor.core.domain.ActivityLeaderboardPage
import com.reversetutor.core.domain.ActivityParticipation
import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.OnlineActivityPage
import com.reversetutor.core.domain.ContentRepository
import com.reversetutor.core.domain.OnlineAsset
import com.reversetutor.core.domain.OnlineContentArticle
import com.reversetutor.core.domain.OnlineContentPage
import com.reversetutor.core.domain.OnlineContentSummary
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.domain.OnlineInsightRepositoryContract
import com.reversetutor.core.domain.WeeklyOnlineInsight
import com.reversetutor.core.domain.ReleaseMetadata
import com.reversetutor.core.domain.SyncPushResult
import com.reversetutor.core.domain.SyncTransport
import com.reversetutor.core.domain.UpdateRepository
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.remote.OnlineApi
import com.reversetutor.core.remote.ActivityApi
import com.reversetutor.core.remote.ActivityProgress
import com.reversetutor.core.remote.OnlineActivity
import com.reversetutor.core.remote.OnlineWriteIdentity
import com.reversetutor.core.remote.InsightApi
import com.reversetutor.core.remote.WeeklyInsightRequest
import com.reversetutor.core.remote.ContentApi
import com.reversetutor.core.remote.OnlineAssetRef
import com.reversetutor.core.remote.OnlineContentItem
import com.reversetutor.core.remote.OnlineResult
import com.reversetutor.core.remote.OnlineSyncEntityTypes
import com.reversetutor.core.remote.SyncPushRequest

class OnlineActivityRepository(
    private val api: ActivityApi
) : ActivityRepository {
    override suspend fun listCachedActivities(): List<ActivitySummary> =
        when (val result = api.listActivities()) {
            is OnlineResult.Success -> result.value.items.map {
                ActivitySummary(
                    id = it.id,
                    title = it.title,
                    revision = it.revision,
                    startsAtEpochMillis = it.startsAtEpochMillis,
                    endsAtEpochMillis = it.endsAtEpochMillis,
                    description = it.description,
                    requiresOnlineConfirmation = it.requiresOnlineConfirmation,
                    allowsDeferredProgress = it.allowsDeferredProgress,
                    state = it.state,
                    sessionTemplateId = it.sessionTemplateId
                )
            }
            is OnlineResult.Failure -> emptyList()
        }

    override suspend fun list(cursor: String?, limit: Int): OnlineData<OnlineActivityPage> =
        when (val result = api.listActivities(cursor, limit)) {
            is OnlineResult.Success -> OnlineData.Content(
                OnlineActivityPage(
                    items = result.value.items.map { it.toDomain() },
                    nextCursor = result.value.nextCursor,
                    updatedAtEpochMillis = result.value.updatedAtEpochMillis
                )
            )
            is OnlineResult.Failure -> OnlineData.Failure(result.code, result.retryable)
        }

    override suspend fun detail(activityId: String): OnlineData<ActivitySummary> =
        api.getActivity(activityId).mapOnline { it.toDomain() }

    override suspend fun leaderboard(
        activityId: String,
        cursor: String?,
        limit: Int
    ): OnlineData<ActivityLeaderboardPage> = when (
        val result = api.activityLeaderboard(activityId, cursor, limit)
    ) {
        is OnlineResult.Success -> OnlineData.Content(
            ActivityLeaderboardPage(
                items = result.value.items.map {
                    ActivityLeaderboardEntry(
                        it.rank,
                        it.displayName,
                        it.avatarUrl,
                        it.progress,
                        it.isCurrentUser
                    )
                },
                nextCursor = result.value.nextCursor,
                updatedAtEpochMillis = result.value.updatedAtEpochMillis
            )
        )
        is OnlineResult.Failure -> OnlineData.Failure(result.code, result.retryable)
    }

    override suspend fun join(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation> = api.joinActivity(
        activityId,
        OnlineWriteIdentity(userId, deviceId, revision, idempotencyKey)
    ).mapOnline { it.toDomain() }

    override suspend fun updateProgress(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String,
        progress: Long
    ): OnlineData<ActivityParticipation> = api.updateActivityProgress(
        activityId,
        OnlineWriteIdentity(userId, deviceId, revision, idempotencyKey),
        progress
    ).mapOnline { it.toDomain() }

    override suspend fun leave(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation> = api.leaveActivity(
        activityId,
        OnlineWriteIdentity(userId, deviceId, revision, idempotencyKey)
    ).mapOnline { it.toDomain() }
}

private fun OnlineActivity.toDomain(): ActivitySummary = ActivitySummary(
    id = id,
    title = title,
    revision = revision,
    startsAtEpochMillis = startsAtEpochMillis,
    endsAtEpochMillis = endsAtEpochMillis,
    description = description,
    requiresOnlineConfirmation = requiresOnlineConfirmation,
    allowsDeferredProgress = allowsDeferredProgress,
    state = state,
    sessionTemplateId = sessionTemplateId
)

private fun ActivityProgress.toDomain(): ActivityParticipation = ActivityParticipation(
    activityId,
    userId,
    joined,
    progress,
    revision,
    state,
    idempotencyKey
)

private inline fun <T, R> OnlineResult<T>.mapOnline(transform: (T) -> R): OnlineData<R> = when (this) {
    is OnlineResult.Success -> OnlineData.Content(transform(value))
    is OnlineResult.Failure -> OnlineData.Failure(code, retryable)
}

class OnlineContentRepository(
    private val api: ContentApi
) : ContentRepository {
    override suspend fun feed(
        cursor: String?,
        limit: Int,
        types: Set<String>,
        etag: String?
    ): OnlineData<OnlineContentPage> = when (
        val result = api.contentFeed(cursor, limit, types, etag)
    ) {
        is OnlineResult.Success -> OnlineData.Content(
            OnlineContentPage(
                version = result.value.version,
                updatedAtEpochMillis = result.value.updatedAtEpochMillis,
                items = result.value.items.map { it.toDomain() },
                nextCursor = result.value.nextCursor
            )
        )
        is OnlineResult.Failure -> OnlineData.Failure(result.code, result.retryable)
    }

    override suspend fun detail(slug: String): OnlineData<OnlineContentArticle> =
        when (val result = api.contentDetail(slug)) {
            is OnlineResult.Success -> OnlineData.Content(
                OnlineContentArticle(
                    summary = result.value.item.toDomain(),
                    bodyMarkdown = result.value.bodyMarkdown,
                    bodyAssets = result.value.bodyAssets.map { it.toDomain() }
                )
            )
            is OnlineResult.Failure -> OnlineData.Failure(result.code, result.retryable)
        }
}

class OnlineInsightRepository(
    private val api: InsightApi
) : OnlineInsightRepositoryContract {
    override suspend fun weekly(
        userId: String,
        deviceId: String,
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        statistics: Map<String, Long>
    ): OnlineData<WeeklyOnlineInsight> = api.weeklyInsight(
        WeeklyInsightRequest(
            userId,
            deviceId,
            spaceId,
            weekStartEpochMillis,
            sourceRevision,
            statistics
        )
    ).mapOnline {
        WeeklyOnlineInsight(
            it.spaceId,
            it.weekStartEpochMillis,
            it.sourceRevision,
            it.summary
        )
    }
}

private fun OnlineContentItem.toDomain(): OnlineContentSummary = OnlineContentSummary(
    id = id,
    slug = slug,
    type = type,
    title = title,
    summary = summary,
    illustrationTemplate = illustrationTemplate,
    illustrationDialogues = illustration.dialogues,
    illustrationPalette = illustration.palette,
    cover = cover?.toDomain(),
    publisherName = publisherName,
    publishedAtEpochMillis = publishedAtEpochMillis,
    contentVersion = contentVersion
)

private fun OnlineAssetRef.toDomain(): OnlineAsset = OnlineAsset(
    url = url,
    mimeType = mimeType,
    width = width,
    height = height,
    bytes = bytes,
    sha256 = sha256
)

class OnlineSyncTransport(
    private val api: OnlineApi
) : SyncTransport {
    override suspend fun push(envelope: SyncEnvelope): SyncPushResult {
        if (envelope.ownership != SyncOwnership.Shared) {
            return SyncPushResult.Rejected(
                errorCode = "ownership_not_syncable",
                retryable = false
            )
        }
        if (envelope.entityType !in AllowedSharedEntityTypes) {
            return SyncPushResult.Rejected(
                errorCode = "entity_type_not_syncable",
                retryable = false
            )
        }
        val response = api.pushSync(
            SyncPushRequest(
                userId = envelope.ownerId,
                deviceId = envelope.deviceId,
                items = listOf(envelope)
            )
        )
        return when (response) {
            is OnlineResult.Failure -> SyncPushResult.Rejected(
                errorCode = response.code,
                retryable = response.retryable
            )
            is OnlineResult.Success -> {
                val result = response.value.items.firstOrNull { it.envelopeId == envelope.id }
                    ?: response.value.items.firstOrNull { it.entityId == envelope.entityId }
                val remoteRevision = result?.remoteRevision
                if (result?.accepted == true && remoteRevision != null) {
                    SyncPushResult.Accepted(remoteRevision)
                } else {
                    SyncPushResult.Rejected(
                        errorCode = result?.errorCode ?: "invalid_sync_response",
                        retryable = result?.retryable ?: false
                    )
                }
            }
        }
    }

    companion object {
        val AllowedSharedEntityTypes: Set<String> = OnlineSyncEntityTypes.Allowed
    }
}

class OnlineUpdateRepository(
    private val api: OnlineApi
) : UpdateRepository {
    override suspend fun latestRelease(): ReleaseMetadata? =
        when (val result = api.latestRelease()) {
            is OnlineResult.Success -> ReleaseMetadata(
                versionName = result.value.versionName,
                versionCode = result.value.versionCode,
                minimumSupportedVersionCode = result.value.minimumSupportedVersionCode,
                downloadUrl = result.value.downloadUrl,
                sha256 = result.value.sha256
            )
            is OnlineResult.Failure -> null
        }
}
