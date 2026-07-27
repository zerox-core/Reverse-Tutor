package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.WeeklySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun planCrudCompletionAndOrderingUseRepositoryAndFeatureConfiguration() = runTest {
        val saved = mutableListOf<StudyPlanTask>()
        val store = RecordingConfigurationStore()
        val port = object : WeeklyDashboardPort {
            override suspend fun loadLocalSnapshot() = WeeklyDashboardSnapshot(
                summary = null,
                tasks = listOf(
                    StudyPlanTask("first", WeeklyDashboardSpaceId, "First", state = StudyPlanTaskState.Planned),
                    StudyPlanTask("second", WeeklyDashboardSpaceId, "Second", state = StudyPlanTaskState.Planned)
                )
            )

            override suspend fun saveTask(task: StudyPlanTask): StudyPlanTask {
                saved += task
                return task
            }
        }
        var id = 0
        val viewModel = WeeklyDashboardViewModel(
            port = port,
            configurationStore = store,
            scope = this,
            nowEpochMillis = { 100L },
            taskId = { "new-${++id}" }
        )
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.AddPlan(" New plan "))
        advanceUntilIdle()
        assertEquals("New plan", saved.last().title)

        viewModel.onAction(WeeklyDashboardUiAction.EditPlan("first", "Edited"))
        advanceUntilIdle()
        assertEquals("Edited", saved.last().title)

        viewModel.onAction(WeeklyDashboardUiAction.TogglePlanCompletion("second"))
        advanceUntilIdle()
        assertEquals(StudyPlanTaskState.Completed, saved.last().state)
        assertEquals(100L, saved.last().completedAtEpochMillis)

        viewModel.onAction(WeeklyDashboardUiAction.DeletePlan("first"))
        advanceUntilIdle()
        assertEquals(StudyPlanTaskState.Cancelled, saved.last().state)

        viewModel.onAction(WeeklyDashboardUiAction.MovePlan("new-1", "second", placeAfter = false))
        assertEquals(viewModel.uiState.value.tasks.map { it.id }, store.saved.last().planOrderIds)
    }

    @Test
    fun layoutSavePersistsButDiscardDoesNot() = runTest {
        val store = RecordingConfigurationStore()
        val viewModel = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(),
            configurationStore = store,
            scope = this
        )
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit))
        viewModel.onAction(
            WeeklyDashboardUiAction.EditLayout(
                WeeklyLayoutAction.Resize(WeeklyWidgetKind.TodayPlan.name, WeeklyWidgetSize.TwoByTwo)
            )
        )
        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Discard))
        assertTrue(store.saved.isEmpty())

        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit))
        viewModel.onAction(
            WeeklyDashboardUiAction.EditLayout(
                WeeklyLayoutAction.Resize(WeeklyWidgetKind.TodayPlan.name, WeeklyWidgetSize.TwoByTwo)
            )
        )
        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Save))
        assertEquals(WeeklyWidgetSize.TwoByTwo, store.saved.single().visibleWidgets.first().size)
        assertFalse(viewModel.uiState.value.layout.editing)
    }

    @Test
    fun failedPlanMutationRetainsExactRequestForRetry() = runTest {
        var failures = 1
        val saved = mutableListOf<StudyPlanTask>()
        val port = object : WeeklyDashboardPort {
            override suspend fun loadLocalSnapshot() = WeeklyDashboardSnapshot(null, emptyList())
            override suspend fun saveTask(task: StudyPlanTask): StudyPlanTask {
                if (failures-- > 0) error("write failed")
                saved += task
                return task
            }
        }
        val viewModel = WeeklyDashboardViewModel(
            port = port,
            scope = this,
            taskId = { "retry-task" },
            nowEpochMillis = { 50L }
        )
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.AddPlan("Retained title", "Retained detail"))
        advanceUntilIdle()
        assertEquals("retry-task", viewModel.uiState.value.pendingPlanRetry?.id)

        viewModel.onAction(WeeklyDashboardUiAction.RetryPlanMutation)
        advanceUntilIdle()
        assertEquals("Retained title", saved.single().title)
        assertNull(viewModel.uiState.value.pendingPlanRetry)
    }

    @Test
    fun failedConfigurationSaveRetainsDraftAndRetryCommitsLatestDraft() = runTest {
        val store = RecordingConfigurationStore(failSaves = 1)
        val viewModel = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(),
            configurationStore = store,
            scope = this
        )
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit))
        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(
            WeeklyLayoutAction.Resize(WeeklyWidgetKind.WeakPoints.name, WeeklyWidgetSize.OneByTwo)
        ))
        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Save))

        assertTrue(viewModel.uiState.value.layout.editing)
        assertEquals(WeeklyWidgetSize.OneByOne, viewModel.uiState.value.layout.persisted
            .visibleWidgets.first { it.kind == WeeklyWidgetKind.WeakPoints }.size)
        assertEquals(WeeklyWidgetSize.OneByTwo, viewModel.uiState.value.layout.draft
            .visibleWidgets.first { it.kind == WeeklyWidgetKind.WeakPoints }.size)
        assertTrue(viewModel.uiState.value.pendingConfigurationRetry != null)

        viewModel.onAction(WeeklyDashboardUiAction.EditLayout(
            WeeklyLayoutAction.Resize(WeeklyWidgetKind.WeakPoints.name, WeeklyWidgetSize.TwoByTwo)
        ))
        viewModel.onAction(WeeklyDashboardUiAction.RetryConfigurationSave)

        assertFalse(viewModel.uiState.value.layout.editing)
        assertEquals(WeeklyWidgetSize.TwoByTwo, viewModel.uiState.value.layout.persisted
            .visibleWidgets.first { it.kind == WeeklyWidgetKind.WeakPoints }.size)
        assertNull(viewModel.uiState.value.pendingConfigurationRetry)
    }

    @Test
    fun corruptLoadIsExplicitAndRetryRestoresSnapshotAcrossViewModelRecreation() = runTest {
        val restored = WeeklyDashboardConfiguration.defaults().copy(defaultScope = WeeklyWidgetSource.None)
        val store = RecordingConfigurationStore(
            loadResults = ArrayDeque(listOf(
                WeeklyConfigurationLoadResult.Failure("corrupt snapshot"),
                WeeklyConfigurationLoadResult.Loaded(restored)
            ))
        )
        val first = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(),
            configurationStore = store,
            scope = this
        )
        advanceUntilIdle()
        assertEquals("corrupt snapshot", first.uiState.value.configurationFailure?.message)

        first.onAction(WeeklyDashboardUiAction.RetryConfigurationLoad)
        assertNull(first.uiState.value.configurationFailure)
        assertEquals(WeeklyWidgetSource.None, first.uiState.value.layout.persisted.defaultScope)

        store.loadResults.add(WeeklyConfigurationLoadResult.Loaded(first.uiState.value.layout.persisted))
        val recreated = WeeklyDashboardViewModel(
            port = FakeWeeklyDashboardPort(),
            configurationStore = store,
            scope = this
        )
        advanceUntilIdle()
        assertEquals(WeeklyWidgetSource.None, recreated.uiState.value.layout.persisted.defaultScope)
    }

    @Test
    fun defaultScopeCommitDoesNotOverwriteExplicitWidgetSources() = runTest {
        val explicit = WeeklyDashboardConfiguration.defaults().copy(
            widgets = WeeklyDashboardConfiguration.defaults().widgets.map { widget ->
                if (widget.kind == WeeklyWidgetKind.TokenUsage) widget.copy(source = WeeklyWidgetSource.None) else widget
            }
        )
        val store = RecordingConfigurationStore(
            loadResults = ArrayDeque(listOf(WeeklyConfigurationLoadResult.Loaded(explicit)))
        )
        val viewModel = WeeklyDashboardViewModel(FakeWeeklyDashboardPort(), store, scope = this)
        advanceUntilIdle()

        viewModel.onAction(WeeklyDashboardUiAction.ChangeDefaultScope(
            WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("session-a"))
        ))

        assertEquals(WeeklySourceMode.Sessions, viewModel.uiState.value.layout.persisted.defaultScope.mode)
        assertEquals(WeeklyWidgetSource.None, viewModel.uiState.value.layout.persisted.widgets
            .first { it.kind == WeeklyWidgetKind.TokenUsage }.source)
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

    private class RecordingConfigurationStore(
        var failSaves: Int = 0,
        val loadResults: ArrayDeque<WeeklyConfigurationLoadResult> = ArrayDeque()
    ) : WeeklyDashboardConfigurationStore {
        val saved = mutableListOf<WeeklyDashboardConfiguration>()
        override fun load(): WeeklyConfigurationLoadResult =
            if (loadResults.isEmpty()) WeeklyConfigurationLoadResult.Missing else loadResults.removeFirst()

        override fun save(configuration: WeeklyDashboardConfiguration): WeeklyConfigurationSaveResult {
            if (failSaves-- > 0) return WeeklyConfigurationSaveResult.Failure("config write failed")
            saved += configuration
            return WeeklyConfigurationSaveResult.Saved
        }
    }
}
