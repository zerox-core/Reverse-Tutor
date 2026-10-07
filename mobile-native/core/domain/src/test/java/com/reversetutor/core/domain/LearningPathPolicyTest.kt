package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** R86：有序学习路径——构建归一化与状态驱动推进（纯确定性，无时钟无随机）。 */
class LearningPathPolicyTest {

    private fun path(vararg labels: String): LearningPath =
        LearningPathPolicy.fromLabels(labels.toList())

    private fun mastered(label: String): Pair<String, ConceptLearningState> =
        label to ConceptLearningState(status = ConceptStatus.Mastered, confidence = 1f, attemptCount = 3)

    private fun stable(label: String): Pair<String, ConceptLearningState> =
        label to ConceptLearningState(status = ConceptStatus.Stable, confidence = 0.8f, attemptCount = 2)

    // ---- 构建归一化 ----

    @Test
    fun fromLabelsTrimsDedupesAndDropsBlanks() {
        val path = LearningPathPolicy.fromLabels(listOf(" 密度 ", "", "浮力", "密度", "压强"))
        assertEquals(3, path.size)
        assertEquals(listOf("密度", "浮力", "压强"), path.nodes.map { it.key })
        assertEquals(listOf("密度", "浮力", "压强"), path.nodes.map { it.label })
    }

    @Test
    fun fromLabelsCapsAtMaxNodes() {
        val path = LearningPathPolicy.fromLabels((1..20).map { "知识点$it" })
        assertEquals(LearningPathPolicy.MAX_NODES, path.size)
        assertEquals("知识点1", path.keyAt(0))
        assertEquals("知识点12", path.keyAt(11))
    }

    @Test
    fun emptyPathDecidesStayWithoutTarget() {
        val decision = LearningPathPolicy.decide(LearningPath(), emptyMap(), "任意目标")
        assertEquals(PathMove.Stay, decision.move)
        assertEquals(-1, decision.targetIndex)
    }

    // ---- 起点 ----

    @Test
    fun unknownCurrentAndNoStatesStartsAtPathHead() {
        val decision = LearningPathPolicy.decide(path("密度", "浮力", "压强"), emptyMap(), "把物理讲明白")
        assertEquals(PathMove.Start, decision.move)
        assertEquals(0, decision.targetIndex)
        assertEquals("密度", decision.targetLabel)
    }

    @Test
    fun unknownCurrentWithStatesAlignsToFirstUnmastered() {
        val decision = LearningPathPolicy.decide(
            path("密度", "浮力", "压强"), mapOf(mastered("密度")), "把物理讲明白"
        )
        assertEquals(PathMove.Advance, decision.move)
        assertEquals(1, decision.targetIndex)
        assertEquals("浮力", decision.targetLabel)
    }

    // ---- 推进 / 停留 ----

    @Test
    fun masteredCurrentAdvancesToNextUnmastered() {
        val decision = LearningPathPolicy.decide(
            path("密度", "浮力", "压强"), mapOf(mastered("密度")), "密度"
        )
        assertEquals(PathMove.Advance, decision.move)
        assertEquals(1, decision.targetIndex)
        assertEquals("浮力", decision.targetLabel)
    }

    @Test
    fun stableCountsAsMasteredForAdvancement() {
        val decision = LearningPathPolicy.decide(path("密度", "浮力"), mapOf(stable("密度")), "密度")
        assertEquals(PathMove.Advance, decision.move)
        assertEquals(1, decision.targetIndex)
    }

    @Test
    fun unmasteredCurrentStaysToConsolidate() {
        val states = mapOf(
            mastered("密度"),
            "浮力" to ConceptLearningState(status = ConceptStatus.Exploring, attemptCount = 1)
        )
        val decision = LearningPathPolicy.decide(path("密度", "浮力", "压强"), states, "浮力")
        assertEquals(PathMove.Stay, decision.move)
        assertEquals(1, decision.targetIndex)
    }

    // ---- 回退 ----

    @Test
    fun fragileCurrentWithFailuresRegressesToNearestOpenFoundation() {
        val states = mapOf(
            mastered("密度"),
            "浮力" to ConceptLearningState(status = ConceptStatus.Exploring, attemptCount = 2),
            "压强" to ConceptLearningState(status = ConceptStatus.Fragile, attemptCount = 4, failureStreak = 2)
        )
        val decision = LearningPathPolicy.decide(path("密度", "浮力", "压强"), states, "压强")
        assertEquals(PathMove.Regress, decision.move)
        assertEquals(1, decision.targetIndex)
        assertEquals("浮力", decision.targetLabel)
    }

    @Test
    fun fragileCurrentWithoutOpenFoundationStays() {
        val states = mapOf(
            mastered("密度"),
            "浮力" to ConceptLearningState(status = ConceptStatus.Fragile, attemptCount = 4, failureStreak = 3)
        )
        val decision = LearningPathPolicy.decide(path("密度", "浮力"), states, "浮力")
        assertEquals(PathMove.Stay, decision.move)
        assertEquals(1, decision.targetIndex)
    }

    // ---- 完成与前段回填 ----

    @Test
    fun allMasteredCompletesThePath() {
        val decision = LearningPathPolicy.decide(
            path("密度", "浮力"), mapOf(mastered("密度"), stable("浮力")), "浮力"
        )
        assertEquals(PathMove.Completed, decision.move)
        assertEquals(-1, decision.targetIndex)
    }

    @Test
    fun masteredCurrentWithEarlierGapRegressesToFillIt() {
        val states = mapOf(
            "浮力" to ConceptLearningState(status = ConceptStatus.Exploring, attemptCount = 1),
            mastered("压强")
        )
        val decision = LearningPathPolicy.decide(path("密度", "浮力", "压强"), states, "压强")
        assertEquals(PathMove.Regress, decision.move)
        assertEquals(0, decision.targetIndex)
        assertEquals("密度", decision.targetLabel)
    }
}
