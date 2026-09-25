package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R88 章节切换卡片策略验收：提案产出、认可判定、卡片编解码、
 * TurnPlan 标签归一与 selector 目标标签透传，全部确定性断言。
 */
class ChapterTransitionPolicyTest {

    private val path = LearningPathPolicy.fromLabels(listOf("力学", "电学", "电磁感应"))

    private fun plan(
        move: PathMove?,
        position: Int,
        size: Int,
        label: String = ""
    ) = TurnPlan(
        actionType = TeachingAction.Practice,
        pathMove = move,
        pathPosition = position,
        pathSize = size,
        pathLabel = label
    )

    @Test
    fun advanceProposalCarriesFromAndToLabels() {
        val proposal = ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, 1, 3), path)!!
        assertEquals(PathMove.Advance, proposal.move)
        assertEquals("力学", proposal.fromLabel)
        assertEquals("电学", proposal.toLabel)
        assertEquals(1, proposal.position)
        assertEquals(3, proposal.size)
    }

    @Test
    fun advanceToHeadWithoutPathFallsBackToPlanLabel() {
        val proposal = ChapterTransitionPolicy.proposalFor(
            plan(PathMove.Advance, 0, 1, "电学"),
            LearningPath()
        )!!
        assertEquals("", proposal.fromLabel)
        assertEquals("电学", proposal.toLabel)
        assertEquals(0, proposal.position)
    }

    @Test
    fun regressProposalKeepsNextChapterAsContext() {
        val proposal = ChapterTransitionPolicy.proposalFor(plan(PathMove.Regress, 0, 3), path)!!
        assertEquals(PathMove.Regress, proposal.move)
        assertEquals("力学", proposal.toLabel)
        assertEquals("电学", proposal.fromLabel)
    }

    @Test
    fun completedProposalPointsAtLastNode() {
        val proposal = ChapterTransitionPolicy.proposalFor(plan(PathMove.Completed, -1, 3), path)!!
        assertEquals(PathMove.Completed, proposal.move)
        assertEquals("电磁感应", proposal.fromLabel)
        assertEquals("", proposal.toLabel)
        assertEquals(2, proposal.position)
    }

    @Test
    fun stayStartAndBadBoundsProduceNoProposal() {
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Stay, 1, 3), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Start, 0, 3), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(null, 1, 3), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, -1, 3), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, 3, 3), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, 1, 0), path))
        assertNull(ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, 1, 13), path))
    }

    @Test
    fun userAffirmationsMatchEverydayPhrasings() {
        assertTrue(ChapterTransitionPolicy.isTransitionAffirmation("对的，马上进入电学章节吧"))
        assertTrue(ChapterTransitionPolicy.isTransitionAffirmation("好的"))
        assertTrue(ChapterTransitionPolicy.isTransitionAffirmation("嗯嗯，开始吧"))
        assertTrue(ChapterTransitionPolicy.isTransitionAffirmation("ok，继续"))
        assertTrue(ChapterTransitionPolicy.isTransitionAffirmation("  没问题。 "))
    }

    @Test
    fun questionsNegationsAndLongTeachingDoNotMatch() {
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation("电学难吗？"))
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation("先不学电学"))
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation("好的，我们先不学电学了"))
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation("这道题用串联的思路再讲一遍"))
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation("对" + "这题换个方法讲。".repeat(10)))
        assertFalse(ChapterTransitionPolicy.isTransitionAffirmation(""))
    }

    @Test
    fun cardTextRoundTripsThroughParsing() {
        val proposal = ChapterTransitionPolicy.proposalFor(plan(PathMove.Advance, 1, 3), path)!!
        val text = ChapterTransitionPolicy.cardText(proposal)
        assertTrue(text.startsWith(ChapterTransitionPolicy.CARD_PREFIX))
        assertEquals(proposal, ChapterTransitionPolicy.parseCardText(text))
    }

    @Test
    fun parseRejectsOrdinaryAndMalformedText() {
        assertNull(ChapterTransitionPolicy.parseCardText("普通系统提示"))
        assertNull(ChapterTransitionPolicy.parseCardText("✦ 章节更新｜stay｜a｜b｜0｜3"))
        assertNull(ChapterTransitionPolicy.parseCardText("✦ 章节更新｜advance｜a｜b｜9｜3"))
        assertNull(ChapterTransitionPolicy.parseCardText("✦ 章节更新｜advance｜a｜b｜1｜"))
    }

    @Test
    fun turnPlanNormalizationBoundsPathLabel() {
        val normalized = TurnPlan(
            actionType = TeachingAction.Practice,
            pathLabel = "超".repeat(80)
        ).normalized()
        assertEquals(LearningPathPolicy.NODE_LABEL_MAX, normalized.pathLabel.length)
    }

    @Test
    fun selectorCarriesTargetLabelIntoPlan() {
        val plan = TeachingActionSelector.select(
            GuidedLearningTurnInput(
                sessionId = "s-1",
                windowId = "w-1",
                spaceId = "sp-1",
                conceptKey = "力学",
                userIntentHint = UserIntent.AnswerAttempt,
                conceptStates = mapOf(
                    "力学" to ConceptLearningState(status = ConceptStatus.Mastered)
                ),
                learningPath = path
            )
        )
        assertEquals(PathMove.Advance, plan.pathMove)
        assertEquals("电学", plan.pathLabel)
        assertEquals(1, plan.pathPosition)
        assertEquals(3, plan.pathSize)
    }
}