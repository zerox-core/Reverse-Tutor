package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1 notification precise routing: the dispatcher must resolve the background
 * job's session id and hand it to the notifier so a notification tap can
 * deep-link into the originating session.
 */
class BackgroundGenerationNotificationDispatcherTest {

    private class RecordingNotifier : BackgroundGenerationNotifier {
        val completed = mutableListOf<Pair<String, String?>>()
        val failed = mutableListOf<Pair<String, String?>>()

        override fun notifyCompleted(jobId: String, sessionId: String?) {
            completed += jobId to sessionId
        }

        override fun notifyFailed(jobId: String, sessionId: String?) {
            failed += jobId to sessionId
        }
    }

    private fun dispatcher(
        notifier: RecordingNotifier,
        notificationEnabled: suspend () -> Boolean = { true },
        permissionGranted: () -> Boolean = { true },
        resolveSessionId: suspend (String) -> String? = { null }
    ) = BackgroundGenerationNotificationDispatcher(
        notificationEnabled = notificationEnabled,
        notificationsPermissionGranted = permissionGranted,
        notifierSupplier = { notifier },
        resolveJobSessionId = resolveSessionId
    )

    @Test
    fun completedOutcomeRoutesResolvedSessionIdToNotifier() = runTest {
        val notifier = RecordingNotifier()
        val testDispatcher = dispatcher(
            notifier = notifier,
            resolveSessionId = { jobId -> if (jobId == "job-1") "session-1" else null }
        )

        testDispatcher.dispatch(
            jobId = "job-1",
            outcome = BackgroundGenerationOutcome.Completed(assistantMessageId = "m-1")
        )

        assertEquals(listOf("job-1" to "session-1"), notifier.completed)
        assertTrue(notifier.failed.isEmpty())
    }

    @Test
    fun failedOutcomeWithMissingJobRoutesNullSessionId() = runTest {
        val notifier = RecordingNotifier()
        val testDispatcher = dispatcher(
            notifier = notifier,
            resolveSessionId = { null }
        )

        testDispatcher.dispatch(
            jobId = "job-missing",
            outcome = BackgroundGenerationOutcome.Failed(message = "boom")
        )

        assertEquals(listOf("job-missing" to null), notifier.failed)
        assertTrue(notifier.completed.isEmpty())
    }

    @Test
    fun notificationDisabledSkipsNotifier() = runTest {
        val notifier = RecordingNotifier()
        val testDispatcher = dispatcher(
            notifier = notifier,
            notificationEnabled = { false }
        )

        testDispatcher.dispatch(
            jobId = "job-1",
            outcome = BackgroundGenerationOutcome.Completed(assistantMessageId = "m-1")
        )

        assertTrue(notifier.completed.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test
    fun permissionNotGrantedSkipsNotifier() = runTest {
        val notifier = RecordingNotifier()
        val testDispatcher = dispatcher(
            notifier = notifier,
            permissionGranted = { false }
        )

        testDispatcher.dispatch(
            jobId = "job-1",
            outcome = BackgroundGenerationOutcome.Completed(assistantMessageId = "m-1")
        )

        assertTrue(notifier.completed.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }
}
