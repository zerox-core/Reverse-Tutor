package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePageIndicatorTest {
    @Test
    fun homeStartsAtTheSecondWorkspaceAnchor() {
        assertEquals(
            1f,
            workspaceIndicatorProgress(page = 1, offsetFraction = 0f, pageCount = 4)
        )
    }

    @Test
    fun progressTracksBothDirectionsFromHome() {
        assertEquals(
            0.65f,
            workspaceIndicatorProgress(page = 1, offsetFraction = -0.35f, pageCount = 4)
        )
        assertEquals(
            1.35f,
            workspaceIndicatorProgress(page = 1, offsetFraction = 0.35f, pageCount = 4)
        )
    }

    @Test
    fun graphProgressContinuesTowardCommunity() {
        assertEquals(
            2.4f,
            workspaceIndicatorProgress(page = 2, offsetFraction = 0.4f, pageCount = 4)
        )
    }

    @Test
    fun progressIsClampedToAvailableWorkspaceAnchors() {
        assertEquals(
            0f,
            workspaceIndicatorProgress(page = 0, offsetFraction = -0.5f, pageCount = 4)
        )
        assertEquals(
            3f,
            workspaceIndicatorProgress(page = 3, offsetFraction = 0.5f, pageCount = 4)
        )
        assertEquals(
            0f,
            workspaceIndicatorProgress(page = 0, offsetFraction = 0f, pageCount = 0)
        )
    }
}
