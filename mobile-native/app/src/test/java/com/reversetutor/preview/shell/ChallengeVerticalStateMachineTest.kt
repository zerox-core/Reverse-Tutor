package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeVerticalStateMachineTest {
    @Test
    fun everyEntryClearsReturnEligibilityAndDetailLock() {
        val previous = ChallengeVerticalState(
            returnPhase = ChallengeReturnPhase.EligibleForNextGesture,
            contentAtBottom = true,
            detailOpen = true
        )

        val entered = reduceChallengeVerticalState(previous, ChallengeVerticalEvent.Entered)

        assertEquals(ChallengeVerticalState(), entered)
        assertFalse(entered.outerPagerEnabled)
    }

    @Test
    fun reachingBottomArmsOnlyUntilThatGestureEnds() {
        val duringGesture = reduceChallengeVerticalState(
            ChallengeVerticalState(),
            ChallengeVerticalEvent.ContentScrollChanged(atBottom = true, gestureActive = true)
        )

        assertEquals(ChallengeReturnPhase.ArmedUntilGestureEnds, duringGesture.returnPhase)
        assertFalse(duringGesture.outerPagerEnabled)

        val afterGesture = reduceChallengeVerticalState(
            duringGesture,
            ChallengeVerticalEvent.ContentScrollChanged(atBottom = true, gestureActive = false)
        )

        assertEquals(ChallengeReturnPhase.EligibleForNextGesture, afterGesture.returnPhase)
        assertTrue(afterGesture.outerPagerEnabled)
    }

    @Test
    fun beingAtBottomWithoutACompletedContentGestureDoesNotUnlockPager() {
        val state = reduceChallengeVerticalState(
            ChallengeVerticalState(),
            ChallengeVerticalEvent.ContentScrollChanged(atBottom = true, gestureActive = false)
        )

        assertEquals(ChallengeReturnPhase.Locked, state.returnPhase)
        assertFalse(state.outerPagerEnabled)
    }

    @Test
    fun leavingBottomRelocksAndDetailLocksBothScrollOwners() {
        val eligible = ChallengeVerticalState(
            returnPhase = ChallengeReturnPhase.EligibleForNextGesture,
            contentAtBottom = true
        )
        val detail = reduceChallengeVerticalState(eligible, ChallengeVerticalEvent.DetailOpened)

        assertFalse(detail.listScrollEnabled)
        assertFalse(detail.outerPagerEnabled)

        val closed = reduceChallengeVerticalState(detail, ChallengeVerticalEvent.DetailClosed)
        assertTrue(closed.listScrollEnabled)
        assertTrue(closed.outerPagerEnabled)

        val movedAway = reduceChallengeVerticalState(
            closed,
            ChallengeVerticalEvent.ContentScrollChanged(atBottom = false, gestureActive = true)
        )
        assertFalse(movedAway.outerPagerEnabled)
    }

    @Test
    fun challengePagerKeepsTheConfirmedHighSnapThreshold() {
        assertEquals(0.65f, ChallengePagerPolicy.EntryPositionalThreshold)
    }
}
