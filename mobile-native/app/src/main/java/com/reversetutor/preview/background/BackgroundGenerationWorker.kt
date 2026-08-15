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
import kotlinx.coroutines.flow.first

class BackgroundGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(InputJobId) ?: return Result.failure()
        val outcome = DataModule.backgroundGenerationRepository(applicationContext)
            .runGenerationJob(jobId, System.currentTimeMillis())
        notifyIfNeeded(jobId, outcome)
        return when (outcome) {
            is BackgroundGenerationOutcome.Completed,
            is BackgroundGenerationOutcome.Discarded,
            BackgroundGenerationOutcome.Cancelled -> Result.success()
            is BackgroundGenerationOutcome.Failed,
            BackgroundGenerationOutcome.MissingJob -> Result.failure()
        }
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
