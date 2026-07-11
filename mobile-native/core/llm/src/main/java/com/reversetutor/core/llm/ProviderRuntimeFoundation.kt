package com.reversetutor.core.llm

import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection

data class ProviderRequestDescriptor(
    val protocol: ModelProtocol,
    val endpoint: String,
    val requiresPython: Boolean = false
)

interface ProviderProtocolAdapter {
    val protocol: ModelProtocol
    fun buildDiscoveryRequest(connection: ProviderConnection): ProviderRequestDescriptor
}

class ProviderAdapterRegistry private constructor(
    adapters: List<ProviderProtocolAdapter>
) {
    private val adaptersByProtocol = adapters.associateBy(ProviderProtocolAdapter::protocol)

    fun adapterFor(protocol: ModelProtocol): ProviderProtocolAdapter =
        requireNotNull(adaptersByProtocol[protocol]) {
            "No provider adapter registered for $protocol"
        }

    companion object {
        fun defaults(): ProviderAdapterRegistry = ProviderAdapterRegistry(
            listOf(
                OpenAiProtocolAdapter,
                AnthropicProtocolAdapter,
                GeminiProtocolAdapter
            )
        )
    }
}

private object OpenAiProtocolAdapter : ProviderProtocolAdapter {
    override val protocol = ModelProtocol.OpenAiCompatible

    override fun buildDiscoveryRequest(
        connection: ProviderConnection
    ): ProviderRequestDescriptor = ProviderRequestDescriptor(
        protocol = protocol,
        endpoint = connection.baseUrl.normalizedBaseUrl() + "/models"
    )
}

private object AnthropicProtocolAdapter : ProviderProtocolAdapter {
    override val protocol = ModelProtocol.AnthropicCompatible

    override fun buildDiscoveryRequest(
        connection: ProviderConnection
    ): ProviderRequestDescriptor = ProviderRequestDescriptor(
        protocol = protocol,
        endpoint = connection.baseUrl.normalizedBaseUrl() + "/models"
    )
}

private object GeminiProtocolAdapter : ProviderProtocolAdapter {
    override val protocol = ModelProtocol.GeminiNative

    override fun buildDiscoveryRequest(
        connection: ProviderConnection
    ): ProviderRequestDescriptor = ProviderRequestDescriptor(
        protocol = protocol,
        endpoint = connection.baseUrl.normalizedBaseUrl() + "/models"
    )
}

private fun String?.normalizedBaseUrl(): String = orEmpty().trim().trimEnd('/')
