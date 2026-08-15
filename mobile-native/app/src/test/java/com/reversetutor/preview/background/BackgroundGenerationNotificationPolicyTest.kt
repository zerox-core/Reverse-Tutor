package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundGenerationNotificationPolicyTest {

    private val completed = BackgroundGenerationOutcome.Completed("assistant-1")
    private val failed = BackgroundGenerationOutcome.Failed("provider error")
    private val discarded = BackgroundGenerationOutcome.Discarded("stale token")
    private val cancelled = BackgroundGenerationOutcome.Cancelled
    private val missingJob = BackgroundGenerationOutcome.MissingJob

    private val policy = BackgroundGenerationNotificationPolicy

    @Test
    fun toggleOffNeverNotifies() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(completed, notificationEnabled = false, notificationsPermissionGranted = true)
        )
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(failed, notificationEnabled = false, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun permissionDeniedNeverNotifies() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(completed, notificationEnabled = true, notificationsPermissionGranted = false)
        )
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(failed, notificationEnabled = true, notificationsPermissionGranted = false)
        )
    }

    @Test
    fun completedNotifiesWhenEnabledAndPermitted() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.Completed,
            policy.resolve(completed, notificationEnabled = true, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun failedNotifiesWhenEnabledAndPermitted() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.Failed,
            policy.resolve(failed, notificationEnabled = true, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun cancelledNeverNotifies() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(cancelled, notificationEnabled = true, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun discardedNeverNotifies() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(discarded, notificationEnabled = true, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun missingJobNeverNotifies() {
        assertEquals(
            BackgroundGenerationNotificationPolicy.NotificationKind.None,
            policy.resolve(missingJob, notificationEnabled = true, notificationsPermissionGranted = true)
        )
    }

    @Test
    fun sameJobIdProducesSameNotificationId() {
        val jobId = "generation-msg-1-token-abc"
        val first = policy.notificationIdFor(jobId)
        val second = policy.notificationIdFor(jobId)
        assertEquals(first, second)
    }

    @Test
    fun notificationIdIsNonNegative() {
        assertTrue(policy.notificationIdFor("any-job-id") >= 0)
        assertTrue(policy.notificationIdFor("") >= 0)
    }

    @Test
    fun distinctJobIdsProduceDistinctNotificationIds() {
        // Not a strict uniqueness guarantee across all strings, but these two are distinct.
        val a = policy.notificationIdFor("generation-msg-1-token-abc")
        val b = policy.notificationIdFor("generation-msg-2-token-xyz")
        assertTrue(a != b)
    }
}
