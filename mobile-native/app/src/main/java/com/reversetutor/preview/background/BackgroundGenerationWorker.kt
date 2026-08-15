package com.reversetutor.preview.background

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.data.memory.ErrorLogInput
import com.reversetutor.core.model.ErrorLogOrigin
import kotlinx.coroutines.flow.first

class BackgroundGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(InputJobId) ?: return Result.failure()
        val repository = DataModule.backgroundGenerationRepository(applicationContext)
        val outcome = repository
            .runGenerationJob(jobId, System.currentTimeMillis())
        recordDiagnosticIfNeeded(jobId, outcome, repository.getJob(jobId)?.userMessageId)
        notifyIfNeeded(jobId, outcome)
        return when (outcome) {
            is BackgroundGenerationOutcome.Completed,
            is BackgroundGenerationOutcome.Discarded,
            BackgroundGenerationOutcome.Cancelled -> Result.success()
            is BackgroundGenerationOutcome.Failed,
            BackgroundGenerationOutcome.MissingJob -> Result.failure()
        }
    }

    private suspend fun recordDiagnosticIfNeeded(
        jobId: String,
        outcome: BackgroundGenerationOutcome,
        sourceMessageId: String?
    ) {
        val diagnostic = GenerationDiagnosticPolicy.forBackgroundOutcome(outcome) ?: return
        DataModule.memoryRepository(applicationContext).logError(
            input = ErrorLogInput(
                title = diagnostic.title,
                detail = diagnostic.detail,
                sourceMessageId = sourceMessageId
            ),
            nowEpochMillis = System.currentTimeMillis(),
            errorId = "diagnostic-background-$jobId",
            origin = ErrorLogOrigin.Generation,
            code = diagnostic.code
        )
    }

    private suspend fun notifyIfNeeded(
        jobId: String,
        outcome: BackgroundGenerationOutcome
    ) {
        val preferences = DataModule.appPreferencesRepository(applicationContext)
            .preferences
            .first()
        val permissionGranted = NotificationManagerCompat
            .from(applicationContext).areNotificationsEnabled()
        val kind = BackgroundGenerationNotificationPolicy.resolve(
            outcome = outcome,
            notificationEnabled = preferences.backgroundGenerationNotificationEnabled,
            notificationsPermissionGranted = permissionGranted
        )
        if (kind == BackgroundGenerationNotificationPolicy.NotificationKind.None) return
        val notifier = AndroidBackgroundGenerationNotifier(applicationContext)
        when (kind) {
            BackgroundGenerationNotificationPolicy.NotificationKind.Completed ->
                notifier.notifyCompleted(jobId)
            BackgroundGenerationNotificationPolicy.NotificationKind.Failed ->
                notifier.notifyFailed(jobId)
            BackgroundGenerationNotificationPolicy.NotificationKind.None -> Unit
        }
    }

    companion object {
        const val InputJobId = "background_generation_job_id"

        fun request(jobId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<BackgroundGenerationWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(InputJobId, jobId)
                        .build()
                )
                .build()

        fun uniqueWorkName(jobId: String): String = "background-generation-$jobId"

        fun enqueue(context: Context, jobId: String) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    uniqueWorkName(jobId),
                    ExistingWorkPolicy.REPLACE,
                    request(jobId)
                )
        }
    }
}
