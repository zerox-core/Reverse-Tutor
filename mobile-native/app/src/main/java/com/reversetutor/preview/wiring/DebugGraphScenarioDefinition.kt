package com.reversetutor.preview.wiring

import com.reversetutor.core.data.graph.GraphEdgeInput
import com.reversetutor.core.data.graph.GraphNodeInput
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole

data class DebugGraphScenarioSeed(
    val sessions: List<DebugSessionSeed>,
    val messages: List<Message>,
    val anchors: List<DebugAnchorSeed>,
    val nodes: List<DebugGraphNodeSeed>,
    val edges: List<GraphEdgeInput>
)

data class DebugSessionSeed(
    val id: String,
    val title: String
)

data class DebugAnchorSeed(
    val id: String,
    val title: String,
    val body: String,
    val sourceMessageId: String
)

data class DebugGraphNodeSeed(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val status: GraphNodeStatus = GraphNodeStatus.Active,
    val sourceMemoryId: String? = null,
    val hidden: Boolean = false
) {
    fun toInput(): GraphNodeInput = GraphNodeInput(
        id = id,
        label = label,
        kind = kind,
        status = status,
        sourceMemoryId = sourceMemoryId
    )
}

object DebugGraphScenarioDefinition {
    const val MarkerId = "debug-graph-seed-v1"
    private const val SpaceId = "default-space"

