package com.reversetutor.feature.chat

import com.reversetutor.core.domain.InitiativePlan
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope

/**
 * Lightweight heartbeat/initiative dispatch port (P6 8 -> 9 + app). Unlike
 * [BackgroundTurnPreparationPort], a heartbeat/initiative turn has no user
 * message, so it carries an [InitiativePlan] plus the immutable turn envelope.
 * It never calls a Provider or writes an assistant record — it only enqueues
 * exactly one target-bound background job that the Worker executes as the sole
 * Provider/assistant writer.
 */
fun interface HeartbeatTurnDispatchPort {
    suspend fun dispatch(request: HeartbeatTurnDispatchRequest): HeartbeatTurnDispatchResult

    /** Default no-op used when the app has not wired the production dispatcher. */
    data object Unavailable : HeartbeatTurnDispatchPort {
        override suspend fun dispatch(request: HeartbeatTurnDispatchRequest) =
            HeartbeatTurnDispatchResult.Unavailable
    }
}

data class HeartbeatTurnDispatchRequest(
    val spaceId: String,
    val targetWindowId: String,
    val plan: InitiativePlan,
    val envelope: LlmAssistantTurnEnvelope
)

sealed interface HeartbeatTurnDispatchResult {
    data class Queued(val jobId: String) : HeartbeatTurnDispatchResult
    data object Held : HeartbeatTurnDispatchResult
    data object Silent : HeartbeatTurnDispatchResult
    data object Failed : HeartbeatTurnDispatchResult
    data object Unavailable : HeartbeatTurnDispatchResult
}
