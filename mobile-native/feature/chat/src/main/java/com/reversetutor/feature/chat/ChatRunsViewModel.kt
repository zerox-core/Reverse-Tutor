package com.reversetutor.feature.chat

import com.reversetutor.core.model.DomainError
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val runs: List<ChatRunItemUiState> = emptyList(),
    val isLoading: Boolean = false,
    val pendingRunIds: Set<String> = emptySet(),
    val isModelSwitching: Boolean = false,
    val errorMessage: String? = null
)

sealed interface ChatRunsUiAction {
    object Refresh : ChatRunsUiAction
    data class Stop(val runId: String) : ChatRunsUiAction
    data class Retry(val runId: String) : ChatRunsUiAction
    data class SwitchSessionModel(val modelBindingId: String) : ChatRunsUiAction
}

interface ChatRunsPort {
    suspend fun loadRuns(sessionId: String): List<TurnRun>
    suspend fun stopRun(runId: String): List<TurnRun>
    suspend fun retryRun(runId: String): List<TurnRun>
    suspend fun switchSessionModel(sessionId: String, modelBindingId: String)
}

fun interface ChatRunsViewModelFactory {
    fun create(
        sessionId: String,
        initialModelBindingId: String?,
        scope: CoroutineScope
    ): ChatRunsViewModel
}

class ChatRunsPortViewModelFactory(
    private val port: ChatRunsPort
) : ChatRunsViewModelFactory {
    override fun create(
        sessionId: String,
        initialModelBindingId: String?,
        scope: CoroutineScope
    ): ChatRunsViewModel =
        ChatRunsViewModel(
            sessionId = sessionId,
            initialModelBindingId = initialModelBindingId,
            port = port,
            scope = scope
        )
}

class ChatRunsViewModel(
    private val sessionId: String,
    initialModelBindingId: String?,
    private val port: ChatRunsPort,
    private val scope: CoroutineScope
) {
    private val mutableUiState = MutableStateFlow(
        ChatRunsUiState(
            sessionId = sessionId,
            sessionModelBindingId = initialModelBindingId
        )
    )
    val uiState: StateFlow<ChatRunsUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: ChatRunsUiAction) {
        when (action) {
            ChatRunsUiAction.Refresh -> refresh()
            is ChatRunsUiAction.Stop -> runOperation(action.runId) {
                port.stopRun(action.runId)
            }
            is ChatRunsUiAction.Retry -> runOperation(action.runId) {
                port.retryRun(action.runId)
            }
            is ChatRunsUiAction.SwitchSessionModel -> switchSessionModel(action.modelBindingId)
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val runs = port.loadRuns(sessionId)
                mutableUiState.update {
                    it.copy(
                        runs = runs.toRunItems(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toChatRunsErrorMessage()
                    )
                }
            }
        }
    }

    private fun runOperation(
        runId: String,
        operation: suspend () -> List<TurnRun>
    ) {
        scope.launch {
            mutableUiState.update {
                it.copy(
                    pendingRunIds = it.pendingRunIds + runId,
                    errorMessage = null
                )
            }
            try {
                val runs = operation()
                mutableUiState.update {
                    it.copy(
                        runs = runs
                            .filter { run -> run.sessionId == sessionId }
                            .toRunItems(),
                        pendingRunIds = it.pendingRunIds - runId,
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        pendingRunIds = it.pendingRunIds - runId,
                        errorMessage = error.toChatRunsErrorMessage()
                    )
                }
            }
        }
    }

    private fun switchSessionModel(modelBindingId: String) {
        scope.launch {
            mutableUiState.update {
                it.copy(isModelSwitching = true, errorMessage = null)
            }
            try {
                port.switchSessionModel(sessionId, modelBindingId)
                mutableUiState.update {
                    it.copy(
                        sessionModelBindingId = modelBindingId,
                        isModelSwitching = false,
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isModelSwitching = false,
                        errorMessage = error.toChatRunsErrorMessage()
                    )
                }
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

private fun Throwable.toChatRunsErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to update conversation runs"
