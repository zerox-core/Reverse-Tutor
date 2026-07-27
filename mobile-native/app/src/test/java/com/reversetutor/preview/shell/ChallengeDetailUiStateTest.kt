package com.reversetutor.preview.shell

import com.reversetutor.core.domain.ActivitySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeDetailUiStateTest {
    @Test
    fun absentRuntimeIsTruthfullyOfflineAndCannotJoin() {
        val state = ChallengeDetailUiState.from(runtime = null, fallbackJoined = false)

        assertEquals(ChallengeAvailability.Offline, state.availability)
        assertEquals(ChallengeJoinAction.Unavailable, state.joinAction)
        assertTrue(state.goal.contains("未提供"))
        assertTrue(state.sourcesLabel.contains("未提供"))
    }

    @Test
    fun nonRetryableMissingActivityIsUnavailable() {
        val state = ChallengeDetailUiState.from(
            ChallengeRuntimeState(
                failure = ChallengeRuntimeFailure(
                    code = "no_active_activity",
                    retryable = false,
                    operation = ChallengeRuntimeOperation.Load
                )
            ),
            fallbackJoined = false
        )

        assertEquals(ChallengeAvailability.Unavailable, state.availability)
        assertEquals("当前没有可用活动", state.availabilityLabel)
    }

    @Test
    fun joinFailureKeepsConfirmedDetailAndOffersRetryWithoutFabricatingParticipation() {
        val state = ChallengeDetailUiState.from(
            ChallengeRuntimeState(
                activity = ActivitySummary(
                    id = "activity-1",
                    title = "真实活动",
                    revision = 2L,
                    description = "真实目标",
                    state = "active",
                    sessionTemplateId = "template-1"
                ),
                failure = ChallengeRuntimeFailure(
                    code = "network_failure",
                    retryable = true,
                    operation = ChallengeRuntimeOperation.Join
                )
            ),
            fallbackJoined = false
        )

        assertEquals("真实活动", state.title)
        assertEquals(ChallengeAvailability.Available, state.availability)
        assertEquals(ChallengeJoinAction.Retry, state.joinAction)
        assertFalse(state.participationLabel.contains("已加入"))
        assertTrue(state.sourcesLabel.contains("template-1"))
    }
}
