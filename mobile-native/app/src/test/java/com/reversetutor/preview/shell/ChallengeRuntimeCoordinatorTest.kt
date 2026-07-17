package com.reversetutor.preview.shell

import com.reversetutor.core.domain.ActivityLeaderboardEntry
import com.reversetutor.core.domain.ActivityLeaderboardPage
import com.reversetutor.core.domain.ActivityParticipation
import com.reversetutor.core.domain.ActivityRepository
import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.OnlineActivityPage
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.remote.OnlineSessionIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRuntimeCoordinatorTest {
    @Test
    fun loadSelectsFirstActiveActivityAndConfirmsItsLeaderboard() = runBlocking {
        val active = activity(id = "active-2", state = "active")
        val leaderboard = leaderboard()
        val repository = FakeActivityRepository(
            listResults = listOf(
                OnlineData.Content(
                    OnlineActivityPage(
                        items = listOf(activity(id = "ended-1", state = "ended"), active),
                        nextCursor = null,
                        updatedAtEpochMillis = 100L
                    )
                )
            ),
            leaderboardResults = listOf(OnlineData.Content(leaderboard))
        )
        val coordinator = ChallengeRuntimeCoordinator(repository) { identity() }

        coordinator.load()

        assertFalse(coordinator.state.value.loading)
        assertEquals(active, coordinator.state.value.activity)
        assertEquals(leaderboard, coordinator.state.value.leaderboard)
        assertNull(coordinator.state.value.participation)
        assertNull(coordinator.state.value.failure)
        assertEquals(listOf("active-2"), repository.leaderboardActivityIds)
    }

    @Test
    fun joinUsesAuthenticatedIdentityAndDeterministicIdempotencyKey() = runBlocking {
        val active = activity(id = "active-2", revision = 7L)
        val participation = participation(
            activityId = active.id,
            userId = "account-9",
            revision = 8L,
            idempotencyKey = "join:active-2:account-9:7"
        )
        val repository = FakeActivityRepository(
            listResults = listOf(page(active)),
            leaderboardResults = listOf(OnlineData.Content(leaderboard())),
            joinResults = listOf(OnlineData.Content(participation))
        )
        val coordinator = ChallengeRuntimeCoordinator(repository) {
            OnlineSessionIdentity(accountId = "account-9", deviceId = "device-4")
        }
        coordinator.load()

        coordinator.join()

        assertEquals(participation, coordinator.state.value.participation)
        assertTrue(coordinator.state.value.participation?.joined == true)
        assertEquals(
            JoinCall(
                activityId = "active-2",
                userId = "account-9",
                deviceId = "device-4",
                revision = 7L,
                idempotencyKey = "join:active-2:account-9:7"
            ),
            repository.joinCalls.single()
        )
    }

    @Test
    fun failedJoinDoesNotFabricateParticipationAndCanRetry() = runBlocking {
        val active = activity(id = "active-2", revision = 7L)
        val confirmed = participation(
            activityId = active.id,
            userId = "account-9",
            revision = 8L,
            idempotencyKey = "join:active-2:account-9:7"
        )
        val repository = FakeActivityRepository(
            listResults = listOf(page(active)),
            leaderboardResults = listOf(OnlineData.Content(leaderboard())),
            joinResults = listOf(
                OnlineData.Failure("network_failure", retryable = true),
                OnlineData.Content(confirmed)
            )
        )
        val coordinator = ChallengeRuntimeCoordinator(repository) { identity() }
        coordinator.load()

        coordinator.join()

        assertNull(coordinator.state.value.participation)
        assertEquals(
            ChallengeRuntimeFailure(
                code = "network_failure",
                retryable = true,
                operation = ChallengeRuntimeOperation.Join
            ),
            coordinator.state.value.failure
        )

        coordinator.retry()

        assertEquals(confirmed, coordinator.state.value.participation)
        assertEquals(2, repository.joinCalls.size)
        assertEquals(repository.joinCalls[0], repository.joinCalls[1])
    }

    @Test
    fun loadFailurePreservesLastConfirmedParticipation() = runBlocking {
        val active = activity(id = "active-2")
        val confirmed = participation(activityId = active.id)
        val repository = FakeActivityRepository(
            listResults = listOf(
                page(active),
                OnlineData.Failure("server_error", retryable = true)
            ),
            leaderboardResults = listOf(OnlineData.Content(leaderboard())),
            joinResults = listOf(OnlineData.Content(confirmed))
        )
        val coordinator = ChallengeRuntimeCoordinator(repository) { identity() }
        coordinator.load()
        coordinator.join()

        coordinator.load()

        assertEquals(confirmed, coordinator.state.value.participation)
        assertEquals(active, coordinator.state.value.activity)
        assertEquals("server_error", coordinator.state.value.failure?.code)
        assertTrue(coordinator.state.value.failure?.retryable == true)
    }

    @Test
    fun leaderboardFailurePreservesTheLastConfirmedChallengeSnapshot() = runBlocking {
        val first = activity(id = "active-1")
        val replacement = activity(id = "active-2")
        val confirmed = participation(activityId = first.id)
        val confirmedLeaderboard = leaderboard()
        val repository = FakeActivityRepository(
            listResults = listOf(page(first), page(replacement)),
            leaderboardResults = listOf(
                OnlineData.Content(confirmedLeaderboard),
                OnlineData.Failure("network_failure", retryable = true)
            ),
            joinResults = listOf(OnlineData.Content(confirmed))
        )
        val coordinator = ChallengeRuntimeCoordinator(repository) { identity() }
        coordinator.load()
        coordinator.join()

        coordinator.load()

        assertEquals(first, coordinator.state.value.activity)
        assertEquals(confirmed, coordinator.state.value.participation)
        assertEquals(confirmedLeaderboard, coordinator.state.value.leaderboard)
        assertEquals("network_failure", coordinator.state.value.failure?.code)
    }

    @Test
    fun localOnlyJoinKeepsReferenceChallengeUnjoined() = runBlocking {
        var identityRequests = 0
        val coordinator = ChallengeRuntimeCoordinator(activityRepository = null) {
            identityRequests += 1
            identity()
        }

        coordinator.load()
        coordinator.join()

        assertNull(coordinator.state.value.activity)
        assertNull(coordinator.state.value.participation)
        assertFalse(coordinator.state.value.loading)
        assertEquals(0, identityRequests)
    }
}

