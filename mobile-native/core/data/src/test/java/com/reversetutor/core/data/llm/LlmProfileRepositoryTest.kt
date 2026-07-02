package com.reversetutor.core.data.llm

import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProfileRepositoryTest {
    @Test
    fun saveProfileStoresOnlySecretReferenceInRoomAndSecretValueInStore() = runBlocking {
        val dao = FakeLlmProfileDao()
        val secretStore = FakeSecretStore()
        val repository = LlmProfileRepository(dao, secretStore)

        val saved = repository.saveProfile(
            input = LlmProfileInput(
                name = " Work model ",
                provider = LlmProviderKind.OpenAiCompatible,
                model = " gpt-4o-mini ",
                baseUrl = " https://api.openai.com/v1 ",
                apiKey = " sk-test "
            ),
            nowEpochMillis = 10L,
            profileId = "profile-1"
        )

        assertEquals("profile-1", saved.id)
        assertEquals("Work model", saved.name)
        assertEquals("gpt-4o-mini", saved.model)
        assertEquals("https://api.openai.com/v1", saved.baseUrl)
        assertEquals("llm-secret-profile-1", dao.entities["profile-1"]?.secretRef)
        assertEquals("sk-test", secretStore.values["llm-secret-profile-1"])
        assertFalse(dao.entities.values.any { entity -> entity.toString().contains("sk-test") })
    }

    @Test
    fun activateProfileSwitchesEnabledProfileAndDeleteRemovesSecret() = runBlocking {
        val dao = FakeLlmProfileDao()
        val secretStore = FakeSecretStore()
        val repository = LlmProfileRepository(dao, secretStore)

        repository.saveProfile(
            input = LlmProfileInput(
                name = "First",
                provider = LlmProviderKind.Custom,
                model = "demo",
                baseUrl = null,
                apiKey = "first-key"
            ),
            nowEpochMillis = 10L,
            profileId = "first"
        )
        repository.saveProfile(
            input = LlmProfileInput(
                name = "Second",
                provider = LlmProviderKind.Local,
                model = "local-model",
                baseUrl = "http://localhost:11434",
                apiKey = null
            ),
            nowEpochMillis = 20L,
            profileId = "second"
        )

        assertTrue(repository.activateProfile("second", nowEpochMillis = 30L))
        assertFalse(dao.entities.getValue("first").enabled)
        assertTrue(dao.entities.getValue("second").enabled)
        assertEquals(listOf("second", "first"), repository.listProfiles().map { it.id })

        assertTrue(repository.deleteProfile("first"))
        assertNull(dao.entities["first"])
        assertFalse(secretStore.values.containsKey("llm-secret-first"))
    }
}

private class FakeLlmProfileDao : LlmProfileDao {
    val entities = linkedMapOf<String, LlmProfileEntity>()

    override suspend fun upsert(profile: LlmProfileEntity) {
        entities[profile.id] = profile
    }

    override suspend fun getById(id: String): LlmProfileEntity? =
        entities[id]

    override suspend fun listBySpace(spaceId: String): List<LlmProfileEntity> =
        entities.values
            .filter { it.spaceId == spaceId }
            .sortedByDescending { it.updatedAtEpochMillis }

    override suspend fun listAll(): List<LlmProfileEntity> =
        entities.values.sortedByDescending { it.updatedAtEpochMillis }

    override suspend fun setEnabledForSpace(spaceId: String, enabledProfileId: String, updatedAtEpochMillis: Long) {
        entities.replaceAll { _, profile ->
            if (profile.spaceId == spaceId) {
                profile.copy(
                    enabled = profile.id == enabledProfileId,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            } else {
                profile
            }
        }
    }

    override suspend fun deleteById(id: String): Int =
        if (entities.remove(id) == null) 0 else 1
}

private class FakeSecretStore : SecretStore {
    val values = linkedMapOf<String, String>()

    override suspend fun put(ref: String, secret: String) {
        values[ref] = secret
    }

    override suspend fun get(ref: String): String? =
        values[ref]

    override suspend fun delete(ref: String) {
        values.remove(ref)
    }
}
