package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalPresetModelsTest {
    @Test
    fun catalogMapsEveryFormalPresetDetailBoard() {
        assertEquals(
            setOf(
                "716:684",
                "716:779",
                "716:871",
                "716:963",
                "716:1055",
                "716:1147",
                "716:1239",
                "716:1331"
            ),
            FormalLearningPresets.all.map { it.figmaNodeId }.toSet()
        )
    }

    @Test
    fun everyPresetCreatesAStudentRoleDraftForTheUserTeacher() {
        FormalLearningPresets.all.forEach { preset ->
            val draft = preset.toDraft()

            assertTrue(draft.role.startsWith("AI 学生 ${preset.learnerName}"))
            assertTrue(draft.profileText.contains("用户作为老师负责讲解"))
            assertFalse(draft.role.contains("导师"))
            assertFalse(draft.role.contains("教练"))
            assertTrue(draft.validationErrors().isEmpty())
        }
    }

    @Test
    fun presetIdsAndNodeIdsAreUnique() {
        val presets = FormalLearningPresets.all

        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertEquals(presets.size, presets.map { it.figmaNodeId }.distinct().size)
    }
}
