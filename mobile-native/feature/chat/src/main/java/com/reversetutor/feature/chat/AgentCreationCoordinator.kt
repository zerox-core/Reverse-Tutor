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
    val docAnalysis: AgentCreationDocAnalysis? = null,
    /** R92：检测到有上次未完成的快照，等用户选「继续上次 / 创建新会话」。 */
    val resumeAvailable: Boolean = false
) {
    /** R102：了解度降级为纯展示值——分数不再封顶，也不再驱动流程推进（改由槽位确认状态裁决）。 */
    val displayedUnderstanding: Int
        get() = rawUnderstanding

    val understandingHigh: Boolean get() = displayedUnderstanding >= 85
    val understandingLow: Boolean get() = displayedUnderstanding < 50

    /** 创建可用：必填齐全即常驻可点。 */
    val canCreate: Boolean get() = draft.validationErrors().isEmpty()
}

class AgentCreationCoordinator(
    private val gateway: AgentCreationGateway,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val stateStore: AgentCreationStateStore? = null,
    /** R100：方案B 状态机灰度（2026-10-04 用户拍板）。false = 完全走 R99 契约路径。 */
    private val graphEnabled: Boolean = false
) {
    /**
     * R91：状态变更即时通知——用户消息发出后气泡立刻上屏，
     * 不等整轮 LLM 生成结束才和回复一起出现（2026-09-26 用户真机反馈）。
     */
    var onStateChanged: (() -> Unit)? = null

    var state: AgentCreationUiState = AgentCreationUiState()
        private set(value) {
            field = value
            onStateChanged?.invoke()
        }

    private val history = mutableListOf<AgentCreationHistoryTurn>()
    private var planner = AgentCreationFollowUpPlanner()
    private var entrySequence = 0
    /** R102：信息槽位确认闸门——propose/confirm 全在代码层，模型对状态转移零投票权。 */
    private var slots = CreationSlots()
    /** R102：上一轮悬置提案对应的槽位；本轮用户文本先对它做确认信号判定。 */
    private var lastAskedSlot: CreationSlot? = null
    /** R92：检测到但未恢复的快照——入口弹窗确认「继续上次」才真正恢复。 */
    private var pendingSnapshot: AgentCreationSnapshot? = null

    /** R100 方案B 状态机：graphEnabled 时由它裁决「什么时候该出孵化草案 / 流程图」。 */
    internal val creationGraph = AgentCreationGraph()

    init {
        // R92：有本地快照不再静默恢复（R85 是静默恢复）——先挂起，
        // 入口弹窗问「继续上次还是创建新会话」；直接默认进上次的，
        // 想新建会话的人只能硬着头皮改旧草案，太费劲（2026-09-26 用户拍板）。
        val snapshot = stateStore?.load()
        if (snapshot != null && snapshot.feed.isNotEmpty()) {
            pendingSnapshot = snapshot
            state = AgentCreationUiState(resumeAvailable = true)
        }
    }

    fun start() {
        // R92：快照待确认期间不发开场白——等用户在弹窗里二选一。
        if (state.resumeAvailable) return
        if (state.phase != AgentCreationPhase.Idle) return
        state = state.copy(
            phase = AgentCreationPhase.Conversing,
            feed = state.feed + AgentCreationFeedEntry.Assistant(
                id = nextId(),
                text = "想学什么？直接说就行——比如「我想把初中物理浮力这块讲明白」，也可以随时把教材、试卷发给我。"
            )
        )
        persist()
    }

    fun markCreated() {
        state = state.copy(phase = AgentCreationPhase.Created)
        // R85：创建成功后清掉本地快照，下次进创建窗口从零开始。
        stateStore?.clear()
    }

    /** R92 入口询问「继续上次」：把挂起的快照恢复进对话，接着聊。 */
    fun resumePending(): Boolean {
        val snapshot = pendingSnapshot ?: return false
        pendingSnapshot = null
        restore(snapshot)
        persist()
        return true
    }

    /** R92 入口询问「创建新会话」：清掉旧快照与追问进度，从零开始。 */
    fun startFresh() {
        pendingSnapshot = null
        stateStore?.clear()
        history.clear()
        planner = AgentCreationFollowUpPlanner()
        entrySequence = 0
        slots = CreationSlots()
        lastAskedSlot = null
        state = AgentCreationUiState()
        if (graphEnabled) creationGraph.restoreFromSnapshot(false)
        start()
    }

    /** 用户发一条文字消息：→ P2' 生成 → 更新了解程度/草案/追问。 */
    suspend fun sendUserText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || state.busy || state.phase != AgentCreationPhase.Conversing) return
        if (graphEnabled) {
            // R100 方案B：草案待确认期间，确认语走确认节点，其余文字一律当批注打回访谈。
            if (creationGraph.state == CreationState.ConfirmDraft) {
                appendUser(trimmed)
                history += AgentCreationHistoryTurn(isUser = true, text = trimmed)
                if (isIncubationConfirm(trimmed)) {
                    confirmIncubation()
                } else {
                    supersedePendingIncubationCard()
                    creationGraph.transitionTo(CreationState.Interview)
                    converseTurn(trimmed)
                }
                persist()
                return
            }
            // 流程图出完后继续发消息 = 修订：重开访谈轨道；首轮说话进访谈。
            creationGraph.reopenForRevision()
            creationGraph.noteUserSpoke()
        }
        // 10.3：用户明确说「别问了 / 直接生成」→ 立即收敛，本轮即按收敛策略执行。
        if (AgentCreationFollowUpPlanner.isStopAsking(trimmed)) {
            planner.forceConverge()
        }
        appendUser(trimmed)
        history += AgentCreationHistoryTurn(isUser = true, text = trimmed)
        converseTurn(trimmed)
        persist()
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
            persist()
            return
        }
        updateFileCard(cardId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzed) }
        state = state.copy(phase = AgentCreationPhase.Conversing, docAnalysis = analysis)
        appendAssistant(
            "我看了这份《${analysis.materialTitle}》。" +
                (analysis.summary.ifBlank { "大纲：" + analysis.outline.take(3).joinToString("、") }) +
                "\n建议路径：${analysis.suggestedPath.firstOrNull() ?: "按大纲顺序过一遍"}。你想怎么学？"
        )
        persist()
    }

    /** 失败的文件卡重试分析。 */
    suspend fun retryDocumentAnalysis(entryId: String) {
        val card = state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>()
            .firstOrNull { it.id == entryId } ?: return
        if (card.status != AgentCreationFeedEntry.FileCard.FileStatus.Failed) return
        updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzing) }
        val analysis = runCatching { gateway.analyzeDocument(card.fileName) }.getOrNull() ?: run {
            updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Failed) }
            persist()
            return
        }
        updateFileCard(entryId) { it.copy(status = AgentCreationFeedEntry.FileCard.FileStatus.Analyzed) }
        state = state.copy(docAnalysis = state.docAnalysis ?: analysis)
        persist()
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
            slots = slots,
            detScore = AgentCreationUnderstanding.deterministicScore(state.draft, hasDoc),
            documentAvailable = hasDoc
        )
        state = state.copy(busy = true, generationError = null)
        // R93 流式上屏：网关边生成边回调口语全量快照，懒建一个占位 Assistant 气泡随回调生长；
        // 占位气泡不进 history、不落正式文案，轮次结束无论成败都撤掉，由下方逻辑落定正式气泡。
        // R98 阶段化思考块（2026-09-28 用户拍板方案）：思考块展示「产品在做什么」的阶段流
        // （理解输入 → 连接模型 → 深度思考 → 生成回复 → 自检修正），活动段有且仅有一个；
        // 模型原始推理完整保留进二级折叠——取消 600 字截窗（实测 997/1857 字思考被裁到 600，
        // 开头的理解与规划恰是被裁部分，用户反馈「看不到思考逻辑」）。
        var streamingEntryId: String? = null
        var spokenStarted = false
        var thinkingSeconds = 0L
        val reasoningBuffer = StringBuilder()
        val turnStartedAt = nowEpochMillis()
        val stageList = mutableListOf<AgentCreationStageEvent>()
        var activeStageStartedAt = turnStartedAt
        var decisionReceived = false
        fun stageElapsed(since: Long) = ((nowEpochMillis() - since) / 1000L).coerceAtLeast(0L)
        fun activateStage(key: String, label: String) {
            val idx = stageList.indexOfLast { it.active }
            if (idx >= 0 && stageList[idx].key == key) return
            if (idx >= 0) {
                stageList[idx] = stageList[idx].copy(active = false, elapsedSeconds = stageElapsed(activeStageStartedAt))
            }
            stageList += AgentCreationStageEvent(key, label, active = true)
            activeStageStartedAt = nowEpochMillis()
        }
        fun upsertPlaceholder(text: String) {
            val placeholderId = streamingEntryId
            val elapsedSeconds = if (spokenStarted) thinkingSeconds else stageElapsed(turnStartedAt)
            val reasoningShown = reasoningBuffer.toString().ifEmpty { null }
            val stagesSnapshot = stageList.toList()
            if (placeholderId == null) {
                val entry = AgentCreationFeedEntry.Assistant(
                    id = nextId(),
                    text = text,
                    reasoning = reasoningShown,
                    reasoningElapsedSeconds = elapsedSeconds,
                    thinkingDone = spokenStarted,
                    stages = stagesSnapshot
                )
                streamingEntryId = entry.id
                state = state.copy(feed = state.feed + entry)
            } else {
                state = state.copy(
                    feed = state.feed.map {
                        if (it.id == placeholderId && it is AgentCreationFeedEntry.Assistant) {
                            it.copy(
                                text = text,
                                reasoning = reasoningShown ?: it.reasoning,
                                reasoningElapsedSeconds = elapsedSeconds,
                                thinkingDone = spokenStarted,
                                stages = stagesSnapshot
                            )
                        } else it
                    }
                )
            }
        }
        // R98 轮次一启动就落「理解输入」段——阶段块从第 0 秒可见。
        activateStage("understand", "正在理解你的输入…")
        upsertPlaceholder("")
        val result = runConverseWithRetry(
            userText = userText,
            strategy = strategy,
            onThinkingDecision = { decision ->
                // 决策结果即产品流程：「理解输入」段改写成决策结论，并补「连接模型」段
                // 覆盖请求发出到首块到达的等待（旧网关不回调时保留原文案，首块到达时直接进下一段）。
                if (!decisionReceived) {
                    decisionReceived = true
                    activateStage("connect", "正在连接模型…")
                    val idx = stageList.indexOfLast { it.key == "understand" }
                    if (idx >= 0) {
                        stageList[idx] = stageList[idx].copy(
                            label = if (decision.enabled) "这是值得深想的问题 · 开启深度思考" else "直接回答"
                        )
                    }
                    upsertPlaceholder("")
                }
            },
            onRetryAttempt = {
                // R98 自检与修正：重试不再静默——补一段「自检未通过」，
                // 并重置本轮流式缓存（修 R97 静默重试把两段思考拼进一个块的问题）。
                activateStage("selfcheck", "第一次生成没通过自检 · 正在重新生成")
                reasoningBuffer.setLength(0)
                spokenStarted = false
                thinkingSeconds = 0L
                upsertPlaceholder("")
            },
            onPartialSpoken = { partial ->
                if (!spokenStarted) {
                    thinkingSeconds = stageElapsed(turnStartedAt)
                    spokenStarted = true
                    activateStage("generating", "生成回复中…")
                }
                upsertPlaceholder(partial)
            },
            onReasoning = { chunk ->
                if (!spokenStarted) {
                    activateStage("thinking", "深度思考中…")
                    reasoningBuffer.append(chunk)
                    upsertPlaceholder("")
                }
            }
        )
        // R98：收掉活动段并补「完成」汇总段；reasoning 全量携带到正式气泡（不再截窗），
        // 思考仍不进 history、不落快照。
        val carriedReasoning = reasoningBuffer.toString().ifEmpty { null }
        val carriedSeconds = if (spokenStarted) thinkingSeconds else stageElapsed(turnStartedAt)
        run {
            val idx = stageList.indexOfLast { it.active }
            if (idx >= 0) {
                stageList[idx] = stageList[idx].copy(active = false, elapsedSeconds = stageElapsed(activeStageStartedAt))
            }
        }
        val totalSeconds = stageElapsed(turnStartedAt)
        val doneLabel = if (carriedReasoning != null) {
            "共用时 " + totalSeconds + "s（思考 " + thinkingSeconds + "s + 生成 " +
                (totalSeconds - thinkingSeconds).coerceAtLeast(0L) + "s）"
        } else {
            "共用时 " + totalSeconds + "s"
        }
        stageList += AgentCreationStageEvent("done", doneLabel, elapsedSeconds = totalSeconds)
        val carriedStages = stageList.toList()
        streamingEntryId?.let { placeholderId ->
            state = state.copy(feed = state.feed.filterNot { it.id == placeholderId })
            streamingEntryId = null
        }
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
        // R87：主动问过的「要资料 / 确认路径」也记账，各只问一次。
        if (strategy.shouldRequestDocument) planner.markDocumentAsked()
        if (strategy.mustConfirmPath) planner.markPathConfirmAsked()

        // R102 确认闸门：模型回包 draft 只能进 Proposed；确认信号（代码判定）才升 Confirmed；
        // 草案只落 Confirmed 值——模型对「什么算定稿」零投票权。
        val converging = strategy.converge || planner.shouldConverge()
        val roundNow = planner.rounds
        // ① 上一轮悬置提案 × 本轮用户文本：确认信号判定（非正面回答一律拦截）。
        val asked = lastAskedSlot
        if (asked != null && slots.statusOf(asked) == SlotStatus.Proposed &&
            !CreationConfirmationSignals.isNonAnswer(userText)
        ) {
            val proposed = slots.valueOf(asked)
            if (CreationConfirmationSignals.isExplicitConfirm(userText) ||
                CreationConfirmationSignals.hasConfirmPrefix(userText) ||
                CreationConfirmationSignals.overlapsProposal(proposed, userText)
            ) {
                slots = slots.confirm(asked, round = roundNow)
            }
        }
        // ② 本轮模型提案逐个过闸：用户亲口给值 → Confirmed；收敛兜底（Goal 除外）→ Confirmed；
        // 其余只进 Proposed、不落草案。
        val patch = turn.draft
        var newDraft = state.draft
        if (patch != null) {
            fun gate(slot: CreationSlot, value: String?): Boolean {
                if (value.isNullOrBlank()) return true
                val v = value.trim()
                if (slots.statusOf(slot) == SlotStatus.Confirmed) {
                    return slots.valueOf(slot) == v
                }
                if (CreationConfirmationSignals.userSourced(v, userText)) {
                    slots = slots.confirm(slot, v, roundNow)
                    return true
                }
                if (converging && slot != CreationSlot.Goal) {
                    slots = slots.confirm(slot, v, roundNow)
                    return true
                }
                slots = slots.propose(slot, v, roundNow)
                return false
            }
            val filtered = patch.copy(
                goal = patch.goal.takeIf { gate(CreationSlot.Goal, it) },
                learnerRole = patch.learnerRole.takeIf { gate(CreationSlot.LearnerRole, it) },
                persona = patch.persona.takeIf { gate(CreationSlot.Persona, it) },
                dialogueStrategy = patch.dialogueStrategy.takeIf { gate(CreationSlot.TeachingStyle, it) },
                plan = patch.plan.takeIf { gate(CreationSlot.Constraints, it) },
                learningScope = patch.learningScope.takeIf { gate(CreationSlot.Constraints, it) },
                stageMilestones = patch.stageMilestones.takeIf { gate(CreationSlot.Constraints, it) }
            )
            newDraft = filtered.applyTo(state.draft)
        }
        // ③ 收敛兜底：学习者角色仍空 → 默认值直接 Confirmed（用户止损路径；Goal 永不默认）。
        if (converging && slots.statusOf(CreationSlot.LearnerRole) != SlotStatus.Confirmed) {
            slots = slots.confirm(CreationSlot.LearnerRole, "基础未说明，按零基础起步", roundNow)
        }
        // ④ 已确认值同步进草案（只填空白——提案值与未确认值一律不进草案）。
        run {
            var synced = newDraft
            if (synced.goal.isBlank() && slots.statusOf(CreationSlot.Goal) == SlotStatus.Confirmed) {
                synced = synced.copy(goal = slots.valueOf(CreationSlot.Goal))
            }
            if (synced.learnerRole.isBlank() && slots.statusOf(CreationSlot.LearnerRole) == SlotStatus.Confirmed) {
                synced = synced.copy(learnerRole = slots.valueOf(CreationSlot.LearnerRole))
            }
            if (synced.persona.isBlank() && slots.statusOf(CreationSlot.Persona) == SlotStatus.Confirmed) {
                synced = synced.copy(persona = slots.valueOf(CreationSlot.Persona))
            }
            if (synced.dialogueStrategy.isBlank() && slots.statusOf(CreationSlot.TeachingStyle) == SlotStatus.Confirmed) {
                synced = synced.copy(dialogueStrategy = slots.valueOf(CreationSlot.TeachingStyle))
            }
            newDraft = synced
        }
        // ⑤ R102 title 兜底：Goal 已确认且 goal 非空、title 仍空时才合成提案
        // （修复「魔方」事故放大器——目标未经用户确认前绝不派生标题）。
        if (newDraft.title.isBlank() &&
            slots.statusOf(CreationSlot.Goal) == SlotStatus.Confirmed &&
            newDraft.goal.isNotBlank()
        ) {
            newDraft = newDraft.copy(title = AgentCreationUnderstanding.proposeTitle(newDraft.goal))
        }
        // R86 学习路径的客户端兜底：LLM 没给路径时，用文档分析的「建议路径」
        // （教材目录顺序）补齐——有教材按教材、没教材靠 LLM 分解，两源统一。
        if (newDraft.learningPath.isEmpty()) {
            val docPath = state.docAnalysis?.suggestedPath.orEmpty()
                .map { it.trim().take(48) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(12)
            if (docPath.isNotEmpty()) {
                newDraft = newDraft.copy(learningPath = docPath)
            }
        }
        val draftChanged = newDraft != state.draft

        // 10.2 双源融合：u_raw = 0.5×u_llm + 0.5×u_det，单调不减（R102 起纯展示、无封顶）。
        val fused = AgentCreationUnderstanding.fuse(
            llmScore = turn.understanding,
            detScore = AgentCreationUnderstanding.deterministicScore(newDraft, hasDoc),
            previousFused = state.rawUnderstanding
        )

        // 10.3 requestDocument 客户端门槛：goal+role 就绪、无文档、满 2 轮才放行。
        // R87：策略快照判定本轮该主动要资料时，不等 LLM 自觉，客户端直接放行。
        val allowRequestDocument = (turn.requestDocument || strategy.shouldRequestDocument) &&
            !hasDoc &&
            newDraft.goal.isNotBlank() &&
            newDraft.learnerRole.isNotBlank() &&
            planner.rounds >= 2

        state = state.copy(
            rawUnderstanding = fused,
            draft = newDraft,
            requestDocumentActive = allowRequestDocument
        )

        // R102 追问把关：推进条件从「分数驱动」改为「确认驱动」——必填槽全 Confirmed 且
        // （收敛或可选槽全 Confirmed）即就绪出草案；目标未确认时强制追问目标，模型高分压不住。
        var followUp = turn.followUpQuestion
        // R87：「确认路径 / 主动要资料」是客户端排定的关键动作，了解度高分也不许吞掉。
        val proactiveAsk = strategy.mustConfirmPath || strategy.shouldRequestDocument
        val goalMissing = slots.statusOf(CreationSlot.Goal) != SlotStatus.Confirmed
        val readyForProposal = !goalMissing && slots.requiredConfirmed() &&
            (converging || slots.optionalAllConfirmed())
        if (readyForProposal && !proactiveAsk) {
            followUp = null
        } else if (followUp == null && strategy.targetFollowUpField != null) {
            followUp = planner.fieldByLabel(strategy.targetFollowUpField)?.fallbackQuestion
        }
        // R102 修正：planner 本轮目标本来就是 Goal 时，保留 LLM 给的选项问题（比兜底话术更有价值）。
        if (goalMissing && !proactiveAsk &&
            strategy.targetFollowUpField != AgentCreationFollowUpPlanner.Field.Goal.label
        ) {
            followUp = AgentCreationFollowUpPlanner.Field.Goal.fallbackQuestion
        }
        // R87 兜底：LLM 没问时客户端确定性补上（确认路径优先于要资料）。
        if (proactiveAsk && followUp == null && strategy.mustConfirmPath) {
            followUp = planner.pathConfirmQuestion(newDraft.learningPath)
        }
        if (proactiveAsk && followUp == null && strategy.shouldRequestDocument) {
            followUp = AgentCreationFollowUpPlanner.DOC_REQUEST_FALLBACK
        }
        // R102：记录本轮悬置追问对应的槽位，下一轮用户文本先对它做确认信号判定。
        lastAskedSlot = when {
            followUp == null || proactiveAsk -> null
            goalMissing -> CreationSlot.Goal
            else -> CreationSlot.fromLabel(strategy.targetFollowUpField)
        }

        // R102 修正：收敛但目标未确认时不说「不问了」——本轮还在强制追问目标。
        val note = turn.assistantNote ?: if (converging && !goalMissing) {
            "好的，不问了。草案缺的我按常规补上了，你看看有没有要改的，没问题就点右上角创建。"
        } else {
            null
        }
        val spoken = note ?: followUp ?: "我更新了一下草案，继续聊聊？"
        appendAssistant(spoken, reasoning = carriedReasoning, reasoningElapsedSeconds = carriedSeconds, stages = carriedStages)
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

        // R102：出孵化草案只读槽位确认状态——了解度分数对状态转移零投票权。
        if (graphEnabled && creationGraph.state == CreationState.Interview && readyForProposal) {
            runIncubationNode()
        }
    }

    /** R100 方案B · CONFIRM_DRAFT → GENERATE_PROFILE → GENERATE_LEARNING_FLOW：用户确认孵化草案。 */
    suspend fun confirmIncubation() {
        if (!graphEnabled || state.busy || creationGraph.state != CreationState.ConfirmDraft) return
        val card = state.feed.filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>()
            .firstOrNull { it.status == AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm }
            ?: return
        val incubation = card.incubation
        // 草案结论叠进正式配置——「确认前不落库」的红线在确认这一刻解除。
        var newDraft = state.draft.copy(
            persona = incubation.personaHypothesis,
            dialogueStrategy = incubation.teachingStyle
        )
        if (incubation.milestones.isNotEmpty()) {
            newDraft = newDraft.copy(stageMilestones = incubation.milestones.joinToString(" → "))
        }
        if (newDraft.plan.isBlank() && incubation.stageGoals.isNotEmpty()) {
            newDraft = newDraft.copy(plan = incubation.stageGoals.joinToString("\n"))
        }
        state = state.copy(draft = newDraft)
        state = state.copy(
            feed = state.feed.map {
                if (it.id == card.id && it is AgentCreationFeedEntry.IncubationDraftCard) {
                    it.copy(status = AgentCreationFeedEntry.IncubationDraftCard.Status.Confirmed)
                } else it
            }.filterNot { it is AgentCreationFeedEntry.DraftCard } +
                AgentCreationFeedEntry.DraftCard(id = nextId(), configuration = newDraft)
        )
        appendAssistant("草案确认了——人物性格和教学方式已经定进草案，接下来给你生成学习流程图。")
        creationGraph.transitionTo(CreationState.GenerateProfile)
        creationGraph.transitionTo(CreationState.GenerateLearningFlow)
        runLearningFlowNode()
        persist()
    }

    /** R100 方案B · DRAFT_PROPOSAL 节点：生成孵化草案卡（失败退回访谈，对话与草案保留）。 */
    private suspend fun runIncubationNode() {
        creationGraph.transitionTo(CreationState.DraftProposal)
        state = state.copy(busy = true)
        val incubation = runNodeWithRetry {
            gateway.proposeIncubation(history.toList(), state.draft, state.docAnalysis)
        }
        state = state.copy(busy = false)
        if (incubation == null) {
            // 生成失败：回访谈轨道，下一轮收敛时可再试；不打断对话。
            creationGraph.transitionTo(CreationState.Interview)
            appendAssistant("草案整理卡了一下，没关系——你接着说，我一会儿再整理。")
            return
        }
        // 孵化草案卡单卡化：待确认卡永远只有一张，沉在流尾。
        state = state.copy(
            feed = state.feed.filterNot {
                it is AgentCreationFeedEntry.IncubationDraftCard &&
                    it.status == AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm
            } + AgentCreationFeedEntry.IncubationDraftCard(
                id = nextId(),
                incubation = incubation
            )
        )
        appendAssistant("我按咱们聊的整理了一版养成草案，你看看——没问题就点「确认草案」，要改哪里直接回复告诉我。")
        creationGraph.transitionTo(CreationState.ConfirmDraft)
    }

    /** R100 方案B · GENERATE_LEARNING_FLOW 节点：流程图失败静默降级（草案与创建不受影响）。 */
    private suspend fun runLearningFlowNode() {
        val flow = runNodeWithRetry {
            gateway.generateLearningFlow(state.draft, state.docAnalysis)
        }
        if (flow == null || flow.topics.isEmpty()) {
            // 静默降级：流程图是增强项，失败不阻塞创建。
            creationGraph.transitionTo(CreationState.Done)
            return
        }
        // 学习路径为空时用流程图主题序补齐——图谱与路径同源。
        if (state.draft.learningPath.isEmpty()) {
            state = state.copy(draft = state.draft.copy(learningPath = flow.topics.map { it.title }))
        }
        state = state.copy(
            feed = state.feed.filterNot { it is AgentCreationFeedEntry.LearningFlowCard } +
                AgentCreationFeedEntry.LearningFlowCard(id = nextId(), flow = flow)
        )
        appendAssistant("学习流程图好了——按这个顺序先基础后提升。点右上角「创建并进入聊天」就可以开始上课。")
        creationGraph.transitionTo(CreationState.Done)
    }

    /** R100 节点调用重试：失败原样重试 1 次，仍失败返回 null 由各节点自行降级。 */
    private suspend fun <T> runNodeWithRetry(call: suspend () -> T): T? {
        repeat(2) { attempt ->
            val outcome = runCatching { call() }
            outcome.fold(
                onSuccess = { return it },
                onFailure = { failure -> if (failure is CancellationException) throw failure }
            )
            if (attempt == 1) return null
        }
        return null
    }

    /** R100 批注打回：把待确认草案卡标记为已作废（保留在流里当历史）。 */
    private fun supersedePendingIncubationCard() {
        state = state.copy(
            feed = state.feed.map {
                if (it is AgentCreationFeedEntry.IncubationDraftCard &&
                    it.status == AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm
                ) {
                    it.copy(status = AgentCreationFeedEntry.IncubationDraftCard.Status.Superseded)
                } else it
            }
        )
    }

    /** R100 确认语判定：整句就是短肯定才算确认，其余文字一律按批注处理。 */
    private fun isIncubationConfirm(text: String): Boolean = INCUBATION_CONFIRM.matches(text)

    /** 生成失败 → 自动原样重试 1 次；仍失败按生成失败降级。R93：onPartialSpoken 透传流式口语快照；R95：onReasoning 透传思考流；R98：onThinkingDecision 透传决策、onRetryAttempt 在重试前回调（自检段 + 缓存重置）。 */
    private suspend fun runConverseWithRetry(
        userText: String,
        strategy: AgentCreationTurnStrategy,
        onThinkingDecision: (ThinkingBudgetDecider.Decision) -> Unit = {},
        onRetryAttempt: () -> Unit = {},
        onPartialSpoken: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {}
    ): AgentCreationTurnResult? {
        repeat(2) { attempt ->
            if (attempt > 0) onRetryAttempt()
            val outcome = runCatching {
                gateway.converseStreaming(history, userText, state.draft, state.docAnalysis, strategy, onPartialSpoken, onReasoning, onThinkingDecision)
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

    private fun appendAssistant(
        text: String,
        reasoning: String? = null,
        reasoningElapsedSeconds: Long = 0L,
        stages: List<AgentCreationStageEvent> = emptyList()
    ) {
        state = state.copy(
            feed = state.feed + AgentCreationFeedEntry.Assistant(
                id = nextId(),
                text = text,
                reasoning = reasoning,
                reasoningElapsedSeconds = reasoningElapsedSeconds,
                thinkingDone = reasoning != null,
                stages = stages
            )
        )
        history += AgentCreationHistoryTurn(isUser = false, text = text)
    }

    private fun updateFileCard(id: String, transform: (AgentCreationFeedEntry.FileCard) -> AgentCreationFeedEntry.FileCard) {
        state = state.copy(feed = state.feed.map { if (it.id == id && it is AgentCreationFeedEntry.FileCard) transform(it) else it })
    }

    private fun nextId(): String = "entry-${nowEpochMillis()}-${entrySequence++}"

    /** R85：把当前状态写成快照；Idle 无内容时不写，避免覆盖有效快照。 */
    private fun persist() {
        val store = stateStore ?: return
        if (state.phase == AgentCreationPhase.Idle) return
        store.save(
            AgentCreationSnapshot(
                draft = state.draft.deepCopy(),
                feed = state.feed,
                rawUnderstanding = state.rawUnderstanding,
                requestDocumentActive = state.requestDocumentActive,
                history = history.toList(),
                planner = planner.exportState(),
                docAnalysis = state.docAnalysis,
                entrySequence = entrySequence,
                slots = slots.toNamedEntries()
            )
        )
    }

    /** R85：从快照恢复；busy / generationError 属瞬态一律复位，文件卡「分析中」由编解码层降级。 */
    private fun restore(snapshot: AgentCreationSnapshot) {
        history.clear()
        history += snapshot.history
        planner.restoreState(snapshot.planner)
        slots = CreationSlots.fromNamedEntries(snapshot.slots)
        if (slots.entries.isEmpty()) {
            // R102 旧快照迁移：草案已有字段一律降 Proposed，要求用户确认一次。
            slots = CreationSlots.migratedFrom(snapshot.draft)
        }
        lastAskedSlot = null
        entrySequence = snapshot.entrySequence
        state = AgentCreationUiState(
            phase = if (snapshot.feed.isEmpty()) AgentCreationPhase.Idle else AgentCreationPhase.Conversing,
            feed = snapshot.feed,
            draft = snapshot.draft,
            rawUnderstanding = snapshot.rawUnderstanding,
            requestDocumentActive = snapshot.requestDocumentActive,
            docAnalysis = snapshot.docAnalysis
        )
        if (graphEnabled) {
            // R100 首版无 checkpoint：孵化卡 / 流程图卡不落快照，恢复后回到访谈轨道。
            creationGraph.restoreFromSnapshot(snapshot.feed.isNotEmpty())
        }
    }

    private companion object {
        /** R100 草案确认语：整句短肯定才算确认，长句一律按批注处理。 */
        val INCUBATION_CONFIRM = Regex(
            "^(确认|确认了|确认草案|可以|可以了|行|好|好的|没问题|就这样|就这样吧|通过|ok|okay)[！!。.~～ ]*$",
            RegexOption.IGNORE_CASE
        )
    }
}
