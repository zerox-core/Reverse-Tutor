package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.WeeklySummary
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyDashboardUiState(
    val summary: WeeklySummary? = null,
    val tasks: List<StudyPlanTask> = emptyList(),
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface WeeklyDashboardUiAction {
    object RefreshLocal : WeeklyDashboardUiAction
    data class ConnectivityChanged(val isOnline: Boolean) : WeeklyDashboardUiAction
}

data class WeeklyDashboardSnapshot(
    val summary: WeeklySummary?,
    val tasks: List<StudyPlanTask>
)

interface WeeklyDashboardPort {
    suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot
}

fun interface WeeklyDashboardViewModelFactory {
    fun create(scope: CoroutineScope): WeeklyDashboardViewModel
}

class WeeklyDashboardPortViewModelFactory(
    private val port: WeeklyDashboardPort,
    private val initialOnline: Boolean = true
) : WeeklyDashboardViewModelFactory {
    override fun create(scope: CoroutineScope): WeeklyDashboardViewModel =
        WeeklyDashboardViewModel(
            port = port,
            initialOnline = initialOnline,
            scope = scope
        )
}

class WeeklyDashboardViewModel(
    private val port: WeeklyDashboardPort,
    initialOnline: Boolean = true,
    private val scope: CoroutineScope
) {
    private val mutableUiState = MutableStateFlow(
        WeeklyDashboardUiState(isOnline = initialOnline)
    )
    val uiState: StateFlow<WeeklyDashboardUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: WeeklyDashboardUiAction) {
        when (action) {
            WeeklyDashboardUiAction.RefreshLocal -> refresh()
            is WeeklyDashboardUiAction.ConnectivityChanged -> mutableUiState.update {
                it.copy(isOnline = action.isOnline)
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val snapshot = port.loadLocalSnapshot()
                mutableUiState.update {
                    it.copy(
                        summary = snapshot.summary,
                        tasks = snapshot.tasks,
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
                        errorMessage = error.toWeeklyDashboardErrorMessage()
                    )
                }
            }
        }
    }
}

private fun Throwable.toWeeklyDashboardErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to load the weekly dashboard"
