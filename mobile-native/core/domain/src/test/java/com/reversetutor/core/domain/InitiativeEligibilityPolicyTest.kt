package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Window heartbeat eligibility and initiative-plan policy tests.
 *
 * Package A (task 5): a heartbeat plan never calls the Provider, never writes a
 * prewritten reminder, and never targets a window other than the eligible one.
 * Roots are eligible by default; children are eligible only after an explicit
 * enable command.
 */
class InitiativeEligibilityPolicyTest {

    private val now: Long = 1_000_000_000L

    private fun root(kind: WindowKind) = WindowRef("w-$kind", "root-1", null, kind)

    private fun input(
        window: WindowRef,
        state: WindowHeartbeatState,
        scenarioConfigured: Boolean = true,
        activeUser: Boolean = false,
        unread: Boolean = false,
        inCooldown: Boolean = false,
        generating: Boolean = false,
        learningScope: ScopeRelation? = null,
        reanchor: String? = null,
        evidenceHandles: List<String> = listOf("ev-1", "ev-2"),
        toneConstraints: List<String> = listOf("be warm")
    ) = InitiativeEligibilityInput(
        window = window,
        heartbeatState = state,
        scenarioConfigured = scenarioConfigured,
        hasActiveUserActivity = activeUser,
        hasUnreadMessages = unread,
        inCooldown = inCooldown,
        cooldownRemainingMillis = if (inCooldown) 5_000L else 0L,
        isGenerating = generating,
        learningScope = learningScope,
        scopeReanchorConstraint = reanchor,
        topologyNodes = listOf("topo-1"),
        evidenceHandles = evidenceHandles,
        toneConstraints = toneConstraints,
        planValidityMillis = 60_000L,
        defaultCooldownMillis = 30_000L
    )

    @Test
    fun root_is_eligible_but_no_scenario_yields_silent() {
        val companionRoot = root(WindowKind.COMPANION_ROOT)
        assertTrue(InitiativeEligibilityPolicy.canPlanFor(companionRoot, WindowHeartbeatState.RootEnabled))
        val decision = InitiativeEligibilityPolicy.decide(
            input(companionRoot, WindowHeartbeatState.RootEnabled, scenarioConfigured = false),
            now
        )
        assertEquals(InitiativeDecision.Silent, decision)
    }

    @Test
    fun child_is_ineligible_until_enable_window_heartbeat_command() {
        val child = WindowRef("child-1", "root-1", "parent-1", WindowKind.CHILD)
        assertFalse(InitiativeEligibilityPolicy.canPlanFor(child, WindowHeartbeatState.ChildDisabled))
        assertEquals(InitiativeDecision.Silent, InitiativeEligibilityPolicy.decide(input(child, WindowHeartbeatState.ChildDisabled), now))
        assertTrue(InitiativeEligibilityPolicy.canPlanFor(child, WindowHeartbeatState.ExplicitlyEnabled))
        val decision = InitiativeEligibilityPolicy.decide(input(child, WindowHeartbeatState.ExplicitlyEnabled), now)
        assertTrue(decision is InitiativeDecision.Eligible)
    }

    @Test
    fun enabled_child_targets_itself_not_parent_or_sibling() {
        val child = WindowRef("child-1", "root-1", "parent-1", WindowKind.CHILD)
        val decision = InitiativeEligibilityPolicy.decide(input(child, WindowHeartbeatState.ExplicitlyEnabled), now)
        assertTrue(decision is InitiativeDecision.Eligible)
        val plan = (decision as InitiativeDecision.Eligible).plan
        assertEquals("child-1", plan.targetWindowId)
        assertFalse(plan.targetWindowId == "parent-1")
        assertFalse(plan.targetWindowId == "root-1")
    }

    @Test
    fun active_user_or_unread_message_holds_candidate() {
        val learningRoot = root(WindowKind.LEARNING_ROOT)
        val active = InitiativeEligibilityPolicy.decide(input(learningRoot, WindowHeartbeatState.RootEnabled, activeUser = true), now)
        val unread = InitiativeEligibilityPolicy.decide(input(learningRoot, WindowHeartbeatState.RootEnabled, unread = true), now)
        assertEquals(InitiativeDecision.Held, active)
        assertEquals(InitiativeDecision.Held, unread)
    }

    @Test
    fun cooldown_holds_candidate() {
        val learningRoot = root(WindowKind.LEARNING_ROOT)
        val decision = InitiativeEligibilityPolicy.decide(input(learningRoot, WindowHeartbeatState.RootEnabled, inCooldown = true), now)
        assertEquals(InitiativeDecision.Held, decision)
    }

    @Test
    fun plan_contains_expiry_target_and_evidence_handles_not_fixed_copy() {
        val learningRoot = root(WindowKind.LEARNING_ROOT)
        val decision = InitiativeEligibilityPolicy.decide(input(learningRoot, WindowHeartbeatState.RootEnabled), now)
        assertTrue(decision is InitiativeDecision.Eligible)
        val plan = (decision as InitiativeDecision.Eligible).plan
        assertEquals("w-LEARNING_ROOT", plan.targetWindowId)
        assertEquals(now + 60_000L, plan.expiryEpochMillis)
        assertEquals(30_000L, plan.minCooldownMillis)
        assertEquals(listOf("ev-1", "ev-2"), plan.evidenceHandles)
        assertEquals(listOf("be warm"), plan.toneConstraints)
        assertEquals("learning_refresh_probe", plan.intent)
        // A plan is structured; it never carries a prewritten user-visible reminder.
        assertFalse(plan.javaClass.declaredFields.any { it.name.contains("message", ignoreCase = true) })
    }

    @Test
    fun learning_window_scope_reanchor_can_hold_or_constrain_plan() {
        val learningRoot = root(WindowKind.LEARNING_ROOT)
        val held = InitiativeEligibilityPolicy.decide(
            input(
                learningRoot,
                WindowHeartbeatState.RootEnabled,
                learningScope = ScopeRelation.SUSTAINED_OUT_OF_SCOPE,
                reanchor = "stay in scope"
            ),
            now
        )
        assertEquals(InitiativeDecision.Held, held)

        val eligible = InitiativeEligibilityPolicy.decide(
            input(learningRoot, WindowHeartbeatState.RootEnabled, learningScope = ScopeRelation.RELATED_EVOLUTION),
            now
        )
        assertTrue(eligible is InitiativeDecision.Eligible)
    }
}
