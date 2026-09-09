package com.reversetutor.preview.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.RoomAssistantReplyArtifactStore
import com.reversetutor.core.data.agent.RoomSessionDocumentStore
import com.reversetutor.core.data.agent.RoomSessionTableStore
import com.reversetutor.core.data.agent.RoomToolCallReceiptStore
import com.reversetutor.core.data.agent.SessionDocumentRepository
import com.reversetutor.core.data.agent.SessionTableRepository
import com.reversetutor.core.data.agent.SessionToolExecutionRepository
import com.reversetutor.core.data.agent.ToolCallReceiptRepository
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.data.learning.LearningLedgerRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.llm.FakeLlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.preview.BuildConfig
import com.reversetutor.preview.R
import com.reversetutor.preview.wiring.DebugLlmBootstrapConfig
import com.reversetutor.preview.wiring.HybridLlmRuntimeMode
import com.reversetutor.preview.wiring.runtimeMode
import com.reversetutor.preview.wiring.session.PostTurnProjector
import com.reversetutor.preview.wiring.session.TurnProjectionSink

class BackgroundGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(InputJobId) ?: return Result.failure()
        val repository = DataModule.backgroundGenerationRepository(
            applicationContext,
            runtime = backgroundGenerationRuntimeFor(
                DebugLlmBootstrapConfig.from(
                    apiKey = BuildConfig.DEBUG_LLM_API_KEY,
                    baseUrl = BuildConfig.DEBUG_LLM_BASE_URL,
                    defaultModel = BuildConfig.DEBUG_LLM_DEFAULT_MODEL,
                    fallbackModels = BuildConfig.DEBUG_LLM_FALLBACK_MODELS
                ).runtimeMode()
            )
        )
        // Promote to a foreground service while generating so the process is
        // not frozen when the app is backgrounded; completion then persists and
        // the outcome notification can be posted even off-screen.
        runCatching { setForeground(createForegroundInfo()) }
        val outcome = repository
            .runGenerationJob(jobId, System.currentTimeMillis())
        if (outcome is BackgroundGenerationOutcome.Completed) {
            repository.getJob(jobId)?.let { job ->
                // The reply is already persisted by ChatGenerationRepository.
                // Agent tools are non-blocking post-turn work and cannot create
                // a second assistant message or turn a good reply into failure.
                runCatching {
                    completionProcessor(applicationContext).process(
                        jobId = jobId,
                        job = job,
                        outcome = outcome,
                        nowEpochMillis = System.currentTimeMillis()
                    )
                }
            }
        }
        BackgroundGenerationOutcomeHandler(applicationContext).handle(
            jobId = jobId,
            outcome = outcome,
            sourceMessageId = repository.getJob(jobId)?.userMessageId
        )
        return when (outcome) {
            is BackgroundGenerationOutcome.Completed,
            is BackgroundGenerationOutcome.Discarded,
            BackgroundGenerationOutcome.Cancelled,
            BackgroundGenerationOutcome.AlreadyRunning -> Result.success()
            is BackgroundGenerationOutcome.Failed,
            BackgroundGenerationOutcome.MissingJob -> Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        // Ensure the shared channel exists before building the notification.
        runCatching { AndroidBackgroundGenerationNotifier(applicationContext) }
        val notification = NotificationCompat.Builder(
            applicationContext,
            AndroidBackgroundGenerationNotifier.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在生成回复")
            .setContentText("小岚正在思考，生成完成后会通知你。")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                GenerationForegroundNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(GenerationForegroundNotificationId, notification)
        }
    }

    companion object {
        private const val GenerationForegroundNotificationId = 73201

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

private fun completionProcessor(context: Context): BackgroundTurnCompletionProcessor {
    val database = DataModule.database(context)
    val agentDao = database.sessionAgentDao()
    val ledger = LearningLedgerRepository(database.learningLedgerDao())
    val sources = DataModule.sourceRepository(context)
    return BackgroundTurnCompletionProcessor(
        artifacts = AssistantReplyArtifactRepository(RoomAssistantReplyArtifactStore(agentDao)),
        tools = SessionToolExecutionRepository(
            documents = SessionDocumentRepository(RoomSessionDocumentStore(agentDao)),
            tables = SessionTableRepository(RoomSessionTableStore(agentDao)),
            receipts = ToolCallReceiptRepository(RoomToolCallReceiptStore(agentDao))
        ),
        projector = PostTurnProjector(
            TurnProjectionSink { jobId, outcome ->
                ledger.appendLearningFactIfAbsent(
                    LearningFactReceipt(
                        knowledgePoint = outcome.knowledgePoint,
                        evidenceType = outcome.evidenceType,
                        result = outcome.evidenceStatus,
                        confidence = ((outcome.correctness + outcome.depth) / 2f).coerceIn(0f, 1f),
                        sourceWindowId = requireNotNull(outcome.windowId),
                        sourceTurnId = "background:$jobId",
                        occurredAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }
        ),
        loadCurrentSourceRevision = { spaceId, sourceHandle ->
            currentSourceRevision(sources, spaceId, sourceHandle)
        }
    )
}

private suspend fun currentSourceRevision(
    sources: SourceRepository,
    spaceId: String,
    sourceHandle: String
): String? {
    val sourceId = sourceHandle.removePrefix("source:")
        .substringBeforeLast(':')
        .takeIf { it.isNotBlank() } ?: return null
    return sources.listSourcesWithChunks(spaceId)
        .firstOrNull { it.source.id == sourceId }
        ?.let { "rev-${it.source.id}-${it.source.createdAtEpochMillis}" }
}

internal fun backgroundGenerationRuntimeFor(
    mode: HybridLlmRuntimeMode
): LlmGenerationRuntime? = when (mode) {
    HybridLlmRuntimeMode.Fake -> FakeLlmGenerationRuntime()
    HybridLlmRuntimeMode.Production -> null
}
