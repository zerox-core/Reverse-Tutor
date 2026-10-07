package com.reversetutor.feature.chat

import com.reversetutor.core.protocol.NativeSessionPresetValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSessionTemplatesTest {
    @Test
    fun builtInTemplatesCoverApprovedCreateSessionCategories() {
        assertEquals(
            listOf("school", "exam", "work", "language", "habit", "skill"),
            BuiltInSessionTemplates.all.map { it.id }
        )
        BuiltInSessionTemplates.all.forEach { template ->
            assertTrue(template.title.isNotBlank())
            assertTrue(template.role.isNotBlank())
            assertTrue(template.goal.isNotBlank())
            assertTrue(template.profileText.isNotBlank())
        }
        assertTrue(BuiltInSessionTemplates.all.map { it.title }.contains("校内学习"))
    }

    @Test
    fun customDraftBuildsTrimmedProfileInputWithDeferredSourceHandoff() {
        val draft = NewSessionDraft(
            title = "  Piano basics  ",
            role = "  practice coach ",
            goal = "  learn sight reading ",
            profileText = "  patient and rhythmic ",
            sourceHandoffRequested = true
        )

        val input = draft.toCreationInput()

        assertEquals("Piano basics", input.title)
        assertEquals("practice coach", input.role)
        assertEquals("learn sight reading", input.goal)
        assertEquals("patient and rhythmic", input.profileText)
        assertTrue(input.sourceHandoffRequested)
    }

    @Test
    fun safePresetJsonBuildsCreationInput() {
        val result = NativeSessionPresetValidator.validate(
            """
            {
              "schema": "reverse_tutor_preset_v1",
              "title": "Preset flow",
              "role": "Preset tutor",
              "goal": "Validate preset creation",
              "profile": "Careful and explicit",
              "sourceHandoff": "deferred"
            }
            """.trimIndent()
        )

        val input = NewSessionDraft.fromPreset(result.preset!!).toCreationInput()

        assertEquals("Preset flow", input.title)
        assertEquals("Preset tutor", input.role)
        assertEquals("Validate preset creation", input.goal)
        assertEquals("Careful and explicit", input.profileText)
        assertTrue(input.sourceHandoffRequested)
    }
}
