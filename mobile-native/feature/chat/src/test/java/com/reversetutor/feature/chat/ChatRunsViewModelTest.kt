package com.reversetutor.feature.chat

import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRunsViewModelTest {
    @Test
    fun independentRunsRenderAndStopSeparately() {
        val port = FakeChatRunsPort(
            runs = listOf(
                run(id = "run-a", turnId = "turn-a", sequence = 1, state = TurnRunState.Running),
                run(id = "run-b", turnId = "turn-b", sequence = 2, state = TurnRunState.Waiting)
            )
        )
        val viewModel = ChatRunsViewModel(
            sessionId = "session-1",
            initialModelBindingId = "model-old",
            port = port
        )

        assertEquals(listOf("run-a", "run-b"), viewModel.uiState.value.runs.map { it.id })

        viewModel.onAction(ChatRunsUiAction.Stop("run-a"))

        assertEquals(listOf("run-a"), port.stoppedRunIds)
        assertEquals(TurnRunState.Cancelled, viewModel.uiState.value.runs.first { it.id == "run-a" }.state)
        assertEquals(TurnRunState.Waiting, viewModel.uiState.value.runs.first { it.id == "run-b" }.state)
    }

    @Test
    fun retryTargetsOnlyOneRun() {
        val port = FakeChatRunsPort(
            runs = listOf(
                run(id = "run-a", turnId = "turn-a", sequence = 1, state = TurnRunState.Failed),
                run(id = "run-b", turnId = "turn-b", sequence = 2, state = TurnRunState.Failed)
            )
        )
        val viewModel = ChatRunsViewModel(
            sessionId = "session-1",
            initialModelBindingId = "model-old",
            port = port
        )

        viewModel.onAction(ChatRunsUiAction.Retry("run-b"))

        assertEquals(listOf("run-b"), port.retriedRunIds)
        assertEquals(0, viewModel.uiState.value.runs.first { it.id == "run-a" }.attempt)
        assertEquals(1, viewModel.uiState.value.runs.first { it.id == "run-b" }.attempt)
    }

    @Test
    fun switchingSessionModelDoesNotMutateAlreadySentRuns() {
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
        val viewModel = ChatRunsViewModel(
            sessionId = "session-1",
            initialModelBindingId = "model-old",
            port = port
        )

        viewModel.onAction(ChatRunsUiAction.SwitchSessionModel("model-new"))

        assertEquals("model-new", viewModel.uiState.value.sessionModelBindingId)
        assertEquals("model-old", viewModel.uiState.value.runs.single().modelBindingId)
        assertEquals(listOf("session-1" to "model-new"), port.sessionModelChanges)
        assertTrue(port.runs.single().modelBindingId == "model-old")
    }

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
