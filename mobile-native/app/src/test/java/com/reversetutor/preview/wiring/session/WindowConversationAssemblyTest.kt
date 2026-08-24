package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.InitiativePlan
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.feature.chat.InitiativeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Window conversation assembly tests.
 *
 * Package B (task 8): the assembly wires the pure policies + read-only fakes.
 * A fake root projects a root-enabled heartbeat; a fake child stays disabled
 * until an explicit enable command; initiative evaluation produces a structured
 * [InitiativePlan] only and never touches a Provider/message-write seam.
 */
class WindowConversationAssemblyTest {

    private val now: Long = 1_000_000L

    private fun root(id: String, kind: WindowKind) = WindowRef(id, id, null, kind)

    private fun child(id: String, parentId: String) = WindowRef(id, "root-1", parentId, WindowKind.CHILD)

    private fun eligibility(
        window: WindowRef,
        state: WindowHeartbeatState,
        scenarioConfigured: Boolean = true
    ) = InitiativeEligibilityInput(
        window = window,
        heartbeatState = state,
        scenarioConfigured = scenarioConfigured,
        hasActiveUserActivity = false,
        hasUnreadMessages = false,
        inCooldown = false,
        cooldownRemainingMillis = 0L,
        isGenerating = false,
        learningScope = null,
        scopeReanchorConstraint = null,
        topologyNodes = listOf("topo-1"),
        evidenceHandles = listOf("ev-1"),
        toneConstraints = emptyList(),
        planValidityMillis = 60_000L,
        defaultCooldownMillis = 30_000L
    )

    private fun assembly(
        window: WindowRef,
        heartbeatState: WindowHeartbeatState,
        intent: LearningIntentEnvelope? = null,
        signals: List<ScopeSignal> = emptyList(),
        eligibilityInput: InitiativeEligibilityInput? = null
    ) = WindowConversationAssembly(
        readWindow = { window },
        readHeartbeatState = { heartbeatState },
        readScope = { intent to signals },
        readEligibilityInput = { eligibilityInput ?: eligibility(window, heartbeatState) },
        nowEpochMillis = { now }
    )

    @Test
    fun fake_root_projects_root_enabled_heartbeat() {
        val asm = assembly(root("r1", WindowKind.COMPANION_ROOT), WindowHeartbeatState.RootEnabled)
        assertEquals(WindowHeartbeatState.RootEnabled, asm.heartbeat("r1").state)
    }

    @Test
    fun fake_child_stays_disabled_until_enable_heartbeat_command() {
        val asm = assembly(child("c1", "p1"), WindowHeartbeatState.ChildDisabled)
        assertEquals(WindowHeartbeatState.ChildDisabled, asm.heartbeat("c1").state)
        assertEquals(WindowHeartbeatState.ExplicitlyEnabled, asm.enableChildHeartbeat("c1").state)
    }

    @Test
    fun initiative_decision_produces_plan_only() {
        val w = root("l1", WindowKind.LEARNING_ROOT)
        val asm = assembly(w, WindowHeartbeatState.RootEnabled, eligibilityInput = eligibility(w, WindowHeartbeatState.RootEnabled))
        val init = asm.initiative("l1")
        assertEquals(InitiativeStatus.ELIGIBLE, init.status)
        assertNotNull(init.plan)
        assertEquals("l1", init.plan!!.targetWindowId)
        assertEquals("learning_refresh_probe", init.plan!!.intent)

        val planFields = InitiativePlan::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(planFields.any { it.contains("message") || it.contains("reminder") || it.contains("provider") || it.contains("request") })
    }

    @Test
    fun eligibility_evaluation_has_no_provider_or_write_seam() {
        val memberNames = (
            WindowConversationAssembly::class.java.declaredFields.map { it.name } +
                WindowConversationAssembly::class.java.declaredMethods.map { it.name }
            )
        assertFalse(memberNames.any { it.contains("ChatGeneration", ignoreCase = true) || it.contains("generateReply", ignoreCase = true) || it.contains("runTurn", ignoreCase = true) })

        val w = root("l1", WindowKind.LEARNING_ROOT)
        val asm = assembly(w, WindowHeartbeatState.RootEnabled, eligibilityInput = eligibility(w, WindowHeartbeatState.RootEnabled))
        val init = asm.initiative("l1")
        assertTrue(init.status == InitiativeStatus.ELIGIBLE)

        val receipt = asm.prepareDispatch("l1")
        assertEquals("l1", receipt.targetWindowId)
        assertFalse(receipt.persisted)
    }
}
