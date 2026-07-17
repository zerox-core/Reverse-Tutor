package com.reversetutor.feature.memory

enum class ContextHubSection(
    val label: String
) {
    Overview("概览"),
    Graph("图谱"),
    Anchors("锚点"),
    Notes("随笔"),
    Errors("错因"),
    SessionSettings("设置")
}

data class ContextHubUiState(
    val sessionId: String?,
    val sessionTitle: String,
    val sessionStatusLabel: String,
    val overviewLines: List<String>,
    val graphState: KnowledgeGraphUiState,
    val sections: List<ContextHubSectionState>
) {
    val hasActiveSession: Boolean
        get() = !sessionId.isNullOrBlank()

    companion object {
        fun fromActiveSession(
            sessionId: String?,
            sessionTitle: String?
        ): ContextHubUiState {
            val normalizedTitle = sessionTitle?.trim().takeUnless { it.isNullOrBlank() }
            val active = !sessionId.isNullOrBlank() && normalizedTitle != null
            return ContextHubUiState(
                sessionId = sessionId,
                sessionTitle = normalizedTitle ?: "未选择会话",
                sessionStatusLabel = if (active) {
                    "已连接当前会话"
                } else {
                    "请先从会话列表打开一个会话，再查看上下文证据。"
                },
                overviewLines = listOf(
                    "范围：当前会话的学习证据",
                    "图谱：native Canvas 已接入，可能还没有节点",
                    "资料：聊天、随笔和导入资料会逐步沉淀到这里"
                ),
                graphState = KnowledgeGraphUiState.from(
                    nodes = emptyList(),
                    edges = emptyList()
                ),
                sections = ContextHubSection.entries.map { it.toState() }
            )
        }

        fun fromMemorySnapshot(
            sessionId: String?,
            sessionTitle: String?,
            snapshot: ContextMemorySnapshot,
            graphState: KnowledgeGraphUiState? = null
        ): ContextHubUiState {
            val base = fromActiveSession(sessionId = sessionId, sessionTitle = sessionTitle)
            val resolvedGraphState = graphState ?: base.graphState
            return base.copy(
                sessionStatusLabel = if (base.hasActiveSession) {
                    "已连接当前会话的学习证据"
                } else {
                    base.sessionStatusLabel
                },
                overviewLines = listOf(
                    "锚点：${snapshot.anchors.size}",
                    "随笔：${snapshot.notes.size}",
                    "未解决错因：${snapshot.errors.count { !it.resolved }}",
                    "图谱节点：${resolvedGraphState.nodes.size}",
                    "图谱关系：${resolvedGraphState.edgeCount}",
                    "图谱状态：${resolvedGraphState.status.label}"
                ),
                graphState = resolvedGraphState,
                sections = ContextHubSection.entries.map { section ->
                    section.toState(snapshot, resolvedGraphState)
                }
            )
        }
    }
}

data class ContextMemorySnapshot(
    val anchors: List<ContextMemoryEntry> = emptyList(),
    val notes: List<ContextMemoryEntry> = emptyList(),
    val errors: List<ContextErrorEntry> = emptyList()
)

