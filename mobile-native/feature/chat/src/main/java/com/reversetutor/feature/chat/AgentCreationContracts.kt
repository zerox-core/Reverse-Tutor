package com.reversetutor.feature.chat

/**
 * Agent 会话创建 · P2' 对话契约（设计方案 v3）。
 *
 * 每轮对话 LLM 只输出一个 JSON 对象：
 * { understanding, followUpQuestion, assistantNote, requestDocument, draft{...} }
 * 本文件承载契约的数据结构与网关接口；JSON 解析见 [AgentCreationParser]，
 * 状态机见 [AgentCreationCoordinator]。
 */

/** P1 文档分析产物（R-A 由 Fake 网关产出，R-C 接真实分析）。 */
data class AgentCreationDocAnalysis(
    val materialTitle: String = "",
    val materialType: String = "其他",
    val outline: List<String> = emptyList(),
    val knowledgePoints: List<String> = emptyList(),
    val difficulty: Float = 0.5f,
    val prerequisites: List<String> = emptyList(),
    val suggestedPath: List<String> = emptyList(),
    val summary: String = ""
)

/**
 * P2' 轮次结果。draft 是「字段级补丁」：LLM 每轮输出完整草案，
 * 但解析端只保留实际出现的字段，由协调器叠加到上一轮草案上，
 * 保证「未提及的保持上一轮值」不依赖模型自觉。
 */
data class AgentCreationTurnResult(
    val understanding: Int = 0,
    val followUpQuestion: String? = null,
    val assistantNote: String? = null,
    val requestDocument: Boolean = false,
    val draft: AgentCreationDraftPatch? = null
)

/** 草案字段级补丁：null = 本轮未提及，保持原值。 */
data class AgentCreationDraftPatch(
    val title: String? = null,
    val learnerRole: String? = null,
    val learnerProfile: String? = null,
    val learnerDisplayName: String? = null,
    val goal: String? = null,
    val plan: String? = null,
    val learningScope: String? = null,
    val modules: String? = null,
    val stageMilestones: String? = null,
    val dialogueStrategy: String? = null,
    val feedbackIntensity: Int? = null,
    val probingIntensity: Int? = null,
    val scaffoldingIntensity: Int? = null,
    val correctionPersistence: String? = null,
    val reviewFrequency: String? = null,
    val speakingTone: String? = null,
    val story: String? = null,
    val openingMessage: String? = null,
    val persona: String? = null,
    /** R86：有序学习路径（3-8 个知识点，先基础后提升）；null = 本轮未提及，保持原值。 */
    val learningPath: List<String>? = null
) {
    fun applyTo(base: NewSessionConfiguration): NewSessionConfiguration = base.copy(
        title = title ?: base.title,
        learnerRole = learnerRole ?: base.learnerRole,
        learnerProfile = learnerProfile ?: base.learnerProfile,
        learnerDisplayName = learnerDisplayName ?: base.learnerDisplayName,
        goal = goal ?: base.goal,
        plan = plan ?: base.plan,
        learningScope = learningScope ?: base.learningScope,
        modules = modules ?: base.modules,
        stageMilestones = stageMilestones ?: base.stageMilestones,
        dialogueStrategy = dialogueStrategy ?: base.dialogueStrategy,
        feedbackIntensity = feedbackIntensity ?: base.feedbackIntensity,
        probingIntensity = probingIntensity ?: base.probingIntensity,
        scaffoldingIntensity = scaffoldingIntensity ?: base.scaffoldingIntensity,
        correctionPersistence = correctionPersistence ?: base.correctionPersistence,
        reviewFrequency = reviewFrequency ?: base.reviewFrequency,
        speakingTone = speakingTone ?: base.speakingTone,
        story = story ?: base.story,
        openingMessage = openingMessage ?: base.openingMessage,
        persona = persona ?: base.persona,
        learningPath = learningPath ?: base.learningPath
    )
}

/** 对话历史轮（喂给网关的纯文本形态）。 */
data class AgentCreationHistoryTurn(
    val isUser: Boolean,
    val text: String
)

/**
 * Agent 创建网关。R-A 由 [FakeAgentCreationGateway] 驱动演示；
 * R-B 起换成复用 core/llm transport + LlmProfile 的生产实现（不经教学轮 envelope）。
 */
interface AgentCreationGateway {
    suspend fun converse(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?,
        strategy: AgentCreationTurnStrategy = AgentCreationTurnStrategy()
    ): AgentCreationTurnResult

    /**
     * R93 流式对话：边生成边上屏（2026-09-26 用户拍板：会话与创建链路全部走流式）。
     * onPartialSpoken 收到「截至目前的完整口语文本」（全量覆盖语义，非增量），
     * 由协调器维护的占位气泡随回调生长、轮次落定即撤。默认退回非流式 converse，
     * Fake 与测试网关零改动。
     */
    suspend fun converseStreaming(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?,
        strategy: AgentCreationTurnStrategy = AgentCreationTurnStrategy(),
        onPartialSpoken: (String) -> Unit = {}
    ): AgentCreationTurnResult = converse(history, userText, currentDraft, docAnalysis, strategy)

    /** P1 文档分析。R-A 返回脚本化结果；R-C 接真实解析文本。 */
    suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis
}

/** 对话流条目（UI 渲染模型）。 */
sealed interface AgentCreationFeedEntry {
    val id: String

    data class Assistant(override val id: String, val text: String) : AgentCreationFeedEntry

    data class User(override val id: String, val text: String) : AgentCreationFeedEntry

    data class FileCard(
        override val id: String,
        val fileName: String,
        val sizeLabel: String,
        val status: FileStatus
    ) : AgentCreationFeedEntry {
        enum class FileStatus(val label: String) {
            Analyzing("分析中"),
            Analyzed("已分析"),
            Failed("失败 · 可重试")
        }
    }

    /** 草案字段卡：草案发生实质变化时在流中浮现一版。 */
    data class DraftCard(
        override val id: String,
        val configuration: NewSessionConfiguration
    ) : AgentCreationFeedEntry
}
