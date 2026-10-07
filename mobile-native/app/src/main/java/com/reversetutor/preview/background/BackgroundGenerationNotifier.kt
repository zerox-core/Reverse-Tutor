package com.reversetutor.preview.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reversetutor.preview.MainActivity
import com.reversetutor.preview.R

/**
 * Posts user-facing notifications for background generation outcomes.
 *
 * Notification copy is generic by design: it never includes user message text,
 * session titles, model names, provider identifiers, URLs, error originals,
 * API keys or any secret material.
 */
interface BackgroundGenerationNotifier {
    fun notifyCompleted(jobId: String, sessionId: String? = null)
    fun notifyFailed(jobId: String, sessionId: String? = null)
}

class AndroidBackgroundGenerationNotifier(
    private val appContext: Context
) : BackgroundGenerationNotifier {

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "后台生成任务完成或失败时的提醒"
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    override fun notifyCompleted(jobId: String, sessionId: String?) {
        post(
            jobId = jobId,
            sessionId = sessionId,
            title = "后台生成完成",
            text = "一个后台生成任务已完成，可在应用中查看结果。"
        )
    }

    override fun notifyFailed(jobId: String, sessionId: String?) {
        post(
            jobId = jobId,
            sessionId = sessionId,
            title = "后台生成失败",
            text = "一个后台生成任务未能完成，请稍后重试。"
        )
    }

    private fun post(jobId: String, sessionId: String?, title: String, text: String) {
        val notificationId = BackgroundGenerationNotificationPolicy.notificationIdFor(jobId)
        val tapIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (sessionId != null) {
                    putExtra(EXTRA_OPEN_SESSION_ID, sessionId)
                }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "background_generation"
        const val CHANNEL_NAME = "后台生成"
        const val EXTRA_OPEN_SESSION_ID = "com.reversetutor.preview.extra.OPEN_SESSION_ID"
    }
}
