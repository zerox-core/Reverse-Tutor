package com.reversetutor.graphtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.feature.memory.BlackHoleGraphScreen
import com.reversetutor.feature.memory.GraphLayoutEdge
import com.reversetutor.feature.memory.GraphLayoutNode
import com.reversetutor.feature.memory.GraphScope
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import com.reversetutor.feature.memory.GraphRenderStatus

/**
 * 黑洞图谱真机交互验证专用壳。
 *
 * 数据为内置演示集（与真实 App 数据完全隔离），目的在于验证：
 * 物理手感（拖拽/惯性/碰撞/弹开）、遗忘吸收节奏、黑洞轨道、
 * 相机平移缩放、节点选中与时间倍率。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                // 回到前台（ON_RESUME）自动重置图谱：
                // 演示数据被黑洞吸收完后，无需杀进程重开。
                var resetKey by remember { mutableIntStateOf(0) }
                var selectedNodeId by remember { mutableStateOf<String?>(null) }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            resetKey += 1
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val graphState = remember(resetKey) { DemoGraph.state() }
                BlackHoleGraphScreen(
                    state = graphState,
                    onSelectedNodeChange = { selectedNodeId = it },
                    title = "黑洞图谱测试",
                    subtitle = "演示数据 · 真机交互验证版",
                    onBack = { finish() }
                )
            }
        }
    }
}

private object DemoGraph {

    private data class Spec(
        val id: String,
        val label: String,
        val kind: GraphNodeKind,
        val status: GraphNodeStatus = GraphNodeStatus.Active
    )

    private val specs: List<Spec> = listOf(
        // 目标簇
        Spec("g1", "高考数学冲刺", GraphNodeKind.Requirement),
        Spec("g2", "错题复盘", GraphNodeKind.Requirement),
        Spec("g3", "导数大题保分", GraphNodeKind.Requirement, GraphNodeStatus.NeedsReview),
        // 概念簇
        Spec("c1", "导数", GraphNodeKind.Concept),
        Spec("c2", "三角函数", GraphNodeKind.Concept),
        Spec("c3", "数列", GraphNodeKind.Concept),
        Spec("c4", "立体几何", GraphNodeKind.Concept),
        Spec("c5", "概率统计", GraphNodeKind.Concept),
        Spec("c6", "圆锥曲线", GraphNodeKind.Concept),
        Spec("c7", "平面向量", GraphNodeKind.Concept),
        Spec("c8", "函数单调性", GraphNodeKind.Concept, GraphNodeStatus.Approved),
        Spec("c9", "参数方程", GraphNodeKind.Other, GraphNodeStatus.Archived),
        // 资料簇
        Spec("s1", "五年真题", GraphNodeKind.Source),
        Spec("s2", "错题本", GraphNodeKind.Source),
        Spec("s3", "一轮复习讲义", GraphNodeKind.Source),
        Spec("s4", "课堂笔记", GraphNodeKind.Source),
        // 会话簇
        Spec("m1", "10月周测复盘", GraphNodeKind.Session),
        Spec("m2", "导数专题问答", GraphNodeKind.Session),
        Spec("m3", "开学摸底分析", GraphNodeKind.Session),
        // 角色簇
        Spec("p1", "王老师", GraphNodeKind.Person),
        Spec("p2", "我", GraphNodeKind.Person)
    )

    private val edgeSpecs: List<Pair<String, String>> = listOf(
        "g1" to "g2", "g1" to "g3", "g2" to "g3",
        "g1" to "c1", "g3" to "c1", "g1" to "c6", "g1" to "c4",
        "g2" to "c2", "g2" to "c5",
        "c1" to "c8", "c2" to "c7", "c3" to "c1", "c4" to "c7", "c6" to "c7", "c6" to "c9",
        "s1" to "g1", "s2" to "g2", "s3" to "c5", "s3" to "c3", "s4" to "c4",
        "m1" to "g2", "m2" to "c1", "m3" to "c5",
        "p1" to "m2", "p1" to "m1", "p2" to "m1", "p2" to "m2", "p2" to "m3"
    )

    fun state(): KnowledgeGraphUiState = KnowledgeGraphUiState(
        scope = GraphScope.Global,
        status = GraphRenderStatus.Ready,
        title = "黑洞图谱测试",
        summary = "演示数据",
        nodes = specs.mapIndexed { index, spec ->
            GraphLayoutNode(
                id = spec.id,
                label = spec.label,
                kind = spec.kind,
                status = spec.status,
                x = 0f,
                y = 0f,
                radius = if (spec.kind == GraphNodeKind.Requirement) 34f else 26f
            )
        },
        visibleEdges = edgeSpecs.mapIndexed { index, (from, to) ->
            GraphLayoutEdge(
                id = "e$index",
                fromNodeId = from,
                toNodeId = to,
                relation = "关联"
            )
        },
        invalidEdgeCount = 0
    )
}
