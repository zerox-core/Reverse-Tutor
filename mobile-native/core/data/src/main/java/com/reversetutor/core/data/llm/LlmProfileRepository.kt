package com.reversetutor.core.data.llm

import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import java.util.UUID

class LlmProfileRepository(
    private val llmProfileDao: LlmProfileDao,
    private val secretStore: SecretStore,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun saveProfile(
        input: LlmProfileInput,
        nowEpochMillis: Long,
        profileId: String = "llm-profile-${UUID.randomUUID()}"
    ): LlmProfile {
        val normalized = input.normalized()
        require(normalized.name.isNotEmpty()) { "Profile name is required." }
        require(normalized.model.isNotEmpty()) { "Model is required." }

        val existing = llmProfileDao.getById(profileId)
        val secretRef = when {
            normalized.apiKey != null -> {
                val ref = secretRef(profileId)
                secretStore.put(ref, normalized.apiKey)
                ref
            }
            existing?.secretRef != null -> existing.secretRef
            else -> null
        }
        val profile = LlmProfile(
            id = profileId,
            spaceId = existing?.spaceId ?: defaultSpaceId,
            name = normalized.name,
            provider = normalized.provider,
            model = normalized.model,
            secretRef = secretRef,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            baseUrl = normalized.baseUrl,
            enabled = existing?.enabled ?: llmProfileDao.listBySpace(defaultSpaceId).isEmpty()
        )

        llmProfileDao.upsert(profile.toEntity())
        return profile
    }

    suspend fun listProfiles(spaceId: String = defaultSpaceId): List<LlmProfile> =
        llmProfileDao.listBySpace(spaceId)
            .map { it.toDomain() }
            .sortedWith(
                compareByDescending<LlmProfile> { it.enabled }
                    .thenByDescending { it.updatedAtEpochMillis }
            )

    suspend fun activateProfile(
        profileId: String,
        nowEpochMillis: Long
    ): Boolean {
        val profile = llmProfileDao.getById(profileId) ?: return false
        llmProfileDao.setEnabledForSpace(
            spaceId = profile.spaceId,
            enabledProfileId = profileId,
            updatedAtEpochMillis = nowEpochMillis
        )
        return true
    }

    suspend fun deleteProfile(profileId: String): Boolean {
        val profile = llmProfileDao.getById(profileId) ?: return false
        val deleted = llmProfileDao.deleteById(profileId) > 0
        if (deleted && profile.secretRef != null) {
            secretStore.delete(profile.secretRef)
        }
        return deleted
    }

    suspend fun getSecret(ref: String): String? = secretStore.get(ref)

    private fun secretRef(profileId: String): String = "llm-secret-$profileId"
}

data class LlmProfileInput(
    val name: String,
    val provider: LlmProviderKind,
    val model: String,
    val baseUrl: String?,
    val apiKey: String?
) {
    fun normalized(): LlmProfileInput =
        copy(
            name = name.trim(),
            model = model.trim(),
            baseUrl = baseUrl?.trim()?.ifEmpty { null },
            apiKey = apiKey?.trim()?.ifEmpty { null }
        )
}
