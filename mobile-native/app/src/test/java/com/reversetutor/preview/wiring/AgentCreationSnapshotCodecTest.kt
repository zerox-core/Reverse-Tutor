package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.AgentCreationDocAnalysis
import com.reversetutor.feature.chat.AgentCreationFeedEntry
import com.reversetutor.feature.chat.AgentCreationHistoryTurn
import com.reversetutor.feature.chat.AgentCreationPlannerState
import com.reversetutor.feature.chat.AgentCreationSnapshot
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R85：创建会话快照编解码——往返无损、损坏安全、分析中文件卡降级。 */
class AgentCreationSnapshotCodecTest {

    private fun fullSnapshot(): AgentCreationSnapshot = AgentCreationSnapshot(
        draft = NewSessionConfiguration(
            title = "浮力讲学练",
            learnerRole = "初二学生",
            goal = "把浮力讲明白",
            persona = "慢热但较真",
            learningScope = "力学",
            stageMilestones = "概念→例题",
            feedbackIntensity = 4
        ),
        feed = listOf(
            AgentCreationFeedEntry.Assistant(id = "e-1", text = "想学什么？"),
            AgentCreationFeedEntry.User(id = "e-2", text = "我想学物理:浮力（力学）"),
            AgentCreationFeedEntry.FileCard(
                id = "e-3",
                fileName = "讲义.pdf",
                sizeLabel = "12 KB",
                status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzed
            ),
            AgentCreationFeedEntry.DraftCard(
                id = "e-4",
                configuration = NewSessionConfiguration(title = "草案卡", persona = "急性子")
            )
        ),
        rawUnderstanding = 58,
        requestDocumentActive = true,
        history = listOf(
            AgentCreationHistoryTurn(isUser = true, text = "我想学物理"),
            AgentCreationHistoryTurn(isUser = false, text = "目标是什么？")
        ),
        planner = AgentCreationPlannerState(
            rounds = 3,
            askedCounts = mapOf("学习目标" to 1, "人物性格" to 2),
            converged = true
        ),
        docAnalysis = AgentCreationDocAnalysis(
            materialTitle = "物理讲义",
            materialType = "教材",
            outline = listOf("浮力", "压强"),
            knowledgePoints = listOf("浮力"),
            difficulty = 0.7f,
            prerequisites = listOf("密度"),
            suggestedPath = listOf("浮力→压强"),
            summary = "覆盖两章"
        ),
        entrySequence = 7
    )

    @Test
    fun snapshotRoundTripPreservesEverything() {
        val decoded = AgentCreationSnapshotCodec.decode(
            AgentCreationSnapshotCodec.encode(fullSnapshot())
        )
        assertEquals(fullSnapshot(), decoded)
    }

    @Test
    fun corruptPayloadDecodesToNull() {
        assertNull(AgentCreationSnapshotCodec.decode(""))
        assertNull(AgentCreationSnapshotCodec.decode("plain garbage"))
        assertNull(AgentCreationSnapshotCodec.decode("2:v1"))
    }

    @Test
    fun analyzingFileCardRestoresAsRetryableFailure() {
        val snapshot = AgentCreationSnapshot(
            feed = listOf(
                AgentCreationFeedEntry.FileCard(
                    id = "e-1",
                    fileName = "讲义.pdf",
                    sizeLabel = "12 KB",
                    status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzing
                )
            )
        )
        val decoded = AgentCreationSnapshotCodec.decode(AgentCreationSnapshotCodec.encode(snapshot))!!
        val card = decoded.feed.single() as AgentCreationFeedEntry.FileCard
        assertEquals(AgentCreationFeedEntry.FileCard.FileStatus.Failed, card.status)
    }
}
