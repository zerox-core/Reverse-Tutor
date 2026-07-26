package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.SessionHomePersistence

class SharedPreferencesSessionHomePersistence(
    context: Context
) : SessionHomePersistence {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PreferenceFile,
        Context.MODE_PRIVATE
    )

    override fun hasObservedWelcomeSession(): Boolean =
        preferences.getBoolean(WelcomeObservedKey, false)

    override fun setWelcomeSessionObserved() {
        preferences.edit().putBoolean(WelcomeObservedKey, true).apply()
    }

    override fun isWelcomeDeletionSuppressed(): Boolean =
        preferences.getBoolean(WelcomeSuppressedKey, false)

    override fun setWelcomeDeletionSuppressed(suppressed: Boolean) {
        preferences.edit().putBoolean(WelcomeSuppressedKey, suppressed).apply()
    }

    override fun avatarVisible(sessionId: String): Boolean? {
        val key = AvatarPrefix + sessionId
        return if (preferences.contains(key)) preferences.getBoolean(key, true) else null
    }

    override fun setAvatarVisible(sessionId: String, visible: Boolean) {
        preferences.edit().putBoolean(AvatarPrefix + sessionId, visible).apply()
    }

    override fun pinnedAt(sessionId: String): Long? {
        val key = PinnedAtPrefix + sessionId
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    override fun setPinnedAt(sessionId: String, pinnedAtEpochMillis: Long?) {
        val editor = preferences.edit()
        if (pinnedAtEpochMillis == null) {
            editor.remove(PinnedAtPrefix + sessionId)
        } else {
            editor.putLong(PinnedAtPrefix + sessionId, pinnedAtEpochMillis)
        }
        editor.apply()
    }

    override fun clearSessionMetadata(sessionId: String) {
        preferences.edit()
            .remove(AvatarPrefix + sessionId)
            .remove(PinnedAtPrefix + sessionId)
            .apply()
    }

    override fun pendingDeleteAt(sessionId: String): Long? {
        val key = PendingDeletePrefix + sessionId
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    override fun pendingDeletes(): Map<String, Long> =
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(PendingDeletePrefix)) return@mapNotNull null
            val dueAt = value as? Long ?: return@mapNotNull null
            key.removePrefix(PendingDeletePrefix) to dueAt
        }.toMap()

    override fun setPendingDeleteAt(sessionId: String, dueAtEpochMillis: Long) {
        preferences.edit().putLong(PendingDeletePrefix + sessionId, dueAtEpochMillis).apply()
    }

    override fun clearPendingDelete(sessionId: String) {
        preferences.edit().remove(PendingDeletePrefix + sessionId).apply()
    }

    private companion object {
        const val PreferenceFile = "session_home_feature_state"
        const val WelcomeObservedKey = "welcome_observed"
        const val WelcomeSuppressedKey = "welcome_deleted"
        const val AvatarPrefix = "avatar_visible:"
        const val PinnedAtPrefix = "pinned_at:"
        const val PendingDeletePrefix = "pending_delete:"
    }
}
