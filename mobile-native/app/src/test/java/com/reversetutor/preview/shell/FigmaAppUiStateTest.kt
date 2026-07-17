package com.reversetutor.preview.shell

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
}
