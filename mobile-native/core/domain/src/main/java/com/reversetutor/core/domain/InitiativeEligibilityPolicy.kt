package com.reversetutor.core.domain

/**
 * Pure window heartbeat eligibility and initiative-plan policy.
 *
 * This object never reads a clock, persistent storage, a data-access layer, or
 * an LLM; time is passed in by the caller. Every root is heartbeat-enabled by
 * default; a child has no schedule until an explicit [EnableWindowHeartbeatCommand].
 */
object InitiativeEligibilityPolicy {

    private fun isEnabled(state: WindowHeartbeatState): Boolean =
        state != WindowHeartbeatState.ChildDisabled

    fun canPlanFor(window: WindowRef, heartbeatState: WindowHeartbeatState): Boolean =
        when (window.kind) {
            WindowKind.CHILD -> heartbeatState == WindowHeartbeatState.ExplicitlyEnabled
            else -> isEnabled(heartbeatState)
        }

    fun decide(input: InitiativeEligibilityInput, nowEpochMillis: Long): InitiativeDecision {
        if (!canPlanFor(input.window, input.heartbeatState)) return InitiativeDecision.Silent
        if (!input.scenarioConfigured) return InitiativeDecision.Silent
        return when {
            input.hasActiveUserActivity -> InitiativeDecision.Held
            input.isGenerating -> InitiativeDecision.Held
            input.hasUnreadMessages -> InitiativeDecision.Held
            input.inCooldown -> InitiativeDecision.Held
            input.learningScope == ScopeRelation.SUSTAINED_OUT_OF_SCOPE -> InitiativeDecision.Held
            else -> InitiativeDecision.Eligible(buildPlan(input, nowEpochMillis))
        }
    }

    private fun buildPlan(input: InitiativeEligibilityInput, nowEpochMillis: Long): InitiativePlan {
        val tone = input.toneConstraints + listOfNotNull(input.scopeReanchorConstraint).filter { it.isNotBlank() }
        return InitiativePlan(
            targetWindowId = input.window.id,
            intent = intentFor(input.window.kind),
            topologyNodes = input.topologyNodes,
            evidenceHandles = input.evidenceHandles,
            toneConstraints = tone.distinct(),
            expiryEpochMillis = nowEpochMillis + input.planValidityMillis,
            minCooldownMillis = input.defaultCooldownMillis,
            deliveryPolicy = "write_only"
        )
    }

    private fun intentFor(kind: WindowKind): String = when (kind) {
        WindowKind.COMPANION_ROOT -> "companion_check_in"
        WindowKind.LEARNING_ROOT, WindowKind.TASK_ROOT -> "learning_refresh_probe"
        WindowKind.CHILD -> "child_window_engagement"
    }
}
