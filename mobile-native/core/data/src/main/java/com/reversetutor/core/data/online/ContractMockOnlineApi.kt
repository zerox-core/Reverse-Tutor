package com.reversetutor.core.data.online

import com.reversetutor.core.remote.ActivityPage
import com.reversetutor.core.remote.ActivityProgress
import com.reversetutor.core.remote.ContentFeedPage
import com.reversetutor.core.remote.LeaderboardItem
import com.reversetutor.core.remote.LeaderboardPage
import com.reversetutor.core.remote.OnlineActivity
import com.reversetutor.core.remote.OnlineApi
import com.reversetutor.core.remote.OnlineContentDetail
import com.reversetutor.core.remote.OnlineContentItem
import com.reversetutor.core.remote.OnlineIllustrationConfig
import com.reversetutor.core.remote.OnlineRelease
import com.reversetutor.core.remote.OnlineResult
import com.reversetutor.core.remote.OnlineWriteIdentity
import com.reversetutor.core.remote.SyncPullRequest
import com.reversetutor.core.remote.SyncPullResponse
import com.reversetutor.core.remote.SyncPushItemResult
import com.reversetutor.core.remote.SyncPushRequest
import com.reversetutor.core.remote.SyncPushResponse
import com.reversetutor.core.remote.WeeklyInsight
import com.reversetutor.core.remote.WeeklyInsightRequest

class ContractMockOnlineApi : OnlineApi {
    private val content = OnlineContentItem(
        id = "public-028",
        slug = "verify-before-opening-links",
        type = "public_interest",
        title = "别让陌生链接替你做决定",
        summary = "三个对话，识别常见网络诈骗",
        illustrationTemplate = "dialogue-security-01",
        illustration = OnlineIllustrationConfig(
            dialogues = listOf("这个链接安全吗？", "先核对来源，再决定是否打开。"),
            palette = "cool-blue-amber"
        ),
        cover = null,
        publisherName = "Reverse Tutor 公益编辑部",
        publishedAtEpochMillis = 1_783_814_400_000,
        contentVersion = 2
    )
    private val activity = OnlineActivity(
        id = "focus-week-2026-07",
        title = "七天专注讲解挑战",
        revision = 3,
        startsAtEpochMillis = 1_783_785_600_000,
        endsAtEpochMillis = 1_784_390_400_000,
        description = "连续七天完成一次反向讲解并留下复盘。",
        requiresOnlineConfirmation = false,
        allowsDeferredProgress = true,
        state = "active",
        sessionTemplateId = "challenge-focus-week-v1"
    )
    private val participation = mutableMapOf<String, ActivityProgress>()

    override suspend fun contentFeed(
        cursor: String?,
        limit: Int,
        types: Set<String>,
        etag: String?
    ): OnlineResult<ContentFeedPage> = OnlineResult.Success(
        ContentFeedPage(
            version = 28,
            updatedAtEpochMillis = 1_783_857_000_000,
            items = listOf(content).filter { types.isEmpty() || it.type in types }.take(limit),
            nextCursor = null
        )
    )

    override suspend fun contentDetail(slug: String): OnlineResult<OnlineContentDetail> =
        if (slug == content.slug) {
            OnlineResult.Success(
                OnlineContentDetail(
                    item = content,
                    bodyMarkdown = "## 先停一下\n\n核对发送者、域名和页面索取的信息。",
                    bodyAssets = emptyList()
                )
            )
        } else {
            OnlineResult.Failure("not_found", retryable = false)
        }

    override suspend fun listActivities(cursor: String?, limit: Int): OnlineResult<ActivityPage> =
        OnlineResult.Success(ActivityPage(listOf(activity).take(limit), null, 1_783_857_000_000))

    override suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity> =
        if (activityId == activity.id) OnlineResult.Success(activity)
        else OnlineResult.Failure("not_found", retryable = false)

    override suspend fun activityLeaderboard(
        activityId: String,
        cursor: String?,
        limit: Int
    ): OnlineResult<LeaderboardPage> = OnlineResult.Success(
        LeaderboardPage(
            items = listOf(LeaderboardItem(1, "学习者", null, 7, true)).take(limit),
            nextCursor = null,
            updatedAtEpochMillis = 1_783_857_000_000
        )
    )

    override suspend fun joinActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress> = saveParticipation(activityId, write, true, 0, "joined")

    override suspend fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress> {
        val current = participation[activityId]
        val next = maxOf(current?.progress ?: 0, progress)
        return saveParticipation(activityId, write, true, next, "joined")
    }

    override suspend fun leaveActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress> = saveParticipation(
        activityId,
        write,
        false,
        participation[activityId]?.progress ?: 0,
        "left"
    )

    override suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse> =
        OnlineResult.Success(
            SyncPushResponse(
                cursor = request.cursor,
                items = request.items.map {
                    SyncPushItemResult(it.id, it.entityId, accepted = true, remoteRevision = it.revision)
                }
            )
        )

    override suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse> =
        OnlineResult.Success(SyncPullResponse(request.cursor, emptyList()))

    override suspend fun weeklyInsight(request: WeeklyInsightRequest): OnlineResult<WeeklyInsight> =
        OnlineResult.Success(
            WeeklyInsight(
                request.spaceId,
                request.weekStartEpochMillis,
                request.sourceRevision,
                "本周反向讲解保持稳定。"
            )
        )

    override suspend fun latestRelease(): OnlineResult<OnlineRelease> = OnlineResult.Success(
        OnlineRelease(
            versionName = "1.0.0",
            versionCode = 100,
            minimumSupportedVersionCode = 90,
            downloadUrl = "https://cdn.example.com/reverse-tutor-1.0.0.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )
    )

    private fun saveParticipation(
        activityId: String,
        write: OnlineWriteIdentity,
        joined: Boolean,
        progress: Long,
        state: String
    ): OnlineResult<ActivityProgress> {
        if (activityId != activity.id) return OnlineResult.Failure("not_found", false)
        val value = ActivityProgress(
            activityId = activityId,
            userId = write.userId,
            joined = joined,
            progress = progress,
            revision = write.revision + 1,
            state = state,
            idempotencyKey = write.idempotencyKey
        )
        participation[activityId] = value
        return OnlineResult.Success(value)
    }
}
