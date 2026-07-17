package com.reversetutor.core.data.online

import com.reversetutor.core.domain.OnlineContentPage
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.domain.OnlineActivityPage
import com.reversetutor.core.domain.ActivityLeaderboardPage
import com.reversetutor.core.domain.ActivityParticipation
import com.reversetutor.core.domain.WeeklyOnlineInsight
import com.reversetutor.core.remote.ContentApi
import com.reversetutor.core.remote.ContentFeedPage
import com.reversetutor.core.remote.OnlineAssetRef
import com.reversetutor.core.remote.OnlineContentDetail
import com.reversetutor.core.remote.OnlineContentItem
import com.reversetutor.core.remote.OnlineIllustrationConfig
import com.reversetutor.core.remote.OnlineResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineContentRepositoryTest {
    @Test
    fun feedMapsWireModelsWithoutExposingRemoteTypes() = runBlocking {
        val api = StubContentApi(
            feedResult = OnlineResult.Success(
                ContentFeedPage(
                    version = 28,
                    updatedAtEpochMillis = 100,
                    items = listOf(contentItem()),
                    nextCursor = "next"
                )
            )
        )

        val result = OnlineContentRepository(api).feed(limit = 10)

        val page = (result as OnlineData.Content<OnlineContentPage>).value
        assertEquals("public-028", page.items.single().id)
        assertEquals("cool-blue-amber", page.items.single().illustrationPalette)
        assertEquals("next", page.nextCursor)
    }

    @Test
    fun failurePreservesStableCodeAndRetryability() = runBlocking {
        val api = StubContentApi(
            feedResult = OnlineResult.Failure("server_error", retryable = true)
        )

        val result = OnlineContentRepository(api).feed()

        assertEquals(OnlineData.Failure("server_error", retryable = true), result)
    }

    @Test
    fun contractMockProvidesDeterministicV1ContentAndActivity() = runBlocking {
        val mock = ContractMockOnlineApi()

        val feed = (mock.contentFeed() as OnlineResult.Success).value
        val activities = (mock.listActivities() as OnlineResult.Success).value

        assertEquals("public-028", feed.items.single().id)
        assertEquals("focus-week-2026-07", activities.items.single().id)
        assertTrue(activities.items.single().allowsDeferredProgress)
    }

    @Test
    fun activityRepositoryExposesCompleteParticipationFlow() = runBlocking {
        val repository = OnlineActivityRepository(ContractMockOnlineApi())

        val page = repository.list()
        val joined = repository.join(
            "focus-week-2026-07",
            userId = "user-1",
            deviceId = "device-1",
            revision = 0,
            idempotencyKey = "join-1"
        )
        val progressed = repository.updateProgress(
            "focus-week-2026-07",
            userId = "user-1",
            deviceId = "device-1",
            revision = 1,
            idempotencyKey = "progress-1",
            progress = 3
        )
        val left = repository.leave(
            "focus-week-2026-07",
            userId = "user-1",
            deviceId = "device-1",
            revision = 2,
            idempotencyKey = "leave-1"
        )
        val leaderboard = repository.leaderboard("focus-week-2026-07")

        assertEquals("focus-week-2026-07", (page as OnlineData.Content<OnlineActivityPage>).value.items.single().id)
        assertTrue((joined as OnlineData.Content<ActivityParticipation>).value.joined)
        assertEquals(3, (progressed as OnlineData.Content<ActivityParticipation>).value.progress)
        assertFalse((left as OnlineData.Content<ActivityParticipation>).value.joined)
        assertEquals(
            "学习者",
            (leaderboard as OnlineData.Content<ActivityLeaderboardPage>)
                .value.items.single().displayName
        )
    }

    @Test
    fun insightRepositoryMapsAggregatedRequestAndResponse() = runBlocking {
        val repository = OnlineInsightRepository(ContractMockOnlineApi())

        val result = repository.weekly(
            userId = "user-1",
            deviceId = "device-1",
            spaceId = "space-1",
            weekStartEpochMillis = 100,
            sourceRevision = 4,
            statistics = mapOf("activeDays" to 3)
        )

        val insight = (result as OnlineData.Content<WeeklyOnlineInsight>).value
        assertEquals("space-1", insight.spaceId)
        assertEquals(4, insight.sourceRevision)
    }

    private fun contentItem() = OnlineContentItem(
        id = "public-028",
        slug = "verify-links",
        type = "public_interest",
        title = "Verify links",
        summary = "Pause first",
        illustrationTemplate = "dialogue-security-01",
        illustration = OnlineIllustrationConfig(
            dialogues = listOf("Safe?"),
            palette = "cool-blue-amber"
        ),
        cover = OnlineAssetRef("https://cdn.example/cover.webp", "image/webp", 800, 600),
        publisherName = "Editors",
        publishedAtEpochMillis = 90,
        contentVersion = 2
    )
}

private class StubContentApi(
    private val feedResult: OnlineResult<ContentFeedPage>
) : ContentApi {
    override suspend fun contentFeed(
        cursor: String?,
        limit: Int,
        types: Set<String>,
        etag: String?
    ): OnlineResult<ContentFeedPage> = feedResult

    override suspend fun contentDetail(slug: String): OnlineResult<OnlineContentDetail> =
        OnlineResult.Failure("not_found", retryable = false)
}
