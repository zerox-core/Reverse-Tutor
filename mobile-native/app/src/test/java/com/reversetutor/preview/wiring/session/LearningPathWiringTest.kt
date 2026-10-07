package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ConceptStatus
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.MasterySnapshot
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.preview.wiring.NewSessionSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R86：学习路径接线——配置→有序路径、掌握度→概念状态、编解码 30 字段兼容。 */
class LearningPathWiringTest {

    private fun mastery(knowledgePoint: String, score: Float, attempts: Int) = MasterySnapshot(
        knowledgePoint = knowledgePoint,
        score = score,
        reviewIntervalDays = 1,
        nextReviewAtEpochMillis = 0L,
        attempts = attempts,
        band = "test"
    )

    private fun contextWithMastery(projections: List<MasterySnapshot>) = ConversationContextContract(
        spaceId = "space-a",
        sessionId = "child-2",
        prerequisiteGaps = emptyList(),
        relatedMemory = emptyList(),
        sourceEvidence = emptyList(),
        historicalErrors = emptyList(),
        pendingReviewKnowledgePoints = emptyList(),
        recentMessages = emptyList(),
        warnings = emptyList(),
        masteryProjections = projections
    )

    @Test
    fun masteryProjectionsMapOntoConceptStatesByScoreBand() {
        val input = NewSessionConfiguration(title = "物理", goal = "学力学")
            .toGuidedLearningTurnInput(
                spaceId = "space-a",
                sessionId = "child-2",
                context = contextWithMastery(
                    listOf(
                        mastery("密度", 92f, 5),
                        mastery("浮力", 60f, 3),
                        mastery("压强", 30f, 2),
                        mastery("杠杆", 10f, 4)
                    )
                )
            )

        assertEquals(ConceptStatus.Mastered, input.conceptStateFor("密度").status)
        assertEquals(ConceptStatus.Stable, input.conceptStateFor("浮力").status)
        assertEquals(ConceptStatus.Exploring, input.conceptStateFor("压强").status)
        assertEquals(ConceptStatus.Fragile, input.conceptStateFor("杠杆").status)
        assertEquals(0.92f, input.conceptStateFor("密度").confidence, 0.001f)
        assertEquals(5, input.conceptStateFor("密度").attemptCount)
    }

    @Test
    fun configurationLearningPathBecomesOrderedPath() {
        val input = NewSessionConfiguration(
            title = "物理",
            goal = "学力学",
            learningPath = listOf(" 密度 ", "", "浮力", "密度", "压强")
        ).toGuidedLearningTurnInput(
            spaceId = "space-a",
            sessionId = "child-2",
            context = ConversationContextContract.empty("space-a", "child-2")
        )

        assertEquals(3, input.learningPath.size)
        assertEquals(listOf("密度", "浮力", "压强"), input.learningPath.nodes.map { it.key })
    }

    @Test
    fun sessionWithoutPathKeepsEmptyPathAndNoStates() {
        val input = NewSessionConfiguration(title = "物理").toGuidedLearningTurnInput(
            spaceId = "space-a",
            sessionId = "child-2",
            context = ConversationContextContract.empty("space-a", "child-2")
        )

        assertEquals(0, input.learningPath.size)
        assertTrue(input.conceptStates.isEmpty())
    }

    @Test
    fun codecRoundTripsLearningPath() {
        val configuration = NewSessionConfiguration(
            title = "物理",
            goal = "学力学",
            persona = "慢热但较真",
            learningPath = listOf("密度", "浮力:入门", "压强")
        )
        val decoded = NewSessionSnapshotCodec.decodeConfiguration(
            NewSessionSnapshotCodec.encodeConfiguration(configuration)
        )
        assertEquals(configuration, decoded)
    }

    @Test
    fun legacyTwentyNineFieldConfigurationDecodesWithEmptyPath() {
        val decoded = NewSessionSnapshotCodec.decodeConfiguration(pack(List(29) { "" }))
        assertTrue(decoded.learningPath.isEmpty())
        assertEquals("", decoded.persona)
    }

    private fun pack(values: List<String>): String = buildString {
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }
}
