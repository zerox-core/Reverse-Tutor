package com.reversetutor.feature.chat

import com.reversetutor.core.model.TutorSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun offlineStateRetainsLocalSessions() {
        val localSession = TutorSession(
            id = "session-1",
            spaceId = "space-1",
            title = "Local session",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2
        )
        val viewModel = HomeViewModel(
            port = FakeHomePort(localSessions = listOf(localSession))
        )

        viewModel.onAction(HomeUiAction.ConnectivityChanged(isOnline = false))

        assertFalse(viewModel.uiState.value.isOnline)
        assertEquals(listOf(localSession), viewModel.uiState.value.sessions)
    }
}
