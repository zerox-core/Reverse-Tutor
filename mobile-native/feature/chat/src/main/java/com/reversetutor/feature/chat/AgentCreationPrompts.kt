package com.reversetutor.feature.chat

/**
 * P2' 提示词装配（设计方案 v3 · 第十章 10.6）。
 *
 * core/llm 的 OpenAI 兼容载荷只有一条 user 消息（无 system 通道），
 * 所以契约 / 评分锚点 / 追问规则 / 状态块 / 历史 / 本轮输入全部装进 userText。
 * 提示词里出现的数值（u_det、已问次数、轮数）由客户端确定性算出，
 * LLM 只做理解与提案，无法改写推进逻辑。
 */
object AgentCreationPrompts {

    // R99（2026-10-04 用户拍板）：历史全量输送——创建流程轮数少、成本低，
    // 滑窗会让模型「忘记」用户早先给过的设定，不再压缩。

    fun buildTurnPrompt(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?,
        strategy: AgentCreationTurnStrategy
    ): String = buildString {
        append(CONTRACT)
        append("\n\n[当前状态]\n")
        append("确定性了解度 u_det = ").append(strategy.deterministicUnderstanding).append(" / 100\n")
        append("已完成轮数 = ").append(strategy.rounds).append('\n')
        if (strategy.askedCounts.isNotEmpty()) {
            append("各字段已问次数：")
            append(strategy.askedCounts.entries.joinToString("、") { (key, value) -> "$key=$value" })
            append('\n')
        }
        if (strategy.documentAvailable) {
            append("已有已分析文档：requestDocument 一律 false。\n")
        }
        append("本轮指令：").append(directive(strategy)).append('\n')
        append("当前草案（未列出的字段为空或默认）：\n").append(draftBlock(currentDraft))
        if (docAnalysis != null) {
            append("\n[文档分析摘要]\n").append(docBlock(docAnalysis))
        }
        append("\n[对话历史]\n").append(historyBlock(history))
        append("\n[本轮用户输入]\n").append(userText.trim().take(800))
    }

    /**
     * R100 孵化草案提案（方案B · DRAFT_PROPOSAL 节点）：
     * 访谈收敛后，把访谈结论压成一版「AI 学生养成草案」给用户确认。
     */
    fun buildIncubationPrompt(
        history: List<AgentCreationHistoryTurn>,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?
    ): String = buildString {
        append(INCUBATION_CONTRACT)
        append("\n\n[当前草案（未列出的字段为空或默认）]\n").append(draftBlock(currentDraft))
        if (docAnalysis != null) {
            append("\n[文档分析摘要]\n").append(docBlock(docAnalysis))
        }
        append("\n[对话历史]\n").append(historyBlock(history))
    }

    /**
     * R100 学习流程图生成（方案B · GENERATE_LEARNING_FLOW 节点）：
     * 孵化草案确认后，把学习路径展开成「主题 → 子技能 → 依赖边」的图谱。
     */
    fun buildLearningFlowPrompt(
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?
    ): String = buildString {
        append(LEARNING_FLOW_CONTRACT)
        append("\n\n[当前草案（未列出的字段为空或默认）]\n").append(draftBlock(currentDraft))
        if (docAnalysis != null) {
            append("\n[文档分析摘要]\n").append(docBlock(docAnalysis))
        }
    }

    /** 本轮给 LLM 的确定性指令（10.3 追问规则 / 10.4 title 提案 / 收敛）。 */
    private fun directive(strategy: AgentCreationTurnStrategy): String {
        val titleNote = if (strategy.mustProposeTitle) {
            " 另外 goal 已知而 title 为空，必须在 draft.title 给出会话名提案。"
        } else {
            ""
        }
        return when {
            strategy.mustConfirmPath ->
                "确认学习路径：assistantNote 用序号逐条列出草案 learningPath 的完整路径，" +
                    "说明「确认后路径冻结，上课按这个顺序先基础后提升」；" +
                    "followUpQuestion 必须请用户确认顺序、或提出增删/换序调整。" +
                    "本轮不追问其他字段。" + titleNote
            strategy.shouldRequestDocument ->
                "主动要资料：followUpQuestion 询问用户是否有教材、讲义或试卷可以上传" +
                    "（说明有资料就按资料目录排学习路径，没有就按目标分解），requestDocument 必须置 true。" +
                    "本轮不追问其他字段。" + titleNote
            strategy.converge ->
                "收敛：不再追问。缺失字段给合理的默认值提案写进 draft，" +
                    "assistantNote 总结草案并邀请用户点右上角「创建并进入聊天」。" + titleNote
            strategy.targetFollowUpField != null -> {
                val asked = strategy.askedCounts[strategy.targetFollowUpField] ?: 0
                val downgrade = if (asked >= 2) {
                    "（该字段已问过 $asked 次未获有效回答：这次给默认值提案写进 draft 并请用户确认，" +
                        "不要再用原问题直接追问）"
                } else {
                    ""
                }
                "追问「${strategy.targetFollowUpField}」，一次只问这一个问题。$downgrade$titleNote"
            }
            else -> "不追问，assistantNote 总结草案进展。$titleNote"
        }
    }