    fun build(): DebugGraphScenarioSeed {
        val sessions = listOf(
            DebugSessionSeed("debug-session-python", "Python 入门路线"),
            DebugSessionSeed("debug-session-graph", "知识图谱算法"),
            DebugSessionSeed("debug-session-api", "百炼 API 接入"),
            DebugSessionSeed("debug-session-review", "阶段复盘与下一步")
        )
        val messages = listOf(
            message("debug-message-python-user", "debug-session-python", MessageRole.User, "解释 Python 异步任务"),
            message("debug-message-python-assistant", "debug-session-python", MessageRole.Assistant, "异步任务由事件循环调度，协程可以等待 I/O 而不阻塞线程。", "debug-message-python-user"),
            message("debug-message-graph-user", "debug-session-graph", MessageRole.User, "如何把节点关系展示成全局图谱"),
            message("debug-message-graph-assistant", "debug-session-graph", MessageRole.Assistant, "先用节点和边表达事实，再用画布负责布局、缩放和邻域高亮。", "debug-message-graph-user"),
            message("debug-message-api-user", "debug-session-api", MessageRole.User, "百炼 API 的模型如何接入"),
            message("debug-message-api-assistant", "debug-session-api", MessageRole.Assistant, "Provider 配置负责地址与模型选择，生成运行时只消费稳定的协议输入。", "debug-message-api-user"),
            message("debug-message-review-user", "debug-session-review", MessageRole.User, "复盘这些会话之间的共同知识"),
            message("debug-message-review-assistant", "debug-session-review", MessageRole.Assistant, "共同主题是结构化证据、可验证的图谱关系和可替换的运行时。", "debug-message-review-user")
        )
        val anchors = listOf(
            anchor("debug-anchor-python", "Python 异步基础", "事件循环把等待中的 I/O 与可运行任务组织起来。", "debug-message-python-assistant"),
            anchor("debug-anchor-data", "数据处理", "数据处理是 Python 基础能力和图谱证据之间的共享概念。", "debug-message-python-assistant"),
            anchor("debug-anchor-layout", "图布局", "布局算法决定节点在画布中的空间位置，但不改变领域关系。", "debug-message-graph-assistant"),
            anchor("debug-anchor-evidence", "证据链", "节点详情应能回到产生它的会话消息。", "debug-message-graph-assistant"),
            anchor("debug-anchor-provider", "Provider 配置", "Provider 配置保存模型连接能力的边界，不承载 UI 状态。", "debug-message-api-assistant"),
            anchor("debug-anchor-qwen", "Qwen 模型", "模型名称是能力选择的一部分，不能泄露密钥或请求细节。", "debug-message-api-assistant"),
            anchor("debug-anchor-review", "阶段复盘", "复盘把不同会话中的证据组织为下一步学习路径。", "debug-message-review-assistant"),
            anchor("debug-anchor-reliability", "可靠性", "幂等、失败安全和可回滚是后台能力的共同约束。", "debug-message-review-assistant")
        )
        val nodes = listOf(
            node("debug-graph-seed-v1", "Debug 场景标记", GraphNodeKind.Other, GraphNodeStatus.Hidden, hidden = true),
            node("debug-node-session-python", "Python 入门路线", GraphNodeKind.Session),
            node("debug-node-session-graph", "知识图谱算法", GraphNodeKind.Session),
            node("debug-node-session-api", "百炼 API 接入", GraphNodeKind.Session),
            node("debug-node-session-review", "阶段复盘与下一步", GraphNodeKind.Session),
            node("debug-node-python", "Python 基础", GraphNodeKind.Concept, sourceMemoryId = "memory-debug-anchor-python"),
            node("debug-node-async", "异步编程", GraphNodeKind.Concept),
            node("debug-node-data", "数据处理", GraphNodeKind.Requirement, sourceMemoryId = "memory-debug-anchor-data"),
            node("debug-node-graph", "知识图谱", GraphNodeKind.Concept),
            node("debug-node-layout", "图布局", GraphNodeKind.Other, sourceMemoryId = "memory-debug-anchor-layout"),
            node("debug-node-relation", "节点关系", GraphNodeKind.Concept),
            node("debug-node-zoom", "语义缩放", GraphNodeKind.Other),
            node("debug-node-api", "百炼 API", GraphNodeKind.Source),
            node("debug-node-provider", "Provider 配置", GraphNodeKind.Source, sourceMemoryId = "memory-debug-anchor-provider"),
            node("debug-node-qwen", "Qwen 模型", GraphNodeKind.Source, sourceMemoryId = "memory-debug-anchor-qwen"),
            node("debug-node-runtime", "生成运行时", GraphNodeKind.Other),
            node("debug-node-evidence", "证据链", GraphNodeKind.Requirement, sourceMemoryId = "memory-debug-anchor-evidence"),
            node("debug-node-global-graph", "全局图谱", GraphNodeKind.Other),
            node("debug-node-reliability", "可靠性", GraphNodeKind.Requirement, sourceMemoryId = "memory-debug-anchor-reliability"),
            node("debug-node-review", "复盘记录", GraphNodeKind.Person, sourceMemoryId = "memory-debug-anchor-review"),
            node("debug-node-goal", "学习目标", GraphNodeKind.Requirement)
        )
        val edges = listOf(
            edge("debug-node-session-python", "debug-node-python", "包含"),
            edge("debug-node-session-python", "debug-node-async", "推进"),
            edge("debug-node-session-python", "debug-node-data", "产出"),
            edge("debug-node-session-graph", "debug-node-graph", "研究"),
            edge("debug-node-session-graph", "debug-node-layout", "使用"),
            edge("debug-node-session-graph", "debug-node-relation", "定义"),
            edge("debug-node-session-graph", "debug-node-zoom", "验证"),
            edge("debug-node-session-api", "debug-node-api", "接入"),
            edge("debug-node-session-api", "debug-node-provider", "配置"),
            edge("debug-node-session-api", "debug-node-qwen", "选择"),
            edge("debug-node-session-api", "debug-node-runtime", "驱动"),
            edge("debug-node-session-review", "debug-node-review", "记录"),
            edge("debug-node-session-review", "debug-node-goal", "形成"),
            edge("debug-node-python", "debug-node-async", "依赖"),
            edge("debug-node-python", "debug-node-data", "支撑"),
            edge("debug-node-python", "debug-node-global-graph", "汇入"),
            edge("debug-node-graph", "debug-node-layout", "需要"),
            edge("debug-node-layout", "debug-node-relation", "呈现"),
            edge("debug-node-relation", "debug-node-global-graph", "组成"),
            edge("debug-node-zoom", "debug-node-global-graph", "辅助"),
            edge("debug-node-qwen", "debug-node-provider", "绑定"),
            edge("debug-node-provider", "debug-node-runtime", "提供"),
            edge("debug-node-runtime", "debug-node-evidence", "生成"),
            edge("debug-node-evidence", "debug-node-review", "支持"),
            edge("debug-node-reliability", "debug-node-global-graph", "约束")
        )
        return DebugGraphScenarioSeed(sessions, messages, anchors, nodes, edges)
    }

    private fun message(
        id: String,
        sessionId: String,
        role: MessageRole,
        text: String,
        parentMessageId: String? = null
    ) = Message(
        id = id,
        spaceId = SpaceId,
        sessionId = sessionId,
        role = role,
        text = text,
        createdAtEpochMillis = 0L,
        parentMessageId = parentMessageId
    )

    private fun anchor(id: String, title: String, body: String, sourceMessageId: String) =
        DebugAnchorSeed(id, title, body, sourceMessageId)

    private fun node(
        id: String,
        label: String,
        kind: GraphNodeKind,
        status: GraphNodeStatus = GraphNodeStatus.Active,
        sourceMemoryId: String? = null,
        hidden: Boolean = false
    ) = DebugGraphNodeSeed(id, label, kind, status, sourceMemoryId, hidden)

    private fun edge(fromNodeId: String, toNodeId: String, relation: String) =
        GraphEdgeInput(
            id = "debug-edge-$fromNodeId-$toNodeId",
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            relation = relation
        )
}
