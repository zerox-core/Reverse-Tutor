package com.reversetutor.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    val preferences: Flow<AppPreferences> = dataStore.data.map { stored ->
        AppPreferences(
            theme = stored[AppPreferenceKeys.theme]?.let(::parseTheme) ?: AppPreferences.defaults.theme,
            globalAvatarVisible = stored[AppPreferenceKeys.globalAvatarVisible]
                ?: AppPreferences.defaults.globalAvatarVisible,
            challengeReminderEnabled = stored[AppPreferenceKeys.challengeReminderEnabled]
                ?: AppPreferences.defaults.challengeReminderEnabled,
            hapticFeedbackEnabled = stored[AppPreferenceKeys.hapticFeedbackEnabled]
                ?: AppPreferences.defaults.hapticFeedbackEnabled,
            primaryMemo = stored[AppPreferenceKeys.primaryMemo] ?: AppPreferences.defaults.primaryMemo,
            secondaryMemo = stored[AppPreferenceKeys.secondaryMemo]
                ?: AppPreferences.defaults.secondaryMemo,
            scratchMemo = stored[AppPreferenceKeys.scratchMemo] ?: AppPreferences.defaults.scratchMemo
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.theme] = theme.name
        }
    }

    suspend fun setGlobalAvatarVisible(visible: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.globalAvatarVisible] = visible
        }
    }

    suspend fun setChallengeReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.challengeReminderEnabled] = enabled
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.hapticFeedbackEnabled] = enabled
        }
    }

    suspend fun updateMemo(slot: MemoSlot, value: String) {
        dataStore.edit { preferences ->
            val key = when (slot) {
                MemoSlot.Primary -> AppPreferenceKeys.primaryMemo
                MemoSlot.Secondary -> AppPreferenceKeys.secondaryMemo
                MemoSlot.Scratch -> AppPreferenceKeys.scratchMemo
            }
            preferences[key] = value
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun parseTheme(raw: String): ThemePreference =
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.System
}
