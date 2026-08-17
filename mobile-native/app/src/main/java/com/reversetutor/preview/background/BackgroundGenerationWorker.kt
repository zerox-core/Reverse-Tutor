package com.reversetutor.preview.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.llm.FakeLlmGenerationRuntime

class BackgroundGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(InputJobId) ?: return Result.failure()
        // Keep WorkManager execution on the same preview runtime as HybridAppGraph.
        // Constructing the repository without an explicit runtime falls back to the
        // production HTTP runtime, which makes local/device preview jobs diverge from
        // foreground generation and can attempt a real provider call.
        val repository = DataModule.backgroundGenerationRepository(
            applicationContext,
            runtime = FakeLlmGenerationRuntime()
        )
        val outcome = repository
            .runGenerationJob(jobId, System.currentTimeMillis())
        BackgroundGenerationOutcomeHandler(applicationContext).handle(
            jobId = jobId,
            outcome = outcome,
            sourceMessageId = repository.getJob(jobId)?.userMessageId
        )
        return when (outcome) {
            is BackgroundGenerationOutcome.Completed,
            is BackgroundGenerationOutcome.Discarded,
            BackgroundGenerationOutcome.Cancelled -> Result.success()
            is BackgroundGenerationOutcome.Failed,
            BackgroundGenerationOutcome.MissingJob -> Result.failure()
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
