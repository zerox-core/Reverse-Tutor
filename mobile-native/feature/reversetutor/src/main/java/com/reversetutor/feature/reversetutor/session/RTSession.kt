package com.reversetutor.feature.reversetutor.session

/**
 * 反转家教算法内核的会话状态（对应 spike 的 session dict）。
 *
 * 只包含算法内核实际读写的字段；与主 app 的会话模型解耦，
 * 由接入层（feature/chat 或未来的 Room 仓储）负责映射。
 */
data class RTSession(
    val sessionId: String = "",
    val topic: String = "",
    val studentId: String = "anonymous",
    val grade: String = "",
    val cardId: String = "",
    val understandingLevel: Int = 1,
    val initialUnderstandingLevel: Int = 1,
    val emotion: String = "neutral",
    val currentRound: Int = 0,
    val targetBlindSpot: String = "",
    val confusedConcepts: List<String> = emptyList(),
    val masteredConcepts: List<String> = emptyList(),
    val toolTrace: List<ToolTraceEntry> = emptyList(),
    val breakthroughEvents: List<BreakthroughEvent> = emptyList(),
    val struggleEvents: List<StruggleEvent> = emptyList(),
    val knowledgeGraphNodes: List<KgNode> = emptyList(),
    val status: String = "active"
)

/** 工具调用轨迹（spike: session["tool_trace"] entry，扁平列表带 round）。 */
data class ToolTraceEntry(
    val tool: String,
    val round: Int,
    val callId: String = ""
)

/** 顿悟事件（spike: session["breakthrough_events"] entry，event 含 round）。 */
data class BreakthroughEvent(
    val round: Int,
    val concept: String = ""
)

/** 挣扎事件。 */
data class StruggleEvent(
    val round: Int,
    val concept: String = ""
)

/** 知识图谱节点（concept_status 的「对话事实」来源）。 */
data class KgNode(
    val id: String,
    val status: String = "not_started",
    val level: Double = 0.0
)
