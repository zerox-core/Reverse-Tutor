package com.reversetutor.core.domain

import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRunCoordinatorTest {
    @Test
    fun independentRunsAreImmediatelyRunnable() {
        val repository = FakeConversationRunRepository()
        val coordinator = coordinator(repository)

        val first = coordinator.createRun(command(turnId = "turn-1", userMessageId = "message-1"))
        val second = coordinator.createRun(command(turnId = "turn-2", userMessageId = "message-2"))

        assertTrue(first is RunDispatch.Ready)
        assertTrue(second is RunDispatch.Ready)
        assertEquals(TurnRunState.Running, first.run.state)
        assertEquals(TurnRunState.Running, second.run.state)
        assertEquals(listOf(1L, 2L), repository.runs.values.map { it.sequence })
    }

    @Test
    fun explicitDependentFollowUpWaitsUntilParentCompletes() {
        val repository = FakeConversationRunRepository()
        val coordinator = coordinator(repository)
        val parent = coordinator.createRun(command("turn-parent", "message-parent")).run

        val childDispatch = coordinator.createRun(
            command(
                turnId = "turn-child",
                userMessageId = "message-child",
                parentTurnId = parent.turnId
            )
        )

        assertTrue(childDispatch is RunDispatch.WaitingForDependency)
        assertEquals(TurnRunState.Waiting, childDispatch.run.state)

        val completion = coordinator.completeRun(
            runId = parent.id,
            attempt = parent.attempt,
            resultMessageId = "assistant-parent"
        )

        assertTrue(completion is RunCompletion.Accepted)
        assertEquals(TurnRunState.Running, repository.findLatestRun("turn-child")?.state)
    }

    @Test
    fun retryIncrementsAttemptAndRejectsOlderLateCompletion() {
        val repository = FakeConversationRunRepository()
        val coordinator = coordinator(repository)
        val original = coordinator.createRun(command("turn-1", "message-1")).run

        val retry = coordinator.retryRun(original.id)

        assertEquals(original.turnId, retry.turnId)
        assertEquals(1, retry.attempt)
        assertEquals(original.modelBindingId, retry.modelBindingId)
        assertEquals(original.contextSnapshotId, retry.contextSnapshotId)

        val late = coordinator.completeRun(
            runId = original.id,
            attempt = original.attempt,
            resultMessageId = "late-assistant"
        )
        val current = coordinator.completeRun(
            runId = retry.id,
            attempt = retry.attempt,
            resultMessageId = "current-assistant"
        )

        assertEquals(RunCompletion.StaleAttempt, late)
        assertTrue(current is RunCompletion.Accepted)
        assertEquals("current-assistant", repository.findLatestRun("turn-1")?.resultMessageId)
    }

    @Test
    fun deletedSessionRejectsCompletion() {
        val repository = FakeConversationRunRepository()
        val coordinator = coordinator(repository)
        val run = coordinator.createRun(command("turn-1", "message-1")).run
        repository.deletedSessions += run.sessionId

        val result = coordinator.completeRun(run.id, run.attempt, "assistant-1")

        assertEquals(RunCompletion.SessionDeleted, result)
        assertFalse(repository.findRun(run.id)?.isTerminal == true)
    }

    private fun coordinator(repository: FakeConversationRunRepository): ConversationRunCoordinator {
        var id = 0
        var now = 100L
        return ConversationRunCoordinator(
            repository = repository,
            idGenerator = { "generated-${++id}" },
            nowEpochMillis = { ++now }
        )
    }

    private fun command(
        turnId: String,
        userMessageId: String,
        parentTurnId: String? = null
    ) = CreateTurnRunCommand(
        spaceId = "space-1",
        sessionId = "session-1",
        turnId = turnId,
        userMessageId = userMessageId,
        modelBindingId = "binding-1",
        contextVersion = 1L,
        contextMessageIds = listOf("history-1", userMessageId),
        parentTurnId = parentTurnId
    )
}

private class FakeConversationRunRepository : ConversationRunRepository {
    val runs = linkedMapOf<String, TurnRun>()
    val snapshots = linkedMapOf<String, ContextSnapshot>()
    val deletedSessions = mutableSetOf<String>()
    private val sequences = mutableMapOf<String, Long>()

    override fun nextSequence(spaceId: String, sessionId: String): Long {
        val key = "$spaceId:$sessionId"
        val next = (sequences[key] ?: 0L) + 1L
        sequences[key] = next
        return next
    }

    override fun saveRun(run: TurnRun): TurnRun {
        runs[run.id] = run
        return run
    }

    override fun saveContextSnapshot(snapshot: ContextSnapshot): ContextSnapshot {
        snapshots[snapshot.id] = snapshot
        return snapshot
    }

    override fun findRun(runId: String): TurnRun? = runs[runId]

    override fun findLatestRun(turnId: String): TurnRun? =
        runs.values.filter { it.turnId == turnId }.maxByOrNull { it.attempt }

    override fun findWaitingRuns(parentTurnId: String): List<TurnRun> =
        runs.values.filter {
            it.parentTurnId == parentTurnId && it.state == TurnRunState.Waiting
        }

    override fun isSessionDeleted(sessionId: String): Boolean = sessionId in deletedSessions
}
