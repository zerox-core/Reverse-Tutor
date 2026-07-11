package com.reversetutor.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationModelBindingCompatibilityTest {
    @Test
    fun legacyProfileDefaultsModelBindingForSessionsAndSettings() {
        val session = TutorSession(
            id = "session-1",
            spaceId = "space-1",
            title = "Session",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            llmProfileId = "profile-1"
        )
        val settings = SessionSettings(
            id = "settings-1",
            spaceId = "space-1",
            sessionId = "session-1",
            llmProfileId = "profile-1"
        )

        assertEquals("profile-1", session.modelBindingId)
        assertEquals("profile-1", settings.modelBindingId)
    }

    @Test
    fun explicitModelBindingOverridesLegacyCompatibilityDefault() {
        val session = TutorSession(
            id = "session-1",
            spaceId = "space-1",
            title = "Session",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            llmProfileId = "profile-1",
            modelBindingId = "binding-2"
        )

        assertEquals("binding-2", session.modelBindingId)
    }
}
