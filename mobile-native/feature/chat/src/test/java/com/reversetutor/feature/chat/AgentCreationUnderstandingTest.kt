package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第十章算法纯函数测试：u_det 权重表、双源融合（封顶/单调）、
 * 追问优先级 / 同字段 2 次降级 / 8 轮软上限 / 「别问了」立即收敛。
 */
class AgentCreationUnderstandingTest {

    // ---------- u_det 确定性字段覆盖分 ----------

    @Test
    fun deterministicScoreWeightsFollowTable() {
        val empty = NewSessionConfiguration()
        assertEquals(0, AgentCreationUnderstanding.deterministicScore(empty, hasAnalyzedDocument = false))

        val goalOnly = empty.copy(goal = "期末冲刺")
        assertEquals(20, AgentCreationUnderstanding.deterministicScore(goalOnly, false))

        val requiredReady = empty.copy(title = "t", learnerRole = "r")
        assertEquals(35, AgentCreationUnderstanding.deterministicScore(requiredReady, false))

        val full = empty.copy(
            goal = "g", learnerRole = "r", title = "t", learnerProfile = "p",
            dialogueStrategy = "多追问", plan = "分三阶段",
            story = "晚自习教室"
        )
        // 20+20+15+5(profile)+15+10+5 = 90（persona 未填）
        assertEquals(90, AgentCreationUnderstanding.deterministicScore(full, false))
        // persona 15 + 文档 10 → 115 钳到 100
        assertEquals(100, AgentCreationUnderstanding.deterministicScore(full.copy(persona = "较真"), true))
    }

    @Test
    fun personaCarriesChainWeight() {
        val empty = NewSessionConfiguration()
        assertEquals(15, AgentCreationUnderstanding.deterministicScore(empty.copy(persona = "慢热较真"), false))
        assertTrue(AgentCreationUnderstanding.hasPersona(empty.copy(persona = "x")))
        assertFalse(AgentCreationUnderstanding.hasPersona(empty))
    }

    @Test
    fun teachingStyleAndConstraintsDetection() {
        val base = NewSessionConfiguration()
        assertFalse(AgentCreationUnderstanding.hasTeachingStyle(base))
        assertFalse(AgentCreationUnderstanding.hasConstraints(base))

        assertTrue(AgentCreationUnderstanding.hasTeachingStyle(base.copy(dialogueStrategy = "先复述再追问")))
        assertTrue(AgentCreationUnderstanding.hasTeachingStyle(base.copy(feedbackIntensity = 4)))
        assertTrue(AgentCreationUnderstanding.hasTeachingStyle(base.copy(probingIntensity = 2)))
        assertTrue(AgentCreationUnderstanding.hasTeachingStyle(base.copy(scaffoldingIntensity = 5)))

        assertTrue(AgentCreationUnderstanding.hasConstraints(base.copy(plan = "三阶段")))
        assertTrue(AgentCreationUnderstanding.hasConstraints(base.copy(stageMilestones = "阶段一：概念")))
        assertTrue(AgentCreationUnderstanding.hasConstraints(base.copy(learningScope = "力学")))
    }

    @Test
    fun openingMessageDeviationCountsAsStory() {
        val base = NewSessionConfiguration()
        val customized = base.copy(openingMessage = "老师，我卡住了……")
        assertEquals(5, AgentCreationUnderstanding.deterministicScore(customized, false))
    }

    // ---------- 双源融合 ----------

    @Test
    fun fuseBlendsHalfHalf() {
        // 0.5×80 + 0.5×60 = 70
        assertEquals(70, AgentCreationUnderstanding.fuse(80, 60, 0, requiredFieldsReady = true))
    }

    @Test
    fun fuseCapsAtSixtyWhenRequiredMissing() {
        assertEquals(60, AgentCreationUnderstanding.fuse(100, 100, 0, requiredFieldsReady = false))
        assertEquals(50, AgentCreationUnderstanding.fuse(85, 15, 0, requiredFieldsReady = false))
    }

    @Test
    fun fuseIsMonotonicNonDecreasing() {
        // 新一轮算出来更低时保持上一轮值
        assertEquals(58, AgentCreationUnderstanding.fuse(10, 10, 58, requiredFieldsReady = true))
        // 但不超过当前封顶
        assertEquals(60, AgentCreationUnderstanding.fuse(10, 10, 85, requiredFieldsReady = false))
    }

