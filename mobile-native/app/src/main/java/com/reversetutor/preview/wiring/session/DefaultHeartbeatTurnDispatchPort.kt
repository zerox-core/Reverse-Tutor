package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationRepository
import com.reversetutor.core.data.background.BackgroundInitiativeInput
import com.reversetutor.feature.chat.HeartbeatTurnDispatchPort
import com.reversetutor.feature.chat.HeartbeatTurnDispatchRequest
import com.reversetutor.feature.chat.HeartbeatTurnDispatchResult

/**
 * Production [HeartbeatTurnDispatchPort]. It projects a plan-driven initiative
 * [HeartbeatTurnDispatchRequest] into a target-bound background initiative job
 * via [BackgroundGenerationRepository.enqueueInitiativeJob] — no user message,
 * no user input text, correct [spaceId]/[targetWindowId]. The Worker remains
 * the sole Provider and assistant writer; this adapter only enqueues a job.
 */
class DefaultHeartbeatTurnDispatchPort(
    private val backgroundGenerationRepository: BackgroundGenerationRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : HeartbeatTurnDispatchPort {

    override suspend fun dispatch(request: HeartbeatTurnDispatchRequest): HeartbeatTurnDispatchResult =
        try {
            val job = backgroundGenerationRepository.enqueueInitiativeJob(
                input = BackgroundInitiativeInput(
                    spaceId = request.spaceId,
                    targetWindowId = request.targetWindowId,
                    initiativeSource = request.plan.intent,
                    envelope = request.envelope
                ),
                nowEpochMillis = nowEpochMillis()
            )
            HeartbeatTurnDispatchResult.Queued(job.id)
        } catch (_: Exception) {
            HeartbeatTurnDispatchResult.Failed
        }
}
