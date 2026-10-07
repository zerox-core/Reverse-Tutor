package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome

/**
 * Decides and posts the user-facing notification for a background generation
 * outcome, resolving the job's session id so a notification tap can deep-link
 * into the originating session (S1 precise routing).
 *
 * Pure JVM collaborator with lambda dependencies. The outcome handler wires
 * production defaults: preference flag, permission probe, notifier factory and
 * the background repository session lookup.
 */
class BackgroundGenerationNotificationDispatcher(
    private val notificationEnabled: suspend () -> Boolean,
    private val notificationsPermissionGranted: () -> Boolean,
    private val notifierSupplier: () -> BackgroundGenerationNotifier,
    private val resolveJobSessionId: suspend (String) -> String?
) {
    suspend fun dispatch(jobId: String, outcome: BackgroundGenerationOutcome) {
        val kind = BackgroundGenerationNotificationPolicy.resolve(
            outcome = outcome,
            notificationEnabled = notificationEnabled(),
            notificationsPermissionGranted = notificationsPermissionGranted()
        )
        if (kind == BackgroundGenerationNotificationPolicy.NotificationKind.None) return
        val notifier = notifierSupplier()
        val sessionId = resolveJobSessionId(jobId)
        when (kind) {
            BackgroundGenerationNotificationPolicy.NotificationKind.Completed ->
                notifier.notifyCompleted(jobId, sessionId)
            BackgroundGenerationNotificationPolicy.NotificationKind.Failed ->
                notifier.notifyFailed(jobId, sessionId)
            BackgroundGenerationNotificationPolicy.NotificationKind.None -> Unit
        }
    }
}
