package com.reversetutor.core.domain

import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import java.util.UUID

data class CreateTurnRunCommand(
    val spaceId: String,
    val sessionId: String,
    val turnId: String,
    val userMessageId: String,
    val modelBindingId: String,
    val contextVersion: Long,
    val contextMessageIds: List<String>,
    val parentTurnId: String? = null
)

sealed interface RunDispatch {
    val run: TurnRun

    data class Ready(override val run: TurnRun) : RunDispatch

    data class WaitingForDependency(
        override val run: TurnRun,
        val parentTurnId: String
    ) : RunDispatch
}

sealed interface RunCompletion {
    data class Accepted(
        val run: TurnRun,
        val releasedRuns: List<TurnRun>
    ) : RunCompletion

    data object NotFound : RunCompletion
    data object StaleAttempt : RunCompletion
    data object SessionDeleted : RunCompletion
    data object AlreadyTerminal : RunCompletion
}

class ConversationRunCoordinator(
    private val repository: ConversationRunRepository,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun createRun(command: CreateTurnRunCommand): RunDispatch {
        val dependencyPending = isDependencyPending(command.parentTurnId)
        val createdAt = nowEpochMillis()
        val state = if (dependencyPending) TurnRunState.Waiting else TurnRunState.Running
        val run = repository.createRunWithSnapshot(
            PersistTurnRunCommand(
                runId = idGenerator(),
                snapshotId = idGenerator(),
                spaceId = command.spaceId,
                sessionId = command.sessionId,
                turnId = command.turnId,
                userMessageId = command.userMessageId,
                modelBindingId = command.modelBindingId,
                contextVersion = command.contextVersion,
                contextMessageIds = command.contextMessageIds.toList(),
                parentTurnId = command.parentTurnId,
                initialState = state,
                createdAtEpochMillis = createdAt
            )
        )

        return if (dependencyPending) {
            RunDispatch.WaitingForDependency(run, requireNotNull(command.parentTurnId))
        } else {
            RunDispatch.Ready(run)
        }
    }

    suspend fun retryRun(runId: String): TurnRun {
        val previous = requireNotNull(repository.findRun(runId)) {
            "TurnRun not found: $runId"
        }
        val dependencyPending = isDependencyPending(previous.parentTurnId)
        return requireNotNull(
            repository.retryLatestAttempt(
                PersistTurnRetryCommand(
                    runId = runId,
                    replacementRunId = idGenerator(),
                    initialState = if (dependencyPending) TurnRunState.Waiting else TurnRunState.Running,
                    createdAtEpochMillis = nowEpochMillis()
                )
            )
        ) { "Only the latest attempt can be retried" }
    }

    suspend fun completeRun(
        runId: String,
        attempt: Int,
        resultMessageId: String
    ): RunCompletion {
        return when (
            val persisted = repository.completeCurrentAttempt(
                PersistTurnCompletionCommand(
                    runId = runId,
                    attempt = attempt,
                    resultMessageId = resultMessageId,
                    completedAtEpochMillis = nowEpochMillis()
                )
            )
        ) {
            is PersistTurnCompletion.Accepted -> RunCompletion.Accepted(
                persisted.completedRun,
                persisted.releasedRuns
            )
            PersistTurnCompletion.NotFound -> RunCompletion.NotFound
            PersistTurnCompletion.StaleAttempt -> RunCompletion.StaleAttempt
            PersistTurnCompletion.SessionDeleted -> RunCompletion.SessionDeleted
            PersistTurnCompletion.AlreadyTerminal -> RunCompletion.AlreadyTerminal
        }
    }

    private suspend fun isDependencyPending(parentTurnId: String?): Boolean {
        if (parentTurnId == null) return false
        return repository.findLatestRun(parentTurnId)?.isTerminal == false
    }
}
