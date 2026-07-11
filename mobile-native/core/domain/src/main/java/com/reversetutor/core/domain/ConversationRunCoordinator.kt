package com.reversetutor.core.domain

import com.reversetutor.core.model.ContextSnapshot
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
    fun createRun(command: CreateTurnRunCommand): RunDispatch {
        val sequence = repository.nextSequence(command.spaceId, command.sessionId)
        val snapshot = ContextSnapshot(
            id = idGenerator(),
            spaceId = command.spaceId,
            sessionId = command.sessionId,
            turnId = command.turnId,
            version = command.contextVersion,
            messageIds = command.contextMessageIds.toList(),
            parentTurnId = command.parentTurnId,
            maxSequence = sequence,
            createdAtEpochMillis = nowEpochMillis()
        )
        repository.saveContextSnapshot(snapshot)

        val dependencyPending = command.parentTurnId
            ?.let(repository::findLatestRun)
            ?.isTerminal
            ?.not()
            ?: false
        val createdAt = nowEpochMillis()
        val state = if (dependencyPending) TurnRunState.Waiting else TurnRunState.Running
        val run = TurnRun(
            id = idGenerator(),
            spaceId = command.spaceId,
            turnId = command.turnId,
            sessionId = command.sessionId,
            userMessageId = command.userMessageId,
            sequence = sequence,
            contextVersion = command.contextVersion,
            modelBindingId = command.modelBindingId,
            parentTurnId = command.parentTurnId,
            contextSnapshotId = snapshot.id,
            state = state,
            createdAtEpochMillis = createdAt,
            startedAtEpochMillis = createdAt.takeIf { state == TurnRunState.Running }
        )
        repository.saveRun(run)

        return if (dependencyPending) {
            RunDispatch.WaitingForDependency(run, requireNotNull(command.parentTurnId))
        } else {
            RunDispatch.Ready(run)
        }
    }

    fun retryRun(runId: String): TurnRun {
        val previous = requireNotNull(repository.findRun(runId)) {
            "TurnRun not found: $runId"
        }
        val latest = repository.findLatestRun(previous.turnId)
        require(latest?.id == previous.id) {
            "Only the latest attempt can be retried"
        }
        repository.saveRun(
            previous.copy(
                state = TurnRunState.Discarded,
                completedAtEpochMillis = nowEpochMillis()
            )
        )
        val dependencyPending = previous.parentTurnId
            ?.let(repository::findLatestRun)
            ?.isTerminal
            ?.not()
            ?: false
        val now = nowEpochMillis()
        return repository.saveRun(
            previous.copy(
                id = idGenerator(),
                attempt = previous.attempt + 1,
                state = if (dependencyPending) TurnRunState.Waiting else TurnRunState.Running,
                createdAtEpochMillis = now,
                startedAtEpochMillis = now.takeUnless { dependencyPending },
                completedAtEpochMillis = null,
                resultMessageId = null,
                error = null
            )
        )
    }

    fun completeRun(
        runId: String,
        attempt: Int,
        resultMessageId: String
    ): RunCompletion {
        val run = repository.findRun(runId) ?: return RunCompletion.NotFound
        if (repository.isSessionDeleted(run.sessionId)) {
            return RunCompletion.SessionDeleted
        }
        val latest = repository.findLatestRun(run.turnId)
        if (latest?.id != run.id || latest.attempt != attempt || run.attempt != attempt) {
            return RunCompletion.StaleAttempt
        }
        if (run.isTerminal) {
            return RunCompletion.AlreadyTerminal
        }

        val completed = repository.saveRun(
            run.copy(
                state = TurnRunState.Completed,
                completedAtEpochMillis = nowEpochMillis(),
                resultMessageId = resultMessageId
            )
        )
        val released = repository.findWaitingRuns(run.turnId).mapNotNull { waiting ->
            if (repository.isSessionDeleted(waiting.sessionId)) {
                null
            } else {
                repository.saveRun(
                    waiting.copy(
                        state = TurnRunState.Running,
                        startedAtEpochMillis = nowEpochMillis()
                    )
                )
            }
        }
        return RunCompletion.Accepted(completed, released)
    }
}
