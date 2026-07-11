package com.reversetutor.feature.chat

import com.reversetutor.core.model.TutorSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val sessions: List<TutorSession> = emptyList(),
    val selectedSessionId: String? = null,
    val isOnline: Boolean = true
)

sealed interface HomeUiAction {
    object Refresh : HomeUiAction
    data class SelectSession(val sessionId: String?) : HomeUiAction
    data class ConnectivityChanged(val isOnline: Boolean) : HomeUiAction
}

interface HomePort {
    fun loadLocalSessions(): List<TutorSession>
}

class FakeHomePort(
    localSessions: List<TutorSession> = emptyList()
) : HomePort {
    var localSessions: List<TutorSession> = localSessions

    override fun loadLocalSessions(): List<TutorSession> = localSessions
}

class HomeViewModel(
    private val port: HomePort,
    initialOnline: Boolean = true
) {
    private val mutableUiState = MutableStateFlow(
        HomeUiState(
            sessions = port.loadLocalSessions(),
            isOnline = initialOnline
        )
    )
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    fun onAction(action: HomeUiAction) {
        mutableUiState.value = when (action) {
            HomeUiAction.Refresh -> mutableUiState.value.copy(
                sessions = port.loadLocalSessions()
            )
            is HomeUiAction.SelectSession -> mutableUiState.value.copy(
                selectedSessionId = action.sessionId
            )
            is HomeUiAction.ConnectivityChanged -> mutableUiState.value.copy(
                isOnline = action.isOnline
            )
        }
    }
}
