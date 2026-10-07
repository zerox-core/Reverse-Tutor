package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.InitiativeDecision
import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.InitiativeEligibilityPolicy
import com.reversetutor.core.domain.InitiativePlan
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.feature.chat.HeartbeatTurnDispatchPort
import com.reversetutor.feature.chat.HeartbeatTurnDispatchRequest
import com.reversetutor.feature.chat.HeartbeatTurnDispatchResult

/**
 * Outcome of a window heartbeat evaluation.
 * - [Silent]: not eligible / no scenario / dispatch declined -> no job.
 * - [Held]: a scenario exists but a hold condition (cooldown/unread/foreground
 *   generation/scope re-anchor) keeps the candidate.
 * - [Scheduled]: exactly one target-bound background job was dispatched.
 */
sealed interface HeartbeatDecision {
    data object Silent : HeartbeatDecision
    data object Held : HeartbeatDecision
    data class Scheduled(val jobId: String) : HeartbeatDecision
}

/**
 * Converts an [InitiativePlan] into a single [HeartbeatTurnDispatchPort] call.
 *
 * It never calls a Provider, never writes an assistant message, never selects a
 * session by timestamp, and never runs `SessionConversationAssembly.runTurn()`.
 * It only projects eligibility and, on an [InitiativeDecision.Eligible], enqueues
 * exactly one job targeting the exact eligible window.
 */
class WindowHeartbeatCoordinator(
    private val port: HeartbeatTurnDispatchPort = HeartbeatTurnDispatchPort.Unavailable
) {

    suspend fun run(
        input: InitiativeEligibilityInput,
        nowEpochMillis: Long,
        envelope: LlmAssistantTurnEnvelope
    ): HeartbeatDecision = when (val decision = InitiativeEligibilityPolicy.decide(input, nowEpochMillis)) {
        InitiativeDecision.Silent -> HeartbeatDecision.Silent
        InitiativeDecision.Held -> HeartbeatDecision.Held
        is InitiativeDecision.Eligible -> dispatchEligible(
            plan = decision.plan,
            spaceId = input.spaceId,
            targetWindowId = input.window.id,
            envelope = envelope
        )
    }

    private suspend fun dispatchEligible(
        plan: InitiativePlan,
        spaceId: String,
        targetWindowId: String,
        envelope: LlmAssistantTurnEnvelope
    ): HeartbeatDecision = when (val result = port.dispatch(
        HeartbeatTurnDispatchRequest(
            spaceId = spaceId,
            targetWindowId = targetWindowId,
            plan = plan,
            envelope = envelope
        )
    )) {
        is HeartbeatTurnDispatchResult.Queued -> HeartbeatDecision.Scheduled(result.jobId)
        HeartbeatTurnDispatchResult.Held -> HeartbeatDecision.Held
        HeartbeatTurnDispatchResult.Silent,
        HeartbeatTurnDispatchResult.Failed,
        HeartbeatTurnDispatchResult.Unavailable -> HeartbeatDecision.Silent
    }
}
