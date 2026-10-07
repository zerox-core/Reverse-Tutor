package com.reversetutor.core.llm

import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderRuntimeFoundationTest {
    @Test
    fun registryBuildsProtocolSpecificRequestsWithoutNetworkCalls() {
        val registry = ProviderAdapterRegistry.defaults()
        val connection = ProviderConnection(
            id = "connection-1",
            spaceId = "space-1",
            name = "Gemini",
            protocol = ModelProtocol.GeminiNative,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta"
        )

        val request = registry.adapterFor(connection.protocol).buildDiscoveryRequest(connection)

        assertEquals(ModelProtocol.GeminiNative, request.protocol)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", request.endpoint)
        assertFalse(request.requiresPython)
    }
}
