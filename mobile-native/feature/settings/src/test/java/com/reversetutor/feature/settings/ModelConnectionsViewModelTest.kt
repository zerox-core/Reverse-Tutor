package com.reversetutor.feature.settings

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConnectionsViewModelTest {
    @Test
    fun refreshReportsLoadingThenSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val snapshot = snapshot()
        val port = object : ModelConnectionsPort {
            override suspend fun loadSnapshot(): ModelConnectionsSnapshot {
                gate.await()
                return snapshot
            }

            override suspend fun selectSessionModel(sessionId: String, modelBindingId: String) = Unit
        }
        val viewModel = viewModel(port)

        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("model-a", "model-b"), viewModel.uiState.value.bindings.map { it.id })
    }

    @Test
    fun refreshErrorPreservesExistingSnapshot() = runTest {
        val port = FakeModelConnectionsPort(
            connections = snapshot().connections,
            bindings = snapshot().bindings
        )
        val viewModel = viewModel(port)
        advanceUntilIdle()
        port.loadError = IllegalStateException("model read failed")

        viewModel.onAction(ModelConnectionsUiAction.Refresh)
        advanceUntilIdle()

        assertEquals("model read failed", viewModel.uiState.value.errorMessage)
        assertEquals(listOf("model-a", "model-b"), viewModel.uiState.value.bindings.map { it.id })
    }

    @Test
    fun modelSelectionChangesSessionScopeOnly() = runTest {
        val snapshot = snapshot()
        val port = FakeModelConnectionsPort(
            connections = snapshot.connections,
            bindings = snapshot.bindings
        )
        val viewModel = viewModel(port)
        advanceUntilIdle()

        viewModel.onAction(ModelConnectionsUiAction.SelectSessionModel("model-b"))
        advanceUntilIdle()

        assertEquals("model-b", viewModel.uiState.value.sessionModelBindingId)
        assertEquals(listOf("session-1" to "model-b"), port.sessionModelChanges)
        assertFalse(viewModel.uiState.value.isModelSwitching)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(port: ModelConnectionsPort) =
        ModelConnectionsViewModel(
            sessionId = "session-1",
            initialSessionModelBindingId = "model-a",
            port = port,
            scope = this
        )

    private fun snapshot(): ModelConnectionsSnapshot {
        val connection = ProviderConnection(
            id = "connection-1",
            spaceId = "space-1",
            name = "Local gateway",
            protocol = ModelProtocol.OpenAiCompatible
        )
        return ModelConnectionsSnapshot(
            connections = listOf(connection),
            bindings = listOf(
                ModelBinding(
                    id = "model-a",
                    spaceId = "space-1",
                    connectionId = connection.id,
                    modelId = "model-a",
                    availability = ModelAvailability.Available
                ),
                ModelBinding(
                    id = "model-b",
                    spaceId = "space-1",
                    connectionId = connection.id,
                    modelId = "model-b",
                    availability = ModelAvailability.Available
                )
            )
        )
    }
}
