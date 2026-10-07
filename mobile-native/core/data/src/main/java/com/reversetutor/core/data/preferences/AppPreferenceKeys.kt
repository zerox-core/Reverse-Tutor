package com.reversetutor.core.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object AppPreferenceKeys {
    val theme = stringPreferencesKey("theme")
    val globalAvatarVisible = booleanPreferencesKey("global_avatar_visible")
    val challengeReminderEnabled = booleanPreferencesKey("challenge_reminder_enabled")
    val hapticFeedbackEnabled = booleanPreferencesKey("haptic_feedback_enabled")
    val primaryMemo = stringPreferencesKey("memo_primary")
    val secondaryMemo = stringPreferencesKey("memo_secondary")
    val scratchMemo = stringPreferencesKey("memo_scratch")
    val backgroundGenerationNotificationEnabled =
        booleanPreferencesKey("background_generation_notification_enabled")
    val webSearchEnabled = booleanPreferencesKey("web_search_enabled")
    val visionAssistEnabled = booleanPreferencesKey("vision_assist_enabled")
    val visionModelName = stringPreferencesKey("vision_model_name")

    val persistedNames = listOf(
        theme.name,
        globalAvatarVisible.name,
        challengeReminderEnabled.name,
        hapticFeedbackEnabled.name,
        primaryMemo.name,
        secondaryMemo.name,
        scratchMemo.name,
        backgroundGenerationNotificationEnabled.name,
        webSearchEnabled.name,
        visionAssistEnabled.name,
        visionModelName.name
    )
}
