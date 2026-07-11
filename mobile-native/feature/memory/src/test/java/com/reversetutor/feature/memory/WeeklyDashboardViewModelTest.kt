package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.WeeklySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeeklyDashboardViewModelTest {
    @Test
    fun offlineStateRetainsLocalWeeklyContent() {
        val summary = WeeklySummary(
            id = "summary-1",
            spaceId = "space-1",
            weekStartEpochMillis = 1,
            weekEndEpochMillis = 2,
            summary = "Local summary"
        )
        val task = StudyPlanTask(
            id = "task-1",
            spaceId = "space-1",
            title = "Review local notes"
        )
        val viewModel = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(
                summary = summary,
                tasks = listOf(task)
            )
        )

        viewModel.onAction(WeeklyDashboardUiAction.ConnectivityChanged(isOnline = false))

        assertFalse(viewModel.uiState.value.isOnline)
        assertEquals(summary, viewModel.uiState.value.summary)
        assertEquals(listOf(task), viewModel.uiState.value.tasks)
    }
}
