package com.reversetutor.preview.background

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundGenerationNotificationDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Before
    fun clearNotifications() {
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel(AndroidBackgroundGenerationNotifier.CHANNEL_ID)
    }

    @After
    fun cleanUpNotifications() {
        notificationManager.cancelAll()
    }

    @Test
    fun notifierPostsCompletedAndFailedNotificationsWithSafeCopy() {
        assertTrue(
            "POST_NOTIFICATIONS must be granted before this deterministic device test",
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )

        val notifier = AndroidBackgroundGenerationNotifier(context)
        notifier.notifyCompleted("device-completed-job")
        notifier.notifyFailed("device-failed-job")

        val completed = awaitNotification("device-completed-job")
        val failed = awaitNotification("device-failed-job")

        assertNotNull(completed)
        assertNotNull(failed)
        assertEquals(AndroidBackgroundGenerationNotifier.CHANNEL_ID, completed?.notification?.channelId)
        assertEquals(AndroidBackgroundGenerationNotifier.CHANNEL_ID, failed?.notification?.channelId)

        val completedText = notificationText(completed?.notification)
        val failedText = notificationText(failed?.notification)
        assertTrue(completedText.contains("后台生成完成"))
        assertTrue(failedText.contains("后台生成失败"))
        listOf(completedText, failedText).forEach { text ->
            assertTrue(text.contains("一个后台生成任务"))
            assertTrue(!text.contains("Authorization", ignoreCase = true))
            assertTrue(!text.contains("Bearer", ignoreCase = true))
            assertTrue(!text.contains("sk-", ignoreCase = true))
            assertTrue(!text.contains("https://", ignoreCase = true))
        }
    }

    private fun notificationText(notification: android.app.Notification?): String {
        assertNotNull(notification)
        val extras = requireNotNull(notification).extras
        return listOf(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
        ).filterNotNull().joinToString(" ")
    }

    private fun awaitNotification(jobId: String): StatusBarNotification? {
        val notificationId = BackgroundGenerationNotificationPolicy.notificationIdFor(jobId)
        repeat(20) {
            notificationManager.activeNotifications.firstOrNull { it.id == notificationId }?.let {
                return it
            }
            Thread.sleep(50)
        }
        return null
    }
}
