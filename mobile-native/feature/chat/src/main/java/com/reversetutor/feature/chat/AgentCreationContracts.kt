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

/**
 * R98 阶段事件：思考块由「原始推理残片」改为「产品流程窗口」（2026-09-28 用户拍板）——
 * 每轮创建对话按固定阶段推进（理解输入 → 连接模型 → 深度思考 → 生成回复 → 自检修正），
 * 各段带耗时、活动段有且仅有一个；模型原始推理完整保留在 Assistant.reasoning、
 * 收进二级折叠，不再 600 字截窗（实测思考 997/1857 字被裁到 600，
 * 开头的理解与规划恰是被裁掉的部分，用户「看不到思考逻辑」）。
 */
data class AgentCreationStageEvent(
    val key: String,
    val label: String,
    val elapsedSeconds: Long = 0L,
    val active: Boolean = false
)

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
     * R95：onReasoning 逐段收到模型思考流（reasoning_content），正文未到时
     * 先流进占位气泡，静默推理期也有内容在动；默认空实现，旧网关零改动。
     */
    suspend fun converseStreaming(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?,
        strategy: AgentCreationTurnStrategy = AgentCreationTurnStrategy(),
        onPartialSpoken: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onThinkingDecision: (ThinkingBudgetDecider.Decision) -> Unit = {}
    ): AgentCreationTurnResult = converse(history, userText, currentDraft, docAnalysis, strategy)

    /** P1 文档分析。R-A 返回脚本化结果；R-C 接真实解析文本。 */
    suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis

    /**
     * R100 孵化草案提案（方案B · DRAFT_PROPOSAL 节点）：访谈收敛后生成
     * 「人物性格假设 / 教学方式 / 阶段目标 / 里程碑」草案给用户确认。
     * 默认实现从现有草案确定性拼装，Fake 与测试网关零改动。
     */
    suspend fun proposeIncubation(
        history: List<AgentCreationHistoryTurn>,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?
    ): AgentCreationIncubation = AgentCreationIncubation(
        personaHypothesis = currentDraft.persona.ifBlank { "慢热但较真，卡壳时会先复述一遍自己的问题" },
        teachingStyle = currentDraft.dialogueStrategy.ifBlank { "先听你讲，卡壳处追问，讲完带你复盘" },
        stageGoals = currentDraft.learningPath.take(3).ifEmpty {
            listOf(currentDraft.goal.ifBlank { "把目标主题讲明白" })
        },
        milestones = currentDraft.stageMilestones
            .takeIf { it.isNotBlank() && it != "未设置" }
            ?.split("→", "->", "，", ",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: currentDraft.learningPath.take(3)
    )

    /**
     * R100 学习流程图生成（方案B · GENERATE_LEARNING_FLOW 节点）：
     * 主题 → 子技能 → 依赖边。默认实现从学习路径确定性展开（顺序链），
     * Fake 与测试网关零改动；生产网关走 LLM 契约生成。
     */
    suspend fun generateLearningFlow(
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?
    ): AgentCreationLearningFlow {
        val titles = currentDraft.learningPath.ifEmpty {
            docAnalysis?.suggestedPath.orEmpty()
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
        if (titles.isEmpty()) return AgentCreationLearningFlow()
        return AgentCreationLearningFlow(
            topics = titles.map { AgentCreationFlowTopic(title = it) },
            edges = titles.zipWithNext { a, b -> AgentCreationFlowEdge(fromTitle = a, toTitle = b) }
        )
    }
}

/**
 * R100 孵化草案（方案B 状态机 · DRAFT_PROPOSAL 节点产物，2026-10-04 用户拍板落地）：
 * 访谈收敛后先给用户看的「AI 学生养成草案」——确认前不落库、不进正式草案卡。
 */
data class AgentCreationIncubation(
    val personaHypothesis: String = "",
    val teachingStyle: String = "",
    val stageGoals: List<String> = emptyList(),
    val milestones: List<String> = emptyList()
)

/** R100 学习流程图主题节点（GENERATE_LEARNING_FLOW 节点产物）。 */
data class AgentCreationFlowTopic(
    val title: String,
    val subSkills: List<String> = emptyList()
)

/** R100 学习流程图依赖边：from 必须先于 to 学。语义对齐图谱 relation=depends_on。 */
data class AgentCreationFlowEdge(
    val fromTitle: String,
    val toTitle: String,
    val relation: String = "depends_on"
)

/** R100 学习流程图：主题 → 子技能 → 依赖边（首版仅存内存，Room 落库留后续）。 */
data class AgentCreationLearningFlow(
    val topics: List<AgentCreationFlowTopic> = emptyList(),
    val edges: List<AgentCreationFlowEdge> = emptyList()
)

/** 对话流条目（UI 渲染模型）。 */
sealed interface AgentCreationFeedEntry {
    val id: String

    /**
     * R98 阶段化思考块：stages 非空且含活动段 = 本轮进行中；
     * thinkingDone = 思考阶段已结束（正文已开始接管）；
     * reasoning 全量保留（不再 600 截窗），收进 UI 二级折叠；
     * reasoning 与 stages 均不落快照——编解码器契约保持 [TagAssistant, id, text] 不变。
     */
    data class Assistant(
        override val id: String,
        val text: String,
        val reasoning: String? = null,
        val reasoningElapsedSeconds: Long = 0L,
        val thinkingDone: Boolean = false,
        val stages: List<AgentCreationStageEvent> = emptyList()
    ) : AgentCreationFeedEntry

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

    /**
     * R100 孵化草案卡（方案B）：访谈收敛后浮现一版待确认草案；
     * 用户确认 → Confirmed，批注打回 → 旧卡 Superseded、新卡 PendingConfirm。
     * 首版无 checkpoint——不落快照，进程重建后不恢复。
     */
    data class IncubationDraftCard(
        override val id: String,
        val incubation: AgentCreationIncubation,
        val status: Status = Status.PendingConfirm
    ) : AgentCreationFeedEntry {
        enum class Status { PendingConfirm, Confirmed, Superseded }
    }

    /** R100 学习流程图卡（方案B）：孵化草案确认后生成的学习路径图谱预览。 */
    data class LearningFlowCard(
        override val id: String,
        val flow: AgentCreationLearningFlow
    ) : AgentCreationFeedEntry
}
