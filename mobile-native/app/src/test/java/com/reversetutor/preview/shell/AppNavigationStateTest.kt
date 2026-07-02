package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun defaultDestinationIsSessions() {
        val state = AppNavigationState()

        assertEquals(AppDestination.Sessions, state.current)
        assertEquals(listOf(AppDestination.Sessions), state.backStack)
    }

    @Test
    fun chatBackReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.Chat)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
        assertEquals(listOf(AppDestination.Sessions), transition.state.backStack)
    }

    @Test
    fun contextBackReturnsToChat() {
        val state = AppNavigationState().navigate(AppDestination.ContextHub)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Chat, transition.state.current)
        assertEquals(
            listOf(AppDestination.Sessions, AppDestination.Chat),
            transition.state.backStack
        )
    }

    @Test
    fun modalBackClosesModalBeforeNavigating() {
        val state = AppNavigationState()
            .navigate(AppDestination.Chat)
            .openModal(AppModal.Status)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Chat, transition.state.current)
        assertNull(transition.state.modal)
    }

    @Test
    fun sessionsBackAllowsSystemExit() {
        val transition = AppNavigationState().handleSystemBack()

        assertEquals(BackResult.AllowSystemExit, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
    }

    @Test
    fun topLevelDestinationBackReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.Settings)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
    }

    @Test
    fun sourcesBackReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.Sources)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
    }
}
