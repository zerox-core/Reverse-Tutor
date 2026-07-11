package com.reversetutor.core.data.online

import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.ReleaseMetadata
import com.reversetutor.core.domain.SyncCoordinator
import com.reversetutor.core.domain.SyncPushResult
import com.reversetutor.core.domain.SyncRepository
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.remote.ActivityProgress
import com.reversetutor.core.remote.OnlineActivity
import com.reversetutor.core.remote.OnlineApi
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRepositoryAdaptersTest {
    @Test
    fun activityAndReleaseAdaptersExposeDomainTypesOnly() = runBlocking {
        val api = FakeOnlineApi().apply {
            activities = OnlineResult.Success(
                listOf(OnlineActivity("activity-1", "Focus", 3, 10, 20))
            )
            release = OnlineResult.Success(OnlineRelease("2.0", 20, 10, "https://download", "abc"))
        }

        assertEquals(
            listOf(ActivitySummary("activity-1", "Focus", 3, 10, 20)),
            OnlineActivityRepository(api).listCachedActivities()
        )
        assertEquals(
            ReleaseMetadata("2.0", 20, 10, "https://download", "abc"),
            OnlineUpdateRepository(api).latestRelease()
        )
    }

    @Test
    fun adaptersReturnLocalSafeFallbacksOnOnlineFailure() = runBlocking {
        val api = FakeOnlineApi().apply {
            activities = OnlineResult.Failure("network_failure", retryable = true)
            release = OnlineResult.Failure("network_failure", retryable = true)
        }

        assertTrue(OnlineActivityRepository(api).listCachedActivities().isEmpty())
        assertNull(OnlineUpdateRepository(api).latestRelease())
    }

    @Test
    fun syncRejectsLocalOnlyEntityBeforeCallingApi() = runBlocking {
        val api = FakeOnlineApi()

        val result = OnlineSyncTransport(api).push(envelope(entityType = "chat_message"))

        assertEquals(
            SyncPushResult.Rejected("entity_type_not_syncable", retryable = false),
            result
        )
        assertEquals(0, api.pushCalls)
    }

    @Test
    fun syncRejectsNonSharedOwnershipBeforeCallingApi() = runBlocking {
        val api = FakeOnlineApi()

        val result = OnlineSyncTransport(api).push(
            envelope(entityType = "study_plan").copy(ownership = SyncOwnership.Local)
        )

        assertEquals(
            SyncPushResult.Rejected("ownership_not_syncable", retryable = false),
            result
        )
        assertEquals(0, api.pushCalls)
    }

    @Test
    fun syncAllowsOnlyDeclaredSharedEntityTypes() = runBlocking {
        OnlineSyncTransport.AllowedSharedEntityTypes.forEach { entityType ->
            val api = FakeOnlineApi().apply {
                push = OnlineResult.Success(
                    SyncPushResponse(
                        cursor = "1",
                        items = listOf(
                            SyncPushItemResult(
                                envelopeId = "envelope-1",
                                entityId = "entity-1",
                                accepted = true,
                                remoteRevision = 2
                            )
                        )
                    )
                )
            }
            assertEquals(
                SyncPushResult.Accepted(2),
                OnlineSyncTransport(api).push(envelope(entityType))
            )
            assertEquals(1, api.pushCalls)
        }
        assertEquals(
            setOf("activity_progress", "study_plan", "sync_summary", "user_setting"),
            OnlineSyncTransport.AllowedSharedEntityTypes
        )
    }

    @Test
    fun retryableNetworkFailureKeepsEnvelopePending() = runBlocking {
        val envelope = envelope("study_plan")
        val repository = RecordingSyncRepository(listOf(envelope))
        val api = FakeOnlineApi().apply {
            push = OnlineResult.Failure("timeout", retryable = true)
        }

        val result = SyncCoordinator(repository, OnlineSyncTransport(api)).pushPending()

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("envelope-1"), repository.pending.map { it.id })
        assertFalse(repository.deleted)
        assertEquals(listOf(Triple("envelope-1", "timeout", true)), repository.failures)
    }

    private fun envelope(entityType: String) = SyncEnvelope(
        id = "envelope-1",
        spaceId = "space-1",
        entityId = "entity-1",
        entityType = entityType,
        ownerId = "user-1",
        deviceId = "device-1",
        revision = 1,
        idempotencyKey = "sync-1",
        ownership = SyncOwnership.Shared,
        payload = "{}"
    )
}

private class RecordingSyncRepository(
    initial: List<SyncEnvelope>
) : SyncRepository {
    val pending = initial.toMutableList()
    val failures = mutableListOf<Triple<String, String, Boolean>>()
    var deleted = false

    override suspend fun pendingEnvelopes(limit: Int): List<SyncEnvelope> = pending.take(limit)

    override suspend fun markSucceeded(envelopeId: String, remoteRevision: Long) {
        deleted = true
        pending.removeAll { it.id == envelopeId }
    }

    override suspend fun markFailed(envelopeId: String, error: String, retryable: Boolean) {
        failures += Triple(envelopeId, error, retryable)
    }

    override suspend fun readCursor(spaceId: String, entityType: String): SyncCursor? = null

    override suspend fun saveCursor(cursor: SyncCursor): SyncCursor = cursor
}

private class FakeOnlineApi : OnlineApi {
    var activities: OnlineResult<List<OnlineActivity>> = OnlineResult.Success(emptyList())
    var push: OnlineResult<SyncPushResponse> = OnlineResult.Success(
        SyncPushResponse(cursor = null, items = emptyList())
    )
    var release: OnlineResult<OnlineRelease> = OnlineResult.Success(
        OnlineRelease("1.0", 1, 1)
    )
    var pushCalls = 0

    override suspend fun listActivities(): OnlineResult<List<OnlineActivity>> = activities
    override suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity> =
        OnlineResult.Failure("not_found", false)

    override suspend fun activityLeaderboard(
        activityId: String
    ): OnlineResult<List<ActivityProgress>> = OnlineResult.Success(emptyList())

    override suspend fun joinActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress> = OnlineResult.Failure("not_implemented", false)

    override suspend fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress> = OnlineResult.Failure("not_implemented", false)

    override suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse> {
        pushCalls++
        return push
    }

    override suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse> =
        OnlineResult.Success(SyncPullResponse(null, emptyList()))

    override suspend fun weeklyInsight(
        request: WeeklyInsightRequest
    ): OnlineResult<WeeklyInsight> = OnlineResult.Failure("not_implemented", false)

    override suspend fun latestRelease(): OnlineResult<OnlineRelease> = release
}
