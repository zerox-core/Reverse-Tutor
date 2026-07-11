package com.reversetutor.feature.chat

import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRunsViewModelTest {
    @Test
    fun initialLoadReportsLoadingThenSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val port = object : ChatRunsPort {
            override suspend fun loadRuns(sessionId: String): List<TurnRun> {
                gate.await()
                return listOf(run("run-a", "turn-a", 1, TurnRunState.Running))
            }

            override suspend fun stopRun(runId: String): List<TurnRun> = emptyList()
            override suspend fun retryRun(runId: String): List<TurnRun> = emptyList()
            override suspend fun switchSessionModel(sessionId: String, modelBindingId: String) = Unit
        }
        val viewModel = viewModel(port)

        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("run-a"), viewModel.uiState.value.runs.map { it.id })
    }

    @Test
    fun independentRunsStopAndRetrySeparately() = runTest {
        val port = FakeChatRunsPort(
            runs = listOf(
                run("run-a", "turn-a", 1, TurnRunState.Running),
                run("run-b", "turn-b", 2, TurnRunState.Failed)
            )
        )
        val viewModel = viewModel(port)
        advanceUntilIdle()

        viewModel.onAction(ChatRunsUiAction.Stop("run-a"))
        viewModel.onAction(ChatRunsUiAction.Retry("run-b"))
        advanceUntilIdle()

        assertEquals(listOf("run-a"), port.stoppedRunIds)
        assertEquals(listOf("run-b"), port.retriedRunIds)
        assertEquals(TurnRunState.Cancelled, viewModel.uiState.value.runs.first { it.id == "run-a" }.state)
        assertEquals(1, viewModel.uiState.value.runs.first { it.id == "run-b" }.attempt)
        assertTrue(viewModel.uiState.value.pendingRunIds.isEmpty())
    }

    @Test
    fun actionErrorPreservesIndependentRuns() = runTest {
        val runs = listOf(
            run("run-a", "turn-a", 1, TurnRunState.Running),
            run("run-b", "turn-b", 2, TurnRunState.Waiting)
        )
        val port = FakeChatRunsPort(runs = runs)
        val viewModel = viewModel(port)
        advanceUntilIdle()
        port.stopError = IllegalStateException("stop failed")

        viewModel.onAction(ChatRunsUiAction.Stop("run-a"))
        advanceUntilIdle()

        assertEquals("stop failed", viewModel.uiState.value.errorMessage)
        assertEquals(listOf("run-a", "run-b"), viewModel.uiState.value.runs.map { it.id })
        assertTrue(viewModel.uiState.value.pendingRunIds.isEmpty())
    }

    @Test
    fun switchingSessionModelDoesNotMutateAlreadySentRuns() = runTest {
        val port = FakeChatRunsPort(
            runs = listOf(
                run(
                    id = "run-a",
                    turnId = "turn-a",
                    sequence = 1,
                    state = TurnRunState.Running,
                    modelBindingId = "model-old"
                )
            )
        )
        val viewModel = viewModel(port)
        advanceUntilIdle()

        viewModel.onAction(ChatRunsUiAction.SwitchSessionModel("model-new"))
        advanceUntilIdle()

        assertEquals("model-new", viewModel.uiState.value.sessionModelBindingId)
        assertEquals("model-old", viewModel.uiState.value.runs.single().modelBindingId)
        assertEquals(listOf("session-1" to "model-new"), port.sessionModelChanges)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(port: ChatRunsPort) =
        ChatRunsViewModel(
            sessionId = "session-1",
            initialModelBindingId = "model-old",
            port = port,
            scope = this
        )

    private fun run(
        id: String,
        turnId: String,
        sequence: Long,
        state: TurnRunState,
        modelBindingId: String = "model-old"
    ): TurnRun =
        TurnRun(
            id = id,
            spaceId = "space-1",
            turnId = turnId,
            sessionId = "session-1",
            userMessageId = "message-$turnId",
            sequence = sequence,
            contextVersion = sequence,
            modelBindingId = modelBindingId,
            state = state
        )
}
