package com.reversetutor.core.domain

/**
 * Window heartbeat and initiative-plan contracts.
 *
 * These types live in [core:domain] (non-frozen) and are Android-free. An
 * [InitiativePlan] is structured (target window, intent, topology nodes,
 * evidence handles, tone constraints, expiry, cooldown, delivery policy). It is
 * NOT a provider request and NOT a prewritten user-visible reminder string; the
 * generation algorithm turns the plan into a grounded opening only after
 * eligibility succeeds.
 */

/** Explicit enable command that creates or enables a child heartbeat schedule. */
data class EnableWindowHeartbeatCommand(
    val windowId: String,
    val requestedBy: String
)

data class HeartbeatScheduleContract(
    val windowId: String,
    val enabled: Boolean,
    val minCooldownMillis: Long
)

/**
 * Read-only snapshot a window evaluates to decide initiative eligibility.
 * The policy never reads a clock; [nowEpochMillis] is supplied by the caller.
 */
data class InitiativeEligibilityInput(
    val window: WindowRef,
    val heartbeatState: WindowHeartbeatState,
    val scenarioConfigured: Boolean,
    val hasActiveUserActivity: Boolean,
    val hasUnreadMessages: Boolean,
    val inCooldown: Boolean,
    val cooldownRemainingMillis: Long,
    val isGenerating: Boolean,
    val learningScope: ScopeRelation?,
    val scopeReanchorConstraint: String?,
    val topologyNodes: List<String> = emptyList(),
    val evidenceHandles: List<String> = emptyList(),
    val toneConstraints: List<String> = emptyList(),
    val planValidityMillis: Long,
    val defaultCooldownMillis: Long
)

data class InitiativePlan(
    val targetWindowId: String,
    val intent: String,
    val topologyNodes: List<String>,
    val evidenceHandles: List<String>,
    val toneConstraints: List<String>,
    val expiryEpochMillis: Long,
    val minCooldownMillis: Long,
    val deliveryPolicy: String
)

/** Exhaustive initiative decision. [Silent] is no action; [Held] is a candidate on hold; [Eligible] yields a plan. */
sealed interface InitiativeDecision {
    data object Silent : InitiativeDecision
    data object Held : InitiativeDecision
    data class Eligible(val plan: InitiativePlan) : InitiativeDecision
}
