package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.graph.KnowledgeGraphBuilder
import com.reversetutor.feature.reversetutor.score.ScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 图谱 + 评分对拍测试。期望值由 spike 实际运行输出固定（app/tools/memory.py、evaluation.py）。
 */
class GraphScoreConformanceTest {

    private lateinit var catalog: CurriculumCatalog
    private lateinit var builder: KnowledgeGraphBuilder

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir")
        val assets = "$userDir/src/main/assets"
        catalog = CurriculumCatalog(File("$assets/curriculum.json").readText())
        builder = KnowledgeGraphBuilder(catalog)
    }

    // ---------- 课标先修边 ----------

    @Test
    fun curriculumEdgesCount28() {
        // Python: update_knowledge_graph 后 edges=28
        assertEquals(28, builder.curriculumEdges().size)
        // 二次函数是配方法与顶点公式的课标先修
        assertTrue(builder.curriculumEdges().any { it.from == "二次函数" && it.to == "配方法与顶点公式" })
    }

    // ---------- update_knowledge_graph 两轮（Python 实跑基准） ----------

    @Test
    fun updateGraphRound1MatchesPython() {
        val r = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            addMastered = listOf("集合与常用逻辑用语"),
            addPending = listOf("配方法与顶点公式")))
        // nodes: 集合(mastered, 0.3, 高一, 1)、配方法(learning, 0.15, 高一, 2)
        val jh = r.graph.nodes.first { it.id == "集合与常用逻辑用语" }
        assertEquals("mastered", jh.status)
        assertEquals(0.3, jh.level, 1e-9)
        assertEquals("高一", jh.grade)
        assertEquals(1, jh.difficulty)
        val pf = r.graph.nodes.first { it.id == "配方法与顶点公式" }
        assertEquals("learning", pf.status)
        assertEquals(0.15, pf.level, 1e-9)
        assertEquals("高一", pf.grade)
        assertEquals(2, pf.difficulty)
        // next_optimal: ['函数的概念与表示']
        assertEquals(listOf("函数的概念与表示"), r.nextOptimalConcepts)
        // progress 0.5、weak、清单合并
        assertEquals(0.5, r.learningProgress, 1e-9)
        assertEquals(listOf("配方法与顶点公式"), r.weakAreas)
        assertEquals(listOf("集合与常用逻辑用语"), r.masteredConcepts)
        assertEquals(listOf("配方法与顶点公式"), r.confusedConcepts)
    }

    @Test
    fun updateGraphRound2MatchesPython() {
        val r1 = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            addMastered = listOf("集合与常用逻辑用语"),
            addPending = listOf("配方法与顶点公式")))
        val r2 = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = r1.graph,
            oldMastered = r1.masteredConcepts,
            oldConfused = r1.confusedConcepts,
            addMastered = listOf("配方法与顶点公式")))
        // Python round2: next_optimal 仍为 ['函数的概念与表示']、progress 1.0
        assertEquals(listOf("函数的概念与表示"), r2.nextOptimalConcepts)
        assertEquals(1.0, r2.learningProgress, 1e-9)
        // mastered 升级时 pending 不覆盖 mastered；weak 清空
        assertTrue(r2.weakAreas.isEmpty())
        // 清单：mastered∪、confused−
        assertEquals(listOf("配方法与顶点公式", "集合与常用逻辑用语"), r2.masteredConcepts)
        assertTrue(r2.confusedConcepts.isEmpty())
    }

    @Test
    fun updateGraphMasteryNotDowngradedByPending() {
        // 先记 mastered，再以 pending 传入同名概念 → 保持 mastered、level 不缩水
        val r1 = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            addMastered = listOf("函数的奇偶性")))
        val r2 = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = r1.graph, oldMastered = r1.masteredConcepts,
            addPending = listOf("函数的奇偶性")))
        val n = r2.graph.nodes.first { it.id == "函数的奇偶性" }
        assertEquals("mastered", n.status)
    }

    // ---------- evaluate_session_quality（Python 实跑基准） ----------

    @Test
    fun qualityMatchesPythonBaseline() {
        // Python: rounds=6, tool_div=4, bt=1, sg=2, level=3 →
        // quality=0.84（0.845 银行家舍入！）、goal=0.68、eng=0.84、eff=1.0、depth=0.86
        val q = ScoreEngine.evaluateSessionQuality(ScoreEngine.QualityInput(
            currentRound = 6, userMsgCount = 0, toolDiversity = 4,
            breakthroughs = 1, struggles = 2, understandingLevel = 3,
            toolsUsed = listOf("scaffold_problem")))
        assertEquals(0.68, q.goalAchievement, 1e-9)
        assertEquals(0.84, q.studentEngagement, 1e-9)
        assertEquals(1.0, q.teachingEfficiency, 1e-9)
        assertEquals(0.86, q.conceptualDepth, 1e-9)
        assertEquals(0.84, q.qualityScore, 1e-9)
        // 文案
        assertEquals(listOf("发生了 1 次顿悟，核心概念被真正讲通", "使用了 4 种不同工具，策略路径多样"), q.highlights)
        assertEquals(listOf(
            "未测试迁移能力（可用 challenge_teacher_explanation 或变式问题）",
            "有 2 次卡顿，可以更早给出脚手架"), q.missedOpportunities)
    }

    @Test
    fun qualityDefaultHighlightsWhenEmpty() {
        val q = ScoreEngine.evaluateSessionQuality(ScoreEngine.QualityInput(
            currentRound = 2, userMsgCount = 0, toolDiversity = 1,
            breakthroughs = 0, struggles = 0, understandingLevel = 2))
        assertEquals(listOf("完成了基本的教与学闭环"), q.highlights)
    }

    @Test
    fun qualityRoundsFallbackToUserMsgCount() {
        // currentRound=0 时 rounds = max(userMsgCount, 1)
        val q = ScoreEngine.evaluateSessionQuality(ScoreEngine.QualityInput(
            currentRound = 0, userMsgCount = 3, toolDiversity = 0,
            breakthroughs = 0, struggles = 0, understandingLevel = 1))
        // engagement = 0.4 + 0.1*3 - 0 = 0.7
        assertEquals(0.7, q.studentEngagement, 1e-9)
    }

    // ---------- detect_teaching_failure（Python 实跑基准） ----------

    @Test
    fun failureStallResetsOnProgress() {
        // Python: [1,1,1,2(frustrated)] → stall=0（尾部提升即停）、is_failing=false
        val f = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(1, ""),
            ScoreEngine.InteractionItem(1, ""),
            ScoreEngine.InteractionItem(1, ""),
            ScoreEngine.InteractionItem(2, "frustrated")))
        assertFalse(f.isFailing)
        assertEquals(0, f.stallRounds)
        assertEquals(listOf("学生情绪在近期转为 frustrated/discouraged"), f.failurePoints)
        assertEquals(listOf("保持当前策略并继续观察"), f.correctiveActions)
    }

    @Test
    fun failureDeterioratedWithStall2() {
        // Python: [2, 2(frustrated), 2] → stall=2、恶化 → is_failing=true
        val f = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(2, ""),
            ScoreEngine.InteractionItem(2, "frustrated"),
            ScoreEngine.InteractionItem(2, "")))
        assertTrue(f.isFailing)
        assertEquals(2, f.stallRounds)
        assertTrue(f.probableCauses.contains("当前教学策略与该学生不匹配"))
    }

    @Test
    fun failurePureStall3() {
        // [1,1,1,1] → stall=3 → is_failing=true，两条 failure_points
        val f = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(1, ""), ScoreEngine.InteractionItem(1, ""),
            ScoreEngine.InteractionItem(1, ""), ScoreEngine.InteractionItem(1, "")))
        assertTrue(f.isFailing)
        assertEquals(3, f.stallRounds)
        assertTrue(f.failurePoints.contains("连续 3 轮理解水平无提升"))
        assertFalse(f.failurePoints.any { it.contains("frustrated") })
    }

    @Test
    fun failureHealthyPath() {
        // [1,2,3] → stall=0、无恶化 → 未见明显失败信号
        val f = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(1, ""), ScoreEngine.InteractionItem(2, "curious"),
            ScoreEngine.InteractionItem(3, "")))
        assertFalse(f.isFailing)
        assertEquals(listOf("未见明显失败信号"), f.failurePoints)
        assertEquals("未指定", f.currentStrategy)
    }

    // ---------- clamp_int 语义（先 round 后 clamp） ----------

    @Test
    fun levelOfRoundsBeforeClamp() {
        // Python clamp_int: int(max(1, min(5, round(v))))
        assertEquals(5, ScoreEngine.levelOf(4.6))   // round→5
        assertEquals(5, ScoreEngine.levelOf(5.7))   // round→6→clamp 5
        assertEquals(1, ScoreEngine.levelOf(0.5))   // round(0.5)=0（HALF_EVEN）→clamp 1
        assertEquals(1, ScoreEngine.levelOf(null))
    }
}
