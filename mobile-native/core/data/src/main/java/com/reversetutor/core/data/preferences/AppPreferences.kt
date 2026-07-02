package com.reversetutor.core.data.preferences

data class AppPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val globalAvatarVisible: Boolean = true,
    val primaryMemo: String = "",
    val secondaryMemo: String = "",
    val scratchMemo: String = ""
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
