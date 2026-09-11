package com.reversetutor.core.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesPolicyTest {
    @Test
    fun defaultsCoverThemeAvatarAndMemoPlaceholders() {
        val defaults = AppPreferences.defaults

        assertEquals(ThemePreference.System, defaults.theme)
        assertEquals(true, defaults.globalAvatarVisible)
        assertEquals("", defaults.primaryMemo)
        assertEquals("", defaults.secondaryMemo)
        assertEquals("", defaults.scratchMemo)
        assertEquals(true, defaults.challengeReminderEnabled)
        assertEquals(true, defaults.hapticFeedbackEnabled)
        // NEWMP-V1-018: web search defaults to off to protect token budgets.
        assertEquals(false, defaults.webSearchEnabled)
    }

    @Test
    fun persistedPreferenceKeysDoNotStoreApiSecrets() {
        val forbidden = Regex("api|key|secret|token", RegexOption.IGNORE_CASE)

        AppPreferenceKeys.persistedNames.forEach { name ->
            assertFalse("Preference key must not store secrets: $name", forbidden.containsMatchIn(name))
        }
    }

    @Test
    fun settingsToggleKeysArePartOfThePersistedPreferenceContract() {
        assertTrue(AppPreferenceKeys.challengeReminderEnabled.name in AppPreferenceKeys.persistedNames)
        assertTrue(AppPreferenceKeys.hapticFeedbackEnabled.name in AppPreferenceKeys.persistedNames)
        assertTrue(AppPreferenceKeys.webSearchEnabled.name in AppPreferenceKeys.persistedNames)
    }
}
