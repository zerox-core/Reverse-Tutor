package com.reversetutor.core.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun persistedPreferenceKeysDoNotStoreApiSecrets() {
        val forbidden = Regex("api|key|secret|token", RegexOption.IGNORE_CASE)

        AppPreferenceKeys.persistedNames.forEach { name ->
            assertFalse("Preference key must not store secrets: $name", forbidden.containsMatchIn(name))
        }
    }
}
