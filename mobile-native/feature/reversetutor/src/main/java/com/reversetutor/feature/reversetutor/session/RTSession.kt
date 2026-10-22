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
    val status: String = "active",
    /**
     * 创建期采集的学生人设（2026-10-18 接入）。
     *
     * 会话创建窗口会让用户/模型填出 Role / 画像 / 性格 / 对话策略 / 目标，这些字段
     * 此前只进普通 RAG 链路的模板证据，算法内核完全读不到——同一份「大一学生、活泼
     * 好奇」的人设在两条链路上表现不一致。全为空 = 未采集，提示词退回内置角色卡口径。
     */
    val studentProfile: StudentProfile = StudentProfile(),
    /**
     * 早期被滑动窗口淘汰的历史对话滚动摘要（2026-10-18 接入）。
     *
     * 防止超长对话无上限膨胀炸穿 LLM 窗口。当超出 token 预算或条数上限时，
     * 较早的历史轮次会被压缩并归纳至此处，注入系统提示词。空则不渲染。
     */
    val historySummary: String = ""
)

/**
 * 学生人设块（自由文本，原样渲染进系统提示词的人设段）。
 *
 * 刻意不做结构化拆分：创建窗口采集的就是自由文本，硬塞进 [studentProfile] 那套
 * speaking_patterns/emotional_traits 结构只会丢信息且要猜默认值。
 */
data class StudentProfile(
    /** 身份，如「对日常生活现象充满好奇的大一学生」。 */
    val role: String = "",
    /** 画像/背景，如「公式背得熟，遇概念辨析发懵」。 */
    val profile: String = "",
    /** 性格与说话习惯。 */
    val persona: String = "",
    /** 对话策略（用户希望的互动方式）。 */
    val dialogueStrategy: String = "",
    /** 本次会话的学习目标。 */
    val goal: String = ""
) {
    /** 全部字段为空——提示词不渲染人设段（保持接入前的提示词口径）。 */
    val isBlank: Boolean
        get() = role.isBlank() && profile.isBlank() && persona.isBlank() &&
            dialogueStrategy.isBlank() && goal.isBlank()
}

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
