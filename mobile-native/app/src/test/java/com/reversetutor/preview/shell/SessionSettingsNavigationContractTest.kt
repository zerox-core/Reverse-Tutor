package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSettingsNavigationContractTest {
    @Test
    fun sessionSettingsIndexLivesUnderTheActiveChatAndBackReturnsToChat() {
        val settings = AppNavigationState().navigate(AppDestination.SessionSettings)

        assertEquals(
            listOf(AppDestination.Sessions, AppDestination.Chat, AppDestination.SessionSettings),
            settings.backStack
        )
        assertEquals(AppDestination.Chat, settings.handleSystemBack().state.current)
    }
}
