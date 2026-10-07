package com.reversetutor.core.data.run

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.domain.PersistTurnCompletion
import com.reversetutor.core.domain.PersistTurnCompletionCommand
import com.reversetutor.core.domain.PersistTurnRetryCommand
import com.reversetutor.core.domain.PersistTurnRunCommand
import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState

class ConversationRunRepositoryImpl(
    private val database: ReverseTutorDatabase
) : ConversationRunRepository {
    override suspend fun createRunWithSnapshot(command: PersistTurnRunCommand): TurnRun =
        database.withTransaction {
            check(
                database.sessionDao().getById(command.sessionId) != null &&
                    database.syncDao().getTombstone(SessionEntityType, command.sessionId) == null
            ) { "Session is unavailable: ${command.sessionId}" }
            val sequence = database.turnRunDao().nextSequence(command.spaceId, command.sessionId)
            val snapshot = ContextSnapshot(
                id = command.snapshotId,
                spaceId = command.spaceId,
                sessionId = command.sessionId,
                turnId = command.turnId,
                version = command.contextVersion,
                messageIds = command.contextMessageIds,
                parentTurnId = command.parentTurnId,
                maxSequence = sequence,
                createdAtEpochMillis = command.createdAtEpochMillis
            )
            val run = TurnRun(
                id = command.runId,
                spaceId = command.spaceId,
                turnId = command.turnId,
                sessionId = command.sessionId,
                userMessageId = command.userMessageId,
                sequence = sequence,
                contextVersion = command.contextVersion,
                modelBindingId = command.modelBindingId,
                parentTurnId = command.parentTurnId,
                contextSnapshotId = command.snapshotId,
                state = command.initialState,
                createdAtEpochMillis = command.createdAtEpochMillis,
                startedAtEpochMillis = command.createdAtEpochMillis.takeIf {
                    command.initialState == TurnRunState.Running
                }
            )
            database.turnRunDao().upsertSnapshot(snapshot.toEntity())
            database.turnRunDao().upsertRun(run.toEntity())
            run
        }

    override suspend fun findRun(runId: String): TurnRun? =
        database.turnRunDao().getById(runId)?.toDomain()

    suspend fun saveRun(run: TurnRun): TurnRun {
        database.turnRunDao().upsertRun(run.toEntity())
        return run
    }

    override suspend fun findLatestRun(turnId: String): TurnRun? =
        database.turnRunDao().findLatestByTurnId(turnId)?.toDomain()

    suspend fun findWaitingRuns(parentTurnId: String): List<TurnRun> =
        database.turnRunDao().findWaitingByParentTurnId(parentTurnId).map { it.toDomain() }

    override suspend fun isSessionDeleted(sessionId: String): Boolean =
        database.syncDao().getTombstone(SessionEntityType, sessionId) != null ||
            database.sessionDao().getById(sessionId) == null

    override suspend fun retryLatestAttempt(command: PersistTurnRetryCommand): TurnRun? =
        database.withTransaction {
            val previous = database.turnRunDao().getById(command.runId) ?: return@withTransaction null
            val latest = database.turnRunDao().findLatestByTurnId(previous.turnId)
                ?: return@withTransaction null
            if (latest.id != previous.id || latest.attempt != previous.attempt) {
                return@withTransaction null
            }
            if (
                database.sessionDao().getById(previous.sessionId) == null ||
                database.syncDao().getTombstone(SessionEntityType, previous.sessionId) != null
            ) {
                return@withTransaction null
            }

            database.turnRunDao().upsertRun(
                previous.copy(
                    state = TurnRunState.Discarded.name,
                    completedAtEpochMillis = command.createdAtEpochMillis
                )
            )
            val replacement = previous.copy(
                id = command.replacementRunId,
                attempt = previous.attempt + 1,
                state = command.initialState.name,
                createdAtEpochMillis = command.createdAtEpochMillis,
                startedAtEpochMillis = command.createdAtEpochMillis.takeIf {
                    command.initialState == TurnRunState.Running
                },
                completedAtEpochMillis = null,
                resultMessageId = null,
                errorCode = null,
                errorRetryable = null,
                errorSafeMessage = null,
                errorUserAction = null
            )
            database.turnRunDao().upsertRun(replacement)
            replacement.toDomain()
        }

    override suspend fun completeCurrentAttempt(
        command: PersistTurnCompletionCommand
    ): PersistTurnCompletion =
        database.withTransaction {
            val run = database.turnRunDao().getById(command.runId)
                ?: return@withTransaction PersistTurnCompletion.NotFound
            if (
                database.syncDao().getTombstone(SessionEntityType, run.sessionId) != null ||
                database.sessionDao().getById(run.sessionId) == null
            ) {
                return@withTransaction PersistTurnCompletion.SessionDeleted
            }
            val latest = database.turnRunDao().findLatestByTurnId(run.turnId)
            if (
                latest?.id != run.id ||
                latest.attempt != command.attempt ||
                run.attempt != command.attempt
            ) {
                return@withTransaction PersistTurnCompletion.StaleAttempt
            }
            val domainRun = run.toDomain()
            if (domainRun.isTerminal) {
                return@withTransaction PersistTurnCompletion.AlreadyTerminal
            }

            val completed = run.copy(
                state = TurnRunState.Completed.name,
                completedAtEpochMillis = command.completedAtEpochMillis,
                resultMessageId = command.resultMessageId
            )
            database.turnRunDao().upsertRun(completed)

            val released = database.turnRunDao().findWaitingByParentTurnId(run.turnId)
                .mapNotNull { waiting ->
                    val childLatest = database.turnRunDao().findLatestByTurnId(waiting.turnId)
                    val childDeleted =
                        database.syncDao().getTombstone(SessionEntityType, waiting.sessionId) != null ||
                            database.sessionDao().getById(waiting.sessionId) == null
                    if (childLatest?.id != waiting.id || childDeleted) {
                        null
                    } else {
                        val running = waiting.copy(
                            state = TurnRunState.Running.name,
                            startedAtEpochMillis = command.completedAtEpochMillis
                        )
                        database.turnRunDao().upsertRun(running)
                        running.toDomain()
                    }
                }
            PersistTurnCompletion.Accepted(completed.toDomain(), released)
        }

    suspend fun save(run: TurnRun, snapshot: ContextSnapshot? = null) {
        require(snapshot == null || snapshot.id == run.contextSnapshotId)
        database.withTransaction {
            snapshot?.let { database.turnRunDao().upsertSnapshot(it.toEntity()) }
            database.turnRunDao().upsertRun(run.toEntity())
        }
    }

    suspend fun get(id: String): TurnRun? =
        findRun(id)

    suspend fun listBySession(sessionId: String): List<TurnRun> =
        database.turnRunDao().listBySession(sessionId).map { it.toDomain() }

    suspend fun listActiveBySession(sessionId: String): List<TurnRun> =
        database.turnRunDao().listActiveBySession(sessionId).map { it.toDomain() }

    suspend fun getSnapshot(id: String): ContextSnapshot? =
        database.turnRunDao().getSnapshot(id)?.toDomain()

    suspend fun isWritableAttempt(runId: String): Boolean =
        database.withTransaction {
            val run = database.turnRunDao().getById(runId) ?: return@withTransaction false
            val session = database.sessionDao().getById(run.sessionId) ?: return@withTransaction false
            if (session.archived || database.syncDao().getTombstone(SessionEntityType, run.sessionId) != null) {
                return@withTransaction false
            }
            database.turnRunDao().listBySession(run.sessionId)
                .filter { it.turnId == run.turnId }
                .maxOfOrNull { it.attempt } == run.attempt
        }

    private companion object {
        const val SessionEntityType = "session"
    }
}
