package com.reversetutor.core.domain

import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelCapabilityState
import com.reversetutor.core.model.ModelCapabilitySupport
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConnectionCoordinatorTest {
    @Test
    fun discoveryAndConnectionChecksRouteByProtocolWithoutVendorDtos() = runBlocking {
        val repository = FakeModelConnectionRepository()
        val gateway = FakeModelProviderGateway()
        val coordinator = ModelConnectionCoordinator(repository, gateway)
        val connection = ProviderConnection(
            id = "connection-1",
            spaceId = "space-1",
            name = "Gemini",
            protocol = ModelProtocol.GeminiNative
        )
        repository.connections[connection.id] = connection

        val discovered = coordinator.discoverModels(connection.id)
        val binding = discovered.bindings.single()
        val checked = coordinator.checkModel(binding.id)

        assertEquals(listOf(ModelProtocol.GeminiNative), gateway.discoveredProtocols)
        assertEquals(listOf(ModelProtocol.GeminiNative), gateway.checkedProtocols)
        assertEquals(ModelAvailability.Available, checked.availability)
        assertEquals(ModelCapabilitySupport.Supported, discovered.capabilities.image)
    }
}

private class FakeModelConnectionRepository : ModelConnectionRepository {
    val connections = mutableMapOf<String, ProviderConnection>()
    val bindings = mutableMapOf<String, ModelBinding>()

    override suspend fun findConnection(connectionId: String): ProviderConnection? =
        connections[connectionId]

    override suspend fun findBinding(bindingId: String): ModelBinding? = bindings[bindingId]

    override suspend fun listBindings(connectionId: String): List<ModelBinding> =
        bindings.values.filter { it.connectionId == connectionId }

    override suspend fun saveBinding(binding: ModelBinding): ModelBinding {
        bindings[binding.id] = binding
        return binding
    }
}

private class FakeModelProviderGateway : ModelProviderGateway {
    val discoveredProtocols = mutableListOf<ModelProtocol>()
    val checkedProtocols = mutableListOf<ModelProtocol>()

    override suspend fun discover(connection: ProviderConnection): ModelDiscovery {
        discoveredProtocols += connection.protocol
        return ModelDiscovery(
            modelIds = listOf("gemini-test"),
            capabilities = ModelCapabilityState(
                text = ModelCapabilitySupport.Supported,
                image = ModelCapabilitySupport.Supported
            )
        )
    }

    override suspend fun check(
        connection: ProviderConnection,
        binding: ModelBinding
    ): ModelConnectionCheck {
        checkedProtocols += connection.protocol
        return ModelConnectionCheck(ModelAvailability.Available)
    }
}
