package com.reversetutor.preview.wiring

import com.reversetutor.core.data.llm.LlmProfileInput
import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.model.LlmProviderKind

internal data class DebugLlmBootstrapConfig(
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val fallbackModels: List<String>
) {
    val isComplete: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && defaultModel.isNotBlank()

    fun profiles(): List<DebugLlmProfileSeed> =
        (listOf(defaultModel) + fallbackModels)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .mapIndexed { index, model ->
                DebugLlmProfileSeed(
                    id = if (index == 0) DefaultProfileId else "debug-llm-fallback-$index",
                    name = if (index == 0) "本地测试默认模型" else "本地测试备用模型 $index",
                    model = model,
                    isDefault = index == 0
                )
            }

    companion object {
        const val DefaultProfileId = "debug-llm-default"

        fun from(
            apiKey: String,
            baseUrl: String,
            defaultModel: String,
            fallbackModels: String
        ): DebugLlmBootstrapConfig =
            DebugLlmBootstrapConfig(
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim(),
                defaultModel = defaultModel.trim(),
                fallbackModels = fallbackModels.split(',')
            )
    }
}

internal data class DebugLlmProfileSeed(
    val id: String,
    val name: String,
    val model: String,
    val isDefault: Boolean
)

internal interface DebugLlmProfileStore {
    suspend fun profileIds(): Set<String>
    suspend fun save(seed: DebugLlmProfileSeed, config: DebugLlmBootstrapConfig, nowEpochMillis: Long)
    suspend fun activate(profileId: String, nowEpochMillis: Long)
}

internal class RepositoryDebugLlmProfileStore(
    private val repository: LlmProfileRepository
) : DebugLlmProfileStore {
    override suspend fun profileIds(): Set<String> =
        repository.listProfiles().mapTo(linkedSetOf()) { it.id }

    override suspend fun save(
        seed: DebugLlmProfileSeed,
        config: DebugLlmBootstrapConfig,
        nowEpochMillis: Long
    ) {
        repository.saveProfile(
            input = LlmProfileInput(
                name = seed.name,
                provider = LlmProviderKind.OpenAiCompatible,
                model = seed.model,
                baseUrl = config.baseUrl,
                apiKey = config.apiKey
            ),
            nowEpochMillis = nowEpochMillis,
            profileId = seed.id
        )
    }

    override suspend fun activate(profileId: String, nowEpochMillis: Long) {
        repository.activateProfile(profileId, nowEpochMillis)
    }
}

internal class DebugLlmProfileBootstrapper(
    private val store: DebugLlmProfileStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun ensureProfiles(config: DebugLlmBootstrapConfig) {
        if (!config.isComplete) return

        val existingIds = store.profileIds()
        var createdDefault = false
        config.profiles().forEach { seed ->
            if (seed.id !in existingIds) {
                store.save(seed, config, nowEpochMillis())
                createdDefault = createdDefault || seed.isDefault
            }
        }
        if (createdDefault) {
            store.activate(DebugLlmBootstrapConfig.DefaultProfileId, nowEpochMillis())
        }
    }
}
