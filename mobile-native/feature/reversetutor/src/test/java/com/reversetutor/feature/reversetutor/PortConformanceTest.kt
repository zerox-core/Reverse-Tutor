package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.session.BreakthroughEvent
import com.reversetutor.feature.reversetutor.session.KgNode
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.session.ToolTraceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 对拍测试（conformance）：用例翻译自 spike tests/，断言同输入同输出。
 * 数据源即运行时 assets JSON（内容与代码分离的一致性由此保证）。
 */
class PortConformanceTest {

    private lateinit var catalog: CurriculumCatalog
    private lateinit var cards: StudentCards

    @Before
    fun setUp() {
        // 单测直接从 spike 导出的资产文件读取（与 Android runtime 同一份数据）
        val base = System.getProperty("user.dir")
        val assets = if (File("$base/src/main/assets").isDirectory) "$base/src/main/assets"
        else "$base/src/main/assets" // android library 单测 cwd = 模块根
        catalog = CurriculumCatalog(File("$assets/curriculum.json").readText())
        cards = StudentCards(File("$assets/student_cards.json").readText())
    }

    // ---------- CurriculumCatalog：数据完整性 ----------

    @Test
    fun curriculumHas30Concepts() {
        // spike: CURRICULUM["高中数学"] 30 概念、1 学科、grade_order 3 学段
        assertEquals(1, catalog.subjectCount())
        assertEquals(30, catalog.conceptCount())
    }

    @Test
    fun findHitsAliasAndSubstring() {
        // spike 用例：别名直查
        val r1 = catalog.findInCurriculum("配方")
        assertEquals("配方法与顶点公式", r1.conceptId)
        // 互相包含：查询串更具体（包含概念 id）
        val r2 = catalog.findInCurriculum("函数的单调性与最值的应用")
        assertEquals("函数的单调性与最值", r2.conceptId)
        // 未命中
        assertTrue(catalog.findInCurriculum("量子纠缠").entry == null)
    }

    @Test
    fun conceptStatusPriorityGradeFirst() {
        // 学段未到：高一学生问导数（高二）→ not_learned（curriculum 源）
        val s = RTSession(grade = "高一", understandingLevel = 5, topic = "高中数学")
        val st = catalog.conceptStatus("导数的概念与几何意义", s)
        assertEquals("not_learned", st.expectedStatus)
        assertEquals("curriculum", st.source)
        assertTrue(st.reason.contains("尚未学到"))
    }

    @Test
    fun conceptStatusPriorityKnowledgeGraphOverrides() {
        // 对话事实优先：图谱已记 mastered → 覆盖难度阈值
        val s = RTSession(
            grade = "高一", understandingLevel = 1, topic = "高中数学",
            knowledgeGraphNodes = listOf(KgNode("集合与常用逻辑用语", "mastered", 4.0))
        )
        val st = catalog.conceptStatus("集合与常用逻辑用语", s)
        assertEquals("mastered", st.expectedStatus)
        assertEquals("knowledge_graph", st.source)
    }

    @Test
    fun conceptStatusDifficultyThresholds() {
        // 难度阈值：高一全在 diff 1/2，diff 3 在高二 —— 用例与课标分布对齐
        fun statusFor(concept: String, grade: String, level: Int) =
            catalog.conceptStatus(concept, RTSession(grade = grade, understandingLevel = level, topic = "高中数学"))
        val d1 = statusFor("集合与常用逻辑用语", "高一", 2)        // difficulty 1 ≤ 2
        assertEquals("mastered", d1.expectedStatus)
        val d3 = statusFor("圆锥曲线的标准方程与性质", "高二", 2)  // difficulty 3 == level+1
        assertEquals("learning", d3.expectedStatus)
        val d5 = statusFor("对称化构造", "高三", 2)                // difficulty 5
        assertEquals("not_learned", d5.expectedStatus)
    }

    @Test
    fun conceptStatusBlindSpotConfused() {
        // 盲区命中 → confused（即便难度低于水平）
        val s = RTSession(
            grade = "高一", understandingLevel = 3, topic = "高中数学",
            targetBlindSpot = "函数的单调性与最值"
        )
        assertEquals("confused", catalog.conceptStatus("函数的单调性与最值", s).expectedStatus)
    }

    @Test
    fun conceptStatusFallbackWhenNotInCurriculum() {
        val low = catalog.conceptStatus("量子纠缠", RTSession(grade = "高一", understandingLevel = 2))
        assertEquals("not_learned", low.expectedStatus)
        assertEquals("fallback_level", low.source)
        val high = catalog.conceptStatus("量子纠缠", RTSession(grade = "高三", understandingLevel = 4))
        assertEquals("mastered", high.expectedStatus)
    }

