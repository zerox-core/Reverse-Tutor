package com.reversetutor.core.domain

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelCapabilityState
import com.reversetutor.core.model.ProviderConnection

interface ModelProviderGateway {
    suspend fun discover(connection: ProviderConnection): ModelDiscovery

    suspend fun check(
        connection: ProviderConnection,
        binding: ModelBinding
    ): ModelConnectionCheck
}

data class ModelDiscovery(
    val modelIds: List<String>,
    val capabilities: ModelCapabilityState = ModelCapabilityState()
)

data class ModelDiscoveryResult(
    val connection: ProviderConnection,
    val bindings: List<ModelBinding>,
    val capabilities: ModelCapabilityState
)

data class ModelConnectionCheck(
    val availability: ModelAvailability
)

class ModelConnectionCoordinator(
    private val repository: ModelConnectionRepository,
    private val gateway: ModelProviderGateway,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun discoverModels(connectionId: String): ModelDiscoveryResult {
        val connection = requireNotNull(repository.findConnection(connectionId)) {
            "ProviderConnection not found: $connectionId"
        }
        val discovery = gateway.discover(connection)
        val existing = repository.listBindings(connection.id).associateBy(ModelBinding::modelId)
        val now = nowEpochMillis()
        val bindings = discovery.modelIds.distinct().map { modelId ->
            repository.saveBinding(
                existing[modelId]?.copy(
                    lastCheckedAtEpochMillis = now,
                    updatedAtEpochMillis = now
                ) ?: ModelBinding(
                    id = "${connection.id}:$modelId",
                    spaceId = connection.spaceId,
                    connectionId = connection.id,
                    modelId = modelId,
                    lastCheckedAtEpochMillis = now,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
        }
        return ModelDiscoveryResult(connection, bindings, discovery.capabilities)
    }

    suspend fun checkModel(bindingId: String): ModelBinding {
        val binding = requireNotNull(repository.findBinding(bindingId)) {
            "ModelBinding not found: $bindingId"
        }
        val connection = requireNotNull(repository.findConnection(binding.connectionId)) {
            "ProviderConnection not found: ${binding.connectionId}"
        }
        val result = gateway.check(connection, binding)
        val now = nowEpochMillis()
        return repository.saveBinding(
            binding.copy(
                availability = result.availability,
                lastCheckedAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }
}
