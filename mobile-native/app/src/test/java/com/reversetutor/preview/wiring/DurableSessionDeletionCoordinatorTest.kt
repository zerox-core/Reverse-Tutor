package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.InMemorySessionHomePersistence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableSessionDeletionCoordinatorTest {
    @Test
    fun routeWaiterCancellationDoesNotCancelOwnedFinalization() = runBlocking {
        val persistence = persistedSessionState()
        var sessionExists = true
        val delayEntered = CompletableDeferred<Unit>()
        val releaseDelay = CompletableDeferred<Unit>()
        val deletionCompleted = CompletableDeferred<Unit>()
        val deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = coordinator(
            persistence = persistence,
            deletionScope = deletionScope,
            sessionExists = { sessionExists },
            delete = {
                sessionExists = false
                deletionCompleted.complete(Unit)
                true
            },
            delay = {
                delayEntered.complete(Unit)
                releaseDelay.await()
            }
        )

        try {
            val routeWaiter = async { coordinator.schedule(SessionId, DueAt) }
            withTimeout(2_000L) { delayEntered.await() }
            routeWaiter.cancelAndJoin()
            releaseDelay.complete(Unit)
            withTimeout(2_000L) { deletionCompleted.await() }

            assertNull(persistence.pendingDeleteAt(SessionId))
            assertFalse(sessionExists)
        } finally {
            deletionScope.cancel()
        }
    }

    @Test
    fun directFinalizationCancelsAndJoinsOwnedJobBeforeSingleDelete() = runBlocking {
        val persistence = persistedSessionState()
        var sessionExists = true
        var deleteCalls = 0
        val delayEntered = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()
        val deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = coordinator(
            persistence = persistence,
            deletionScope = deletionScope,
            sessionExists = { sessionExists },
            delete = {
                deleteCalls += 1
                sessionExists = false
                true
            },
            delay = {
                delayEntered.complete(Unit)
                neverRelease.await()
            }
        )

        try {
            val scheduledWaiter = async { coordinator.schedule(SessionId, DueAt) }
            withTimeout(2_000L) { delayEntered.await() }

            assertTrue(coordinator.finalizeDirect(SessionId, DueAt))
            assertTrue(withTimeout(2_000L) { scheduledWaiter.await() })
            assertEquals(1, deleteCalls)
        } finally {
            deletionScope.cancel()
        }
    }

    @Test
    fun falseDeleteKeepsDeadlineAndMetadataForStableRetry() = runBlocking {
        val persistence = persistedSessionState()
        persistence.setAvatarVisible(OtherSessionId, false)
        persistence.setPinnedAt(OtherSessionId, OtherPinnedAt)
        var sessionExists = true
        var shouldDelete = false
        val revisions = mutableListOf<Long>()
        val confirmed = mutableListOf<String>()
        val deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = coordinator(
            persistence = persistence,
            deletionScope = deletionScope,
            sessionExists = { sessionExists },
            delete = { revision ->
                revisions += revision
                if (shouldDelete) sessionExists = false
                shouldDelete
            },
            onConfirmed = confirmed::add,
            delay = {}
        )

        try {
            assertFalse(coordinator.schedule(SessionId, DueAt))
            assertEquals(DueAt, persistence.pendingDeleteAt(SessionId))
            assertEquals(false, persistence.avatarVisible(SessionId))
            assertEquals(PinnedAt, persistence.pinnedAt(SessionId))
            assertTrue(confirmed.isEmpty())

            shouldDelete = true
            assertTrue(coordinator.finalizeDirect(SessionId, DueAt + 1_000L))
            assertEquals(listOf(DueAt, DueAt), revisions)
            assertNull(persistence.pendingDeleteAt(SessionId))
            assertNull(persistence.avatarVisible(SessionId))
            assertNull(persistence.pinnedAt(SessionId))
            assertEquals(false, persistence.avatarVisible(OtherSessionId))
            assertEquals(OtherPinnedAt, persistence.pinnedAt(OtherSessionId))
            assertEquals(listOf(SessionId), confirmed)
        } finally {
            deletionScope.cancel()
        }
    }

    @Test
    fun falseDeleteIsSuccessOnlyWhenRepositoryAbsenceConfirmsPriorDeletion() = runBlocking {
        val persistence = persistedSessionState()
        val deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = coordinator(
            persistence = persistence,
            deletionScope = deletionScope,
            sessionExists = { false },
            delete = { false },
            delay = {}
        )

        try {
            assertTrue(coordinator.finalizeDirect(SessionId, DueAt))
            assertNull(persistence.pendingDeleteAt(SessionId))
            assertNull(persistence.avatarVisible(SessionId))
            assertNull(persistence.pinnedAt(SessionId))
        } finally {
            deletionScope.cancel()
        }
    }

    private fun persistedSessionState() = InMemorySessionHomePersistence().apply {
        setPendingDeleteAt(SessionId, DueAt)
        setAvatarVisible(SessionId, false)
        setPinnedAt(SessionId, PinnedAt)
    }

    private fun coordinator(
        persistence: InMemorySessionHomePersistence,
        deletionScope: CoroutineScope,
        sessionExists: suspend () -> Boolean,
        delete: suspend (revision: Long) -> Boolean,
        onConfirmed: (String) -> Unit = {},
        delay: suspend (Long) -> Unit
    ) = DurableSessionDeletionCoordinator(
        persistence = persistence,
        sessionExists = { sessionExists() },
        deleteSession = { _, _, revision, _ -> delete(revision) },
        onConfirmedDeletion = onConfirmed,
        nowEpochMillis = { DueAt },
        scope = deletionScope,
        delayMillis = delay
    )

    private companion object {
        const val SessionId = "session"
        const val DueAt = 5_000L
        const val PinnedAt = 2_000L
        const val OtherSessionId = "other-session"
        const val OtherPinnedAt = 3_000L
    }
}
