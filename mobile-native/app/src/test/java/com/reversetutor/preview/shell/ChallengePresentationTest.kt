package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengePresentationTest {
    @Test
    fun unjoinedChallengeShowsRecruitmentInsteadOfPersonalProgress() {
        val presentation = ChallengePresentation.from(
            joined = false,
            progress = 12,
            total = 21
        )

        assertEquals("可加入", presentation.statusLabel)
        assertEquals("招募中", presentation.syncLabel)
        assertEquals("已有 386 人参与", presentation.metaLabel)
        assertEquals("挑战周期", presentation.metricTitle)
        assertEquals("21", presentation.metricValue)
        assertEquals(" 天", presentation.metricSuffix)
        assertFalse(presentation.showPersonalProgress)
        assertFalse(presentation.showFeedback)
    }

    @Test
    fun joinedChallengeShowsStablePersonalProgress() {
        val presentation = ChallengePresentation.from(
            joined = true,
            progress = 12,
            total = 21
        )

        assertEquals("进行中", presentation.statusLabel)
        assertEquals("已同步", presentation.syncLabel)
        assertEquals("距离结束 15 天", presentation.metaLabel)
        assertEquals("学习进度", presentation.metricTitle)
        assertEquals("12", presentation.metricValue)
        assertEquals(" / 21 天", presentation.metricSuffix)
        assertEquals(12f / 21f, presentation.progressFraction)
        assertTrue(presentation.showPersonalProgress)
        assertTrue(presentation.showFeedback)
    }
}
