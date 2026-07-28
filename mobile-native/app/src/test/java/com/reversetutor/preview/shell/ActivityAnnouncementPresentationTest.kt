package com.reversetutor.preview.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActivityAnnouncementPresentationTest {
    @Test
    fun announcementUsesPastelChallengeHeroWithoutAnOuterShadowFrame() {
        assertEquals(0.dp, ActivityAnnouncementPresentation.DialogElevation)
        assertEquals(
            listOf(Color(0xFFE7F8FF), Color(0xFFF1ECFF)),
            ActivityAnnouncementPresentation.HeroColors
        )
        assertFalse(ActivityAnnouncementPresentation.UsesOutlinedHero)
    }
}
