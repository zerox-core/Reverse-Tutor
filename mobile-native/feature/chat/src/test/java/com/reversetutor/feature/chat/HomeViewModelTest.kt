package com.reversetutor.feature.chat

import com.reversetutor.core.model.TutorSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun refreshReportsLoadingThenSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val localSession = session("session-1")
        val port = object : HomePort {
            override suspend fun loadLocalSessions(): List<TutorSession> {
                gate.await()
                return listOf(localSession)
            }
        }
        val viewModel = HomeViewModel(port = port, scope = this)

        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(localSession), viewModel.uiState.value.sessions)
    }

    @Test
    fun refreshErrorPreservesPreviouslyLoadedLocalSessions() = runTest {
        val localSession = session("session-1")
        val port = FakeHomePort(localSessions = listOf(localSession))
        val viewModel = HomeViewModel(port = port, scope = this)
        advanceUntilIdle()
        port.loadError = IllegalStateException("local read failed")

        viewModel.onAction(HomeUiAction.Refresh)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("local read failed", viewModel.uiState.value.errorMessage)
        assertEquals(listOf(localSession), viewModel.uiState.value.sessions)
    }

    @Test
    fun offlineStateRetainsLocalSessions() = runTest {
        val localSession = session("session-1")
        val viewModel = HomeViewModel(
            port = FakeHomePort(localSessions = listOf(localSession)),
            scope = this
        )
        advanceUntilIdle()

        viewModel.onAction(HomeUiAction.ConnectivityChanged(isOnline = false))

        assertFalse(viewModel.uiState.value.isOnline)
        assertEquals(listOf(localSession), viewModel.uiState.value.sessions)
    }

    private fun session(id: String) = TutorSession(
        id = id,
        spaceId = "space-1",
        title = "Local session",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2
    )
}
