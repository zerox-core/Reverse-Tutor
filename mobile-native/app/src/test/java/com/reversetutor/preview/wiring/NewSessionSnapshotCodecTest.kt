package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.EditorScrollPosition
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionSection
import org.junit.Assert.assertEquals
import org.junit.Test

class NewSessionSnapshotCodecTest {
    @Test
    fun completeDraftFavoriteAndSessionSnapshotsRoundTripWithoutAliasing() {
        val configuration = NewSessionConfiguration(
            title = "数学：函数",
            learnerRole = "会追问为什么的学生",
            learnerProfile = "基础尚可\n容易跳步骤",
            goal = "把函数讲清楚",
            plan = "三周；每周两次",
            dialogueStrategy = "先听解释，再给反例",
            story = "校园数学社=第一幕",
            sourceSelections = listOf("试卷:A", "笔记\n第二章"),
            customFields = linkedMapOf("情绪" to "紧张:但愿意尝试", "能力" to "推导"),
            openingMessage = "老师，请先讲定义。",
            learnerImageRef = "content://learner/1",
            storyImageRef = "content://story/1",
            builtInPresetId = "formal-math-sprint"
        )
        val draft = NewSessionDraftRecord(
            id = "draft:1",
            configuration = configuration,
            updatedAtEpochMillis = 42L,
            favoriteId = "favorite:1",
            lastSection = NewSessionSection.WorldTree,
            scrollPositions = mapOf("root" to EditorScrollPosition(3, 19))
        )
        val favorite = NewSessionFavorite(
            id = "favorite:1",
            name = "完整收藏",
            configuration = configuration,
            updatedAtEpochMillis = 43L,
            originBuiltInPresetId = "formal-math-sprint"
        )

        assertEquals(configuration, NewSessionSnapshotCodec.decodeConfiguration(NewSessionSnapshotCodec.encodeConfiguration(configuration)))
        assertEquals(listOf(draft), NewSessionSnapshotCodec.decodeDrafts(NewSessionSnapshotCodec.encodeDrafts(listOf(draft))))
        assertEquals(listOf(favorite), NewSessionSnapshotCodec.decodeFavorites(NewSessionSnapshotCodec.encodeFavorites(listOf(favorite))))
    }

    @Test
    fun corruptCollectionEntryIsSkippedWhileOtherEntriesRecover() {
        val good = NewSessionDraftRecord(
            id = "good",
            configuration = NewSessionConfiguration(title = "Good", learnerRole = "Learner"),
            updatedAtEpochMillis = 1L
        )
        val encodedGood = NewSessionSnapshotCodec.encodeDrafts(listOf(good))
        val corruptEntry = "7:invalid"

        assertEquals(listOf(good), NewSessionSnapshotCodec.decodeDrafts(encodedGood + corruptEntry))
    }
}
