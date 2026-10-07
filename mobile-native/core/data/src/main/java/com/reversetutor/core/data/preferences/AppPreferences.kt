package com.reversetutor.core.data.preferences

data class AppPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val globalAvatarVisible: Boolean = true,
    val challengeReminderEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val primaryMemo: String = "",
    val secondaryMemo: String = "",
    val scratchMemo: String = "",
    val backgroundGenerationNotificationEnabled: Boolean = true,
    /** NEWMP-V1-018 + R100: default on — 2026-10-04 用户拍板默认开启联网搜索。 */
    val webSearchEnabled: Boolean = true,
    /** NEWMP-V1-022: permanently on — cloud vision transcription always backs up on-device OCR. */
    val visionAssistEnabled: Boolean = true,
    /** NEWMP-V1-020: blank = use the active profile model; otherwise override the model name for vision calls. */
    val visionModelName: String = ""
) {
    companion object {
        val defaults = AppPreferences()
    }
}

enum class ThemePreference {
    System,
    Light,
    Dark,
    Focus
}

enum class MemoSlot {
    Primary,
    Secondary,
    Scratch
}
