package com.reversetutor.preview.wiring

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLlmProfileBootstrapperTest {
    @Test
    fun completeConfigurationCreatesDefaultAndFallbackProfilesOnce() = runTest {
        val store = FakeDebugLlmProfileStore()
        val bootstrapper = DebugLlmProfileBootstrapper(store, nowEpochMillis = { 100L })
        val config = config()

        bootstrapper.ensureProfiles(config)

        assertEquals(
            listOf(
                "debug-llm-default",
                "debug-llm-fallback-1",
                "debug-llm-fallback-2",
                "debug-llm-fallback-3"
            ),
            store.saved.map { it.id }
        )
        assertEquals(
            listOf(
                "deepseek-v4-flash-0731",
                "qwen3.6-flash",
                "deepseek-v4-flash",
                "qwen3.7-flash"
            ),
            store.saved.map { it.model }
        )
        assertEquals(listOf("debug-llm-default"), store.activated)

        bootstrapper.ensureProfiles(config)

        assertEquals(4, store.saved.size)
        assertEquals(listOf("debug-llm-default"), store.activated)
    }

    @Test
    fun incompleteConfigurationDoesNotCreateProfiles() = runTest {
        val store = FakeDebugLlmProfileStore()
        val bootstrapper = DebugLlmProfileBootstrapper(store)

        bootstrapper.ensureProfiles(config(apiKey = ""))
        bootstrapper.ensureProfiles(config(baseUrl = ""))
        bootstrapper.ensureProfiles(config(defaultModel = ""))

        assertTrue(store.saved.isEmpty())
        assertTrue(store.activated.isEmpty())
    }

    private fun config(
        apiKey: String = "test-key",
        baseUrl: String = "https://example.test/v1",
        defaultModel: String = "deepseek-v4-flash-0731"
    ): DebugLlmBootstrapConfig =
        DebugLlmBootstrapConfig.from(
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            fallbackModels = "qwen3.6-flash,deepseek-v4-flash,qwen3.7-flash"
        )
}

private class FakeDebugLlmProfileStore : DebugLlmProfileStore {
    private val ids = linkedSetOf<String>()
    val saved = mutableListOf<DebugLlmProfileSeed>()
    val activated = mutableListOf<String>()

    override suspend fun profileIds(): Set<String> = ids.toSet()

    override suspend fun save(
        seed: DebugLlmProfileSeed,
        config: DebugLlmBootstrapConfig,
        nowEpochMillis: Long
    ) {
        ids += seed.id
        saved += seed
    }

    override suspend fun activate(profileId: String, nowEpochMillis: Long) {
        activated += profileId
    }
}
