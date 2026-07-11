package com.reversetutor.feature.chat

import com.reversetutor.core.model.TutorSession
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val sessions: List<TutorSession> = emptyList(),
    val selectedSessionId: String? = null,
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface HomeUiAction {
    object Refresh : HomeUiAction
    data class SelectSession(val sessionId: String?) : HomeUiAction
    data class ConnectivityChanged(val isOnline: Boolean) : HomeUiAction
}

interface HomePort {
    suspend fun loadLocalSessions(): List<TutorSession>
}

fun interface HomeViewModelFactory {
    fun create(scope: CoroutineScope): HomeViewModel
}

class HomePortViewModelFactory(
    private val port: HomePort,
    private val initialOnline: Boolean = true
) : HomeViewModelFactory {
    override fun create(scope: CoroutineScope): HomeViewModel =
        HomeViewModel(port = port, initialOnline = initialOnline, scope = scope)
}

class HomeViewModel(
    private val port: HomePort,
    initialOnline: Boolean = true,
    private val scope: CoroutineScope
) {
    private val mutableUiState = MutableStateFlow(
        HomeUiState(isOnline = initialOnline)
    )
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.Refresh -> refresh()
            is HomeUiAction.SelectSession -> mutableUiState.update {
                it.copy(selectedSessionId = action.sessionId)
            }
            is HomeUiAction.ConnectivityChanged -> mutableUiState.update {
                it.copy(isOnline = action.isOnline)
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val sessions = port.loadLocalSessions()
                mutableUiState.update {
                    it.copy(
                        sessions = sessions,
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
                        errorMessage = error.toHomeErrorMessage()
                    )
                }
            }
        }
    }
}

private fun Throwable.toHomeErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to load local sessions"
