package com.reversetutor.core.data

import com.reversetutor.core.data.local.MigrationIds
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.TurnRunEntity
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.model.TurnRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HybridRepositoryContractTest {
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
