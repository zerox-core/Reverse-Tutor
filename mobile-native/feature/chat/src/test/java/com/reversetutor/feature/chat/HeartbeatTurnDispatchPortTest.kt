package com.reversetutor.feature.chat

import com.reversetutor.core.domain.InitiativePlan
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmTurnPlan
import com.reversetutor.core.llm.LlmWindowContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 1: heartbeat/initiative dispatch port contract.
 *
 * The port carries a target window + immutable envelope and never a fake user
 * message. A single dispatch yields exactly one queued result; the request
 * preserves `spaceId` and `targetWindowId` and carries no user-text placeholder.
 */
class HeartbeatTurnDispatchPortTest {

    private fun plan() = InitiativePlan(
        targetWindowId = "w-1",
        intent = "check_in",
        topologyNodes = listOf("topo-1"),
        evidenceHandles = listOf("ev-1"),
        toneConstraints = listOf("be warm"),
        expiryEpochMillis = 1_000L,
        minCooldownMillis = 30_000L,
        deliveryPolicy = "write_only"
    )

    private fun envelope(windowId: String = "w-1") = LlmAssistantTurnEnvelope(
        window = LlmWindowContext(windowId = windowId, rootId = "root-1", windowKind = "TASK_ROOT", forkRevision = 3L),
        turnPlan = LlmTurnPlan(intent = "check_in", actionType = "observe", studentRole = "companion"),
        initiativeSource = "heartbeat"
    )

    @Test
    fun singleDispatchQueuesExactlyOneJob() = runBlocking {
        var calls = 0
        val port = HeartbeatTurnDispatchPort { request ->
            calls++
            HeartbeatTurnDispatchResult.Queued("job-1")
        }
        val result = port.dispatch(request("space-a", "w-1"))
        assertEquals(HeartbeatTurnDispatchResult.Queued("job-1"), result)
        assertEquals(1, calls)
    }

    @Test
    fun requestPreservesSpaceAndTargetAndEnvelope() {
        val request = request(spaceId = "space-a", targetWindowId = "w-1")
        assertEquals("space-a", request.spaceId)
        assertEquals("w-1", request.targetWindowId)
        assertTrue(request.spaceId != request.targetWindowId)
        assertEquals("check_in", request.plan.intent)
        assertEquals("w-1", request.envelope.window.windowId)
    }

    @Test
    fun requestHasNoUserMessagePlaceholder() {
        val fields = HeartbeatTurnDispatchRequest::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("userText", ignoreCase = true) || it.contains("userMessage", ignoreCase = true) })
        assertFalse(fields.any { it.equals("message", ignoreCase = true) || it.equals("text", ignoreCase = true) })
    }

    @Test
    fun heldAndSilentMapDirectly() = runBlocking {
        val held = HeartbeatTurnDispatchPort { HeartbeatTurnDispatchResult.Held }.dispatch(request("s", "w"))
        val silent = HeartbeatTurnDispatchPort { HeartbeatTurnDispatchResult.Silent }.dispatch(request("s", "w"))
        assertEquals(HeartbeatTurnDispatchResult.Held, held)
        assertEquals(HeartbeatTurnDispatchResult.Silent, silent)
    }

    private fun request(spaceId: String, targetWindowId: String) =
        HeartbeatTurnDispatchRequest(spaceId, targetWindowId, plan(), envelope(targetWindowId))
}
