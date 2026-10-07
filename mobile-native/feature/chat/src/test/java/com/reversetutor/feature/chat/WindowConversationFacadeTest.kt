package com.reversetutor.feature.chat

import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.MergeDenial
import com.reversetutor.core.domain.ScopeRelation
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.ScopeSignalCategory
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Windows conversation facade tests.
 *
 * Package B (task 7): the facade projects the pure `core:domain` policies into
 * UI-facing contract slices. It must not import Compose, a DAO/Entity, a
 * Database, a SecretStore, or a generation Repository.
 */
class WindowConversationFacadeTest {

    private val facade = WindowConversationFacade()

    @Test
    fun root_default_heartbeat_is_enabled() {
        val root = WindowRef("r1", "r1", null, WindowKind.COMPANION_ROOT)
        assertEquals(WindowHeartbeatState.RootEnabled, facade.defaultHeartbeat(root).state)
    }

    @Test
    fun child_explicit_enable_action() {
        val child = WindowRef("c1", "r1", "p1", WindowKind.CHILD)
        assertEquals(WindowHeartbeatState.ChildDisabled, facade.defaultHeartbeat(child).state)
        assertEquals(WindowHeartbeatState.ChildDisabled, facade.heartbeat(WindowHeartbeatState.ChildDisabled).state)
        val enabled = facade.enableChildHeartbeat(WindowHeartbeatState.ChildDisabled)
        assertEquals(WindowHeartbeatState.ExplicitlyEnabled, enabled.state)
        assertEquals(WindowHeartbeatState.ExplicitlyEnabled, facade.enableChildHeartbeat(WindowHeartbeatState.ExplicitlyEnabled).state)
    }

    @Test
    fun merge_eligibility_is_projected() {
        val parent = WindowRef("p1", "r1", null, WindowKind.LEARNING_ROOT)
        val child = WindowRef("c1", "r1", "p1", WindowKind.CHILD)
        val allowed = facade.merge(child, parent, "delta-1", 9L)
        assertTrue(allowed.allowed)
        assertEquals("c1|p1|delta-1|9", allowed.commitId)
        assertNull(allowed.denial)

        val sibling = WindowRef("c2", "r1", "p1", WindowKind.CHILD)
        val denied = facade.merge(child, sibling, "delta-1", 9L)
        assertFalse(denied.allowed)
        assertEquals(MergeDenial.SIBLING_TARGET, denied.denial)
    }

    @Test
    fun scope_state_is_projected() {
        val learning = WindowRef("l1", "l1", null, WindowKind.LEARNING_ROOT)
        val envelope = LearningIntentEnvelope("l1", "math", "high_school", "goal", "worked_examples")
        val scope = facade.scope(learning, envelope, listOf(ScopeSignal(ScopeSignalCategory.LEVEL_PROGRESSION, 1)))
        assertEquals(ScopeRelation.RELATED_EVOLUTION, scope.relation)
        assertNull(scope.reanchorConstraint)

        val companionRoot = WindowRef("cr", "cr", null, WindowKind.COMPANION_ROOT)
        val notRun = facade.scope(companionRoot, envelope, emptyList())
        assertNull(notRun.relation)
    }

    @Test
    fun initiative_state_is_projected() {
        val root = WindowRef("l1", "l1", null, WindowKind.LEARNING_ROOT)
        val silent = facade.initiative(eligibility(root, scenarioConfigured = false), 1_000L)
        assertEquals(InitiativeStatus.SILENT, silent.status)
        assertNull(silent.plan)

        val eligible = facade.initiative(eligibility(root, scenarioConfigured = true), 1_000L)
        assertEquals(InitiativeStatus.ELIGIBLE, eligible.status)
        assertNotNull(eligible.plan)
        assertEquals("l1", eligible.plan!!.targetWindowId)
        assertEquals("learning_refresh_probe", eligible.plan!!.intent)

        val held = facade.initiative(eligibility(root, scenarioConfigured = true, inCooldown = true), 1_000L)
        assertEquals(InitiativeStatus.HELD, held.status)
    }

    @Test
    fun project_composes_a_full_snapshot() {
        val child = WindowRef("c1", "r1", "p1", WindowKind.CHILD)
        val snap = facade.project(
            window = child,
            heartbeatState = WindowHeartbeatState.ExplicitlyEnabled,
            merge = facade.merge(child, WindowRef("p1", "r1", null, WindowKind.LEARNING_ROOT), "d1", 5L),
            scope = WindowScopeContract(relation = null),
            initiative = WindowInitiativeContract(InitiativeStatus.SILENT)
        )
        assertEquals("c1", snap.windowId)
        assertEquals("r1", snap.rootId)
        assertEquals("p1", snap.parentId)
        assertEquals(WindowKind.CHILD, snap.kind)
        assertEquals(WindowHeartbeatState.ExplicitlyEnabled, snap.heartbeat.state)
        assertTrue(snap.merge.allowed)
    }

    private fun eligibility(
        window: WindowRef,
        scenarioConfigured: Boolean,
        inCooldown: Boolean = false
    ) = InitiativeEligibilityInput(
        window = window,
        heartbeatState = WindowHeartbeatState.RootEnabled,
        scenarioConfigured = scenarioConfigured,
        hasActiveUserActivity = false,
        hasUnreadMessages = false,
        inCooldown = inCooldown,
        cooldownRemainingMillis = if (inCooldown) 5_000L else 0L,
        isGenerating = false,
        learningScope = null,
        scopeReanchorConstraint = null,
        topologyNodes = listOf("topo-1"),
        evidenceHandles = listOf("ev-1"),
        toneConstraints = emptyList(),
        planValidityMillis = 60_000L,
        defaultCooldownMillis = 30_000L
    )
}
