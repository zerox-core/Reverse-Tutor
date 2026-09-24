package com.reversetutor.feature.chat

import kotlinx.coroutines.CancellationException

/**
 * Agent 会话创建状态机（设计方案 v3 · 第三章）。
 *
 * Idle → AnalyzingDocument ⇄ Conversing → Creating → Created
 * 必填字段（title / learnerRole）任一为空时，展示了解程度封顶 60；
 * 解析失败自动原样重试 1 次，仍失败按生成失败降级（对话与草案保留）。
 *
 * 第十章算法（R-B 起）：追问优先级、同字段 2 次降级、8 轮软上限、
 * 「别问了」立即收敛、了解度双源融合与 title 兜底提案全部由
 * [AgentCreationFollowUpPlanner] / [AgentCreationUnderstanding] 在客户端确定性执行，
 * LLM 只按策略快照生成措辞与草案补丁，客户端最后把关。
 */
enum class AgentCreationPhase {
    Idle,
    AnalyzingDocument,
    Conversing,
    Creating,
    Created
}

data class AgentCreationUiState(
    val phase: AgentCreationPhase = AgentCreationPhase.Idle,
    val feed: List<AgentCreationFeedEntry> = emptyList(),
    val draft: NewSessionConfiguration = NewSessionConfiguration(),
    val rawUnderstanding: Int = 0,
    val busy: Boolean = false,
    val requestDocumentActive: Boolean = false,
    val generationError: String? = null,
    val docAnalysis: AgentCreationDocAnalysis? = null
) {
    /** 兜底钳制：必填缺失时封顶 60（设计方案 §三）。 */
    val displayedUnderstanding: Int
        get() = if (draft.title.isBlank() || draft.learnerRole.isBlank()) {
            rawUnderstanding.coerceAtMost(AgentCreationUnderstanding.CAP_REQUIRED_MISSING)
        } else {
            rawUnderstanding
        }

    val understandingHigh: Boolean get() = displayedUnderstanding >= 70
    val understandingLow: Boolean get() = displayedUnderstanding < 40

    /** 创建可用：必填齐全即常驻可点。 */
    val canCreate: Boolean get() = draft.validationErrors().isEmpty()
}