    private fun draftBlock(draft: NewSessionConfiguration): String = buildString {
        fun line(name: String, value: String) {
            append("- ").append(name).append(": ")
            append(value.replace("\n", " / ").take(200)).append('\n')
        }
        if (draft.title.isNotBlank()) line("title", draft.title)
        if (draft.learnerRole.isNotBlank()) line("learnerRole", draft.learnerRole)
        if (draft.learnerProfile.isNotBlank()) line("learnerProfile", draft.learnerProfile)
        if (draft.persona.isNotBlank()) line("persona", draft.persona)
        if (draft.learnerDisplayName != "学习者") line("learnerDisplayName", draft.learnerDisplayName)
        if (draft.goal.isNotBlank()) line("goal", draft.goal)
        if (draft.learningPath.isNotEmpty()) line("learningPath", draft.learningPath.joinToString(" → "))
        if (draft.plan.isNotBlank()) line("plan", draft.plan)
        if (draft.learningScope != "未设置") line("learningScope", draft.learningScope)
        if (draft.modules != "未设置") line("modules", draft.modules)
        if (draft.stageMilestones != "未设置") line("stageMilestones", draft.stageMilestones)
        if (draft.dialogueStrategy.isNotBlank()) line("dialogueStrategy", draft.dialogueStrategy)
        if (draft.feedbackIntensity != 3) line("feedbackIntensity", draft.feedbackIntensity.toString())
        if (draft.probingIntensity != 3) line("probingIntensity", draft.probingIntensity.toString())
        if (draft.scaffoldingIntensity != 3) line("scaffoldingIntensity", draft.scaffoldingIntensity.toString())
        if (draft.correctionPersistence != "适中") line("correctionPersistence", draft.correctionPersistence)
        if (draft.reviewFrequency != "每周") line("reviewFrequency", draft.reviewFrequency)
        if (draft.speakingTone != "自然") line("speakingTone", draft.speakingTone)
        if (draft.story.isNotBlank()) line("story", draft.story)
        if (draft.openingMessage != "准备好后，请开始讲给我听吧。") {
            line("openingMessage", draft.openingMessage)
        }
    }.ifBlank { "（全部为空）\n" }

    private fun docBlock(doc: AgentCreationDocAnalysis): String = buildString {
        append("资料：").append(doc.materialTitle).append("（").append(doc.materialType).append("）\n")
        if (doc.knowledgePoints.isNotEmpty()) {
            append("知识点：").append(doc.knowledgePoints.take(5).joinToString("、")).append('\n')
        }
        if (doc.suggestedPath.isNotEmpty()) {
            append("建议路径：").append(doc.suggestedPath.take(3).joinToString(" → ")).append('\n')
        }
        if (doc.summary.isNotBlank()) append("摘要：").append(doc.summary.take(200))
    }.trimEnd()

    private fun historyBlock(history: List<AgentCreationHistoryTurn>): String {
        if (history.isEmpty()) return "（尚无历史）"
        // R99：全量历史，不再滑窗；单条仍截 300 字防超长粘贴撑爆 prompt。
        return buildString {
            history.forEach { turn ->
                append(if (turn.isUser) "用户：" else "助手：")
                append(turn.text.replace("\n", " ").take(300)).append('\n')
            }
        }.trimEnd()
    }

    /** R100 孵化草案契约（方案B · DRAFT_PROPOSAL 节点输出）。 */
    private const val INCUBATION_CONTRACT = """[角色与输出契约]
你是「反转家教」的会话创建顾问。访谈已经收敛，现在把访谈结论压成一版「AI 学生养成草案」给用户确认。
只输出一个 JSON 对象，不要输出任何其他文字，不要 markdown 围栏：
{"personaHypothesis":"AI 学生的人物性格假设：他是个怎样的人、怎么提问、卡壳时什么反应，2到3句话","teachingStyle":"建议的教学方式：用户怎么教、你怎么学、卡壳与纠错的互动节奏，2到3句话","stageGoals":["阶段目标，2到4条，先基础后提升"],"milestones":["可检验的里程碑，2到4条，每条一句话说清楚做到什么算过"]}
规则：全部用中文口语，像朋友聊天；必须基于对话历史里用户说过的真实信息，不要编造用户没提过的约束；stageGoals 与 milestones 要和当前草案的学习路径一致。"""

