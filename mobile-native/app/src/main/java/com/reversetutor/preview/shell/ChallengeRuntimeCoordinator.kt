package com.reversetutor.preview.shell

import com.reversetutor.core.domain.ActivityLeaderboardPage
import com.reversetutor.core.domain.ActivityParticipation
import com.reversetutor.core.domain.ActivityRepository
import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.remote.OnlineSessionIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ChallengeRuntimeOperation { Load, Join }

data class ChallengeRuntimeFailure(
    val code: String,
    val retryable: Boolean,
    val operation: ChallengeRuntimeOperation
)

data class ChallengeRuntimeState(
    val loading: Boolean = false,
    val activity: ActivitySummary? = null,
    val participation: ActivityParticipation? = null,
    val leaderboard: ActivityLeaderboardPage? = null,
    val failure: ChallengeRuntimeFailure? = null
) {
    val joined: Boolean
        get() = participation?.joined == true
}

class ChallengeRuntimeCoordinator(
    private val activityRepository: ActivityRepository?,
    private val identityProvider: suspend () -> OnlineSessionIdentity?
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ChallengeRuntimeState())
    val state: StateFlow<ChallengeRuntimeState> = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock {
        loadLocked()
    }

    suspend fun join(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            return joinLocked()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun retry(): ChallengeRuntimeOperation? {
        if (!mutex.tryLock()) return null
        try {
            val failure = mutableState.value.failure?.takeIf { it.retryable } ?: return null
            when (failure.operation) {
                ChallengeRuntimeOperation.Load -> loadLocked()
                ChallengeRuntimeOperation.Join -> joinLocked()
            }
            return failure.operation
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadLocked() {
        val repository = activityRepository ?: return
        val previous = mutableState.value
        mutableState.value = previous.copy(loading = true, failure = null)
        when (val activities = repository.list()) {
            is OnlineData.Failure -> fail(activities, ChallengeRuntimeOperation.Load)
            is OnlineData.Content -> {
                val active = activities.value.items.firstOrNull { it.state == "active" }
                if (active == null) {
                    mutableState.value = previous.copy(
                        loading = false,
                        failure = ChallengeRuntimeFailure(
                            code = "no_active_activity",
                            retryable = false,
                            operation = ChallengeRuntimeOperation.Load
                        )
                    )
                    return
                }
                val activityChanged = previous.activity?.id != active.id
                when (val leaderboard = repository.leaderboard(active.id)) {
                    is OnlineData.Failure -> fail(
                        leaderboard,
                        ChallengeRuntimeOperation.Load
                    )
                    is OnlineData.Content -> {
                        mutableState.value = ChallengeRuntimeState(
                            activity = active,
                            participation = if (activityChanged) null else previous.participation,
                            leaderboard = leaderboard.value
                        )
                    }
                }
            }
        }
    }

    private suspend fun joinLocked(): Boolean {
        val repository = activityRepository ?: return false
        if (mutableState.value.loading || mutableState.value.joined) return false
        val activity = mutableState.value.activity ?: return false
        val previous = mutableState.value
        mutableState.value = previous.copy(loading = true, failure = null)
        val identity = identityProvider()
        if (identity == null) {
            mutableState.value = previous.copy(
                loading = false,
                failure = ChallengeRuntimeFailure(
                    code = "authentication_unavailable",
                    retryable = true,
                    operation = ChallengeRuntimeOperation.Join
                )
            )
            return false
        }
        val idempotencyKey = "join:${activity.id}:${identity.accountId}:${activity.revision}"
        when (val result = repository.join(
            activityId = activity.id,
            userId = identity.accountId,
            deviceId = identity.deviceId,
            revision = activity.revision,
            idempotencyKey = idempotencyKey
        )) {
            is OnlineData.Failure -> fail(result, ChallengeRuntimeOperation.Join)
            is OnlineData.Content -> {
                mutableState.value = previous.copy(
                    loading = false,
                    participation = result.value,
                    failure = null
                )
            }
        }
        return mutableState.value.joined
    }

    private fun fail(
        failure: OnlineData.Failure,
        operation: ChallengeRuntimeOperation
    ) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            failure = failure.toFailure(operation)
        )
    }
}

private fun OnlineData.Failure.toFailure(
    operation: ChallengeRuntimeOperation
) = ChallengeRuntimeFailure(
    code = code,
    retryable = retryable,
    operation = operation
)