    @Test
    fun fuseFallsBackToDeterministicWhenLlmScoreMissing() {
        assertEquals(45, AgentCreationUnderstanding.fuse(null, 45, 0, requiredFieldsReady = true))
    }

    // ---------- title 兜底提案 ----------

    @Test
    fun proposeTitleUsesFirstClauseCappedAtFourteen() {
        assertEquals(
            "把浮力讲明白 · 讲学练",
            AgentCreationUnderstanding.proposeTitle("把浮力讲明白，期末要考")
        )
        val long = "我想系统地把高中物理电磁学这一块彻底搞懂并且能讲给别人听"
        val title = AgentCreationUnderstanding.proposeTitle(long)
        assertTrue(title.endsWith(" · 讲学练"))
        assertEquals(14, title.removeSuffix(" · 讲学练").length)
        assertEquals("新会话 · 讲学练", AgentCreationUnderstanding.proposeTitle("   "))
    }

    // ---------- 追问策略 planner ----------

    @Test
    fun plannerFollowsPriorityQueue() {
        val planner = AgentCreationFollowUpPlanner()
        val empty = NewSessionConfiguration()

        // goal 空 → 先问学习目标
        assertEquals("学习目标", planner.strategyFor(empty, 0, false).targetFollowUpField)

        // goal 填了 → learnerRole
        val withGoal = empty.copy(goal = "g")
        assertEquals("学习者角色", planner.strategyFor(withGoal, 20, false).targetFollowUpField)

        // goal+role 填了 → persona（R84：目标 → 人物性格 → 教学方式的中间环）
        val withRole = withGoal.copy(learnerRole = "r")
        assertEquals("人物性格", planner.strategyFor(withRole, 40, false).targetFollowUpField)

        // goal+role+persona+title 都填 → teachingStyle
        val ready = withRole.copy(persona = "较真", title = "t")
        assertEquals("教学风格偏好", planner.strategyFor(ready, 55, false).targetFollowUpField)
    }

    @Test
    fun plannerSkipsFieldAfterTwoUnansweredAsks() {
        val planner = AgentCreationFollowUpPlanner()
        val empty = NewSessionConfiguration()

        assertEquals("学习目标", planner.strategyFor(empty, 0, false).targetFollowUpField)
        planner.recordRound("学习目标")
        assertEquals("学习目标", planner.strategyFor(empty, 0, false).targetFollowUpField)
        planner.recordRound("学习目标")

        // 同字段问满 2 次 → 降级到下一字段
        assertEquals("学习者角色", planner.strategyFor(empty, 0, false).targetFollowUpField)
    }

    @Test
    fun plannerConvergesAtSoftRoundCap() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(AgentCreationFollowUpPlanner.SOFT_ROUND_CAP) {
            assertFalse(planner.shouldConverge())
            planner.recordRound(null)
        }
        assertTrue(planner.shouldConverge())
        val strategy = planner.strategyFor(NewSessionConfiguration(), 0, false)
        assertTrue(strategy.converge)
        assertNull(strategy.targetFollowUpField)
    }

    @Test
    fun plannerMustProposeTitleWhenGoalSetAndTitleBlank() {
        val planner = AgentCreationFollowUpPlanner()
        val draft = NewSessionConfiguration(goal = "g")
        assertTrue(planner.strategyFor(draft, 20, false).mustProposeTitle)
        assertFalse(planner.strategyFor(draft.copy(title = "t"), 35, false).mustProposeTitle)
    }

    @Test
    fun stopAskingDetection() {
        assertTrue(AgentCreationFollowUpPlanner.isStopAsking("别问了，直接生成吧"))
        assertTrue(AgentCreationFollowUpPlanner.isStopAsking("不用再问了"))
        assertTrue(AgentCreationFollowUpPlanner.isStopAsking("就这样吧"))
        assertFalse(AgentCreationFollowUpPlanner.isStopAsking("我想再聊聊学习目标"))
    }

    @Test
    fun strategyCarriesDeterministicScoreAndAskedCounts() {
        val planner = AgentCreationFollowUpPlanner()
        planner.recordRound("学习目标")
        val strategy = planner.strategyFor(NewSessionConfiguration(), 42, documentAvailable = true)
        assertEquals(42, strategy.deterministicUnderstanding)
        assertEquals(1, strategy.rounds)
        assertEquals(1, strategy.askedCounts["学习目标"])
        assertTrue(strategy.documentAvailable)
    }
}
