package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBehaviorSnapshotTest {
    @Test
    fun futureBehaviorEvidenceConsumesEveryPersistedSessionSetting() {
        val snapshot = NewSessionConfiguration(
            title = "概率论",
            learnerRole = "会追问证明的学生",
            learnerProfile = "谨慎",
            learnerDisplayName = "小概",
            avatarVisible = false,
            goal = "掌握条件概率",
            plan = "每周复盘",
            deadline = "2026-09-01",
            learningScope = "离散概率",
            modules = "事件, 随机变量",
            stageMilestones = "完成贝叶斯练习",
            currentState = "条件概率",
            dialogueStrategy = "先举反例",
            feedbackIntensity = 4,
            probingIntensity = 5,
            scaffoldingIntensity = 2,
            correctionPersistence = "严格",
            reviewFrequency = "高",
            speakingTone = "温和",
            story = "独立世界树",
            sourceSelections = listOf("source-a"),
            customColumns = listOf(CustomColumn("c1", "验收", "能独立证明"))
        )

        val evidence = snapshot.toFutureBehaviorEvidence("session-a")

        assertTrue(evidence.body.contains("小概"))
        assertTrue(evidence.body.contains("掌握条件概率"))
        assertTrue(evidence.body.contains("谨慎"))
        assertTrue(evidence.body.contains("反馈强度=4"))
        assertTrue(evidence.body.contains("独立世界树"))
        assertTrue(evidence.body.contains("验收=能独立证明"))
        assertTrue(evidence.body.contains("source-a"))
    }

    @Test
    fun sourceOwnerIndexAlwaysUsesLatestSnapshotsAndFavorites() {
        val owners = sourceOwnerIds(
            sourceId = "source-a",
            managedTitle = "讲义",
            sessionSnapshots = mapOf(
                "session-a" to NewSessionConfiguration(sourceSelections = emptyList()),
                "session-b" to NewSessionConfiguration(sourceSelections = listOf("source-a"))
            ),
            favorites = listOf(
                NewSessionFavorite("fav-a", "收藏", NewSessionConfiguration(sourceSelections = listOf("讲义")), 1)
            )
        )

        assertEquals(listOf("session-b", "favorite:fav-a"), owners)
        assertFalse("session-a" in owners)
    }
}
