package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome

/**
 * Pure decision logic for background generation notifications.
 *
 * Has no Android dependencies, so it is fully unit-testable on the JVM.
 * The notification policy is explicit opt-in: notifications are sent only for
 * [BackgroundGenerationOutcome.Completed] and [BackgroundGenerationOutcome.Failed],
 * and only when the user has enabled the toggle and granted system notification permission.
 * Notification copy never includes user message text, session titles, model names,
 * provider identifiers, URLs, error originals, API keys or any secret material.
 */
object BackgroundGenerationNotificationPolicy {

    enum class NotificationKind { Completed, Failed, None }

    fun resolve(
        outcome: BackgroundGenerationOutcome,
        notificationEnabled: Boolean,
        notificationsPermissionGranted: Boolean
    ): NotificationKind {
        if (!notificationEnabled) return NotificationKind.None
        if (!notificationsPermissionGranted) return NotificationKind.None
        return when (outcome) {
            is BackgroundGenerationOutcome.Completed -> NotificationKind.Completed
            is BackgroundGenerationOutcome.Failed -> NotificationKind.Failed
            is BackgroundGenerationOutcome.Discarded,
            BackgroundGenerationOutcome.Cancelled,
            BackgroundGenerationOutcome.AlreadyRunning,
            BackgroundGenerationOutcome.MissingJob -> NotificationKind.None
        }
    }

    /**
     * Derives a stable, non-negative notification id from a job id so that the same
     * job never produces two concurrent notifications.
     */
    fun notificationIdFor(jobId: String): Int = jobId.hashCode() and 0x7FFFFFFF
}
