package com.reversetutor.feature.settings

import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection

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
