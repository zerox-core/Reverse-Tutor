package com.reversetutor.core.data

import com.reversetutor.core.data.local.MigrationIds
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.TurnRunEntity
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.model.SessionSettings
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.model.TurnRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HybridRepositoryContractTest {
    @Test
    fun sessionMappingsPreserveExplicitAndLegacyCompatibleBindingIds() {
        val explicit = TutorSession(
            id = "session-explicit",
            spaceId = "space-1",
            title = "Explicit",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            llmProfileId = "profile-legacy",
            modelBindingId = "binding-new"
        )
        val legacyEntity = SessionEntity(
            id = "session-legacy",
            spaceId = "space-1",
            title = "Legacy",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            llmProfileId = "profile-legacy"
        )
        val settings = SessionSettings(
            id = "settings-1",
            spaceId = "space-1",
            sessionId = "session-explicit",
            llmProfileId = "profile-legacy",
            modelBindingId = "binding-new"
        )
        val legacySettings = SessionSettingsEntity(
            id = "settings-legacy",
            spaceId = "space-1",
            sessionId = "session-legacy",
            llmProfileId = "profile-legacy"
        )

        assertEquals("binding-new", explicit.toEntity().modelBindingId)
        assertEquals("profile-legacy", legacyEntity.toDomain().modelBindingId)
        assertEquals("binding-new", settings.toEntity().modelBindingId)
        assertEquals("profile-legacy", legacySettings.toDomain().modelBindingId)
    }

    @Test
    fun legacyProfileMigrationKeepsBindingIdAndDerivesStableConnectionId() {
        assertEquals("profile-1", MigrationIds.modelBindingId("profile-1"))
        assertEquals(
            MigrationIds.providerConnectionId("profile-1"),
            MigrationIds.providerConnectionId("profile-1")
        )
        assertFalse(MigrationIds.providerConnectionId("profile-1").contains("secret"))
    }

    @Test
    fun connectionOwnsSecretReferenceAndBindingDoesNot() {
        val connection = ProviderConnectionEntity(
            id = "connection-1",
            spaceId = "space-1",
            name = "OpenAI",
            protocol = "OpenAiCompatible",
            secretRef = "secret-ref",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val binding = ModelBindingEntity(
            id = "binding-1",
            spaceId = "space-1",
            connectionId = connection.id,
            modelId = "gpt-test",
            displayName = "GPT test",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

        assertEquals("secret-ref", connection.secretRef)
        assertEquals(connection.id, binding.connectionId)
    }

    @Test
    fun multipleActiveRunsInOneSessionRemainDistinctDomainRecords() {
        val first = runEntity(id = "run-1", turnId = "turn-1", sequence = 1L)
        val second = runEntity(id = "run-2", turnId = "turn-2", sequence = 2L)

        assertEquals(TurnRunState.Running, first.toDomain().state)
        assertEquals(TurnRunState.Running, second.toDomain().state)
        assertEquals(listOf("run-1", "run-2"), listOf(first, second).map { it.toDomain().id })
    }

    private fun runEntity(id: String, turnId: String, sequence: Long): TurnRunEntity =
        TurnRunEntity(
            id = id,
            spaceId = "space-1",
            turnId = turnId,
            sessionId = "session-1",
            userMessageId = "message-$sequence",
            sequence = sequence,
            contextVersion = sequence,
            modelBindingId = "binding-1",
            attempt = 0,
            state = TurnRunState.Running.name,
            createdAtEpochMillis = sequence
        )
}
