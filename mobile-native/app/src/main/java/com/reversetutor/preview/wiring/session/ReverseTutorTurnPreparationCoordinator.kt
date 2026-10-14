package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.background.GenerationPartialStore
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.GenerationState
import com.reversetutor.feature.chat.GenerationUiContract
import com.reversetutor.feature.chat.SessionContractProvider
import com.reversetutor.feature.chat.SessionConversationContract
import com.reversetutor.feature.chat.TurnMonologuePort
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.session.RTSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 反转家教后台回合准备与执行协调器。
 *
 * 实现了 [BackgroundTurnPreparationPort] 与 [SessionContractProvider]：
 * 1. 快速校验输入（判空、判删）
 * 2. 维护 [RTSession] 与 [LlmMessage] 状态机
 * 3. 构造并入队后台 Job，原子锁定为 Running（防止 Worker 竞争）
 * 4. 立即向 UI 返回 LOADING 状态的投影契约
 * 5. 异步协程接管 [ToolLoop.runTurn] 多轮工具决策执行
 * 6. 将实时决策轨迹流式写入 [GenerationPartialStore] 供思考抽屉展示
 * 7. 完成后持久化助手回复到消息仓库，生成 READY 终态契约并标记 Job 为 Completed
 */
class ReverseTutorTurnPreparationCoordinator(
    private val isSessionDeleted: suspend (String) -> Boolean,
    private val enqueueJob: suspend (BackgroundGenerationInput, Long) -> BackgroundGenerationJob,
    private val claimJob: suspend (id: String, nowEpochMillis: Long) -> Int = { _, _ -> 1 },
    private val updateJobCompleted: suspend (id: String, nowEpochMillis: Long) -> Unit,
    private val updateJobFailed: suspend (id: String, nowEpochMillis: Long, errorMessage: String) -> Unit,
    private val saveAssistantMessage: suspend (
        spaceId: String,
        sessionId: String,
        text: String,
        monologue: String?,
        nowEpochMillis: Long
    ) -> String,
    private val loadMessages: suspend (String) -> List<ConversationMessageContract> = { emptyList() },
    private val partialStore: GenerationPartialStore? = null,
    private val toolLoop: ToolLoop,
    private val llmClientProvider: suspend () -> ToolLoopLlmClient,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val onSessionUpdated: ((RTSession) -> Unit)? = null
) : BackgroundTurnPreparationPort, SessionContractProvider, TurnMonologuePort {

    private val sessions = ConcurrentHashMap<String, RTSession>()
    private val sessionMessages = ConcurrentHashMap<String, MutableList<LlmMessage>>()
    private val latestContracts = ConcurrentHashMap<String, SessionConversationContract>()

    // 智能插队（2026-10-11 用户拍板）：sessionId → 活动回合 jobId。活动回合执行中
    // 到达的连发请求不新建 job，文本进 pendingInjections 由活动回合的 ToolLoop
    // 每轮吸收（模型带着新内容继续思考），合并为一条回复。
    private val activeJobs = ConcurrentHashMap<String, String>()
    private val pendingInjections =
        ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<String>>()

    /** 原子拉空注入队列（toList+clear 之间可能丢新元素，必须逐个 poll）。 */
    private fun drainInjections(sessionId: String): List<String> {
        val queue = pendingInjections[sessionId] ?: return emptyList()
        val drained = mutableListOf<String>()
        while (true) {
            val text = queue.poll() ?: break
            drained.add(text)
        }
        return drained
    }

    /** R-RT-5：LOADING 期间思考抽屉实时流——委托本协调器自己的 partialStore。 */
    override fun getMonologue(jobId: String, token: String): String? =
        partialStore?.getMonologue(jobId, token)

    override fun getPreview(jobId: String, token: String): String? =
        partialStore?.get(jobId, token)

    override suspend fun prepareAndEnqueue(
        request: BackgroundTurnPreparationRequest
    ): BackgroundTurnPreparationResult {
        try {
            if (request.userText.isBlank() && request.imageAttachments.isEmpty()) {
                return BackgroundTurnPreparationResult.BlankInput
            }
            if (isSessionDeleted(request.sessionId)) {
                return BackgroundTurnPreparationResult.SessionUnavailable
            }

            val currentSession = sessions.getOrPut(request.sessionId) {
                RTSession(
                    sessionId = request.sessionId,
                    topic = request.sessionSnapshot?.title?.ifBlank { "反转家教" } ?: "反转家教",
                    studentId = request.sessionSnapshot?.learnerDisplayName?.ifBlank { "小理" } ?: "小理",
                    grade = "初二",
                    cardId = "proud_average",
                    understandingLevel = 1,
                    initialUnderstandingLevel = 1,
                    currentRound = 0
                )
            }

            val now = nowEpochMillis()

            // 智能插队：该会话已有活动回合且本次为纯文本时，不入队新 job——
            // 文本注入活动回合的 ToolLoop（模型带着新内容继续思考），返回活动
            // job 让 UI 继续观察同一回合；连发合并为一条回复。
            val activeJobId = activeJobs[request.sessionId]
            if (activeJobId != null && request.imageAttachments.isEmpty()) {
                pendingInjections.getOrPut(request.sessionId) {
                    java.util.concurrent.ConcurrentLinkedQueue()
                }.add(request.userText)
                latestContracts[request.sessionId]?.let { contract ->
                    return BackgroundTurnPreparationResult.Queued(activeJobId, contract)
                }
                // 无快照契约（理论上活动回合必有）则继续走新 job 兑底
            }

            val generationInput = BackgroundGenerationInput(
                spaceId = request.spaceId,
                sessionId = request.sessionId,
                userMessageId = request.userMessageId,
                userText = request.userText,
                token = LlmGenerationToken(request.token),
                quoteExcerpt = request.quoteExcerpt,
                imageAttachments = request.imageAttachments
            )

            val job = enqueueJob(generationInput, now)

            // 原子标记为 Running，防止外部 Worker 重复执行冲突
            claimJob(job.id, now)

            val historyMessages = loadMessages(request.sessionId)
            val queuedContract = ReverseTutorContractMapper.mapQueuedContract(
                session = currentSession,
                turnId = job.id,
                messages = historyMessages
            )
            latestContracts[request.sessionId] = queuedContract

            // 启动后台异步协程执行多轮工具决策
            coroutineScope.launch {
                executeTurn(
                    jobId = job.id,
                    request = request,
                    session = currentSession
                )
            }

            return BackgroundTurnPreparationResult.Queued(job.id, queuedContract)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return BackgroundTurnPreparationResult.Failed
        }
    }

    private suspend fun executeTurn(
        jobId: String,
        request: BackgroundTurnPreparationRequest,
        session: RTSession
    ) {
        val token = request.token
        try {
            activeJobs[request.sessionId] = jobId
            partialStore?.setMonologue(jobId, token, "正在分析教师讲解...")

            val history = sessionMessages.getOrPut(request.sessionId) {
                val existing = loadMessages(request.sessionId)
                existing.map { msg ->
                    LlmMessage(
                        role = if (msg.role == "assistant") "assistant" else "user",
                        content = msg.text,
                        timestamp = "",
                        round = 0
                    )
                }.toMutableList()
            }

            val llmClient = llmClientProvider()

            // 独白累积（2026-10-11 两轮拍板）：逐行追加每轮 LLM 内心话
            // （reasoning_content / 过渡语）；工具调用轨迹不写入——浏览者看到的
            // 是纯内心文案推理过程（工具链信息对老师是噪音）。窗口 4000 字。
            // 真流式（2026-10-13 用户拍板「看得到思考过程」）：llm_thinking 增量
            // 先累计为当前轮流式段，过中文比例闸后与已定稿轮次合并覆盖写抽屉，
            // UI 150ms 轮询即实时；llm_round 定稿时把流式段转正并清空缓冲，
            // 避免定稿行与流式段重复。
            val finalizedMonologue = StringBuilder()
            val streamingMonologue = StringBuilder()
            val streamingReasoning = StringBuilder()
            val onEvent: (JSONObject) -> Unit = { event ->
                when (event.optString("type")) {
                    "llm_thinking" -> {
                        event.optString("reasoning").takeIf { it.isNotEmpty() }?.let {
                            streamingReasoning.append(it)
                        }
                        event.optString("content").takeIf { it.isNotEmpty() }?.let {
                            streamingMonologue.append(it)
                        }
                        // ToolLoop 侧 content 已过信封拆分（只含独白增量）。信封流
                        // 优先，reasoning 流过闸兜底；全不过闸就不上抽屉，与轮末
                        // llm_round 闸口径一致。
                        val candidate = when {
                            streamingMonologue.isNotEmpty() &&
                                ToolLoop.isMostlyChinese(streamingMonologue.toString()) ->
                                streamingMonologue.toString()
                            streamingReasoning.isNotEmpty() &&
                                ToolLoop.isMostlyChinese(streamingReasoning.toString()) ->
                                streamingReasoning.toString()
                            else -> ""
                        }
                        if (candidate.isNotEmpty()) {
                            writeMonologueSnapshot(
                                jobId, token, finalizedMonologue.toString(), candidate
                            )
                        }
                    }
                    "llm_round" -> {
                        // ToolLoop 已把「信封优先」选好的内心话放进 content；reasoning 仅
                        // 兜底。2026-10-13 换 isMostlyChinese 比例闸——「含汉字」太宽松，
                        // 英文思考流嵌一句中文就整段漏过（真机实测 2.9% / 6.6% 两轮）。
                        val content = event.optString("content").trim()
                        val reasoning = event.optString("reasoning").trim()
                        val inner = when {
                            content.isNotEmpty() -> content
                            reasoning.isNotEmpty() && ToolLoop.isMostlyChinese(reasoning) -> reasoning
                            else -> ""
                        }
                        if (inner.isNotEmpty()) {
                            // 2026-10-13 用户拍板：剔除「【第N轮内心】」轮次标注，
                            // 抽屉只保留纯内心话，按轮次自然换行。
                            val line = inner.takeLast(1000)
                            if (finalizedMonologue.isNotEmpty()) finalizedMonologue.append('\n')
                            finalizedMonologue.append(line)
                        }
                        // 本轮流式段已定稿（或被闸拦下）→ 清空防跨轮串段，
                        // 并把抽屉重写为纯定稿轨迹。
                        streamingMonologue.setLength(0)
                        streamingReasoning.setLength(0)
                        writeMonologueSnapshot(jobId, token, finalizedMonologue.toString(), "")
                    }
                }
            }

            val turnResult = toolLoop.runTurn(
                session = session,
                historyMessages = history,
                userText = request.userText,
                isOpening = false,
                llmClient = llmClient,
                onEvent = onEvent,
                // 智能插队：每轮吸收连发到达的新消息
                pendingUserTexts = { drainInjections(request.sessionId) }
            )

            // 更新内存状态机
            sessions[request.sessionId] = turnResult.updatedSession
            sessionMessages[request.sessionId] = turnResult.updatedMessages.toMutableList()
            onSessionUpdated?.invoke(turnResult.updatedSession)

            // 提取学生台词与思考独白
            val replyText = turnResult.report.finalReply?.trim()?.ifBlank {
                "老师，我还在思考你刚才说的话，能再解释一下吗？"
            } ?: "老师，我还在思考你刚才说的话，能再解释一下吗？"

            // 终态独白（2026-10-11 真机反馈「独白少内容」）：过程累积的内心轨迹
            // （滚动窗口）+ 终轮内心合并落抽屉，并随消息持久化。
            // 2026-10-13 用户拍板：剔除「### 反转家教决策轨迹」统计块，
            // 抽屉只保留纯内心话；都没有时置空。
            val rawLive = partialStore?.getMonologue(jobId, token).orEmpty()
            val liveMonologue = if (rawLive == "正在分析教师讲解...") "" else rawLive.trim()
            val monologue = when {
                liveMonologue.isNotBlank() -> liveMonologue
                !turnResult.report.finalMonologue.isNullOrBlank() ->
                    turnResult.report.finalMonologue!!
                else -> ""
            }
            partialStore?.setMonologue(jobId, token, monologue)
            partialStore?.append(jobId, token, replyText)

            val now = nowEpochMillis()

            // 持久化学生回复消息
            val assistantMessageId = saveAssistantMessage(
                request.spaceId,
                request.sessionId,
                replyText,
                monologue,
                now
            )

            // 映射终态 UI 契约
            val updatedMessages = loadMessages(request.sessionId)
            val readyContract = ReverseTutorContractMapper.mapToContract(
                session = turnResult.updatedSession,
                turnResult = turnResult,
                assistantMessageId = assistantMessageId,
                turnId = jobId,
                messages = updatedMessages
            )
            latestContracts[request.sessionId] = readyContract

            // 标记 Job 完成
            updateJobCompleted(jobId, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val errorText = e.message ?: "生成失败"
            val now = nowEpochMillis()
            updateJobFailed(jobId, now, errorText)

            // 将最新契约标记为 ERROR 状态
            val current = latestContracts[request.sessionId]
            if (current != null) {
                latestContracts[request.sessionId] = current.copy(
                    generation = GenerationUiContract(
                        state = GenerationState.ERROR,
                        safeError = "生成失败: $errorText"
                    )
                )
            }
        } finally {
            activeJobs.remove(request.sessionId, jobId)
            // 兑底：注入文本在回合收尾后仍有残留（终检后的毫秒缝隙）→ 接续新
            // 回合处理。首条作触发文本，其余留在队列由新回合的每轮吸收消化。
            runCatching {
                val leftover = drainInjections(request.sessionId).filter { it.isNotBlank() }
                if (leftover.isNotEmpty()) {
                    startFollowUpTurn(request, leftover)
                }
            }
        }
    }

    /**
     * 智能插队兑底：活动回合已收束但注入队列仍有残留时，以其首条为触发文本
     * 启动接续回合（此时 activeJobs 已释放，走正常入队路径）；其余文本留在
     * pendingInjections 由接续回合的每轮吸收消化，避免重复注入。
     */
    private suspend fun startFollowUpTurn(
        original: BackgroundTurnPreparationRequest,
        leftover: List<String>
    ) {
        val request = original.copy(
            token = java.util.UUID.randomUUID().toString(),
            userMessageId = "followup-${java.util.UUID.randomUUID()}",
            userText = leftover.first(),
            imageAttachments = emptyList()
        )
        pendingInjections.getOrPut(request.sessionId) {
            java.util.concurrent.ConcurrentLinkedQueue()
        }.addAll(leftover.drop(1))
        prepareAndEnqueue(request)
    }

    /**
     * 真流式抽屉快照（2026-10-13）：已定稿轮次轨迹 + 当前轮流式段合并覆盖写
     * partialStore（覆盖式 API），抽屉 150ms 轮询即逐字实时。滚动窗口 4000 字
     * 与原 appendMonologueLine 口径一致（该函数已被本函数取代并移除）。
     */
    private fun writeMonologueSnapshot(
        jobId: String,
        token: String,
        finalized: String,
        streaming: String
    ) {
        val merged = when {
            finalized.isEmpty() -> streaming
            streaming.isEmpty() -> finalized
            else -> "$finalized\n$streaming"
        }
        if (merged.isEmpty()) return
        val windowed = if (merged.length > 4000) "…" + merged.takeLast(4000) else merged
        partialStore?.setMonologue(jobId, token, windowed)
    }

    override fun getLatestContract(sessionId: String): SessionConversationContract? {
        return latestContracts[sessionId]
    }

    fun getSession(sessionId: String): RTSession? = sessions[sessionId]

    fun setSession(sessionId: String, session: RTSession) {
        sessions[sessionId] = session
    }
}
