package com.reversetutor.preview.background

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.data.memory.ErrorLogInput
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.preferences.AppPreferencesRepository
import com.reversetutor.core.model.ErrorLogOrigin
import kotlinx.coroutines.flow.first

/**
 * App-layer post-processing for a completed background generation outcome.
 *
 * The default dependencies are the production graph. Tests can provide an
 * in-memory-backed repository, a no-op notifier, and a deterministic clock
 * without touching the frozen data or protocol modules.
 */
class BackgroundGenerationOutcomeHandler(
    private val appContext: Context,
    private val memoryRepository: MemoryRepository =
        DataModule.memoryRepository(appContext),
    private val preferencesRepository: AppPreferencesRepository =
        DataModule.appPreferencesRepository(appContext),
    private val notifierFactory: (Context) -> BackgroundGenerationNotifier =
        ::AndroidBackgroundGenerationNotifier,
    private val notificationsPermissionGranted: () -> Boolean = {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    },
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun handle(
        jobId: String,
        outcome: BackgroundGenerationOutcome,
        sourceMessageId: String?
    ) {
        recordDiagnosticIfNeeded(jobId, outcome, sourceMessageId)
        notifyIfNeeded(jobId, outcome)
    }

    private suspend fun recordDiagnosticIfNeeded(
        jobId: String,
        outcome: BackgroundGenerationOutcome,
        sourceMessageId: String?
    ) {
        val diagnostic = GenerationDiagnosticPolicy.forBackgroundOutcome(outcome) ?: return
        memoryRepository.logError(
            input = ErrorLogInput(
                title = diagnostic.title,
                detail = diagnostic.detail,
                sourceMessageId = sourceMessageId
            ),
            nowEpochMillis = clock(),
            errorId = "diagnostic-background-$jobId",
            origin = ErrorLogOrigin.Generation,
            code = diagnostic.code
        )
    }

    private suspend fun notifyIfNeeded(
        jobId: String,
        outcome: BackgroundGenerationOutcome
    ) {
        val preferences = preferencesRepository.preferences.first()
        val kind = BackgroundGenerationNotificationPolicy.resolve(
            outcome = outcome,
            notificationEnabled = preferences.backgroundGenerationNotificationEnabled,
            notificationsPermissionGranted = notificationsPermissionGranted()
        )
        if (kind == BackgroundGenerationNotificationPolicy.NotificationKind.None) return
        val notifier = notifierFactory(appContext)
        when (kind) {
            BackgroundGenerationNotificationPolicy.NotificationKind.Completed ->
                notifier.notifyCompleted(jobId)
            BackgroundGenerationNotificationPolicy.NotificationKind.Failed ->
                notifier.notifyFailed(jobId)
            BackgroundGenerationNotificationPolicy.NotificationKind.None -> Unit
        }
    }
}