data class ContextMemoryEntry(
    val id: String,
    val title: String,
    val body: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

data class ContextErrorEntry(
    val id: String,
    val title: String,
    val detail: String,
    val sourceMessageId: String? = null,
    val resolved: Boolean = false
)

data class ContextHubSectionState(
    val section: ContextHubSection,
    val statusLabel: String,
    val title: String,
    val body: String,
    val nextActions: List<String>
)

private fun ContextHubSection.toState(): ContextHubSectionState =
    when (this) {
        ContextHubSection.Overview -> ContextHubSectionState(
            section = this,
            statusLabel = "空",
            title = "会话证据概览",
            body = "学习摘要、活跃锚点、近期证据和资料状态会在 native Memory 提取完成后显示在这里。",
            nextActions = listOf("继续聊天", "导入资料")
        )
        ContextHubSection.Graph -> ContextHubSectionState(
            section = this,
            statusLabel = GraphRenderStatus.Empty.label,
            title = "还没有图谱节点",
            body = "native Canvas 图谱渲染已经可用。创建图谱节点和关系后会显示在这里。",
            nextActions = listOf("创建随笔或锚点", "导入可形成图谱的资料")
        )
        ContextHubSection.Anchors -> ContextHubSectionState(
            section = this,
            statusLabel = "空",
            title = "还没有锚点",
            body = "要求、资料锚点和导入材料会在锚点持久化与资料关联完成后显示。",
            nextActions = listOf("先在聊天中保留证据", "后续把关键内容设为锚点")
        )
        ContextHubSection.Notes -> ContextHubSectionState(
            section = this,
            statusLabel = "空",
            title = "还没有随笔",
            body = "从聊天消息创建的随笔会在这里沉淀，后续也会支持编辑。",
            nextActions = listOf("在聊天中引用关键内容", "把有价值的消息记为随笔")
        )
        ContextHubSection.Errors -> ContextHubSectionState(
            section = this,
            statusLabel = "空",
            title = "还没有错因记录",
            body = "误解、错误证据和纠正记录会在错因持久化与复盘流程完成后显示。",
            nextActions = listOf("先在聊天中保留纠错过程", "后续进入错因复盘")
        )
        ContextHubSection.SessionSettings -> ContextHubSectionState(
            section = this,
            statusLabel = "待启用",
            title = "会话设置待启用",
            body = "人设、策略、截止时间、头像和变更提醒会在会话设置任务中开放编辑。",
            nextActions = listOf("全局模型配置请到设置", "会话设置持久化完成后再编辑")
        )
    }

private fun ContextHubSection.toState(
    snapshot: ContextMemorySnapshot,
    graphState: KnowledgeGraphUiState
): ContextHubSectionState =
    when (this) {
        ContextHubSection.Overview -> toState()
        ContextHubSection.Graph -> ContextHubSectionState(
            section = this,
            statusLabel = graphState.status.label,
            title = graphState.title,
            body = graphState.summary,
            nextActions = graphState.nextActions()
        )
        ContextHubSection.SessionSettings -> toState()
        ContextHubSection.Anchors -> {
            if (snapshot.anchors.isEmpty()) {
                toState()
            } else {
                ContextHubSectionState(
                    section = this,
                    statusLabel = snapshot.anchors.countLabel("个锚点"),
                    title = "锚点",
                    body = snapshot.anchors.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf(
                        "打开关联资料证据",
                        "打开关联聊天证据",
                        "后续把锚点接入图谱"
                    )
                )
            }
        }
        ContextHubSection.Notes -> {
            if (snapshot.notes.isEmpty()) {
                toState()
            } else {
                ContextHubSectionState(
                    section = this,
                    statusLabel = snapshot.notes.countLabel("篇随笔"),
                    title = "随笔",
                    body = snapshot.notes.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf(
                        "打开关联聊天证据",
                        "后续可从记忆动作编辑或删除"
                    )
                )
            }
        }
        ContextHubSection.Errors -> {
            if (snapshot.errors.isEmpty()) {
                toState()
            } else {
                val openCount = snapshot.errors.count { !it.resolved }
                ContextHubSectionState(
                    section = this,
                    statusLabel = "$openCount 个未解决",
                    title = "错因",
                    body = snapshot.errors.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf(
                        "打开关联聊天证据",
                        "补充纠正证据后解决",
                        "后续接入诊断复盘"
                    )
                )
            }
        }
    }

private fun KnowledgeGraphUiState.nextActions(): List<String> =
    when (status) {
        GraphRenderStatus.Loading -> listOf("等待图谱快照")
        GraphRenderStatus.Empty -> listOf("创建随笔或锚点", "导入可形成图谱的资料")
        GraphRenderStatus.Ready -> listOf("选择节点查看详情", "拖动或缩放 native 图谱")
        GraphRenderStatus.Invalid -> listOf("检查无效关系", "选择有效节点查看证据")
        GraphRenderStatus.Large -> listOf("使用节点列表精确选择", "聚类和筛选后续补齐")
    }

private fun List<ContextMemoryEntry>.countLabel(unit: String): String = "$size $unit"

private fun ContextMemoryEntry.toBodyLine(): String =
    buildString {
        append(title)
        append(": ")
        append(body)
        val references = buildList {
            sourceMessageId?.let { add("聊天消息：$it") }
            sourceId?.let { add("资料：$it") }
        }
        if (references.isNotEmpty()) {
            append("\n证据引用")
            references.forEach { append("\n").append(it) }
        }
    }

private fun ContextErrorEntry.toBodyLine(): String =
    buildString {
        append(title)
        append(": ")
        append(detail)
        append("\n状态：")
        append(if (resolved) "已解决" else "未解决")
        sourceMessageId?.let {
            append("\n证据引用")
            append("\n聊天消息：").append(it)
        }
    }
