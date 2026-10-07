package com.reversetutor.core.domain

/**
 * V2-005: global projection rules.
 *
 * Only Layer-3 active values may leave a window. Learning content
 * (learning events, stated goals) projects as learning facts into the
 * global learning domain; context and preferences project only as
 * pattern summaries; private signals (reminder feedback, daily life)
 * never leave the window. Below the weight floor nothing projects.
 */

enum class ProjectionChannel { LEARNING_FACT, PATTERN_SUMMARY, NONE }

object GlobalProjectionPolicy {

    const val MIN_PROJECTION_WEIGHT: Double = 0.6

    fun channelFor(value: WindowActiveValue): ProjectionChannel {
        if (value.weight < MIN_PROJECTION_WEIGHT) return ProjectionChannel.NONE
        return when (value.category) {
            WindowObservationCategory.LEARNING_EVENT,
            WindowObservationCategory.GOAL_STATEMENT,
            -> ProjectionChannel.LEARNING_FACT
            WindowObservationCategory.ACTIVITY_CONTEXT,
            WindowObservationCategory.PREFERENCE_SIGNAL,
            -> ProjectionChannel.PATTERN_SUMMARY
            WindowObservationCategory.REMINDER_FEEDBACK,
            WindowObservationCategory.DAILY_LIFE,
            -> ProjectionChannel.NONE
        }
    }
}
