package com.reversetutor.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R96 思考预算决策器：规则可解释、逐条可测。 */
class ThinkingBudgetDeciderTest {

    @Test
    fun docAnalysisAlwaysEnablesThinking() {
        val d = ThinkingBudgetDecider.decide("好的", hasDocAnalysis = true, isFirstTurn = false)
        assertTrue(d.enabled)
    }

    @Test
    fun firstTurnEnablesThinking() {
        val d = ThinkingBudgetDecider.decide("想学物理", hasDocAnalysis = false, isFirstTurn = true)
        assertTrue(d.enabled)
    }

    @Test
    fun multipleConstraintsEnableThinking() {
        val d = ThinkingBudgetDecider.decide(
            "讲浮力，并且不要太难，同时还要带练习",
            hasDocAnalysis = false, isFirstTurn = false
        )
        assertTrue(d.enabled)
    }

    @Test
    fun longInputEnablesThinking() {
        val d = ThinkingBudgetDecider.decide("讲".repeat(130), hasDocAnalysis = false, isFirstTurn = false)
        assertTrue(d.enabled)
    }

    @Test
    fun shortPlainAnswerDisablesThinking() {
        val d = ThinkingBudgetDecider.decide("高三的", hasDocAnalysis = false, isFirstTurn = false)
        assertFalse(d.enabled)
    }

    @Test
    fun shortAnswerWithConstraintMarkerKeepsThinkingOn() {
        val d = ThinkingBudgetDecider.decide("不要太难", hasDocAnalysis = false, isFirstTurn = false)
        assertTrue(d.enabled)
    }

    @Test
    fun defaultKeepsThinkingOn() {
        val d = ThinkingBudgetDecider.decide(
            "把电磁感应这部分给学生讲透一点",
            hasDocAnalysis = false, isFirstTurn = false
        )
        assertTrue(d.enabled)
    }
}
