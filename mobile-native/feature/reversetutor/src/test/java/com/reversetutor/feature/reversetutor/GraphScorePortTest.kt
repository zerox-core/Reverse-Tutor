package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.graph.KgEdge
import com.reversetutor.feature.reversetutor.graph.KgGraph
import com.reversetutor.feature.reversetutor.graph.KnowledgeGraphBuilder
import com.reversetutor.feature.reversetutor.score.ScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 图谱与评分的对拍测试：期望值由 spike 实际运行输出固定。
 */
class GraphScorePortTest {

    private lateinit var builder: KnowledgeGraphBuilder

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir")
        val assets = "$userDir/src/main/assets"
        builder = KnowledgeGraphBuilder(CurriculumCatalog(File("$assets/curriculum.json").readText()))
    }

    @Test
    fun curriculumEdgesDerivedFromPrerequisites() {
        // spike: 28 条课标先修边（prereq → concept, "prerequisite"）
        val edges = builder.curriculumEdges()
        assertEquals(28, edges.size)
        assertTrue(edges.all { it.type == "prerequisite" })
    }

    @Test
    fun updateGraphMasteredAndPending() {
        // Python 对拍：
        //   update_graph(mastered=['配方法与顶点公式'], pending=['函数的单调性与最值'])
        //   节点 level: 配方 0.3 mastered / 单调 0.15 learning
        val r = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = KgGraph(),
            addMastered = listOf("配方法与顶点公式"),
            addPending = listOf("函数的单调性与最值")
        ))
        val byId = r.graph.nodes.associateBy { it.id }
        assertEquals("mastered", byId["配方法与顶点公式"]!!.status)
        assertEquals(0.3, byId["配方法与顶点公式"]!!.level, 1e-9)
        assertEquals("learning", byId["函数的单调性与最值"]!!.status)
        assertEquals(0.15, byId["函数的单调性与最值"]!!.level, 1e-9)
        // 课标事实补全：两概念均为高一、难度 2
        assertEquals("高一", byId["配方法与顶点公式"]!!.grade)
        assertEquals(2, byId["函数的单调性与最值"]!!.difficulty)
        // 清单合并
        assertEquals(listOf("配方法与顶点公式"), r.masteredConcepts)
        assertEquals(listOf("函数的单调性与最值"), r.confusedConcepts)
    }

    @Test
    fun pendingDoesNotOverrideMastered() {
        val r = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = KgGraph(nodes = listOf(
                KnowledgeGraphBuilder.KgNodeFull("配方法与顶点公式", "mastered", 0.6))),
            addPending = listOf("配方法与顶点公式")
        ))
        val n = r.graph.nodes.first { it.id == "配方法与顶点公式" }
        assertEquals("mastered", n.status)
        assertEquals(0.6, n.level, 1e-9)
    }

    @Test
    fun nextOptimalFollowsPrereqChain() {
        // Python 对拍：mastered=['集合与常用逻辑用语']（多项先修）→ 后继含其孩子节点，取 3
        val r = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = KgGraph(),
            addMastered = listOf("集合与常用逻辑用语")
        ))
        assertTrue(r.nextOptimalConcepts.isNotEmpty() && r.nextOptimalConcepts.size <= 3)
        // 每个后继自身未 mastered
        for (c in r.nextOptimalConcepts) {
            val node = r.graph.nodes.firstOrNull { it.id == c }
            assertTrue(node == null || node.status != "mastered")
        }
        // 集合的孩子应包含「函数的概念与表示」（prerequisites 首项是集合）
        val children = builder.curriculumEdges().filter { it.from == "集合与常用逻辑用语" }.map { it.to }
        assertTrue(children.contains("函数的概念与表示"))
    }

    @Test
    fun confusedMinusMastered() {
        val r = builder.updateGraph(KnowledgeGraphBuilder.GraphUpdateInput(
            graph = KgGraph(),
            oldConfused = listOf("二次函数"),
            addMastered = listOf("二次函数"),
            addPending = listOf("二次函数", "函数的奇偶性")
        ))
        // 二次函数既在新增 mastered → 从 confused 剔除
        assertTrue(!r.confusedConcepts.contains("二次函数"))
        assertTrue(r.confusedConcepts.contains("函数的奇偶性"))
        assertEquals(listOf("二次函数"), r.masteredConcepts)
    }

    // ---------- ScoreEngine ----------

    @Test
    fun qualityMatchesPython() {
        // Python 对拍：rounds=6, bt=2, sg=0, tool_div=4, level=3
        //   goal=0.3+0.36+0.2=0.86, eng=0.4+0.6=1.0, eff=0.8+0.2=1.0, depth=0.3+0.36+0.2=0.86
        //   quality=(0.86+1.0+1.0+0.86)/4=0.93
        val r = ScoreEngine.evaluateSessionQuality(ScoreEngine.QualityInput(
            currentRound = 6, userMsgCount = 0, toolDiversity = 4,
            breakthroughs = 2, struggles = 0, understandingLevel = 3,
            toolsUsed = listOf("challenge_teacher_explanation", "scaffold_problem")
        ))
        assertEquals(0.86, r.goalAchievement, 1e-9)
        assertEquals(1.0, r.studentEngagement, 1e-9)
        assertEquals(1.0, r.teachingEfficiency, 1e-9)
        assertEquals(0.86, r.conceptualDepth, 1e-9)
        assertEquals(0.93, r.qualityScore, 1e-9)
        assertTrue(r.highlights.any { it.contains("2 次顿悟") })
        assertTrue(r.missedOpportunities.isEmpty())
    }

    @Test
    fun qualityMissedChallengeFlag() {
        val r = ScoreEngine.evaluateSessionQuality(ScoreEngine.QualityInput(
            currentRound = 3, userMsgCount = 0, toolDiversity = 1,
            breakthroughs = 0, struggles = 1, understandingLevel = 2
        ))
        assertTrue(r.missedOpportunities.any { it.contains("未测试迁移能力") })
        assertTrue(r.missedOpportunities.any { it.contains("1 次卡顿") })
    }

    @Test
    fun failureStallDetection() {
        // Python 对拍：levels [1,2,2,2,2] → 从尾往前 2<=2,2<=2,2<=2 stall=3 → is_failing
        val r = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(1, "neutral"),
            ScoreEngine.InteractionItem(2, "neutral"),
            ScoreEngine.InteractionItem(2, "neutral"),
            ScoreEngine.InteractionItem(2, "neutral"),
            ScoreEngine.InteractionItem(2, "neutral")
        ))
        assertTrue(r.isFailing)
        assertEquals(3, r.stallRounds)
        assertTrue(r.failurePoints.any { it.contains("连续 3 轮") })
        assertTrue(r.correctiveActions.contains("切换到几何/图像解释"))
    }

    @Test
    fun failureEmotionOnlyStallTwo() {
        // deteriorated + stall 2 → failing
        val r = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(2, "neutral"),
            ScoreEngine.InteractionItem(2, "frustrated"),
            ScoreEngine.InteractionItem(2, "frustrated")
        ))
        assertTrue(r.isFailing)
    }

    @Test
    fun failureHealthy() {
        val r = ScoreEngine.detectTeachingFailure(listOf(
            ScoreEngine.InteractionItem(2, "neutral"),
            ScoreEngine.InteractionItem(3, "curious")
        ), currentStrategy = "脚手架推进")
        assertTrue(!r.isFailing)
        assertEquals(0, r.stallRounds)
        assertEquals("脚手架推进", r.currentStrategy)
    }
}
