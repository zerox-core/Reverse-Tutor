package com.reversetutor.feature.settings

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val sessionModelBindingId: String? = null,
    val isLoading: Boolean = false,
    val isModelSwitching: Boolean = false,
    val errorMessage: String? = null
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
    suspend fun loadSnapshot(): ModelConnectionsSnapshot
    suspend fun selectSessionModel(sessionId: String, modelBindingId: String)
}

class FakeModelConnectionsPort(
    connections: List<ProviderConnection> = emptyList(),
    bindings: List<ModelBinding> = emptyList()
) : ModelConnectionsPort {
    var connections: List<ProviderConnection> = connections
    var bindings: List<ModelBinding> = bindings
    var loadError: Throwable? = null
    var modelSwitchError: Throwable? = null
    val sessionModelChanges = mutableListOf<Pair<String, String>>()

    override suspend fun loadSnapshot(): ModelConnectionsSnapshot {
        loadError?.let { throw it }
        return ModelConnectionsSnapshot(
            connections = connections.toList(),
            bindings = bindings.toList()
        )
    }

    override suspend fun selectSessionModel(sessionId: String, modelBindingId: String) {
        modelSwitchError?.let { throw it }
        sessionModelChanges += sessionId to modelBindingId
    }
}

fun interface ModelConnectionsViewModelFactory {
    fun create(
        sessionId: String?,
        initialSessionModelBindingId: String?,
        scope: CoroutineScope
    ): ModelConnectionsViewModel
}

class ModelConnectionsPortViewModelFactory(
    private val port: ModelConnectionsPort
) : ModelConnectionsViewModelFactory {
    override fun create(
        sessionId: String?,
        initialSessionModelBindingId: String?,
        scope: CoroutineScope
    ): ModelConnectionsViewModel =
        ModelConnectionsViewModel(
            sessionId = sessionId,
            initialSessionModelBindingId = initialSessionModelBindingId,
            port = port,
            scope = scope
        )
}

class ModelConnectionsViewModel(
    private val sessionId: String?,
    initialSessionModelBindingId: String?,
    private val port: ModelConnectionsPort,
    private val scope: CoroutineScope = defaultModelConnectionsScope()
) {
    private val mutableUiState = MutableStateFlow(
        ModelConnectionsUiState(
            sessionModelBindingId = initialSessionModelBindingId
        )
    )
    val uiState: StateFlow<ModelConnectionsUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: ModelConnectionsUiAction) {
        when (action) {
            ModelConnectionsUiAction.Refresh -> refresh()
            is ModelConnectionsUiAction.SelectConnection -> mutableUiState.update {
                it.copy(selectedConnectionId = action.connectionId)
            }
            is ModelConnectionsUiAction.SelectSessionModel -> selectSessionModel(action.modelBindingId)
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val snapshot = port.loadSnapshot()
                mutableUiState.update { current ->
                    snapshot.toUiState(
                        selectedConnectionId = current.selectedConnectionId,
                        sessionModelBindingId = current.sessionModelBindingId,
                        isLoading = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toModelConnectionsErrorMessage()
                    )
                }
            }
        }
    }

    private fun selectSessionModel(modelBindingId: String) {
        val currentSessionId = sessionId
        if (currentSessionId == null) {
            mutableUiState.update {
                it.copy(errorMessage = "A session is required to select a model")
            }
            return
        }
        scope.launch {
            mutableUiState.update {
                it.copy(isModelSwitching = true, errorMessage = null)
            }
            try {
                port.selectSessionModel(currentSessionId, modelBindingId)
                mutableUiState.update {
                    it.copy(
                        sessionModelBindingId = modelBindingId,
                        isModelSwitching = false,
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isModelSwitching = false,
                        errorMessage = error.toModelConnectionsErrorMessage()
                    )
                }
            }
        }
    }
}

private fun ModelConnectionsSnapshot.toUiState(
    selectedConnectionId: String? = null,
    sessionModelBindingId: String?,
    isLoading: Boolean
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
        sessionModelBindingId = sessionModelBindingId,
        isLoading = isLoading
    )

private fun defaultModelConnectionsScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun Throwable.toModelConnectionsErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to load model connections"
