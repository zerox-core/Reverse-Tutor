package com.reversetutor.feature.chat

import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.model.BackgroundJobStatus

/**
 * NEWMP-V1-006 Task 5: home session-card projection of the same persisted
 * background-job truth the chat page observes. The label never derives from
 * chat text: Queued/Running shows "生成中", Completed/Cancelled/Discarded and
 * a missing job fall back to the latest message summary, and Failed reuses
 * the chat-safe failure mapping so persisted error details (URLs, keys,
 * exception class names) can never reach the home screen.
 */
fun SessionListItem.withActiveGenerationJob(
    activeJob: BackgroundGenerationJob?
): SessionListItem {
    val label = sessionCardGenerationLabel(activeJob) ?: return this
    return copy(statusLabel = label)
}

fun sessionCardGenerationLabel(activeJob: BackgroundGenerationJob?): String? =
    when (activeJob?.status) {
        BackgroundJobStatus.Queued, BackgroundJobStatus.Running -> "生成中"
        BackgroundJobStatus.Failed -> backgroundGenerationUiState(
            BackgroundJobStatus.Failed,
            activeJob.errorMessage
        ).statusLabel
        BackgroundJobStatus.Completed,
        BackgroundJobStatus.Cancelled,
        BackgroundJobStatus.Discarded,
        null -> null
    }
