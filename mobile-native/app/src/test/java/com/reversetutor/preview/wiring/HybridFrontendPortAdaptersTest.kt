package com.reversetutor.preview.wiring

import com.reversetutor.core.domain.ConversationRunCoordinator
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.domain.PersistTurnCompletion
import com.reversetutor.core.domain.PersistTurnCompletionCommand
import com.reversetutor.core.domain.PersistTurnRetryCommand
import com.reversetutor.core.domain.PersistTurnRunCommand
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridFrontendPortAdaptersTest {
    @Test
    fun sessionModelSelectionUsesRepositoryContract() = runBlocking {
        val changes = mutableListOf<Pair<String, String>>()
        val adapter = RepositoryModelConnectionsPortAdapter(
            listConnections = { emptyList() },
            listBindings = { emptyList() },
            setModelBinding = { sessionId, bindingId ->
                changes += sessionId to bindingId
                true
            }
        )

        adapter.selectSessionModel("session-1", "binding-2")

        assertEquals(listOf("session-1" to "binding-2"), changes)
    }

    @Test
    fun stoppingOneRunDoesNotMutateAnotherRun() = runBlocking {
        val running = run("run-a", TurnRunState.Running)
        val waiting = run("run-b", TurnRunState.Waiting)
        val runs = mutableListOf(running, waiting)
        val repository = InMemoryRunRepository(runs)
        val adapter = RepositoryChatRunsPortAdapter(
            listRuns = { runs.toList() },
            findRun = repository::findRun,
            saveRun = { updated ->
                val index = runs.indexOfFirst { it.id == updated.id }
                runs[index] = updated
                updated
            },
            isWritableAttempt = { true },
            runCoordinator = ConversationRunCoordinator(repository),
            setModelBinding = { _, _ -> true },
            nowEpochMillis = { 99L }
        )

        val updated = adapter.stopRun("run-a")

        assertEquals(TurnRunState.Cancelled, updated.first { it.id == "run-a" }.state)
        assertEquals(99L, updated.first { it.id == "run-a" }.completedAtEpochMillis)
        assertEquals(TurnRunState.Waiting, updated.first { it.id == "run-b" }.state)
    }

    @Test
    fun weeklyDashboardUsesLatestLocalSummary() = runBlocking {
        val adapter = RepositoryWeeklyDashboardPortAdapter(
            listSummaries = {
                listOf(
                    com.reversetutor.core.model.WeeklySummary(
                        id = "older",
                        spaceId = "space",
                        weekStartEpochMillis = 1L
                    ),
                    com.reversetutor.core.model.WeeklySummary(
                        id = "latest",
                        spaceId = "space",
                        weekStartEpochMillis = 2L
                    )
                )
            },
            listTasks = { emptyList() }
        )

        val snapshot = adapter.loadLocalSnapshot()

        assertEquals("latest", snapshot.summary?.id)
        assertTrue(snapshot.tasks.isEmpty())
    }

    private fun run(id: String, state: TurnRunState) =
        TurnRun(
            id = id,
            spaceId = "space",
            turnId = "turn-$id",
            sessionId = "session",
            userMessageId = "message-$id",
            sequence = if (id == "run-a") 1L else 2L,
            contextVersion = 1L,
            modelBindingId = "binding",
            state = state
        )

    private class InMemoryRunRepository(
        private val runs: MutableList<TurnRun>
    ) : ConversationRunRepository {
        override suspend fun createRunWithSnapshot(command: PersistTurnRunCommand): TurnRun =
            error("Not used")

        override suspend fun findRun(runId: String): TurnRun? =
            runs.firstOrNull { it.id == runId }

        override suspend fun findLatestRun(turnId: String): TurnRun? =
            runs.filter { it.turnId == turnId }.maxByOrNull { it.attempt }

        override suspend fun isSessionDeleted(sessionId: String): Boolean = false

        override suspend fun retryLatestAttempt(command: PersistTurnRetryCommand): TurnRun? =
            error("Not used")

        override suspend fun completeCurrentAttempt(
            command: PersistTurnCompletionCommand
        ): PersistTurnCompletion = error("Not used")
    }
}
