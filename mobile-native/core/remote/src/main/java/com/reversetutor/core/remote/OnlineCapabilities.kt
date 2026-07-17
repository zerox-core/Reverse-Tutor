package com.reversetutor.core.remote

interface ContentApi {
    suspend fun contentFeed(
        cursor: String? = null,
        limit: Int = 20,
        types: Set<String> = emptySet(),
        etag: String? = null
    ): OnlineResult<ContentFeedPage>

    suspend fun contentDetail(slug: String): OnlineResult<OnlineContentDetail>
}

interface ActivityApi {
    suspend fun listActivities(
        cursor: String? = null,
        limit: Int = 20
    ): OnlineResult<ActivityPage>
    suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity>
    suspend fun activityLeaderboard(
        activityId: String,
        cursor: String? = null,
        limit: Int = 50
    ): OnlineResult<LeaderboardPage>
    suspend fun joinActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress>
    suspend fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress>
    suspend fun leaveActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress>
}

interface SyncApi {
    suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse>
    suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse>
}

interface InsightApi {
    suspend fun weeklyInsight(request: WeeklyInsightRequest): OnlineResult<WeeklyInsight>
}

interface ReleaseApi {
    suspend fun latestRelease(): OnlineResult<OnlineRelease>
}
