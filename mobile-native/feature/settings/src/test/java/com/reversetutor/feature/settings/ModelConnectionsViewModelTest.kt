package com.reversetutor.feature.settings

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConnectionsViewModelTest {
    @Test
    fun modelSelectionChangesSessionScopeOnly() {
        val port = FakeModelConnectionsPort(
            connections = listOf(
                ProviderConnection(
                    id = "connection-1",
                    spaceId = "space-1",
                    name = "Local gateway",
                    protocol = ModelProtocol.OpenAiCompatible
                )
            ),
            bindings = listOf(
                ModelBinding(
                    id = "model-a",
                    spaceId = "space-1",
                    connectionId = "connection-1",
                    modelId = "model-a",
                    availability = ModelAvailability.Available
                ),
                ModelBinding(
                    id = "model-b",
                    spaceId = "space-1",
                    connectionId = "connection-1",
                    modelId = "model-b",
                    availability = ModelAvailability.Available
                )
            )
        )
        val viewModel = ModelConnectionsViewModel(
            sessionId = "session-1",
            initialSessionModelBindingId = "model-a",
            port = port
        )

        viewModel.onAction(ModelConnectionsUiAction.SelectSessionModel("model-b"))

        assertEquals("model-b", viewModel.uiState.value.sessionModelBindingId)
        assertEquals(listOf("session-1" to "model-b"), port.sessionModelChanges)
        assertEquals(listOf("model-a", "model-b"), port.bindings.map { it.id })
    }
}
