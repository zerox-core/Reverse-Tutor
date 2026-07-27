package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.WeeklySummary
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyOperationFailure(val message: String)

enum class WeeklyConfigurationCommitKind {
    Layout,
    PlanOrder,
    DefaultScope
}

data class WeeklyPendingConfigurationCommit(
    val configuration: WeeklyDashboardConfiguration,
    val exitEditingOnSuccess: Boolean,
    val kind: WeeklyConfigurationCommitKind
)

data class WeeklyDashboardUiState(
    val summary: WeeklySummary? = null,
    val tasks: List<StudyPlanTask> = emptyList(),
    val tokenUsage: List<WeeklyTokenUsageEntry> = emptyList(),
    val layout: WeeklyLayoutEditorState = WeeklyLayoutEditorState(
        persisted = WeeklyDashboardConfiguration.defaults()
    ),
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val planMutationInProgress: Boolean = false,
    val pendingPlanRetry: StudyPlanTask? = null,
    val pendingConfigurationRetry: WeeklyPendingConfigurationCommit? = null,
    val snapshotFailure: WeeklyOperationFailure? = null,
    val planFailure: WeeklyOperationFailure? = null,
    val configurationFailure: WeeklyOperationFailure? = null
) {
    val errorMessage: String?
        get() = snapshotFailure?.message ?: planFailure?.message ?: configurationFailure?.message
}

sealed interface WeeklyDashboardUiAction {
    data object RefreshLocal : WeeklyDashboardUiAction
    data object RetryConfigurationLoad : WeeklyDashboardUiAction
    data object RetryConfigurationSave : WeeklyDashboardUiAction
    data object RetryPlanMutation : WeeklyDashboardUiAction
    data class ConnectivityChanged(val isOnline: Boolean) : WeeklyDashboardUiAction
    data class ChangeDefaultScope(val source: WeeklyWidgetSource) : WeeklyDashboardUiAction
    data class EditLayout(val action: WeeklyLayoutAction) : WeeklyDashboardUiAction
    data class AddPlan(
        val title: String,
        val detail: String? = null,
        val dueAtEpochMillis: Long? = null,
        val sourceSessionId: String? = null
    ) : WeeklyDashboardUiAction
    data class EditPlan(
        val id: String,
        val title: String,
        val detail: String? = null,
        val dueAtEpochMillis: Long? = null,
        val sourceSessionId: String? = null
    ) : WeeklyDashboardUiAction
    data class DeletePlan(val id: String) : WeeklyDashboardUiAction
    data class TogglePlanCompletion(val id: String) : WeeklyDashboardUiAction
    data class MovePlan(
        val taskId: String,
        val neighborTaskId: String,
        val placeAfter: Boolean
    ) : WeeklyDashboardUiAction
}

data class WeeklyDashboardSnapshot(
    val summary: WeeklySummary?,
    val tasks: List<StudyPlanTask>,
    val tokenUsage: List<WeeklyTokenUsageEntry> = emptyList()
)

interface WeeklyDashboardPort {
    suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot
    suspend fun saveTask(task: StudyPlanTask): StudyPlanTask = task
}

fun interface WeeklyDashboardViewModelFactory {
    fun create(scope: CoroutineScope): WeeklyDashboardViewModel
}

class WeeklyDashboardPortViewModelFactory(
    private val port: WeeklyDashboardPort,
    private val configurationStore: WeeklyDashboardConfigurationStore = EmptyWeeklyDashboardConfigurationStore,
    private val initialOnline: Boolean = true
) : WeeklyDashboardViewModelFactory {
    override fun create(scope: CoroutineScope): WeeklyDashboardViewModel =
        WeeklyDashboardViewModel(
            port = port,
            configurationStore = configurationStore,
            initialOnline = initialOnline,
            scope = scope
        )
}