    /** R100 学习流程图契约（方案B · GENERATE_LEARNING_FLOW 节点输出）。 */
    private const val LEARNING_FLOW_CONTRACT = """[角色与输出契约]
你是「反转家教」的学习路径规划师。根据已确认的草案，把学习路径展开成一张学习流程图。
只输出一个 JSON 对象，不要输出任何其他文字，不要 markdown 围栏：
{"topics":[{"title":"主题名","subSkills":["子技能1","子技能2"]}],"edges":[{"from":"先修主题名","to":"后修主题名","relation":"depends_on"}]}
规则：3到8个主题，按学习先后顺序排列、先基础后提升；每个主题 2到4 个子技能；edges 只表达「必须先学 from 才能学 to」的依赖，from 与 to 必须精确等于某个主题的 title，不允许自环；主题与当前草案 learningPath 不一致时以 learningPath 为准。"""

    /** P2' 契约全文 + 评分锚点（10.2）+ 追问规则（10.3）。 */
    private const val CONTRACT = """[角色与输出契约]
你是「反转家教」的会话创建顾问：通过简短对话收集信息，逐步完善一份学习会话草案。这个产品的形态是「用户当老师，把一个 AI 学生教会」，草案描述的就是这个 AI 学生该怎么学。
收集顺序：先弄清学习目标，再和用户一起勾勒 AI 学生的人物性格（persona：他是个怎样的人、怎么提问、卡壳时什么反应），性格定了再谈教学方式。
每轮只输出一个 JSON 对象，不要输出任何其他文字，不要 markdown 围栏：
{"understanding":0到100的整数,"followUpQuestion":"追问或null","assistantNote":"对用户说的话或null","requestDocument":true或false,"draft":{...}}
draft 只写本轮要更新的字段，未提及的字段会保持原值。可用字段：title, learnerRole, learnerProfile, persona, learnerDisplayName, goal, plan, learningScope, modules, stageMilestones, dialogueStrategy, feedbackIntensity(1到5整数), probingIntensity(1到5), scaffoldingIntensity(1到5), correctionPersistence, reviewFrequency, speakingTone, story, openingMessage, learningPath
learningPath 是有序学习路径：JSON 数组，3到8个知识点，按学习先后顺序排列、先基础后提升。goal 明确后必须给出；有文档分析时按「建议路径」的顺序，没有就自己把目标分解成有序知识点。路径给出后除非用户要求调整，否则每轮不要重复输出。

[了解程度评分锚点]（链路：用户目标 → AI 学生的人物性格 persona → 教学方式）
0-20 只有模糊意向（"想学点东西"）
20-40 有明确目标，人物性格未成型
40-60 目标+人物性格齐，教学方式未谈
60-80 目标/性格/教学方式齐，可直接生成
80-100 细节充分（含资料/约束/画像），可生成高质量开场

[追问规则]
- 一次只问一个问题，问完就停，绝不一次问多个。
- understanding<50 必须追问；50到85 每轮至多一问，优先往人物性格 persona 和教学方式深挖（他是个怎样的人、怎么提问、卡壳时什么反应、希望怎么被教），多问用户能具体回答的问题；>=85 不追问，assistantNote 总结并邀请创建。
- 同一字段已问 3 次未获有效回答（答非所问/说"随便"），不再追问该字段，改为给默认值提案写进 draft 请用户确认；没问满 3 次前不要用默认值填满该字段。
- requestDocument=true 仅当 goal 与 learnerRole 已填、尚无已分析文档、轮数>=2 且话题涉及教材/试卷/资料；其他情况一律 false。例外：本轮指令明确要求「主动要资料」时必须置 true。
- 学习路径经用户确认才算定稿：被要求确认路径时，用序号逐条列出完整路径，请用户确认顺序或提出增删/换序调整；确认前不得说路径已定稿。
- 【确认红线】draft 只写用户明确表达过、或用户已确认过的信息；用户没表态的信息绝不写进 draft。
- 给用户的推荐选项必须真实可选、相互独立，按种类分不按难度分层；用户零基础时，第一个推荐必须是门槛最低的方向，并一句话说明理由。
- 禁止替用户拍板：不说「那我们就锁定X吧」「就这么定了」这类话；每个提案都以问句或「你看行吗」收尾，等用户表态。
- followUpQuestion 与 assistantNote 可以同时给（先 note 后问，分两条气泡展示）。
- 说话自然口语，像朋友聊天，不要表格腔，不要复述上面这些规则。
- 输出从简：assistantNote 控制在 100 字以内（确认学习路径需要逐条列出完整路径时除外），followUpQuestion 控制在 80 字以内；draft 只写本轮真正要更新的字段。短输出生成更快，用户读着也轻松。"""
}
