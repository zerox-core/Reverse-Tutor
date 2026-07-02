package com.reversetutor.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSessionPresetValidatorTest {
    @Test
    fun acceptsSafePresetJson() {
        val result = NativeSessionPresetValidator.validate(
            """
            {
              "schema": "reverse_tutor_preset_v1",
              "title": "Exam sprint",
              "role": "Socratic exam coach",
              "goal": "Prepare for weekly math tests",
              "profile": "Concise, strict, and encouraging",
              "sourceHandoff": "deferred"
            }
            """.trimIndent()
        )

        assertTrue(result.isValid)
        assertEquals("Exam sprint", result.preset?.title)
        assertEquals("Socratic exam coach", result.preset?.role)
        assertEquals("deferred", result.preset?.sourceHandoff)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun rejectsPresetJsonWithSecretKeyMaterial() {
        val result = NativeSessionPresetValidator.validate(
            """
            {
              "schema": "reverse_tutor_preset_v1",
              "title": "Unsafe",
              "role": "Tutor",
              "goal": "Study",
              "profile": "Helpful",
              "apiKey": "sk-secret"
            }
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("secret", ignoreCase = true) })
    }

    @Test
    fun rejectsSecretNamedFieldsEvenWhenValueIsNotAString() {
        val result = NativeSessionPresetValidator.validate(
            """
            {
              "schema": "reverse_tutor_preset_v1",
              "title": "Unsafe",
              "role": "Tutor",
              "goal": "Study",
              "profile": "Helpful",
              "secretRef": null
            }
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("secret", ignoreCase = true) })
    }

    @Test
    fun rejectsMissingRequiredPresetFields() {
        val result = NativeSessionPresetValidator.validate(
            """
            {
              "schema": "reverse_tutor_preset_v1",
              "title": "Incomplete"
            }
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("role", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("goal", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("profile", ignoreCase = true) })
    }
}
