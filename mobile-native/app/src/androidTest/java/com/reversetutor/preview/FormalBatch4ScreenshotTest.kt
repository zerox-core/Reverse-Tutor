package com.reversetutor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.feature.memory.FormalBranchGraphHeader
import com.reversetutor.feature.memory.FormalBranchGraphScreen
import com.reversetutor.feature.memory.FormalGlobalKnowledgeGraphScreen
import com.reversetutor.feature.memory.FormalSessionWorldTreeScreen
import com.reversetutor.feature.memory.GraphScope
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch4ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureGlobalOverview() = capture("global-overview-717-378.png") {
        FormalGlobalKnowledgeGraphScreen(globalState(), {}, onBack = {}, onSearch = {}, onMore = {})
    }

    @Test
    fun captureGlobalDetail() = capture("global-detail-717-555.png") {
        FormalGlobalKnowledgeGraphScreen(globalState(selectedNodeId = "function"), {}, onBack = {}, onSearch = {}, onMore = {})
    }

    @Test
    fun captureBranchClose() = capture("branch-close-717-167.png") {
        FormalBranchGraphScreen(branchHeader(), branchState(), {}, {}, onMore = {})
    }

    @Test
    fun captureBranchDetail() = capture("branch-detail-717-268.png") {
        FormalBranchGraphScreen(branchHeader(), branchState(selectedNodeId = "domain-trap"), {}, {}, onMore = {})
    }

    @Test
    fun captureSessionTree() = capture("session-tree-717-782.png") {
        FormalSessionWorldTreeScreen(
            title = "Python 概念讲解",
            state = sessionState(),
            showLockedNodes = true,
            onShowLockedNodesChange = {},
            onBack = {},
            onSelectedNodeChange = {},
            onSearch = {},
            onMore = {}
        )
    }

    @Test
    fun captureLockedDetail() = capture("session-locked-717-935.png") {
        FormalSessionWorldTreeScreen(
            title = "Python 概念讲解",
            state = sessionState(selectedNodeId = "decorator"),
            showLockedNodes = true,
            onShowLockedNodesChange = {},
            onBack = {},
            onSelectedNodeChange = {},
            onSearch = {},
            onMore = {}
        )
    }

    @Test
    fun captureHiddenLocked() = capture("session-hidden-800-52.png") {
        FormalSessionWorldTreeScreen(
            title = "Python 概念讲解",
            state = sessionState(),
            showLockedNodes = false,
            onShowLockedNodesChange = {},
            onBack = {},
            onSelectedNodeChange = {},
            onSearch = {},
            onMore = {}
        )
    }

    private fun branchHeader() = FormalBranchGraphHeader(
        title = "高三数学讲题冲刺",
        subtitle = "世界树 · 小岚",
        contextTitle = "当前讲解 · 函数与图像",
        contextSubtitle = "定义域陷阱",
        progressLabel = "12 / 68"
    )

    private fun globalState(selectedNodeId: String? = null) = KnowledgeGraphUiState.from(
        nodes = listOf(
            node("computer", "计算机", GraphNodeKind.Session),
            node("math", "数学", GraphNodeKind.Session),
            node("python", "Python", GraphNodeKind.Concept),
            node("high-math", "高中数学", GraphNodeKind.Requirement),
            node("university-math", "大学数学", GraphNodeKind.Requirement),
            node("data-analysis", "数据分析", GraphNodeKind.Source),
            node("statistics", "统计", GraphNodeKind.Person),
            node("algorithm", "算法", GraphNodeKind.Concept),
            node("data-structure", "数据结构与算法", GraphNodeKind.Concept),
            node("operating-system", "操作系统", GraphNodeKind.Concept),
            node("network", "计算机网络", GraphNodeKind.Concept),
            node("database", "数据库", GraphNodeKind.Concept),
            node("function", "函数", GraphNodeKind.Concept),
            node("sequence", "数列", GraphNodeKind.Requirement),
            node("analytic-geometry", "解析几何", GraphNodeKind.Requirement),
            node("probability", "概率", GraphNodeKind.Requirement),
            node("limit", "极限", GraphNodeKind.Requirement),
            node("calculus", "微积分", GraphNodeKind.Requirement),
            node("linear-algebra", "线性代数", GraphNodeKind.Requirement),
            node("probability-theory", "概率论", GraphNodeKind.Requirement),
            node("variables", "变量与类型", GraphNodeKind.Concept),
            node("oop", "面向对象", GraphNodeKind.Concept),
            node("exception", "异常结构", GraphNodeKind.Concept),
            node("module", "模块", GraphNodeKind.Concept),
            node("decorator", "装饰器", GraphNodeKind.Concept),
            node("generator", "生成器", GraphNodeKind.Concept),
            node("file", "文件操作", GraphNodeKind.Source),
            node("visualization", "可视化", GraphNodeKind.Source),
            node("machine-learning", "机器学习", GraphNodeKind.Source),
            node("higher-math", "高等数学", GraphNodeKind.Requirement),
            node("logic", "逻辑与证明", GraphNodeKind.Requirement),
            node("set-theory", "集合论", GraphNodeKind.Requirement),
            node("mathematical-thinking", "数理思维", GraphNodeKind.Requirement),
            node("ai", "人工智能", GraphNodeKind.Concept)
        ),
        edges = listOf(
            edge("computer", "algorithm"),
            edge("computer", "data-structure"),
            edge("computer", "operating-system"),
            edge("computer", "network"),
            edge("computer", "database"),
            edge("computer", "python"),
            edge("computer", "ai"),
            edge("math", "higher-math"),
            edge("math", "logic"),
            edge("math", "set-theory"),
            edge("math", "mathematical-thinking"),
            edge("math", "high-math"),
            edge("math", "university-math"),
            edge("python", "function"),
            edge("python", "variables"),
            edge("python", "oop"),
            edge("python", "exception"),
            edge("python", "module"),
            edge("python", "decorator"),
            edge("python", "generator"),
            edge("python", "file"),
            edge("python", "data-analysis"),
            edge("high-math", "sequence"),
            edge("high-math", "analytic-geometry"),
            edge("high-math", "probability"),
            edge("university-math", "limit"),
            edge("university-math", "calculus"),
            edge("university-math", "linear-algebra"),
            edge("university-math", "probability-theory"),
            edge("data-analysis", "statistics"),
            edge("data-analysis", "visualization"),
            edge("data-analysis", "machine-learning"),
            edge("ai", "machine-learning")
        ),
        selectedNodeId = selectedNodeId,
        scope = GraphScope.Global
    )

    private fun sessionState(selectedNodeId: String? = null) = KnowledgeGraphUiState.from(
        nodes = listOf(
            node("python", "Python 概念讲解", GraphNodeKind.Session),
            node("student", "学生角色", GraphNodeKind.Person, GraphNodeStatus.Approved),
            node("goal", "学习目标", GraphNodeKind.Requirement),
            node("plan", "学习计划", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("story", "故事剧情", GraphNodeKind.Requirement),
            node("materials", "资料引用", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("system", "设定学架", GraphNodeKind.Other, GraphNodeStatus.Approved),
            node("growth", "知识生长", GraphNodeKind.Other),
            node("basics", "Python 基础", GraphNodeKind.Concept),
            node("variables", "变量与类型", GraphNodeKind.Concept, GraphNodeStatus.Approved),
            node("function", "函数与返回值", GraphNodeKind.Concept),
            node("parameter", "参数与返回值", GraphNodeKind.Concept),
            node("scope", "作用域", GraphNodeKind.Concept),
            node("collections", "列表与字典", GraphNodeKind.Concept, GraphNodeStatus.Approved),
            node("flow", "条件与循环", GraphNodeKind.Concept),
            node("exception", "异常处理", GraphNodeKind.Concept, GraphNodeStatus.NeedsReview),
            node("file", "文件操作", GraphNodeKind.Concept, GraphNodeStatus.Approved),
            node("module", "模块", GraphNodeKind.Concept, GraphNodeStatus.Hidden),
            node("oop", "面向对象", GraphNodeKind.Concept, GraphNodeStatus.Hidden),
            node("decorator", "装饰器", GraphNodeKind.Concept, GraphNodeStatus.Hidden),
            node("project", "项目封装", GraphNodeKind.Concept, GraphNodeStatus.Hidden)
        ),
        edges = listOf(
            edge("python", "student"),
            edge("python", "goal"),
            edge("python", "plan"),
            edge("python", "story"),
            edge("python", "materials"),
            edge("python", "system"),
            edge("python", "growth"),
            edge("python", "basics"),
            edge("basics", "variables"),
            edge("python", "function"),
            edge("function", "parameter"),
            edge("function", "scope"),
            edge("python", "collections"),
            edge("python", "flow"),
            edge("python", "exception"),
            edge("python", "file"),
            edge("file", "module"),
            edge("module", "oop"),
            edge("oop", "decorator"),
            edge("decorator", "project")
        ),
        selectedNodeId = selectedNodeId,
        scope = GraphScope.Session
    )

    private fun branchState(selectedNodeId: String? = null) = KnowledgeGraphUiState.from(
        nodes = listOf(
            node("high-math", "高三数学", GraphNodeKind.Session),
            node("goal", "学习目标", GraphNodeKind.Requirement),
            node("student", "被教对象", GraphNodeKind.Person),
            node("problem", "学习问题", GraphNodeKind.Concept),
            node("function-image", "函数与图像", GraphNodeKind.Concept),
            node("domain-trap", "定义域陷阱", GraphNodeKind.Concept, GraphNodeStatus.Approved),
            node("materials", "资料库", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("wrong-book", "错题本", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("three-period", "三周期情境", GraphNodeKind.Person),
            node("analysis", "解题诊断", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("chat", "聊天实例", GraphNodeKind.Concept),
            node("method", "追问方式", GraphNodeKind.Source, GraphNodeStatus.Approved),
            node("summary", "检验与复盘", GraphNodeKind.Other)
        ),
        edges = listOf(
            edge("high-math", "goal"),
            edge("high-math", "student"),
            edge("high-math", "problem"),
            edge("problem", "function-image"),
            edge("function-image", "domain-trap"),
            edge("high-math", "materials"),
            edge("materials", "wrong-book"),
            edge("high-math", "three-period"),
            edge("high-math", "analysis"),
            edge("high-math", "chat"),
            edge("high-math", "method"),
            edge("high-math", "summary")
        ),
        selectedNodeId = selectedNodeId,
        scope = GraphScope.Session
    )

    private fun node(
        id: String,
        label: String,
        kind: GraphNodeKind,
        status: GraphNodeStatus = GraphNodeStatus.Active
    ) = GraphNode(
        id = id,
        spaceId = "formal-fixture",
        label = label,
        kind = kind,
        createdAtEpochMillis = 0L,
        status = status
    )

    private fun edge(from: String, to: String) = GraphEdge(
        id = "$from-$to",
        spaceId = "formal-fixture",
        fromNodeId = from,
        toNodeId = to,
        relation = "包含",
        createdAtEpochMillis = 0L
    )

    private fun capture(fileName: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            ReverseTutorTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 390.dp, height = 884.dp)
                            .testTag(FixtureTag)
                    ) { content() }
                }
            }
        }
        val bitmap = composeRule.captureFormalFixture(fixtureTag = FixtureTag)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "formal-batch4").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private companion object {
        const val FixtureTag = "formal-batch4-fixture"
    }
}
