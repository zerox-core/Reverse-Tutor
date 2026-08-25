package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmTurnPlan
import com.reversetutor.core.llm.LlmWindowContext
import com.reversetutor.feature.chat.HeartbeatTurnDispatchResult
import com.reversetutor.feature.chat.HeartbeatTurnDispatchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowHeartbeatCoordinatorTest {

    private val now: Long = 1_000_000L

    private fun envelope(windowId: String) = LlmAssistantTurnEnvelope(
        window = LlmWindowContext(windowId = windowId, rootId = windowId, windowKind = "LEARNING_ROOT", forkRevision = 1L),
        turnPlan = LlmTurnPlan(intent = "check-in", actionType = "observe", studentRole = "companion")
    )

    private fun eligibility(
        window: WindowRef,
        state: WindowHeartbeatState,
        scenario: Boolean,
        unread: Boolean = false,
        inCooldown: Boolean = false,
        generating: Boolean = false,
        spaceId: String = "space-default"
    ) = InitiativeEligibilityInput(
        window = window,
        spaceId = spaceId,
        heartbeatState = state,
        scenarioConfigured = scenario,
        hasActiveUserActivity = false,
        hasUnreadMessages = unread,
        inCooldown = inCooldown,
        cooldownRemainingMillis = if (inCooldown) 5_000L else 0L,
        isGenerating = generating,
        learningScope = null,
        scopeReanchorConstraint = null,
        topologyNodes = listOf("topo"),
        evidenceHandles = listOf("ev"),
        toneConstraints = emptyList(),
        planValidityMillis = 60_000L,
        defaultCooldownMillis = 30_000L
    )

    private fun root(kind: WindowKind) = WindowRef("n-$kind", "n-$kind", null, kind)

    @Test
    fun rootWithoutScenarioYieldsSilent() = runBlocking {
        var calls = 0
        val coordinator = WindowHeartbeatCoordinator { calls++; HeartbeatTurnDispatchResult.Silent }
        val decision = coordinator.run(
            eligibility(root(WindowKind.LEARNING_ROOT), WindowHeartbeatState.RootEnabled, scenario = false),
            now,
            envelope("n-LEARNING_ROOT")
        )
        assertEquals(HeartbeatDecision.Silent, decision)
        assertEquals(0, calls)
    }

    @Test
    fun eligibleRootSchedulesExactlyOneTargetBoundJob() = runBlocking {
        val requests = mutableListOf<HeartbeatTurnDispatchRequest>()
        val coordinator = WindowHeartbeatCoordinator { request ->
            requests += request
            HeartbeatTurnDispatchResult.Queued("job-1")
        }
        val decision = coordinator.run(
            eligibility(root(WindowKind.LEARNING_ROOT), WindowHeartbeatState.RootEnabled, scenario = true),
            now,
            envelope("n-LEARNING_ROOT")
        )
        assertEquals(HeartbeatDecision.Scheduled("job-1"), decision)
        assertEquals(1, requests.size)
        assertEquals("n-LEARNING_ROOT", requests.single().targetWindowId)
        assertEquals("space-default", requests.single().spaceId)
        assertTrue(requests.single().spaceId != requests.single().targetWindowId)
    }

    @Test
    fun enabledChildSchedulesItselfOnly() = runBlocking {
        val child = WindowRef("child-1", "root-1", "root-1", WindowKind.CHILD)
        val requests = mutableListOf<HeartbeatTurnDispatchRequest>()
        val coordinator = WindowHeartbeatCoordinator { request ->
            requests += request
            HeartbeatTurnDispatchResult.Queued("job-child")
        }
        val decision = coordinator.run(
            eligibility(child, WindowHeartbeatState.ExplicitlyEnabled, scenario = true),
            now,
            envelope("child-1")
        )
        assertEquals(HeartbeatDecision.Scheduled("job-child"), decision)
        assertEquals("child-1", requests.single().targetWindowId)
        assertTrue(requests.single().targetWindowId != "root-1")
    }

    @Test
    fun unreadAndCooldownAndForegroundHoldPlan() = runBlocking {
        val w = root(WindowKind.LEARNING_ROOT)
        var calls = 0
        val coordinator = WindowHeartbeatCoordinator { calls++; HeartbeatTurnDispatchResult.Queued("job-x") }
        assertEquals(HeartbeatDecision.Held, coordinator.run(eligibility(w, WindowHeartbeatState.RootEnabled, scenario = true, unread = true), now, envelope("n-LEARNING_ROOT")).also { assertTrue(calls == 0) })
        assertEquals(HeartbeatDecision.Held, coordinator.run(eligibility(w, WindowHeartbeatState.RootEnabled, scenario = true, inCooldown = true), now, envelope("n-LEARNING_ROOT")).also { assertTrue(calls == 0) })
        assertEquals(HeartbeatDecision.Held, coordinator.run(eligibility(w, WindowHeartbeatState.RootEnabled, scenario = true, generating = true), now, envelope("n-LEARNING_ROOT")).also { assertTrue(calls == 0) })
    }
}
