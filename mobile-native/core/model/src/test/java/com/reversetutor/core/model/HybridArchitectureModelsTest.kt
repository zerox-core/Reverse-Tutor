package com.reversetutor.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridArchitectureModelsTest {
    @Test
    fun modelConnectionSeparatesProviderSecretFromModelBinding() {
        val connection = ProviderConnection(
            id = "connection-1",
            spaceId = "space-1",
            name = "OpenAI",
            protocol = ModelProtocol.OpenAiCompatible,
            secretRef = "secret-1"
        )
        val binding = ModelBinding(
            id = "binding-1",
            spaceId = "space-1",
            connectionId = connection.id,
            modelId = "gpt-example"
        )

        assertEquals(ModelProtocol.OpenAiCompatible, connection.protocol)
        assertEquals("secret-1", connection.secretRef)
        assertEquals(ModelAvailability.Untested, binding.availability)
        assertFalse(binding.isDefault)
    }

    @Test
    fun turnRunExposesStableLifecycleAndCausalityDefaults() {
        val waitingRun = TurnRun(
            id = "run-1",
            spaceId = "space-1",
            turnId = "turn-1",
            sessionId = "session-1",
            userMessageId = "message-1",
            sequence = 1L,
            contextVersion = 1L,
            modelBindingId = "binding-1"
        )
        val runningRun = waitingRun.copy(state = TurnRunState.Running)
        val completedRun = waitingRun.copy(
            state = TurnRunState.Completed,
            completedAtEpochMillis = 200L
        )

        assertEquals(TurnRunState.Waiting, waitingRun.state)
        assertEquals(0, waitingRun.attempt)
        assertNull(waitingRun.parentTurnId)
        assertFalse(runningRun.isTerminal)
        assertTrue(completedRun.isTerminal)
    }

    @Test
    fun contextSnapshotPreservesLogicalMessageOrder() {
        val snapshot = ContextSnapshot(
            id = "snapshot-1",
            spaceId = "space-1",
            sessionId = "session-1",
            turnId = "turn-1",
            version = 3L,
            messageIds = listOf("message-1", "message-2")
        )

        assertEquals(listOf("message-1", "message-2"), snapshot.messageIds)
        assertEquals(3L, snapshot.version)
    }

    @Test
    fun learningModelsHaveSafeInitialStates() {
        val plan = StudyPlanTask(
            id = "plan-1",
            spaceId = "space-1",
            title = "Review concurrency"
        )
        val summary = WeeklySummary(
            id = "summary-1",
            spaceId = "space-1",
            weekStartEpochMillis = 1_000L
        )
        val tokenUsage = TokenUsageRecord(
            id = "usage-1",
            spaceId = "space-1",
            turnId = "turn-1",
            attempt = 0
        )

        assertEquals(StudyPlanTaskState.Proposed, plan.state)
        assertFalse(summary.stale)
        assertTrue(tokenUsage.estimated)
        assertEquals(0L, tokenUsage.totalTokens)
    }

    @Test
    fun syncModelsDeclareOwnershipAndConflictResolution() {
        val localMessage = SyncEnvelope(
            id = "envelope-1",
            spaceId = "space-1",
            entityId = "message-1",
            entityType = "message",
            ownerId = "owner-1",
            deviceId = "device-1",
            revision = 1L,
            idempotencyKey = "idem-1",
            ownership = SyncOwnership.Local
        )
        val cursor = SyncCursor(
            id = "cursor-1",
            spaceId = "space-1",
            entityType = "study_plan",
            cursor = "next-page"
        )
        val conflict = SyncConflict(
            id = "conflict-1",
            spaceId = "space-1",
            entityId = "plan-1",
            entityType = "study_plan",
            localRevision = 2L,
            remoteRevision = 3L
        )

        assertEquals(SyncOwnership.Local, localMessage.ownership)
        assertEquals(SyncOperation.Upsert, localMessage.operation)
        assertEquals("next-page", cursor.cursor)
        assertEquals(SyncConflictState.Pending, conflict.state)
    }

    @Test
    fun domainErrorsExposeStableRecoveryMetadata() {
        val error = DomainError(
            code = DomainErrorCode.ProviderUnavailable,
            retryable = true,
            safeMessage = "Provider temporarily unavailable"
        )

        assertTrue(error.retryable)
        assertEquals(DomainErrorCode.ProviderUnavailable, error.code)
        assertEquals(DomainUserAction.Retry, error.userAction)
    }
}
