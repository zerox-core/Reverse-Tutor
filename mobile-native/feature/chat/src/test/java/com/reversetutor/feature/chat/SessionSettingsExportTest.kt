package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSettingsExportTest {

    private fun document(): SessionSettingsDocument = SessionSettingsDocument(
        profile = SessionSettingsProfile(
            title = "高三数学",
            learnerDisplayName = "小策",
            learnerRole = "高三学生",
            avatarVisible = false,
            personality = "耐心",
            interactionHabits = "喜欢追问"
        ),
        goalPlan = SessionGoalPlan(
            primaryGoal = "拿下导数",
            deadline = "2026-06-07",
            learningScope = "函数与导数",
            modules = "导数模块",
            stageMilestones = "三月模考",
            weeklyPlan = "每周两套卷",
            currentState = "基础一般"
        ),
        strategy = ConversationStrategy(
            feedbackIntensity = 4,
            probingIntensity = 2,
            scaffoldingIntensity = 5,
            correctionPersistence = "坚持",
            reviewFrequency = "每天",
            speakingTone = "严谨"
        ),
        snapshot = NewSessionConfiguration(
            title = "高三数学",
            story = "导数逆袭",
            builtInPresetId = "preset-1",
            learnerImageRef = null,
            storyImageRef = "story.png",
            sourceSelections = listOf("src-a", "src-b"),
            customColumns = listOf(CustomColumn(id = "c1", name = "易错点", content = "链式法则"))
        ),
        quickTags = mapOf(
            "goal" to TagFieldSelection(listOf(TagSelectionValue("t1", "冲刺"), TagSelectionValue(null, "自定义目标")))
        )
    )

    @Test
    fun configPayloadContainsOnlyTheThreeConfirmedSections() {
        val payload = SessionSettingsExport.buildConfigPayload(document(), exportedAtEpochMillis = 1_000L)
        assertTrue(payload.json.contains("\"type\": \"session-config\""))
        assertTrue(payload.json.contains("\"exportedAtEpochMillis\": 1000"))
        assertTrue(payload.json.contains("\"profile\": {"))
        assertTrue(payload.json.contains("\"goalPlan\": {"))
        assertTrue(payload.json.contains("\"strategy\": {"))
        assertTrue(payload.json.contains("\"primaryGoal\": \"拿下导数\""))
        assertFalse(payload.json.contains("\"snapshot\""))
        assertFalse(payload.json.contains("\"quickTags\""))
        assertEquals("反转家教-窗口配置-高三数学.json", payload.fileName)
    }

    @Test
    fun memoryPayloadAddsSnapshotAndQuickTags() {
        val payload = SessionSettingsExport.buildMemoryPayload(document(), exportedAtEpochMillis = 2_000L)
        assertTrue(payload.json.contains("\"type\": \"session-memory\""))
        assertTrue(payload.json.contains("\"story\": \"导数逆袭\""))
        assertTrue(payload.json.contains("\"builtInPresetId\": \"preset-1\""))
        assertTrue(payload.json.contains("\"learnerImageRef\": null"))
        assertTrue(payload.json.contains("\"sourceSelections\": [\"src-a\", \"src-b\"]"))
        assertTrue(payload.json.contains("\"customColumnCount\": 1"))
        assertTrue(payload.json.contains("\"goal\": [\"冲刺\", \"自定义目标\"]"))
        assertEquals("反转家教-窗口记忆库-高三数学.json", payload.fileName)
    }

    @Test
    fun escapingKeepsJsonValidForQuotesAndNewlines() {
        val tricky = document().copy(
            profile = document().profile.copy(title = "他说\"换行\n反斜杠\\\"", personality = "急性子\n别催")
        )
        val payload = SessionSettingsExport.buildConfigPayload(tricky, exportedAtEpochMillis = 3L)
        assertTrue(payload.json.contains("\"title\": \"他说\\\"换行\\n反斜杠\\\\\\\"\""))
        assertTrue(payload.json.contains("\"personality\": \"急性子\\n别催\""))
        assertFalse(payload.json.contains("急性子\n别催"))
    }

    @Test
    fun fileNameSanitizesIllegalCharactersAndBlankTitle() {
        assertEquals("反转家教-窗口配置-a_b_c.json", sanitizeVia("a/b\\c", "窗口配置"))
        assertEquals("反转家教-窗口配置-未命名会话.json", sanitizeVia("   ", "窗口配置"))
        assertTrue(SessionSettingsExport.sanitizeFileName("  ").isNotBlank())
    }

    private fun sanitizeVia(title: String, kind: String): String {
        val doc = document().copy(profile = document().profile.copy(title = title))
        return if (kind == "窗口配置") {
            SessionSettingsExport.buildConfigPayload(doc, 0L).fileName
        } else {
            SessionSettingsExport.buildMemoryPayload(doc, 0L).fileName
        }
    }
}
