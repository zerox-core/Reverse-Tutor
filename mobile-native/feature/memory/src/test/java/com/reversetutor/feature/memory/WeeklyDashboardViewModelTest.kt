package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.WeeklySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyDashboardViewModelTest {
    @Test
    fun refreshReportsLoadingThenSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val snapshot = snapshot()
        val port = object : WeeklyDashboardPort {
            override suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot {
                gate.await()
                return snapshot
            }
        }
        val viewModel = WeeklyDashboardViewModel(port = port, scope = this)

        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(snapshot.summary, viewModel.uiState.value.summary)
    }

    @Test
    fun refreshErrorPreservesLocalWeeklyContent() = runTest {
        val snapshot = snapshot()
        val port = FakeWeeklyDashboardPort(
            summary = snapshot.summary,
            tasks = snapshot.tasks
        )
        val viewModel = WeeklyDashboardViewModel(port = port, scope = this)
        advanceUntilIdle()
        port.loadError = IllegalStateException("weekly read failed")

        viewModel.onAction(WeeklyDashboardUiAction.RefreshLocal)
        advanceUntilIdle()

        assertEquals("weekly read failed", viewModel.uiState.value.errorMessage)
        assertEquals(snapshot.summary, viewModel.uiState.value.summary)
        assertEquals(snapshot.tasks, viewModel.uiState.value.tasks)
    }

    @Test
    fun offlineStateRetainsLocalWeeklyContent() = runTest {
        val snapshot = snapshot()
        val viewModel = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(
                summary = snapshot.summary,
                tasks = snapshot.tasks
            ),
            scope = this
        )
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.ConnectivityChanged(isOnline = false))

        assertFalse(viewModel.uiState.value.isOnline)
        assertEquals(snapshot.summary, viewModel.uiState.value.summary)
        assertEquals(snapshot.tasks, viewModel.uiState.value.tasks)
    }

    private fun snapshot(): WeeklyDashboardSnapshot =
        WeeklyDashboardSnapshot(
            summary = WeeklySummary(
                id = "summary-1",
                spaceId = "space-1",
                weekStartEpochMillis = 1,
                weekEndEpochMillis = 2,
                summary = "Local summary"
            ),
            tasks = listOf(
                StudyPlanTask(
                    id = "task-1",
                    spaceId = "space-1",
                    title = "Review local notes"
                )
            )
        )
}
