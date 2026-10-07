package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Red slice V2-005: global projection rules. Only Layer-3 active values may
 * leave a window; learning content becomes learning facts, context and
 * preferences become pattern summaries; private signals never project.
 */
class GlobalProjectionContractTest {

    private fun active(category: WindowObservationCategory, weight: Double) = WindowActiveValue(
        category = category,
        slotKey = category.name,
        value = "v",
        revision = 1L,
        weight = weight,
        sourceClass = MemoryObservationSourceClass.USER_STATEMENT,
        provenanceHandle = "turn:w1:t1",
        updatedAtEpochMillis = 100L,
    )

    @Test
    fun `high weight learning event channels to learning fact`() {
        assertEquals(
            ProjectionChannel.LEARNING_FACT,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.LEARNING_EVENT, 0.8)),
        )
    }

    @Test
    fun `high weight goal channels to learning fact`() {
        assertEquals(
            ProjectionChannel.LEARNING_FACT,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.GOAL_STATEMENT, 0.7)),
        )
    }

    @Test
    fun `high weight activity channels to pattern summary`() {
        assertEquals(
            ProjectionChannel.PATTERN_SUMMARY,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.ACTIVITY_CONTEXT, 0.9)),
        )
    }

    @Test
    fun `high weight preference channels to pattern summary`() {
        assertEquals(
            ProjectionChannel.PATTERN_SUMMARY,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.PREFERENCE_SIGNAL, 0.9)),
        )
    }

    @Test
    fun `reminder feedback never projects`() {
        assertEquals(
            ProjectionChannel.NONE,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.REMINDER_FEEDBACK, 1.0)),
        )
    }

    @Test
    fun `daily life never projects`() {
        assertEquals(
            ProjectionChannel.NONE,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.DAILY_LIFE, 1.0)),
        )
    }

    @Test
    fun `low weight value never projects`() {
        assertEquals(
            ProjectionChannel.NONE,
            GlobalProjectionPolicy.channelFor(active(WindowObservationCategory.LEARNING_EVENT, 0.3)),
        )
    }
}