private data class JoinCall(
    val activityId: String,
    val userId: String,
    val deviceId: String,
    val revision: Long,
    val idempotencyKey: String
)

private class FakeActivityRepository(
    listResults: List<OnlineData<OnlineActivityPage>> = emptyList(),
    leaderboardResults: List<OnlineData<ActivityLeaderboardPage>> = emptyList(),
    joinResults: List<OnlineData<ActivityParticipation>> = emptyList()
) : ActivityRepository {
    private val listResults = ArrayDeque(listResults)
    private val leaderboardResults = ArrayDeque(leaderboardResults)
    private val joinResults = ArrayDeque(joinResults)
    val leaderboardActivityIds = mutableListOf<String>()
    val joinCalls = mutableListOf<JoinCall>()

    override suspend fun listCachedActivities(): List<ActivitySummary> = emptyList()

    override suspend fun list(cursor: String?, limit: Int): OnlineData<OnlineActivityPage> =
        listResults.removeFirst()

    override suspend fun detail(activityId: String): OnlineData<ActivitySummary> = error("Not used")

    override suspend fun leaderboard(
        activityId: String,
        cursor: String?,
        limit: Int
    ): OnlineData<ActivityLeaderboardPage> {
        leaderboardActivityIds += activityId
        return leaderboardResults.removeFirst()
    }

    override suspend fun join(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation> {
        joinCalls += JoinCall(activityId, userId, deviceId, revision, idempotencyKey)
        return joinResults.removeFirst()
    }

    override suspend fun updateProgress(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String,
        progress: Long
    ): OnlineData<ActivityParticipation> = error("Not used")

    override suspend fun leave(
        activityId: String,
        userId: String,
        deviceId: String,
        revision: Long,
        idempotencyKey: String
    ): OnlineData<ActivityParticipation> = error("Not used")
}

private fun activity(
    id: String = "active-1",
    revision: Long = 3L,
    state: String = "active"
) = ActivitySummary(
    id = id,
    title = "Challenge",
    revision = revision,
    state = state
)

private fun page(activity: ActivitySummary): OnlineData<OnlineActivityPage> = OnlineData.Content(
    OnlineActivityPage(
        items = listOf(activity),
        nextCursor = null,
        updatedAtEpochMillis = 100L
    )
)

private fun leaderboard() = ActivityLeaderboardPage(
    items = listOf(
        ActivityLeaderboardEntry(
            rank = 1L,
            displayName = "Learner",
            avatarUrl = null,
            progress = 5L,
            isCurrentUser = false
        )
    ),
    nextCursor = null,
    updatedAtEpochMillis = 100L
)

private fun participation(
    activityId: String = "active-1",
    userId: String = "account-9",
    revision: Long = 4L,
    idempotencyKey: String = "join:$activityId:$userId:3"
) = ActivityParticipation(
    activityId = activityId,
    userId = userId,
    joined = true,
    progress = 0L,
    revision = revision,
    state = "joined",
    idempotencyKey = idempotencyKey
)

private fun identity() = OnlineSessionIdentity(
    accountId = "account-9",
    deviceId = "device-4"
)