class WeeklyDashboardViewModel(
    private val port: WeeklyDashboardPort,
    private val configurationStore: WeeklyDashboardConfigurationStore = EmptyWeeklyDashboardConfigurationStore,
    initialOnline: Boolean = true,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val taskId: () -> String = { "weekly-plan-${System.currentTimeMillis()}" }
) {
    private val initialLoadResult = safeLoadConfiguration()
    private val initialConfiguration = when (val result = initialLoadResult) {
        is WeeklyConfigurationLoadResult.Loaded -> result.configuration.normalized()
        WeeklyConfigurationLoadResult.Missing,
        is WeeklyConfigurationLoadResult.Failure -> WeeklyDashboardConfiguration.defaults()
    }
    private val mutableUiState = MutableStateFlow(
        WeeklyDashboardUiState(
            isOnline = initialOnline,
            layout = WeeklyLayoutEditorState(initialConfiguration),
            configurationFailure = (initialLoadResult as? WeeklyConfigurationLoadResult.Failure)
                ?.let { WeeklyOperationFailure(it.message) }
        )
    )
    val uiState: StateFlow<WeeklyDashboardUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: WeeklyDashboardUiAction) {
        when (action) {
            WeeklyDashboardUiAction.RefreshLocal -> refresh()
            WeeklyDashboardUiAction.RetryConfigurationLoad -> retryConfigurationLoad()
            WeeklyDashboardUiAction.RetryConfigurationSave -> retryConfigurationSave()
            WeeklyDashboardUiAction.RetryPlanMutation ->
                mutableUiState.value.pendingPlanRetry?.let(::persistTask)
            is WeeklyDashboardUiAction.ConnectivityChanged -> mutableUiState.update {
                it.copy(isOnline = action.isOnline)
            }
            is WeeklyDashboardUiAction.ChangeDefaultScope -> changeDefaultScope(action.source)
            is WeeklyDashboardUiAction.EditLayout -> editLayout(action.action)
            is WeeklyDashboardUiAction.AddPlan -> addPlan(action)
            is WeeklyDashboardUiAction.EditPlan -> editPlan(action)
            is WeeklyDashboardUiAction.DeletePlan -> mutatePlan(action.id) { task, now ->
                task.copy(
                    state = StudyPlanTaskState.Cancelled,
                    completedAtEpochMillis = null,
                    revision = task.revision + 1L,
                    updatedAtEpochMillis = now
                )
            }
            is WeeklyDashboardUiAction.TogglePlanCompletion -> mutatePlan(action.id) { task, now ->
                val completed = task.state != StudyPlanTaskState.Completed
                task.copy(
                    state = if (completed) StudyPlanTaskState.Completed else StudyPlanTaskState.Planned,
                    completedAtEpochMillis = if (completed) now else null,
                    revision = task.revision + 1L,
                    updatedAtEpochMillis = now
                )
            }
            is WeeklyDashboardUiAction.MovePlan -> movePlan(action)
        }
    }

    private fun editLayout(action: WeeklyLayoutAction) {
        val reduction = WeeklyLayoutReducer.reduce(mutableUiState.value.layout, action)
        mutableUiState.update { state -> state.copy(layout = reduction.state) }
        reduction.configurationToPersist?.let { candidate ->
            persistConfiguration(
                WeeklyPendingConfigurationCommit(
                    candidate,
                    reduction.exitEditingOnSuccess,
                    WeeklyConfigurationCommitKind.Layout
                )
            )
        }
    }

    private fun changeDefaultScope(source: WeeklyWidgetSource) {
        val current = mutableUiState.value.layout.persisted
        val candidate = current.copy(defaultScope = source.normalized()).normalized()
        persistConfiguration(
            WeeklyPendingConfigurationCommit(
                candidate,
                exitEditingOnSuccess = false,
                kind = WeeklyConfigurationCommitKind.DefaultScope
            )
        )
    }

    private fun addPlan(action: WeeklyDashboardUiAction.AddPlan) {
        val title = action.title.trim()
        if (title.isEmpty()) return
        val now = nowEpochMillis()
        persistTask(
            StudyPlanTask(
                id = taskId(),
                spaceId = WeeklyDashboardSpaceId,
                title = title,
                detail = action.detail?.trim()?.takeIf(String::isNotEmpty),
                state = StudyPlanTaskState.Planned,
                dueAtEpochMillis = action.dueAtEpochMillis,
                sourceSessionId = action.sourceSessionId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }

    private fun editPlan(action: WeeklyDashboardUiAction.EditPlan) {
        val title = action.title.trim()
        if (title.isEmpty()) return
        mutatePlan(action.id) { task, now ->
            task.copy(
                title = title,
                detail = action.detail?.trim()?.takeIf(String::isNotEmpty),
                dueAtEpochMillis = action.dueAtEpochMillis,
                sourceSessionId = action.sourceSessionId,
                revision = task.revision + 1L,
                updatedAtEpochMillis = now
            )
        }
    }

    private fun mutatePlan(
        id: String,
        transform: (StudyPlanTask, Long) -> StudyPlanTask
    ) {
        val task = mutableUiState.value.tasks.firstOrNull { it.id == id } ?: return
        persistTask(transform(task, nowEpochMillis()))
    }

    private fun persistTask(task: StudyPlanTask) {
        scope.launch {
            mutableUiState.update {
                it.copy(
                    planMutationInProgress = true,
                    pendingPlanRetry = null,
                    planFailure = null
                )
            }
            runCatching { port.saveTask(task) }
                .onSuccess { saved ->
                    mutableUiState.update { state ->
                        val tasks = (state.tasks.filterNot { it.id == saved.id } + saved)
                            .orderedForToday(state.layout.persisted)
                        state.copy(
                            tasks = tasks,
                            planMutationInProgress = false,
                            pendingPlanRetry = null,
                            planFailure = null
                        )
                    }
                }
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            planMutationInProgress = false,
                            pendingPlanRetry = task,
                            planFailure = WeeklyOperationFailure(error.toWeeklyDashboardErrorMessage())
                        )
                    }
                }
        }
    }

    private fun movePlan(action: WeeklyDashboardUiAction.MovePlan) {
        val state = mutableUiState.value
        val candidate = reorderStudyPlanByNeighbor(
            tasks = state.tasks,
            configuration = state.layout.persisted,
            taskId = action.taskId,
            neighborTaskId = action.neighborTaskId,
            placeAfter = action.placeAfter
        )
        if (candidate == state.layout.persisted) return
        persistConfiguration(
            WeeklyPendingConfigurationCommit(
                candidate,
                exitEditingOnSuccess = false,
                kind = WeeklyConfigurationCommitKind.PlanOrder
            )
        )
    }

    private fun persistConfiguration(pending: WeeklyPendingConfigurationCommit) {
        val result = safeSaveConfiguration(pending.configuration)
        when (result) {
            WeeklyConfigurationSaveResult.Saved -> applyConfigurationSuccess(pending)
            is WeeklyConfigurationSaveResult.Failure -> {
                val currentLayout = mutableUiState.value.layout
                val failedLayout = if (pending.exitEditingOnSuccess) {
                    WeeklyLayoutReducer.reduce(currentLayout, WeeklyLayoutAction.CommitFailed).state
                } else {
                    currentLayout
                }
                mutableUiState.update {
                    it.copy(
                        layout = failedLayout,
                        pendingConfigurationRetry = pending,
                        configurationFailure = WeeklyOperationFailure(result.message)
                    )
                }
            }
        }
    }

    private fun applyConfigurationSuccess(pending: WeeklyPendingConfigurationCommit) {
        mutableUiState.update { state ->
            val current = state.layout
            val nextLayout = if (pending.exitEditingOnSuccess) {
                WeeklyLayoutReducer.reduce(
                    current,
                    WeeklyLayoutAction.CommitSucceeded(pending.configuration)
                ).state
            } else {
                current.copy(
                    persisted = pending.configuration,
                    draft = if (current.editing) current.draft else pending.configuration,
                    saveInProgress = false
                )
            }
            state.copy(
                tasks = state.tasks.orderedForToday(pending.configuration),
                layout = nextLayout,
                pendingConfigurationRetry = null,
                configurationFailure = null
            )
        }
    }

    private fun retryConfigurationSave() {
        val state = mutableUiState.value
        state.pendingConfigurationRetry?.let { pending ->
            val latest = if (pending.kind == WeeklyConfigurationCommitKind.Layout && state.layout.editing) {
                pending.copy(configuration = state.layout.draft.normalized())
            } else {
                pending
            }
            persistConfiguration(latest)
        }
    }

    private fun retryConfigurationLoad() {
        when (val result = safeLoadConfiguration()) {
            is WeeklyConfigurationLoadResult.Loaded -> mutableUiState.update { state ->
                val loaded = result.configuration.normalized()
                state.copy(
                    tasks = state.tasks.orderedForToday(loaded),
                    layout = WeeklyLayoutEditorState(loaded),
                    configurationFailure = null,
                    pendingConfigurationRetry = null
                )
            }
            WeeklyConfigurationLoadResult.Missing -> mutableUiState.update {
                it.copy(configurationFailure = null, pendingConfigurationRetry = null)
            }
            is WeeklyConfigurationLoadResult.Failure -> mutableUiState.update {
                it.copy(configurationFailure = WeeklyOperationFailure(result.message))
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, snapshotFailure = null) }
            try {
                val snapshot = port.loadLocalSnapshot()
                mutableUiState.update {
                    it.copy(
                        summary = snapshot.summary,
                        tasks = snapshot.tasks.orderedForToday(it.layout.persisted),
                        tokenUsage = snapshot.tokenUsage,
                        isLoading = false,
                        snapshotFailure = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        snapshotFailure = WeeklyOperationFailure(error.toWeeklyDashboardErrorMessage())
                    )
                }
            }
        }
    }

    private fun safeLoadConfiguration(): WeeklyConfigurationLoadResult =
        runCatching { configurationStore.load() }.getOrElse { error ->
            WeeklyConfigurationLoadResult.Failure(error.toWeeklyDashboardErrorMessage())
        }

    private fun safeSaveConfiguration(
        configuration: WeeklyDashboardConfiguration
    ): WeeklyConfigurationSaveResult =
        runCatching { configurationStore.save(configuration.normalized()) }.getOrElse { error ->
            WeeklyConfigurationSaveResult.Failure(error.toWeeklyDashboardErrorMessage())
        }
}

private fun Throwable.toWeeklyDashboardErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "本地操作失败"

private object EmptyWeeklyDashboardConfigurationStore : WeeklyDashboardConfigurationStore {
    override fun load(): WeeklyConfigurationLoadResult = WeeklyConfigurationLoadResult.Missing
    override fun save(configuration: WeeklyDashboardConfiguration): WeeklyConfigurationSaveResult =
        WeeklyConfigurationSaveResult.Saved
}

const val WeeklyDashboardSpaceId = "default-space"
