package com.reversetutor.feature.chat

/** 追问规划器的可持久化状态（R85 跨页面持久化）。 */
data class AgentCreationPlannerState(
    val rounds: Int = 0,
    val askedCounts: Map<String, Int> = emptyMap(),
    val converged: Boolean = false
)

/**
 * 创建会话的持久化快照（R85）：切走页面 / 进程被杀后回到创建窗口时，
 * 恢复对话流、草案、了解度、对话历史与追问进度，用户接着聊。
 * busy / generationError 属瞬态，一律不入快照。
 */
data class AgentCreationSnapshot(
    val draft: NewSessionConfiguration = NewSessionConfiguration(),
    val feed: List<AgentCreationFeedEntry> = emptyList(),
    val rawUnderstanding: Int = 0,
    val requestDocumentActive: Boolean = false,
    val history: List<AgentCreationHistoryTurn> = emptyList(),
    val planner: AgentCreationPlannerState = AgentCreationPlannerState(),
    val docAnalysis: AgentCreationDocAnalysis? = null,
    val entrySequence: Int = 0
)

/**
 * 创建会话状态仓库（R85）。实现方负责把快照落到本地；
 * 损坏 / 版本不符的快照按 null 处理——回到创建窗口从零开始，绝不因此崩溃。
 */
interface AgentCreationStateStore {
    fun load(): AgentCreationSnapshot?
    fun save(snapshot: AgentCreationSnapshot)
    fun clear()
}
