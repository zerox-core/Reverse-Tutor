package com.reversetutor.feature.chat

import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.TutorSession

class FakeHomePort(
    localSessions: List<TutorSession> = emptyList()
) : HomePort {
    var localSessions: List<TutorSession> = localSessions
    var loadError: Throwable? = null

    override suspend fun loadLocalSessions(): List<TutorSession> {
        loadError?.let { throw it }
        return localSessions.toList()
    }
}

class FakeChatRunsPort(
    runs: List<TurnRun> = emptyList()
) : ChatRunsPort {
    var runs: List<TurnRun> = runs
        private set
    var loadError: Throwable? = null
    var stopError: Throwable? = null
    var retryError: Throwable? = null
    var modelSwitchError: Throwable? = null
    val stoppedRunIds = mutableListOf<String>()
    val retriedRunIds = mutableListOf<String>()
    val sessionModelChanges = mutableListOf<Pair<String, String>>()

    override suspend fun loadRuns(sessionId: String): List<TurnRun> {
        loadError?.let { throw it }
        return runs.filter { it.sessionId == sessionId }
    }

    override suspend fun stopRun(runId: String): List<TurnRun> {
        stopError?.let { throw it }
        stoppedRunIds += runId
        runs = runs.map { run ->
            if (run.id == runId) run.copy(state = TurnRunState.Cancelled) else run
        }
        return runs
    }

    override suspend fun retryRun(runId: String): List<TurnRun> {
        retryError?.let { throw it }
        retriedRunIds += runId
        runs = runs.map { run ->
            if (run.id == runId) {
                run.copy(
                    attempt = run.attempt + 1,
                    state = TurnRunState.Waiting,
                    error = null,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    resultMessageId = null
                )
            } else {
                run
            }
        }
        return runs
    }

    override suspend fun switchSessionModel(sessionId: String, modelBindingId: String) {
        modelSwitchError?.let { throw it }
        sessionModelChanges += sessionId to modelBindingId
    }
}
