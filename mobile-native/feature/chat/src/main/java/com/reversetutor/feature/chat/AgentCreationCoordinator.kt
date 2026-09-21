package com.reversetutor.feature.chat

import kotlinx.coroutines.CancellationException

/**
 * Agent 会话创建状态机（设计方案 v3 · 第三章）。
 *
 * Idle → AnalyzingDocument ⇄ Conversing → Creating → Created
 * 必填字段（title / learnerRole）任一为空时，展示了解程度封顶 60；
 * 解析失败自动原样重试 1 次，仍失败按生成失败降级（对话与草案保留）。
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
            rawUnderstanding.coerceAtMost(60)
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
        state = state.copy(busy = true, generationError = null)
        val result = runConverseWithRetry(userText)
        state = state.copy(busy = false)
        val turn = result ?: run {
            state = state.copy(
                generationError = "生成暂时失败了，你说的我都记着。重发一句，或改用手动填写。"
            )
            appendAssistant("刚才这轮生成出了点问题。你再说一遍，或者换个说法也行。")
            return
        }
        val newDraft = turn.draft?.applyTo(state.draft) ?: state.draft
        val draftChanged = newDraft != state.draft
        state = state.copy(
            rawUnderstanding = turn.understanding,
            draft = newDraft,
            requestDocumentActive = turn.requestDocument && state.docAnalysis == null
        )
        val spoken = turn.assistantNote ?: turn.followUpQuestion ?: "我更新了一下草案，继续聊聊？"
        appendAssistant(spoken)
        if (turn.followUpQuestion != null && turn.assistantNote != null) {
            appendAssistant(turn.followUpQuestion)
        }
        if (draftChanged) {
            state = state.copy(
                feed = state.feed + AgentCreationFeedEntry.DraftCard(
                    id = nextId(),
                    configuration = newDraft
                )
            )
        }
    }

    /** 生成失败 → 自动原样重试 1 次；仍失败按生成失败降级。 */
    private suspend fun runConverseWithRetry(userText: String): AgentCreationTurnResult? {
        repeat(2) { attempt ->
            val outcome = runCatching {
                gateway.converse(history, userText, state.draft, state.docAnalysis)
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
