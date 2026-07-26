package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.SessionHomePersistence
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DurableSessionDeletionCoordinator(
    private val persistence: SessionHomePersistence,
    private val sessionExists: suspend (String) -> Boolean,
    private val deleteSession: suspend (
        sessionId: String,
        deletedAtEpochMillis: Long,
        revision: Long,
        idempotencyKey: String
    ) -> Boolean,
    private val onConfirmedDeletion: (String) -> Unit,
    private val nowEpochMillis: () -> Long,
    private val scope: CoroutineScope,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) }
) {
    private val ownerLocks = ConcurrentHashMap<String, Mutex>()
    private val scheduledJobs = ConcurrentHashMap<String, Job>()

    suspend fun schedule(sessionId: String, dueAtEpochMillis: Long): Boolean {
        val owner = ownerLock(sessionId)
        val job = owner.withLock {
            scheduledJobs[sessionId]
                ?.takeIf { it.isActive }
                ?: scope.launch(start = CoroutineStart.LAZY) {
                    delayMillis((dueAtEpochMillis - nowEpochMillis()).coerceAtLeast(0L))
                    finalizeScheduled(sessionId)
                }.also {
                    scheduledJobs[sessionId] = it
                    it.start()
                }
        }
        job.join()
        return owner.withLock { isConfirmedDeleted(sessionId) }
    }

    suspend fun startRecovery(sessionId: String, dueAtEpochMillis: Long) {
        val owner = ownerLock(sessionId)
        owner.withLock {
            if (scheduledJobs[sessionId]?.isActive == true) return
            scheduledJobs[sessionId] = scope.launch(start = CoroutineStart.LAZY) {
                delayMillis((dueAtEpochMillis - nowEpochMillis()).coerceAtLeast(0L))
                finalizeScheduled(sessionId)
            }.also(Job::start)
        }
    }

    suspend fun finalizeDirect(sessionId: String, nowEpochMillis: Long): Boolean {
        val owner = ownerLock(sessionId)
        return owner.withLock {
            scheduledJobs.remove(sessionId)?.cancelAndJoin()
            if (isConfirmedDeleted(sessionId)) return@withLock true
            finalizeRepository(sessionId, nowEpochMillis)
        }
    }

    suspend fun cancelAndJoin(sessionId: String) {
        val owner = ownerLock(sessionId)
        owner.withLock {
            scheduledJobs.remove(sessionId)?.cancelAndJoin()
        }
    }

    private suspend fun finalizeScheduled(sessionId: String) {
        val currentJob = currentCoroutineContext()[Job]
        val owner = ownerLock(sessionId)
        try {
            owner.withLock {
                if (scheduledJobs[sessionId] !== currentJob) return
                finalizeRepository(sessionId, nowEpochMillis())
            }
        } finally {
            if (currentJob != null) scheduledJobs.remove(sessionId, currentJob)
        }
    }

    private suspend fun finalizeRepository(sessionId: String, deletedAtEpochMillis: Long): Boolean {
        val revision = persistence.pendingDeleteAt(sessionId) ?: deletedAtEpochMillis
        val deleted = deleteSession(
            sessionId,
            deletedAtEpochMillis,
            revision,
            "session-home-delete-$sessionId-$revision"
        )
        val confirmed = deleted || !sessionExists(sessionId)
        if (confirmed) {
            persistence.clearPendingDelete(sessionId)
            persistence.clearSessionMetadata(sessionId)
            onConfirmedDeletion(sessionId)
        }
        return confirmed
    }

    private suspend fun isConfirmedDeleted(sessionId: String): Boolean =
        persistence.pendingDeleteAt(sessionId) == null && !sessionExists(sessionId)

    private fun ownerLock(sessionId: String): Mutex {
        ownerLocks[sessionId]?.let { return it }
        val created = Mutex()
        return ownerLocks.putIfAbsent(sessionId, created) ?: created
    }
}
