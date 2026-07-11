package com.reversetutor.feature.chat

import com.reversetutor.core.model.DomainError
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatRunItemUiState(
    val id: String,
    val turnId: String,
    val sequence: Long,
    val attempt: Int,
    val state: TurnRunState,
    val modelBindingId: String,
    val error: DomainError?
) {
    val canStop: Boolean
        get() = state == TurnRunState.Waiting || state == TurnRunState.Running

    val canRetry: Boolean
        get() = state == TurnRunState.Failed ||
            state == TurnRunState.Cancelled ||
            state == TurnRunState.Discarded
}

data class ChatRunsUiState(
    val sessionId: String,
    val sessionModelBindingId: String?,
    val runs: List<ChatRunItemUiState> = emptyList()
)

sealed interface ChatRunsUiAction {
    object Refresh : ChatRunsUiAction
    data class Stop(val runId: String) : ChatRunsUiAction
    data class Retry(val runId: String) : ChatRunsUiAction
    data class SwitchSessionModel(val modelBindingId: String) : ChatRunsUiAction
}

interface ChatRunsPort {
    fun loadRuns(sessionId: String): List<TurnRun>
    fun stopRun(runId: String): List<TurnRun>
    fun retryRun(runId: String): List<TurnRun>
    fun switchSessionModel(sessionId: String, modelBindingId: String)
}

class FakeChatRunsPort(
    runs: List<TurnRun> = emptyList()
) : ChatRunsPort {
    var runs: List<TurnRun> = runs
        private set
    val stoppedRunIds = mutableListOf<String>()
    val retriedRunIds = mutableListOf<String>()
    val sessionModelChanges = mutableListOf<Pair<String, String>>()

    override fun loadRuns(sessionId: String): List<TurnRun> =
        runs.filter { it.sessionId == sessionId }

    override fun stopRun(runId: String): List<TurnRun> {
        stoppedRunIds += runId
        runs = runs.map { run ->
            if (run.id == runId) run.copy(state = TurnRunState.Cancelled) else run
        }
        return runs
    }

    override fun retryRun(runId: String): List<TurnRun> {
        retriedRunIds += runId
        runs = runs.map { run ->
            if (run.id == runId) {
                run.copy(
                    attempt = run.attempt + 1,
                    state = TurnRunState.Waiting,
                    error = null,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    resultMessageId = null
                )
            } else {
                run
            }
        }
        return runs
    }

    override fun switchSessionModel(sessionId: String, modelBindingId: String) {
        sessionModelChanges += sessionId to modelBindingId
    }
}

class ChatRunsViewModel(
    private val sessionId: String,
    initialModelBindingId: String?,
    private val port: ChatRunsPort
) {
    private val mutableUiState = MutableStateFlow(
        ChatRunsUiState(
            sessionId = sessionId,
            sessionModelBindingId = initialModelBindingId,
            runs = port.loadRuns(sessionId).toRunItems()
        )
    )
    val uiState: StateFlow<ChatRunsUiState> = mutableUiState.asStateFlow()

    fun onAction(action: ChatRunsUiAction) {
        mutableUiState.value = when (action) {
            ChatRunsUiAction.Refresh -> mutableUiState.value.copy(
                runs = port.loadRuns(sessionId).toRunItems()
            )
            is ChatRunsUiAction.Stop -> mutableUiState.value.copy(
                runs = port.stopRun(action.runId)
                    .filter { it.sessionId == sessionId }
                    .toRunItems()
            )
            is ChatRunsUiAction.Retry -> mutableUiState.value.copy(
                runs = port.retryRun(action.runId)
                    .filter { it.sessionId == sessionId }
                    .toRunItems()
            )
            is ChatRunsUiAction.SwitchSessionModel -> {
                port.switchSessionModel(sessionId, action.modelBindingId)
                mutableUiState.value.copy(
                    sessionModelBindingId = action.modelBindingId
                )
            }
        }
    }
}

private fun List<TurnRun>.toRunItems(): List<ChatRunItemUiState> =
    sortedBy { it.sequence }.map { run ->
        ChatRunItemUiState(
            id = run.id,
            turnId = run.turnId,
            sequence = run.sequence,
            attempt = run.attempt,
            state = run.state,
            modelBindingId = run.modelBindingId,
            error = run.error
        )
    }
