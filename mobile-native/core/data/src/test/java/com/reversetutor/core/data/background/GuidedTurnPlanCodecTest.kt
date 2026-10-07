package com.reversetutor.core.data.background

import com.reversetutor.core.domain.PathMove
import com.reversetutor.core.domain.TeachingAction
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.llm.LlmContextEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R88：turnPlan 后台任务编解码从 8 字段扩到 12（补 R86 遗留的
 * 进程死亡恢复丢路径步态 bug），8 字段旧任务按 legacy 解码。
 */
class GuidedTurnPlanCodecTest {

    @Test
    fun twelveFieldRoundTripPreservesPathFacts() {
        val plan = TurnPlan(
            actionType = TeachingAction.Practice,
            learningObjective = "讲讲串联并联",
            conceptKey = "电学",
            expectedUserMove = "讲一遍",
            pathMove = PathMove.Advance,
            pathPosition = 2,
            pathSize = 5,
            pathLabel = "电学"
        )
        val evidence = plan.toGuidedPlanEvidence().single()
        assertEquals("GuidedTurnPlan", evidence.kind)
        val decoded = evidence.toGuidedTurnPlan()
        assertNotNull(decoded)
        assertEquals(TeachingAction.Practice, decoded!!.actionType)
        assertEquals("电学", decoded.conceptKey)
        assertEquals(PathMove.Advance, decoded.pathMove)
        assertEquals("电学", decoded.pathLabel)
        assertEquals(2, decoded.pathPosition)
        assertEquals(5, decoded.pathSize)
    }

    @Test
    fun legacyEightFieldEvidenceStillDecodesWithoutPathFacts() {
        val body = listOf(
            "Practice", "", "讲讲串联并联", "电学", "讲一遍", "Plain", "1", "None"
        ).joinToString("|")
        val evidence = LlmContextEvidence("guided-turn-plan", "Guided turn plan", body, "GuidedTurnPlan")
        val decoded = evidence.toGuidedTurnPlan()
        assertNotNull(decoded)
        assertEquals(TeachingAction.Practice, decoded!!.actionType)
        assertEquals("电学", decoded.conceptKey)
        assertNull(decoded.pathMove)
        assertEquals("", decoded.pathLabel)
        assertEquals(-1, decoded.pathPosition)
        assertEquals(0, decoded.pathSize)
    }
}