class AgentCreationCoordinator(
    private val gateway: AgentCreationGateway,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    var state: AgentCreationUiState = AgentCreationUiState()
        private set

    private val history = mutableListOf<AgentCreationHistoryTurn>()
    private val planner = AgentCreationFollowUpPlanner()
    private var entrySequence = 0

    fun start() {
        if (state.phase != AgentCreationPhase.Idle) return
        state = state.copy(
            phase = AgentCreationPhase.Conversing,
            feed = state.feed + AgentCreationFeedEntry.Assistant(
                id = nextId(),
                text = "想学什么？直接说就行——比如「我想把初中物理浮力这块讲明白」，也可以随时把教材、试卷发给我。"
            )
        )
    }

    fun markCreated() {
        state = state.copy(phase = AgentCreationPhase.Created)
    }

    /** 用户发一条文字消息：→ P2' 生成 → 更新了解程度/草案/追问。 */
    suspend fun sendUserText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || state.busy || state.phase != AgentCreationPhase.Conversing) return
        // 10.3：用户明确说「别问了 / 直接生成」→ 立即收敛，本轮即按收敛策略执行。
        if (AgentCreationFollowUpPlanner.isStopAsking(trimmed)) {
            planner.forceConverge()
        }
        appendUser(trimmed)
        history += AgentCreationHistoryTurn(isUser = true, text = trimmed)
        converseTurn(trimmed)
    }

    /** 用户经消息框发文件：文件卡 → P1 分析 → 助手带结论续聊。 */
    suspend fun attachDocument(fileName: String, sizeLabel: String) {
        if (state.busy) return
        val cardId = nextId()
        state = state.copy(
            phase = AgentCreationPhase.AnalyzingDocument,
            feed = state.feed + AgentCreationFeedEntry.FileCard(
                id = cardId,
                fileName = fileName,
                sizeLabel = sizeLabel,
                status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzing
            ),
            requestDocumentActive = false
        )
        val analysis = runCatching { gateway.analyzeDocument(fileName) }
            .fold(
                onSuccess = { it },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    null
                }
            )
        if (analysis == null) {
            updateFileCard(cardId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Failed) }
            state = state.copy(phase = AgentCreationPhase.Conversing)
            appendAssistant("这份文件暂时没读出来。可以点文件卡重试，也可以直接用文字描述，咱们接着聊。")
            return
        }
        updateFileCard(cardId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzed) }
        state = state.copy(phase = AgentCreationPhase.Conversing, docAnalysis = analysis)
        appendAssistant(
            "我看了这份《${analysis.materialTitle}》。" +
                (analysis.summary.ifBlank { "大纲：" + analysis.outline.take(3).joinToString("、") }) +
                "\n建议路径：${analysis.suggestedPath.firstOrNull() ?: "按大纲顺序过一遍"}。你想怎么学？"
        )
    }

    /** 失败的文件卡重试分析。 */
    suspend fun retryDocumentAnalysis(entryId: String) {
        val card = state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>()
            .firstOrNull { it.id == entryId } ?: return
        if (card.status != AgentCreationFeedEntry.FileCard.FileStatus.Failed) return
        updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzing) }
        val analysis = runCatching { gateway.analyzeDocument(card.fileName) }.getOrNull() ?: run {
            updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Failed) }
            return
        }
        updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzed) }
        state = state.copy(docAnalysis = state.docAnalysis ?: analysis)
    }

    fun dismissError() {
        state = state.copy(generationError = null)
    }

    /** 最终配置（创建管道消费）。 */
    fun configuration(): NewSessionConfiguration = state.draft.deepCopy()

    private suspend fun converseTurn(userText: String) {
        // 10.1 第 2 步：调用网关前生成策略快照（追问目标 / 收敛 / title 提案义务）。
        val hasDoc = state.docAnalysis != null
        val strategy = planner.strategyFor(
            draft = state.draft,
            detScore = AgentCreationUnderstanding.deterministicScore(state.draft, hasDoc),
            documentAvailable = hasDoc
        )
        state = state.copy(busy = true, generationError = null)
        val result = runConverseWithRetry(userText, strategy)
        state = state.copy(busy = false)
        val turn = result ?: run {
            state = state.copy(
                generationError = "生成暂时失败了，你说的我都记着。重发一句，或改用手动填写。"
            )
            appendAssistant("刚才这轮生成出了点问题。你再说一遍，或者换个说法也行。")
            return
        }
        // 本轮完成：按策略快照记录（追问计数以客户端指令为准，不信模型自觉）。
        planner.recordRound(strategy.targetFollowUpField)

        var newDraft = turn.draft?.applyTo(state.draft) ?: state.draft
        // 10.4 title 提案的客户端兜底：goal 已明确而 title 仍空时直接合成提案，
        // 不等 LLM 下一轮自觉——避免了解度一直卡 60 封顶。
        if (newDraft.title.isBlank() && newDraft.goal.isNotBlank()) {
            newDraft = newDraft.copy(title = AgentCreationUnderstanding.proposeTitle(newDraft.goal))
        }
        val draftChanged = newDraft != state.draft

        // 10.2 双源融合：u_raw = 0.5×u_llm + 0.5×u_det，封顶 + 单调不减。
        val requiredReady = newDraft.title.isNotBlank() && newDraft.learnerRole.isNotBlank()
        val fused = AgentCreationUnderstanding.fuse(
            llmScore = turn.understanding,
            detScore = AgentCreationUnderstanding.deterministicScore(newDraft, hasDoc),
            previousFused = state.rawUnderstanding,
            requiredFieldsReady = requiredReady
        )

        // 10.3 requestDocument 客户端门槛：goal+role 就绪、无文档、满 2 轮才放行。
        val allowRequestDocument = turn.requestDocument &&
            !hasDoc &&
            newDraft.goal.isNotBlank() &&
            newDraft.learnerRole.isNotBlank() &&
            planner.rounds >= 2

        state = state.copy(
            rawUnderstanding = fused,
            draft = newDraft,
            requestDocumentActive = allowRequestDocument
        )

        // 追问把关（10.3）：收敛或高分一律不追问；低分（<40）必问，LLM 没问用兜底话术补上。
        val converging = strategy.converge || planner.shouldConverge()
        var followUp = turn.followUpQuestion
        if (converging || fused >= 70) {
            followUp = null
        } else if (fused < 40 && followUp == null && strategy.targetFollowUpField != null) {
            followUp = planner.fieldByLabel(strategy.targetFollowUpField)?.fallbackQuestion
        }

        val note = turn.assistantNote ?: if (converging) {
            "好的，不问了。草案缺的我按常规补上了，你看看有没有要改的，没问题就点右上角创建。"
        } else {
            null
        }
        val spoken = note ?: followUp ?: "我更新了一下草案，继续聊聊？"
        appendAssistant(spoken)
        if (followUp != null && note != null) {
            appendAssistant(followUp)
        }
        if (draftChanged) {
            // R84：草案卡单卡化——撤掉旧卡、最新草案永远沉在对话流末尾，
            // 让「当前输出状态」持久可见，而不是滚出一串过期的历史卡。
            state = state.copy(
                feed = state.feed.filterNot { it is AgentCreationFeedEntry.DraftCard } +
                    AgentCreationFeedEntry.DraftCard(
                        id = nextId(),
                        configuration = newDraft
                    )
            )
        }
    }

    /** 生成失败 → 自动原样重试 1 次；仍失败按生成失败降级。 */
    private suspend fun runConverseWithRetry(
        userText: String,
        strategy: AgentCreationTurnStrategy
    ): AgentCreationTurnResult? {
        repeat(2) { attempt ->
            val outcome = runCatching {
                gateway.converse(history, userText, state.draft, state.docAnalysis, strategy)
            }
            outcome.fold(
                onSuccess = { return it },
                onFailure = { failure -> if (failure is CancellationException) throw failure }
            )
            if (attempt == 1) return null
        }
        return null
    }

    private fun appendUser(text: String) {
        state = state.copy(feed = state.feed + AgentCreationFeedEntry.User(id = nextId(), text = text))
    }

    private fun appendAssistant(text: String) {
        state = state.copy(feed = state.feed + AgentCreationFeedEntry.Assistant(id = nextId(), text = text))
        history += AgentCreationHistoryTurn(isUser = false, text = text)
    }

    private fun updateFileCard(id: String, transform: (AgentCreationFeedEntry.FileCard) -> AgentCreationFeedEntry.FileCard) {
        state = state.copy(feed = state.feed.map { if (it.id == id && it is AgentCreationFeedEntry.FileCard) transform(it) else it })
    }

    private fun nextId(): String = "entry-${nowEpochMillis()}-${entrySequence++}"
}