    // ---------- StudentCards：结构完整性 + 桶选择 ----------

    @Test
    fun allThreeCardsLoaded() {
        assertEquals(setOf("proud_average", "curious_talker", "quiet_slow_burn"), cards.cards.keys)
        for ((cid, card) in cards.cards) {
            assertEquals(cid, card.cardId)
            assertTrue(card.identityText.isNotEmpty() && card.personalityText.isNotEmpty())
            for (b in StudentCards.BUCKET_NAMES) {
                assertTrue("$cid.$b 应至少 5 条示例", (card.exampleBuckets[b]?.size ?: 0) >= 5)
            }
        }
        // 169 条总量（57+56+56）
        val total = cards.cards.values.sumOf { c -> c.exampleBuckets.values.sumOf { it.size } }
        assertEquals(169, total)
    }

    private fun session(
        level: Int = 3,
        round: Int = 2,
        emotion: String = "neutral",
        toolTrace: List<ToolTraceEntry> = emptyList(),
        breakthroughs: List<BreakthroughEvent> = emptyList(),
        confused: List<String> = emptyList()
    ) = RTSession(
        understandingLevel = level, currentRound = round, emotion = emotion,
        toolTrace = toolTrace, breakthroughEvents = breakthroughs, confusedConcepts = confused
    )

    @Test
    fun bucketsDefaultConfident() {
        assertEquals(listOf("confident_challenging"), StudentCards.selectBuckets(session()))
    }

    @Test
    fun bucketsLowLevelConfused() {
        assertEquals(listOf("confused_low"), StudentCards.selectBuckets(session(level = 2)))
    }

    @Test
    fun bucketsBreakthroughFirst() {
        val s = session(level = 2, breakthroughs = listOf(BreakthroughEvent(round = 2)))
        assertEquals(listOf("breakthrough", "confused_low"), StudentCards.selectBuckets(s))
    }

    @Test
    fun bucketsFacadeCracking() {
        val s = session(toolTrace = listOf(
            ToolTraceEntry("make_deliberate_mistake", 1),
            ToolTraceEntry("check_understanding", 1)))
        assertEquals(listOf("facade_cracking"), StudentCards.selectBuckets(s))
    }

    @Test
    fun bucketsFacadeCrackingWindowExcludesOldRounds() {
        // round 9 时 round=2 的调用在窗口外（round >= cur-1）
        val s = session(round = 9, toolTrace = listOf(
            ToolTraceEntry("make_deliberate_mistake", 2),
            ToolTraceEntry("check_understanding", 2)))
        assertFalse(StudentCards.selectBuckets(s).contains("facade_cracking"))
    }

    @Test
    fun bucketsPressuredByAuthority() {
        val s = session(toolTrace = listOf(ToolTraceEntry("design_counterexample", 1)))
        assertEquals(listOf("pressured_by_authority"), StudentCards.selectBuckets(s))
    }

    @Test
    fun bucketsClosingTakesPrioritySlot() {
        // plan_session_closure 在窗口内 → 告别声线第一，且挤占后仍保 3 桶
        val s = session(toolTrace = listOf(
            ToolTraceEntry("plan_session_closure", 1),
            ToolTraceEntry("design_counterexample", 1)))
        val b = StudentCards.selectBuckets(s)
        assertEquals("wrapping_up", b[0])
        assertTrue(b.contains("pressured_by_authority"))
        assertTrue(b.size <= 3)
    }

    @Test
    fun bucketsBlindspotForHighLevelWithConfusion() {
        val s = session(level = 4, confused = listOf("配方法与顶点公式"))
        assertEquals(listOf("confused_blindspot"), StudentCards.selectBuckets(s))
    }

    @Test
    fun renderCardPromptContainsIdentityAndVoice() {
        val card = cards.getCard("proud_average")!!
        val out = StudentCards.renderCardPrompt(card, session())
        assertTrue(out.contains("陈小磊"))
        assertTrue(out.contains("风格参考") && out.contains("当前声线参考"))
        assertTrue(out.contains("· ")) // 示例台词行
        assertTrue(out.contains("口癖"))
    }

    @Test
    fun renderCardPromptVoiceSwitchesWithState() {
        val card = cards.getCard("quiet_slow_burn")!!
        val calm = StudentCards.renderCardPrompt(card, session())
        val closing = StudentCards.renderCardPrompt(
            card, session(toolTrace = listOf(ToolTraceEntry("plan_session_closure", 1))))
        assertTrue(closing.contains("当前声线参考[wrapping_up]"))
        assertFalse(calm.contains("当前声线参考[wrapping_up]"))
    }
}
