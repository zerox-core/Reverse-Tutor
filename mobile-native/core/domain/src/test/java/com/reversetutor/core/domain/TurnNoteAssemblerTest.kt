package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnNoteAssemblerTest {

    @Test
    fun expandKeywordRaisesHighTierWithBudgets() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(userText = "这个能展开详细讲讲吗，我想多了解一些")
        )

        assertEquals(DensityTier.High, note.densityTier)
        assertEquals(300, note.maxChars)
        assertEquals(2, note.maxNewConcepts)
        assertEquals(1, note.questionBudget)
        assertEquals(listOf("主动要求展开"), note.paceSignals)
    }

    @Test
    fun emotionKeywordDropsToLowTierAndAllowsJustListening() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(userText = "今天有点烦，不想展开任何东西")
        )

        // 情绪信号优先于展开信号。
        assertEquals(DensityTier.Low, note.densityTier)
        assertEquals(80, note.maxChars)
        assertEquals(0, note.maxNewConcepts)
        assertEquals(0, note.questionBudget)
        assertEquals(listOf("情绪低落"), note.paceSignals)
        assertTrue(note.render().contains("可以只是听"))
    }

    @Test
    fun policyEmotionMarkerDropsToLowTier() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(
                userText = "这个知识点我还有一段没弄懂的地方",
                userEmotion = UserEmotionWire.FRUSTRATED
            )
        )

        assertEquals(DensityTier.Low, note.densityTier)
        assertEquals(listOf("情绪低落"), note.paceSignals)
    }

    @Test
    fun shortMessageDropsOneTierToLow() {
        val note = TurnNoteAssembler.assemble(TurnNoteInput(userText = "嗯嗯好的"))

        assertEquals(DensityTier.Low, note.densityTier)
        assertEquals(listOf("消息很短"), note.paceSignals)
    }

    @Test
    fun neutralLongMessageStaysMedium() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(userText = "我觉得这里应该先把定义域确定下来再讨论单调性")
        )

        assertEquals(DensityTier.Medium, note.densityTier)
        assertEquals(160, note.maxChars)
        assertEquals(1, note.maxNewConcepts)
        assertEquals(1, note.questionBudget)
        assertTrue(note.paceSignals.isEmpty())
    }

    @Test
    fun highTierStreakCapsBackToMedium() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(
                userText = "继续展开讲讲",
                recentUserTexts = listOf("再展开讲讲", "展开讲讲这个", "帮我展开下")
            )
        )

        assertEquals(DensityTier.Medium, note.densityTier)
        assertEquals(listOf("主动要求展开", "连续高档回落"), note.paceSignals)
    }

    @Test
    fun highTierStreakBelowLimitStaysHigh() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(
                userText = "继续展开讲讲",
                recentUserTexts = listOf("再展开讲讲", "哦")
            )
        )

        assertEquals(DensityTier.High, note.densityTier)
        assertEquals(listOf("主动要求展开"), note.paceSignals)
    }

    @Test
    fun emptyUserTextStaysMediumWithoutShortSignal() {
        val note = TurnNoteAssembler.assemble(TurnNoteInput(userText = ""))

        assertEquals(DensityTier.Medium, note.densityTier)
        assertTrue(note.paceSignals.isEmpty())
    }

    @Test
    fun learningStatusCombinesKnowledgePointMasteryAndStuckPoint() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(
                userText = "我觉得这里应该先把定义域确定下来再讨论单调性",
                knowledgePoint = "单调性",
                masteryScore = 62.4f,
                lastStuckPoint = "端点取值判断"
            )
        )

        assertEquals("「单调性」掌握约 62%，上次卡在：端点取值判断", note.learningStatus)
    }

    @Test
    fun learningStatusBlankWhenNoKnowledgePoint() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(userText = "我觉得这里应该先把定义域确定下来再讨论单调性")
        )

        assertEquals("", note.learningStatus)
        assertFalse(note.render().contains("学习状态"))
    }

    @Test
    fun suggestedFocusPrefersTurnPlanConcept() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(
                userText = "我觉得这里应该先把定义域确定下来再讨论单调性",
                knowledgePoint = "单调性",
                turnPlan = TurnPlan(
                    actionType = TeachingAction.SocraticQuestion,
                    conceptKey = "因式分解"
                )
            )
        )

        assertEquals("因式分解", note.suggestedFocus)
    }

    @Test
    fun renderOmitsBlankLinesAndCarriesDensityBudget() {
        val note = TurnNote(
            densityTier = DensityTier.Medium,
            suggestedFocus = "单调性"
        )
        val text = note.render()

        assertTrue(text.contains("本轮便签"))
        assertTrue(text.contains("- 建议聚焦：单调性"))
        assertTrue(text.contains("信息密度：中（回复 ≤160 字、最多 1 个新概念、最多 1 个问题）"))
        assertFalse(text.contains("学习状态"))
        assertFalse(text.contains("节奏信号"))
        assertFalse(text.contains("风格提示"))
    }

    @Test
    fun normalizedBoundsLongFields() {
        val note = TurnNote(
            learningStatus = "长".repeat(500),
            paceSignals = listOf("a", "a", "b", "c", "d", "e"),
            styleHint = "短"
        ).normalized()

        assertEquals(TurnNoteAssembler.LEARNING_STATUS_MAX, note.learningStatus.length)
        assertEquals(listOf("a", "b", "c", "d"), note.paceSignals)
        assertEquals("短", note.styleHint)
    }

    @Test
    fun tierForTextClassifiesSingleMessages() {
        assertEquals(DensityTier.High, TurnNoteAssembler.tierForText("举个例子吧"))
        assertEquals(DensityTier.Low, TurnNoteAssembler.tierForText("好累"))
        assertEquals(DensityTier.Low, TurnNoteAssembler.tierForText("好的"))
        assertEquals(
            DensityTier.Medium,
            TurnNoteAssembler.tierForText("这一步为什么可以直接约掉公因式呢？")
        )
        assertEquals(DensityTier.Medium, TurnNoteAssembler.tierForText(""))
    }
}
