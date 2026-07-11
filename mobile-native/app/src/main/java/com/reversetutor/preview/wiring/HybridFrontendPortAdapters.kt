package com.reversetutor.preview.wiring

import com.reversetutor.core.domain.ConversationRunCoordinator
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.model.WeeklySummary
import com.reversetutor.feature.chat.ChatRunsPort
import com.reversetutor.feature.chat.HomePort
import com.reversetutor.feature.memory.WeeklyDashboardPort
import com.reversetutor.feature.memory.WeeklyDashboardSnapshot
import com.reversetutor.feature.settings.ModelConnectionsPort
import com.reversetutor.feature.settings.ModelConnectionsSnapshot

class RepositoryHomePortAdapter(
    private val listSessions: suspend () -> List<TutorSession>
) : HomePort {
    override suspend fun loadLocalSessions(): List<TutorSession> =
        listSessions()
}

class RepositoryChatRunsPortAdapter(
    private val listRuns: suspend (String) -> List<TurnRun>,
    private val findRun: suspend (String) -> TurnRun?,
    private val saveRun: suspend (TurnRun) -> TurnRun,
    private val isWritableAttempt: suspend (String) -> Boolean,
    private val runCoordinator: ConversationRunCoordinator,
    private val setModelBinding: suspend (String, String) -> Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ChatRunsPort {
    override suspend fun loadRuns(sessionId: String): List<TurnRun> =
        listRuns(sessionId)

    override suspend fun stopRun(runId: String): List<TurnRun> {
        val run = requireNotNull(findRun(runId)) { "TurnRun not found: $runId" }
        if (!run.isTerminal) {
            check(isWritableAttempt(runId)) {
                "TurnRun is no longer writable: $runId"
            }
            saveRun(
                run.copy(
                    state = TurnRunState.Cancelled,
                    completedAtEpochMillis = nowEpochMillis()
                )
            )
        }
        return listRuns(run.sessionId)
    }

    override suspend fun retryRun(runId: String): List<TurnRun> {
        val run = requireNotNull(findRun(runId)) { "TurnRun not found: $runId" }
        runCoordinator.retryRun(runId)
        return listRuns(run.sessionId)
    }

    override suspend fun switchSessionModel(sessionId: String, modelBindingId: String) {
        check(setModelBinding(sessionId, modelBindingId)) {
            "Unable to update model binding for session: $sessionId"
        }
    }
}

class RepositoryModelConnectionsPortAdapter(
    private val listConnections: suspend () -> List<ProviderConnection>,
    private val listBindings: suspend () -> List<ModelBinding>,
    private val setModelBinding: suspend (String, String) -> Boolean
) : ModelConnectionsPort {
    override suspend fun loadSnapshot(): ModelConnectionsSnapshot =
        ModelConnectionsSnapshot(
            connections = listConnections(),
            bindings = listBindings()
        )

    override suspend fun selectSessionModel(sessionId: String, modelBindingId: String) {
        check(setModelBinding(sessionId, modelBindingId)) {
            "Unable to update model binding for session: $sessionId"
        }
    }
}

class RepositoryWeeklyDashboardPortAdapter(
    private val listSummaries: suspend () -> List<WeeklySummary>,
    private val listTasks: suspend () -> List<StudyPlanTask>
) : WeeklyDashboardPort {
    override suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot =
        WeeklyDashboardSnapshot(
            summary = listSummaries().maxWithOrNull(
                compareBy<WeeklySummary> { it.weekStartEpochMillis }
                    .thenBy { it.generatedAtEpochMillis }
            ),
            tasks = listTasks()
        )
}
