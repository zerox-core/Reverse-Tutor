package com.reversetutor.preview.shell

import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionPrefillRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FigmaAppUiStateTest {
    @Test
    fun challengeAndAnnouncementStateAreStable() {
        val state = FigmaAppUiState()
            .joinChallenge()
            .dismissActivityAnnouncement()
            .selectSessionSettings(SessionSettingsTab.Persona)

        assertTrue(state.challengeJoined)
        assertEquals("12/21", state.challengeProgressLabel)
        assertTrue(state.activityAnnouncementDismissed)
        assertEquals(SessionSettingsTab.Persona, state.sessionSettingsTab)
    }

    @Test
    fun previewStartsWithoutJoinedChallenge() {
        assertFalse(FigmaAppUiState().challengeJoined)
    }

    @Test
    fun challengePrefillIsTopSurfaceOnFreshState() {
        val prefill = NewSessionPrefillRequest(
            requestId = "challenge-a",
            configuration = NewSessionConfiguration()
        )

        assertFalse(
            shouldShowActivityAnnouncement(
                destination = AppDestination.NewSession,
                dismissed = false,
                firstLaunchImportPromptVisible = false,
                challengeSessionPrefill = prefill
            )
        )
    }

    @Test
    fun cancellingChallengePrefillRestoresExactChallengeContext() {
        val context = ChallengeReturnContext(
            activityList = ChallengeListPosition(index = 4, offset = 27),
            detailList = ChallengeListPosition(index = 6, offset = 13),
            detailOpen = true
        )
        val prefill = NewSessionPrefillRequest(
            requestId = "challenge-a",
            configuration = NewSessionConfiguration()
        )

        assertEquals(
            ChallengeNewSessionCloseDecision.RestoreChallenge(context),
            resolveChallengeNewSessionClose(prefill, context)
        )
    }
}
