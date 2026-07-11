package com.reversetutor.feature.settings

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProviderConnectionItemUiState(
    val id: String,
    val name: String,
    val protocol: ModelProtocol,
    val providerName: String?,
    val baseUrl: String?,
    val enabled: Boolean
)

data class ModelBindingItemUiState(
    val id: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String,
    val availability: ModelAvailability,
    val isDefault: Boolean,
    val enabled: Boolean
)

data class ModelConnectionsUiState(
    val connections: List<ProviderConnectionItemUiState> = emptyList(),
    val bindings: List<ModelBindingItemUiState> = emptyList(),
    val selectedConnectionId: String? = null,
    val sessionModelBindingId: String? = null
)

sealed interface ModelConnectionsUiAction {
    object Refresh : ModelConnectionsUiAction
    data class SelectConnection(val connectionId: String?) : ModelConnectionsUiAction
    data class SelectSessionModel(val modelBindingId: String) : ModelConnectionsUiAction
}

data class ModelConnectionsSnapshot(
    val connections: List<ProviderConnection>,
    val bindings: List<ModelBinding>
)

interface ModelConnectionsPort {
    fun loadSnapshot(): ModelConnectionsSnapshot
    fun selectSessionModel(sessionId: String, modelBindingId: String)
}

class FakeModelConnectionsPort(
    connections: List<ProviderConnection> = emptyList(),
    bindings: List<ModelBinding> = emptyList()
) : ModelConnectionsPort {
    var connections: List<ProviderConnection> = connections
    var bindings: List<ModelBinding> = bindings
    val sessionModelChanges = mutableListOf<Pair<String, String>>()

    override fun loadSnapshot(): ModelConnectionsSnapshot =
        ModelConnectionsSnapshot(connections = connections, bindings = bindings)

    override fun selectSessionModel(sessionId: String, modelBindingId: String) {
        sessionModelChanges += sessionId to modelBindingId
    }
}

class ModelConnectionsViewModel(
    private val sessionId: String?,
    initialSessionModelBindingId: String?,
    private val port: ModelConnectionsPort
) {
    private val mutableUiState = MutableStateFlow(
        port.loadSnapshot().toUiState(
            sessionModelBindingId = initialSessionModelBindingId
        )
    )
    val uiState: StateFlow<ModelConnectionsUiState> = mutableUiState.asStateFlow()

    fun onAction(action: ModelConnectionsUiAction) {
        mutableUiState.value = when (action) {
            ModelConnectionsUiAction.Refresh -> port.loadSnapshot().toUiState(
                selectedConnectionId = mutableUiState.value.selectedConnectionId,
                sessionModelBindingId = mutableUiState.value.sessionModelBindingId
            )
            is ModelConnectionsUiAction.SelectConnection -> mutableUiState.value.copy(
                selectedConnectionId = action.connectionId
            )
            is ModelConnectionsUiAction.SelectSessionModel -> {
                sessionId?.let {
                    port.selectSessionModel(it, action.modelBindingId)
                }
                mutableUiState.value.copy(
                    sessionModelBindingId = action.modelBindingId
                )
            }
        }
    }
}

private fun ModelConnectionsSnapshot.toUiState(
    selectedConnectionId: String? = null,
    sessionModelBindingId: String?
): ModelConnectionsUiState =
    ModelConnectionsUiState(
        connections = connections.map { connection ->
            ProviderConnectionItemUiState(
                id = connection.id,
                name = connection.name,
                protocol = connection.protocol,
                providerName = connection.providerName,
                baseUrl = connection.baseUrl,
                enabled = connection.enabled
            )
        },
        bindings = bindings.map { binding ->
            ModelBindingItemUiState(
                id = binding.id,
                connectionId = binding.connectionId,
                modelId = binding.modelId,
                displayName = binding.displayName,
                availability = binding.availability,
                isDefault = binding.isDefault,
                enabled = binding.enabled
            )
        },
        selectedConnectionId = selectedConnectionId,
        sessionModelBindingId = sessionModelBindingId
    )
