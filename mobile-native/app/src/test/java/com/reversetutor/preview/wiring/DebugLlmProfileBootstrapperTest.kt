package com.reversetutor.preview.wiring

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLlmProfileBootstrapperTest {
    @Test
    fun completeDebugConfigurationUsesProductionRuntime() {
        assertEquals(HybridLlmRuntimeMode.Production, config().runtimeMode())
    }

    @Test
    fun incompleteDebugConfigurationUsesFakeRuntime() {
        assertEquals(HybridLlmRuntimeMode.Fake, config(apiKey = "").runtimeMode())
    }

    @Test
    fun completeConfigurationCreatesQwenFirstProfilesAndReconcilesLaterLaunches() = runTest {
        val store = FakeDebugLlmProfileStore()
        val bootstrapper = DebugLlmProfileBootstrapper(store, nowEpochMillis = { 100L })
        val config = config()

        bootstrapper.ensureProfiles(config)

        assertEquals(
            listOf(
                "debug-llm-default",
                "debug-llm-fallback-1",
                "debug-llm-fallback-2"
            ),
            store.saved.map { it.id }
        )
        assertEquals(
            listOf(
                "qwen3.7-flash",
                "qwen3.6-flash",
                "deepseek-v4-flash"
            ),
            store.saved.map { it.model }
        )
        assertEquals(listOf("debug-llm-default"), store.activated)

        bootstrapper.ensureProfiles(config)

        assertEquals(6, store.saved.size)
        assertEquals(listOf("debug-llm-default"), store.activated)
    }

    @Test
    fun reconcilePrunesStaleDebugFallbacksButKeepsManualProfiles() = runTest {
        val store = FakeDebugLlmProfileStore()
        val bootstrapper = DebugLlmProfileBootstrapper(store, nowEpochMillis = { 100L })

        bootstrapper.ensureProfiles(config())
        // 模拟用户在界面里手动建的配置（非 debug-llm- 前缀，prune 不许碰）。
        store.save(
            DebugLlmProfileSeed(id = "manual-profile-1", name = "手动", model = "some-model", isDefault = false),
            config(),
            200L
        )

        bootstrapper.ensureProfiles(
            config(defaultModel = "gemini-3.5-flash-lite", fallbackModels = "deepseek-v4.1-flash")
        )

        assertEquals(listOf("debug-llm-fallback-2"), store.deleted)
        assertEquals(
            setOf("debug-llm-default", "debug-llm-fallback-1", "manual-profile-1"),
            store.profileIds()
        )
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
        defaultModel: String = "qwen3.7-flash",
        fallbackModels: String = "qwen3.6-flash,deepseek-v4-flash"
    ): DebugLlmBootstrapConfig =
        DebugLlmBootstrapConfig.from(
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            fallbackModels = fallbackModels
        )
}

private class FakeDebugLlmProfileStore : DebugLlmProfileStore {
    private val ids = linkedSetOf<String>()
    val saved = mutableListOf<DebugLlmProfileSeed>()
    val activated = mutableListOf<String>()
    val deleted = mutableListOf<String>()

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

    override suspend fun delete(profileId: String) {
        ids -= profileId
        deleted += profileId
    }
}
