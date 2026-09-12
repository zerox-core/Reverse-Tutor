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
    /** NEWMP-V1-018: off by default — web search inflates prompt tokens. */
    val webSearchEnabled: Boolean = false,
    /** NEWMP-V1-020: off by default — cloud vision transcription bills a multimodal model per image. */
    val visionAssistEnabled: Boolean = false,
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
