package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun chatQueryAndSourceManagementBackReturnToChat() {
        listOf(
            AppDestination.ChatReferences,
            AppDestination.SessionSettingsSources
        ).forEach { destination ->
            val state = AppNavigationState().navigate(destination)

            assertEquals(AppDestination.Chat, state.handleSystemBack().state.current)
        }
    }
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
    fun brainBackReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.ContextHub)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
        assertEquals(listOf(AppDestination.Sessions), transition.state.backStack)
    }

    @Test
    fun weeklyDashboardBackReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.WeeklyDashboard)

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Sessions, transition.state.current)
        assertEquals(listOf(AppDestination.Sessions), transition.state.backStack)
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
    fun drawerBackClosesDrawerBeforeNavigating() {
        val state = AppNavigationState()
            .navigate(AppDestination.Settings)
            .openDrawer()

        val transition = state.handleSystemBack()

        assertEquals(BackResult.Consumed, transition.result)
        assertEquals(AppDestination.Settings, transition.state.current)
        assertFalse(transition.state.drawerOpen)
    }

    @Test
    fun navigatingFromDrawerClosesDrawer() {
        val state = AppNavigationState()
            .openDrawer()
            .navigate(AppDestination.Sources)

        assertEquals(AppDestination.Sources, state.current)
        assertFalse(state.drawerOpen)
    }

    @Test
    fun drawerItemsExcludeBusinessActionsAndDetailRoutes() {
        assertEquals(
            listOf("会话", "社区", "设置"),
            AppDestination.drawerItems.map { it.label }
        )
        assertFalse(AppDestination.drawerItems.any { it.destination == AppDestination.ContextHub })
        assertTrue(AppDestination.drawerItems.first { it.label == "社区" }.enabled)
        assertTrue(AppDestination.drawerItems.none { it.destination == AppDestination.Chat })
        assertTrue(AppDestination.drawerItems.none { it.destination == AppDestination.Challenge })
        assertTrue(AppDestination.drawerItems.none { it.destination == AppDestination.NewSession })
        assertTrue(AppDestination.drawerItems.none { it.destination == AppDestination.ImportExport })
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

    @Test
    fun challengeAndNewSessionBackReturnToSessions() {
        val challenge = AppNavigationState().navigate(AppDestination.Challenge).handleSystemBack()
        val newSession = AppNavigationState().navigate(AppDestination.NewSession).handleSystemBack()

        assertEquals(BackResult.Consumed, challenge.result)
        assertEquals(AppDestination.Sessions, challenge.state.current)
        assertEquals(BackResult.Consumed, newSession.result)
        assertEquals(AppDestination.Sessions, newSession.state.current)
    }

    @Test
    fun searchAndPublicArticleBackReturnToSessions() {
        val destinations = listOf(AppDestination.GlobalSearch, AppDestination.PublicArticle)

        destinations.forEach { destination ->
            val transition = AppNavigationState().navigate(destination).handleSystemBack()

            assertEquals(BackResult.Consumed, transition.result)
            assertEquals(AppDestination.Sessions, transition.state.current)
        }
    }

    @Test
    fun systemDataRoutesBackReturnToSettings() {
        val destinations = listOf(
            AppDestination.ImportExport,
            AppDestination.About,
            AppDestination.TokenUsage,
            AppDestination.Update
        )

        destinations.forEach { destination ->
            val transition = AppNavigationState().navigate(destination).handleSystemBack()

            assertEquals(BackResult.Consumed, transition.result)
            assertEquals(AppDestination.Settings, transition.state.current)
        }
    }

    @Test
    fun spatialWorkspacePagesBackReturnToHome() {
        val destinations = listOf(
            AppDestination.WeeklyDashboard,
            AppDestination.GlobalGraph,
            AppDestination.Community,
            AppDestination.Settings
        )

        destinations.forEach { destination ->
            val transition = AppNavigationState().navigate(destination).handleSystemBack()

            assertEquals(BackResult.Consumed, transition.result)
            assertEquals(AppDestination.Sessions, transition.state.current)
        }
    }

    @Test
    fun challengeUsesTheHomeWorkspaceSlot() {
        assertEquals(WorkspacePage.SessionHome, AppDestination.Challenge.workspacePage)
        assertEquals(WorkspacePage.Settings, AppDestination.Settings.workspacePage)
        assertEquals(WorkspaceVerticalPage.Challenge, AppDestination.Challenge.workspaceVerticalPage)
        assertEquals(WorkspaceVerticalPage.SessionHome, AppDestination.Sessions.workspaceVerticalPage)
    }

    @Test
    fun backFromChallengeReturnsToSessions() {
        val state = AppNavigationState().navigate(AppDestination.Challenge)

        val result = state.handleSystemBack()

        assertEquals(AppDestination.Sessions, result.state.current)
    }

    @Test
    fun sessionSettingsBackReturnsToChat() {
        val destinations = listOf(
            AppDestination.SessionSettingsLibrary,
            AppDestination.SessionSettingsGraph,
            AppDestination.SessionSettingsPersona,
            AppDestination.SessionSettingsPersonalization
        )

        destinations.forEach { destination ->
            val state = AppNavigationState().navigate(destination)
            val transition = state.handleSystemBack()

            assertEquals(BackResult.Consumed, transition.result)
            assertEquals(AppDestination.Chat, transition.state.current)
        }
    }
}
