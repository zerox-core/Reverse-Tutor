package com.reversetutor.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTreeModelsTest {
    @Test
    fun partialDraftKeepsTypedSectionsAndCustomContent() {
        val draft = WorldTreeDraft(
            id = "tree-1",
            spaceId = "space-1",
            sessionId = null,
            templateId = null,
            title = "高三数学讲题冲刺",
            mode = WorldTreeMode.Learning,
            schemaVersion = 1,
            sections = listOf(
                WorldTreeSection(
                    id = "student",
                    type = WorldTreeSectionType.StudentRole,
                    title = "学生角色",
                    order = 0,
                    payload = StudentRolePayload(name = "小岚"),
                    required = true,
                    completed = false
                ),
                WorldTreeSection(
                    id = "custom",
                    type = WorldTreeSectionType.Custom,
                    title = "考试习惯",
                    order = 1,
                    payload = CustomPayload("先检查定义域"),
                    required = false,
                    completed = true
                )
            ),
            sourceIds = emptyList(),
            state = WorldTreeDraftState.Draft,
            createdAtEpochMillis = 10,
            updatedAtEpochMillis = 20
        )

        assertFalse(draft.isReady)
        assertEquals("小岚", (draft.sections[0].payload as StudentRolePayload).name)
        assertEquals("先检查定义域", (draft.sections[1].payload as CustomPayload).content)
        assertTrue(draft.sections[1].isPayloadCompatible())
    }

    @Test
    fun sectionRejectsPayloadFromAnotherType() {
        val section = WorldTreeSection(
            id = "goal",
            type = WorldTreeSectionType.LearningGoal,
            title = "学习目标",
            order = 0,
            payload = CustomPayload("wrong"),
            required = true,
            completed = true
        )

        assertFalse(section.isPayloadCompatible())
    }
}
