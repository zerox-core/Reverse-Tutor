package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.WeeklySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WeeklyDashboardUiState(
    val summary: WeeklySummary? = null,
    val tasks: List<StudyPlanTask> = emptyList(),
    val isOnline: Boolean = true
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
    fun loadLocalSnapshot(): WeeklyDashboardSnapshot
}

class FakeWeeklyDashboardPort(
    summary: WeeklySummary? = null,
    tasks: List<StudyPlanTask> = emptyList()
) : WeeklyDashboardPort {
    var summary: WeeklySummary? = summary
    var tasks: List<StudyPlanTask> = tasks

    override fun loadLocalSnapshot(): WeeklyDashboardSnapshot =
        WeeklyDashboardSnapshot(summary = summary, tasks = tasks)
}

class WeeklyDashboardViewModel(
    private val port: WeeklyDashboardPort,
    initialOnline: Boolean = true
) {
    private val mutableUiState = MutableStateFlow(
        port.loadLocalSnapshot().toUiState(isOnline = initialOnline)
    )
    val uiState: StateFlow<WeeklyDashboardUiState> = mutableUiState.asStateFlow()

    fun onAction(action: WeeklyDashboardUiAction) {
        mutableUiState.value = when (action) {
            WeeklyDashboardUiAction.RefreshLocal -> port.loadLocalSnapshot().toUiState(
                isOnline = mutableUiState.value.isOnline
            )
            is WeeklyDashboardUiAction.ConnectivityChanged -> mutableUiState.value.copy(
                isOnline = action.isOnline
            )
        }
    }
}

private fun WeeklyDashboardSnapshot.toUiState(isOnline: Boolean): WeeklyDashboardUiState =
    WeeklyDashboardUiState(
        summary = summary,
        tasks = tasks,
        isOnline = isOnline
    )
