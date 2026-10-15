package com.reversetutor.feature.reversetutor.loop

import com.reversetutor.core.llm.MonologueEnvelope
import com.reversetutor.core.llm.MonologueStreamSplitter
import com.reversetutor.core.llm.ProviderHttpRequest
import com.reversetutor.core.llm.ProviderHttpResult
import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.guard.MathGuard
import com.reversetutor.feature.reversetutor.memory.RTMemoryStore
import com.reversetutor.feature.reversetutor.prompt.PromptAssembler
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchProvider
import com.reversetutor.feature.reversetutor.session.BreakthroughEvent
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.session.StruggleEvent
import com.reversetutor.feature.reversetutor.session.ToolTraceEntry
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.SessionStateSink
import com.reversetutor.feature.reversetutor.tools.ToolContext
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import com.reversetutor.feature.reversetutor.tools.ToolResult
import com.reversetutor.feature.reversetutor.tools.ToolTraceRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeoutException

// ------------------------------------------------------------------
// 数据模型与传输契约
// ------------------------------------------------------------------

data class ToolCallRequest(
    val callId: String,
    val name: String,
    val arguments: String
)

data class LlmMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCallRequest>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val round: Int? = null,
    val internal: Boolean = false,
    val timestamp: String = ""
)

data class LlmUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class LlmResponse(
    val content: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val finishReason: String = "stop",
    val model: String = "",
    val elapsedMs: Long = 0L,
    val usage: LlmUsage? = null,
    val rawMessage: JSONObject? = null
)

data class LlmCallRecord(
    val step: Int,
    val model: String,
    val elapsedMs: Long,
    val finishReason: String,
    val wantsTools: List<String>,
    val contentPreview: String
)

data class DegradationRecord(
    val type: String,
    val detail: String
)

data class TurnReport(
    val round: Int,
    val llmCalls: MutableList<LlmCallRecord> = mutableListOf(),
    val toolCalls: MutableList<ToolTraceRecord> = mutableListOf(),
    var toolCallCount: Int = 0,
    val degradations: MutableList<DegradationRecord> = mutableListOf(),
    val mathViolations: MutableList<String> = mutableListOf(),
    var finalReply: String? = null,
    val errors: MutableList<String> = mutableListOf(),
    var toolFailures: Int = 0,
    var finalMonologue: String? = null
)

data class TurnResult(
    val updatedSession: RTSession,
    val updatedMessages: List<LlmMessage>,
    val report: TurnReport
)

interface ToolLoopLlmClient {
    suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String? = null
    ): LlmResponse

    /**
     * 真流式可选实现（2026-10-13 用户拍板「看得到思考过程」）：每当模型流出
     * 一段思考就以原始增量回调一次（reasoning_content 增量 / content 中
     * <thinking> 信封独白增量），由 ToolLoop 转成 llm_thinking 事件、协调器
     * 累积过中文比例闸后实时推抽屉。默认退化为一次性 [chat]，既有 Fake/
     * 脚本客户端零改动。
     */
    suspend fun chatStreaming(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String? = null,
        onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit = { _, _ -> }
    ): LlmResponse = chat(messages, tools, model)
}

// ------------------------------------------------------------------
// ToolLoop：多轮自主决策执行引擎
// ------------------------------------------------------------------

class ToolLoop(
    val registry: ToolRegistry,
    val catalog: CurriculumCatalog,
    val store: MaterialStore,
    val maxStepsPerTurn: Int = 12,
    val recentRounds: Int = 3,
    /** 长期记忆存储（阶段 3 首批）；缺省回落到进程级默认实例，保证向后兼容。 */
    val memoryStore: RTMemoryStore? = null,
    /** 外部检索 provider；缺省不注入，等价 `WEB_SEARCH_PROVIDER=off`。 */
    val webSearchProvider: RTWebSearchProvider? = null
) {

    companion object {
        private val UNQUOTED_VALUE_REGEX = Regex(
            """("(?:\\[\s\S]|[^"\\])*"\s*:\s*)([^\s"\[\]\{\},\n][^"\[\]\{\},\n]*?)\s*([,\}\]\n])"""
        )
        private val NUMBER_OR_BOOL_REGEX = Regex("""-?\d+(\.\d+)?|true|false|null""")

        // 2026-10-12 真机反馈「思考链全是英文」：推理模型的原生 reasoning_content
        // 常是英文思考流，与中文人设不符，只把它当兑底。
        // 2026-10-13 真机反馈「多轮思考又输出英文」：仅「含汉字」太宽松——英文思考流
        // 里嵌一句要问的中文就能整段漏过（实测两轮汉字占比仅 2.9% / 6.6%）。
        // 改为比例把关 isMostlyChinese：汉字占「汉字+拉丁字母」≥30% 才视为中文；
        // 信封 / reasoning_content / 过渡语三条候选统一走这道闸，英文宁缺毋滥。
        private const val CHINESE_RATIO_THRESHOLD = 0.30

        fun isMostlyChinese(text: String): Boolean {
            if (text.isEmpty()) return false
            var cjk = 0
            var latin = 0
            for (ch in text) {
                val code = ch.code
                if (code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF) cjk++
                else if (ch in 'a'..'z' || ch in 'A'..'Z') latin++
            }
            val total = cjk + latin
            if (total == 0) return false
            return cjk.toDouble() / total >= CHINESE_RATIO_THRESHOLD
        }

        fun nowIso(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        /**
         * LLM 生成工具参数 JSON 的最佳努力修复（对齐 spike provider._repair_tool_arguments）。
         * 处理常见错误：未加引号的纯字符串值，例如 {"expected_outcomes": 教师给出解释}。
         */
        fun repairToolArguments(raw: String): JSONObject {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return JSONObject()
            try {
                return JSONObject(trimmed)
            } catch (_: Exception) {
                try {
                    val repaired = UNQUOTED_VALUE_REGEX.replace(trimmed) { match ->
                        val prefix = match.groupValues[1]
                        val value = match.groupValues[2].trim()
                        val suffix = match.groupValues[3]
                        if (NUMBER_OR_BOOL_REGEX.matches(value)) {
                            match.value
                        } else {
                            """$prefix"$value"$suffix"""
                        }
                    }
                    return JSONObject(repaired)
                } catch (_: Exception) {
                    return JSONObject().put("_raw_arguments", raw)
                }
            }
        }
    }

    /**
     * 重放历史消息给 LLM（对齐 spike orchestrator._history_for_llm）：
     * 超过 recentRounds 的旧轮次压缩：丢弃 tool 结果及带 tool_calls 的 assistant 骨架，
     * 只保留用户提问与学生的最终回复。首位拼接系统提示词。
     */
    fun historyForLlm(
        session: RTSession,
        messages: List<LlmMessage>,
        persona: JSONObject? = null
    ): List<LlmMessage> {
        val out = mutableListOf<LlmMessage>()
        val sysPrompt = PromptAssembler.buildSystemPrompt(session, persona)
        out.add(LlmMessage(role = "system", content = sysPrompt))

        val current = session.currentRound
        val recent = maxOf(1, recentRounds)

        for (m in messages) {
            val role = m.role
            val r = m.round
            val isOld = (r != null) && (current - r >= recent)

            if (isOld && role == "tool") continue
            if (isOld && role == "assistant" && !m.toolCalls.isNullOrEmpty()) continue

            when (role) {
                "user" -> {
                    if (!m.content.isNullOrEmpty()) {
                        out.add(LlmMessage(role = "user", content = m.content))
                    }
                }
                "tool" -> {
                    out.add(
                        LlmMessage(
                            role = "tool",
                            toolCallId = m.toolCallId,
                            name = m.name,
                            content = m.content ?: ""
                        )
                    )
                }
                "assistant" -> {
                    val content = m.content ?: ""
                    val tc = m.toolCalls
                    if (content.isNotEmpty() || !tc.isNullOrEmpty()) {
                        out.add(
                            LlmMessage(
                                role = "assistant",
                                content = content.ifEmpty { null },
                                toolCalls = tc
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * 执行单轮反转教学交互（对齐 spike orchestrator.run_turn）。
     */
    suspend fun runTurn(
        session: RTSession,
        historyMessages: List<LlmMessage>,
        userText: String? = null,
        isOpening: Boolean = false,
        llmClient: ToolLoopLlmClient,
        personaOverride: JSONObject? = null,
        onEvent: ((JSONObject) -> Unit)? = null,
        /**
         * 智能插队（2026-10-11 用户拍板）：每轮 LLM 调用前拉取连发到达的新用户
         * 消息文本（协调器提供）；非空则注入为 user 消息让模型带着新内容继续
         * 思考。终答轮也先查一次——有新消息则本轮过渡语保留为上下文、继续下一
         * 轮；无才真正收束。连发合并为一条回复，顺序自然正确。
         */
        pendingUserTexts: (() -> List<String>)? = null
    ): TurnResult {
        val roundNo = session.currentRound + (if (isOpening) 0 else 1)
        val messages = historyMessages.toMutableList()
        val ts = nowIso()

        if (isOpening) {
            messages.add(
                LlmMessage(
                    role = "user",
                    content = PromptAssembler.OPENING_INSTRUCTION,
                    timestamp = ts,
                    internal = true,
                    round = roundNo
                )
            )
        } else {
            messages.add(
                LlmMessage(
                    role = "user",
                    content = userText ?: "",
                    timestamp = ts,
                    round = roundNo
                )
            )
        }

        val report = TurnReport(round = roundNo)
        val sink = SessionStateSink()
        val extra = mutableMapOf<String, Any>(CoreTools.KEY_SESSION_STATE_SINK to sink)
        memoryStore?.let { extra[CoreTools.KEY_MEMORY_STORE] = it }
        webSearchProvider?.let { extra[CoreTools.KEY_WEB_SEARCH_PROVIDER] = it }
        val toolContext = ToolContext(
            session = session.copy(currentRound = roundNo),
            curriculumCatalog = catalog,
            materialStore = store,
            extra = extra
        )
        val tools = registry.schemas()

        try {
            var completedWithContent = false
            for (step in 0 until maxStepsPerTurn) {
                // 每轮开始先吸收连发到达的新消息（若有）
                val queuedTexts = pendingUserTexts?.invoke().orEmpty()
                for (text in queuedTexts) {
                    if (text.isNotBlank()) {
                        messages.add(
                            LlmMessage(
                                role = "user",
                                content = text,
                                timestamp = nowIso(),
                                round = roundNo
                            )
                        )
                    }
                }
                val currentLlmMessages = historyForLlm(
                    session = session.copy(currentRound = roundNo),
                    messages = messages,
                    persona = personaOverride
                )

                emitEvent(
                    onEvent,
                    JSONObject().apply {
                        put("type", "status")
                        put("stage", "llm")
                        put("step", report.llmCalls.size + 1)
                    }
                )

                // 真流式（2026-10-13 用户拍板「让我看到思考过程」）：优先走
                // chatStreaming，思考增量（reasoning_content / 信封独白）以
                // llm_thinking 事件透传给协调器实时刷抽屉；客户端未重写时
                // 默认退化为一次性 chat，既有 Fake/脚本客户端行为不变。
                // content 原始增量先过 MonologueStreamSplitter：<thinking> 信封
                // 内的独白按快照差值透出，正文增量丢弃——终答正文走 preview
                // 通道，透流会造成抽屉与气泡重复。
                val monologueSplitter = MonologueStreamSplitter()
                var lastMonologueSnapshot = ""
                val resp = llmClient.chatStreaming(
                    messages = currentLlmMessages,
                    tools = tools,
                    onThinkingDelta = { reasoningDelta, contentDelta ->
                        var monologueDelta = ""
                        if (contentDelta.isNotEmpty()) {
                            monologueSplitter.onChunk(contentDelta)
                            val snapshot = monologueSplitter.monologueSoFar().orEmpty()
                            if (snapshot.length > lastMonologueSnapshot.length &&
                                snapshot.startsWith(lastMonologueSnapshot)
                            ) {
                                monologueDelta = snapshot.substring(lastMonologueSnapshot.length)
                            }
                            lastMonologueSnapshot = snapshot
                        }
                        if (reasoningDelta.isNotEmpty() || monologueDelta.isNotEmpty()) {
                            emitEvent(
                                onEvent,
                                JSONObject().apply {
                                    put("type", "llm_thinking")
                                    put("reasoning", reasoningDelta)
                                    put("content", monologueDelta)
                                }
                            )
                        }
                    }
                )
                report.llmCalls.add(
                    LlmCallRecord(
                        step = report.llmCalls.size + 1,
                        model = resp.model,
                        elapsedMs = resp.elapsedMs,
                        finishReason = resp.finishReason,
                        wantsTools = resp.toolCalls.map { it.name },
                        contentPreview = (resp.content ?: "").take(120)
                    )
                )

                if (resp.toolCalls.isNotEmpty()) {
                    // 内心独白透出（2026-10-11 真机反馈「独白少内容」；2026-10-12「全是英文」）：
                    // 优先取 <thinking> 信封（提示词要求中文），reasoning_content 仅含中文时兑底，
                    // 否则退回过渡语正文；走事件给协调器累积进思考抽屉。
                    // 终止轮不 emit（正文走 preview 通道，避免重复）。
                    val rawReasoning = resp.rawMessage?.optString("reasoning_content") ?: ""
                    // 2026-10-15：先过卫生层——QQ 风格灰色内部块归一成信封、元信息注解丢弃
                    val sanitized = MonologueSanitizer.normalizeForEnvelope(resp.content.orEmpty())
                    val split = if (sanitized.isNotBlank()) MonologueEnvelope.splitComplete(sanitized) else null
                    // 2026-10-13：信封本身也可能是英文（模型不守提示词语言），过渡语兜底同理，
                    // 三条候选统一过 isMostlyChinese 比例闸，全不过就这轮不上内心话。
                    val envelope = split?.monologue.orEmpty()
                    val content = sanitized
                    val inner = when {
                        envelope.isNotBlank() && isMostlyChinese(envelope) -> envelope
                        rawReasoning.isNotBlank() && isMostlyChinese(rawReasoning) -> rawReasoning
                        content.isNotBlank() && isMostlyChinese(content) -> content
                        else -> ""
                    }
                    emitEvent(
                        onEvent,
                        JSONObject().apply {
                            put("type", "llm_round")
                            put("step", report.llmCalls.size)
                            put("reasoning", rawReasoning)
                            put("content", inner)
                        }
                    )
                    messages.add(
                        LlmMessage(
                            role = "assistant",
                            content = resp.content,
                            toolCalls = resp.toolCalls,
                            timestamp = nowIso(),
                            round = roundNo
                        )
                    )

                    for (tc in resp.toolCalls) {
                        val parsedArgs = repairToolArguments(tc.arguments)
                        val (result, trace) = registry.execute(tc.name, parsedArgs, toolContext)
                        val seq = report.toolCalls.size + 1

                        emitEvent(
                            onEvent,
                            JSONObject().apply {
                                put("type", "tool")
                                put("name", tc.name)
                                put("success", result.success)
                                put("seq", seq)
                            }
                        )

                        for (entry in trace) {
                            report.toolCalls.add(entry)
                        }

                        if (!result.success) {
                            val failedAttempts = trace.filter { !it.success }
                            report.toolFailures += failedAttempts.size
                            report.errors.add("[round $roundNo] [${tc.name}] ${result.error}")
                        }

                        val resultJson = serializeToolResult(result)
                        messages.add(
                            LlmMessage(
                                role = "tool",
                                toolCallId = tc.callId,
                                name = tc.name,
                                content = resultJson,
                                timestamp = nowIso(),
                                round = roundNo
                            )
                        )
                    }
                    continue
                }

                if (!resp.content.isNullOrBlank()) {
                    // 终答前最后看一眼有没有连发的新消息（智能插队）：有则本轮
                    // 过渡语保留为 assistant 上下文、新消息注入后继续思考；无才
                    // 真正收束本轮回复。
                    val lateTexts = pendingUserTexts?.invoke().orEmpty()
                    if (lateTexts.any { it.isNotBlank() }) {
                        val split = MonologueEnvelope.splitComplete(MonologueSanitizer.normalizeForEnvelope(resp.content))
                        val rawReasoning = resp.rawMessage?.optString("reasoning_content") ?: ""
                        // 同中间轮（2026-10-13）：三条候选统一过 isMostlyChinese 比例闸。
                        val envelope = split.monologue.orEmpty()
                        val content = MonologueSanitizer.normalizeForEnvelope(resp.content)
                        val inner = when {
                            envelope.isNotBlank() && isMostlyChinese(envelope) -> envelope
                            rawReasoning.isNotBlank() && isMostlyChinese(rawReasoning) -> rawReasoning
                            content.isNotBlank() && isMostlyChinese(content) -> content
                            else -> ""
                        }
                        emitEvent(
                            onEvent,
                            JSONObject().apply {
                                put("type", "llm_round")
                                put("step", report.llmCalls.size)
                                put("reasoning", rawReasoning)
                                put("content", inner)
                            }
                        )
                        messages.add(
                            LlmMessage(
                                role = "assistant",
                                content = resp.content,
                                timestamp = nowIso(),
                                round = roundNo
                            )
                        )
                        for (text in lateTexts) {
                            if (text.isNotBlank()) {
                                messages.add(
                                    LlmMessage(
                                        role = "user",
                                        content = text,
                                        timestamp = nowIso(),
                                        round = roundNo
                                    )
                                )
                            }
                        }
                        continue
                    }
                    val sanitized = MonologueSanitizer.normalizeForEnvelope(resp.content.orEmpty())
                    val split = MonologueEnvelope.splitComplete(sanitized)
                    val rawReasoning = resp.rawMessage?.optString("reasoning_content")?.trim().orEmpty()
                    // 同中间轮（2026-10-13）：信封/经 reasoning 统一过 isMostlyChinese 比例闸。
                    // 终答轮不兜底到 content——无信封时整段 content 就是正文（splitComplete 语义），
                    // 再当独白会与 finalReply 重复。
                    val envelope = split.monologue.orEmpty()
                    val monologueText = when {
                        envelope.isNotBlank() && isMostlyChinese(envelope) -> envelope
                        rawReasoning.isNotBlank() && isMostlyChinese(rawReasoning) -> rawReasoning
                        else -> null
                    }
                    report.finalMonologue = monologueText

                    if (!monologueText.isNullOrBlank()) {
                        emitEvent(
                            onEvent,
                            JSONObject().apply {
                                put("type", "llm_round")
                                put("step", report.llmCalls.size)
                                put("reasoning", rawReasoning)
                                put("content", monologueText)
                            }
                        )
                    }

                    val cleanBody = if (split.body.isNotBlank()) {
                        split.body
                    } else if (!monologueText.isNullOrBlank()) {
                        "老师，我还在思考你刚才说的话，能再解释一下吗？"
                    } else {
                        sanitized
                    }

                    // 2026-10-15：正文兜底剥标签，任何模型怪癖都不裸奔到气泡
                    val finalReply = postProcess(MonologueSanitizer.stripTags(cleanBody), report)
                    report.finalReply = finalReply
                    messages.add(
                        LlmMessage(
                            role = "assistant",
                            content = finalReply,
                            timestamp = nowIso(),
                            round = roundNo
                        )
                    )
                    completedWithContent = true
                    break
                }
            }

            if (!completedWithContent) {
                val fallback = "老师，我有点跟不上，能再说一遍吗？"
                report.finalReply = fallback
                messages.add(
                    LlmMessage(
                        role = "assistant",
                        content = fallback,
                        timestamp = nowIso(),
                        round = roundNo
                    )
                )
            }
        } catch (e: Exception) {
            val errStr = e.message ?: e.toString()
            report.errors.add(errStr)
            val degradation = if (roundNo > 1) {
                "老师……我这边好像有点卡，等一下再问我一遍好不好？"
            } else {
                "老师你好，我准备好学习了——刚才系统稍微卡了一下，我们直接开始吧！"
            }
            report.finalReply = degradation
            report.degradations.add(DegradationRecord("llm_failure", errStr.take(200)))
            messages.add(
                LlmMessage(
                    role = "assistant",
                    content = degradation,
                    timestamp = nowIso(),
                    round = roundNo
                )
            )
        }

        report.toolCallCount = report.toolCalls.size
        val updatedSession = updateState(
            session = session.copy(currentRound = roundNo),
            report = report,
            sink = sink,
            roundNo = roundNo
        )

        return TurnResult(
            updatedSession = updatedSession,
            updatedMessages = messages,
            report = report
        )
    }

    private fun postProcess(content: String, report: TurnReport): String {
        val violations = MathGuard.scanMath(content)
        if (violations.isNotEmpty()) {
            report.mathViolations.addAll(violations.map { it.pattern })
        }
        for (marker in PromptAssembler.STUDENT_IDENTITY_MARKERS) {
            if (content.contains(marker)) {
                report.degradations.add(DegradationRecord("identity_slip", marker))
                break
            }
        }
        return content
    }

    private fun updateState(
        session: RTSession,
        report: TurnReport,
        sink: SessionStateSink,
        roundNo: Int
    ): RTSession {
        val checks = report.toolCalls.filter { it.tool == "check_understanding" && it.success }
        var newLevel = session.understandingLevel
        val mastered = session.masteredConcepts.toMutableList()
        val confused = session.confusedConcepts.toMutableList()

        if (checks.isNotEmpty()) {
            val lastOutput = checks.last().output
            val dataObj = lastOutput.data as? JSONObject
            val level = dataObj?.optInt("overall_level", -1) ?: -1
            if (level in 1..5) {
                newLevel = level
            }
            val concept = dataObj?.optString("concept", "") ?: ""
            if (concept.isNotEmpty() && level in 1..5) {
                if (level >= 4 && !mastered.contains(concept)) {
                    mastered.add(concept)
                    confused.remove(concept)
                } else if (level <= 2 && !confused.contains(concept)) {
                    confused.add(concept)
                }
            }
        }

        var newEmotion = session.emotion
        for (entry in report.toolCalls) {
            if (entry.tool == "determine_intervention_urgency" && entry.success) {
                val dataObj = entry.output.data as? JSONObject
                val u = dataObj?.optInt("urgency_level", 1) ?: 1
                if (u >= 4) {
                    newEmotion = "frustrated"
                }
            }
            if (entry.tool == "simulate_breakthrough" && entry.success) {
                newEmotion = "excited"
            }
            if (entry.tool == "log_struggle_point" && entry.success) {
                if (newEmotion == "neutral") {
                    newEmotion = "confused"
                }
            }
        }

        // 汇聚 SessionStateSink 事件与图谱更新
        val newBreakthroughs = session.breakthroughEvents.toMutableList()
        for (ev in sink.events) {
            if (ev.optString("event_type") == "breakthrough") {
                newBreakthroughs.add(
                    BreakthroughEvent(
                        round = roundNo,
                        concept = ev.optString("concept")
                    )
                )
            }
        }

        val newStruggles = session.struggleEvents.toMutableList()
        for (ev in sink.events) {
            if (ev.optString("event_type") == "struggle") {
                newStruggles.add(
                    StruggleEvent(
                        round = roundNo,
                        concept = ev.optString("topic")
                    )
                )
            }
        }

        val kgNodes = session.knowledgeGraphNodes.toMutableList()
        if (sink.latestGraphNodes.isNotEmpty()) {
            val map = kgNodes.associateBy { it.id }.toMutableMap()
            for (node in sink.latestGraphNodes) {
                map[node.id] = node
            }
            kgNodes.clear()
            kgNodes.addAll(map.values)
        }

        for (m in sink.masteredUpdates) {
            if (!mastered.contains(m)) mastered.add(m)
            confused.remove(m)
        }
        for (c in sink.confusedUpdates) {
            if (!confused.contains(c) && !mastered.contains(c)) confused.add(c)
        }

        val newTraces = session.toolTrace.toMutableList()
        for (t in report.toolCalls) {
            newTraces.add(ToolTraceEntry(tool = t.tool, round = roundNo, callId = t.callId))
        }

        val updated = session.copy(
            understandingLevel = newLevel,
            emotion = newEmotion,
            masteredConcepts = mastered,
            confusedConcepts = confused,
            breakthroughEvents = newBreakthroughs,
            struggleEvents = newStruggles,
            knowledgeGraphNodes = kgNodes,
            toolTrace = newTraces
        )

        // 对齐 spike session_service：每轮结束后写会话快照，供
        // generate_progress_report 跨会话聚合。存档失败不得影响回合结果。
        saveSnapshotQuietly(updated)

        return updated
    }

    /**
     * 会话首次落快照的时间（对齐 spike `session["created_at"]`）。
     * [RTSession] 无 create 时间字段，故在此按 sessionId 记忆；快照按
     * sessionId 覆盖写，首次写入的时间即会话起始时间。
     */
    private val sessionStartedAt = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 写会话快照到长期记忆（对齐 spike `STORE.save_snapshot`）。
     * 任何异常都被吞掉：报告是附加能力，不能拖垮对话回合。
     */
    private fun saveSnapshotQuietly(session: RTSession) {
        val store = memoryStore ?: return
        try {
            val sid = session.sessionId
            if (sid.isEmpty()) return
            val startedAt = sessionStartedAt.getOrPut(sid) { nowIso() }
            val struggles = JSONArray()
            for (e in session.struggleEvents) {
                struggles.put(
                    JSONObject().put("concept", e.concept).put("round", e.round)
                )
            }
            store.saveSnapshot(
                JSONObject()
                    .put("session_id", sid)
                    .put("student_id", session.studentId)
                    .put("topic", session.topic)
                    .put("status", session.status)
                    .put("grade", session.grade)
                    .put("created_at", startedAt)
                    .put("understanding_level", session.understandingLevel)
                    .put("initial_understanding_level", session.initialUnderstandingLevel)
                    .put("mastered_concepts", JSONArray(session.masteredConcepts))
                    .put("confused_concepts", JSONArray(session.confusedConcepts))
                    .put("struggle_events", struggles)
                    .put("messages", JSONArray())
            )
        } catch (_: Exception) {
            // 快照失败静默：不影响回合与 UI
        }
    }

    private fun serializeToolResult(result: ToolResult): String {
        val root = JSONObject()
        root.put("success", result.success)
        root.put("data", result.data ?: JSONObject.NULL)
        root.put("error", result.error ?: JSONObject.NULL)
        val meta = JSONObject().apply {
            put("tool_name", result.metadata.toolName)
            put("execution_time_ms", result.metadata.executionTimeMs)
            put("version", result.metadata.version)
        }
        root.put("metadata", meta)
        return root.toString()
    }

    private fun emitEvent(onEvent: ((JSONObject) -> Unit)?, event: JSONObject) {
        if (onEvent == null) return
        try {
            onEvent(event)
        } catch (_: Exception) {
            // 事件回调不中断主循环
        }
    }
}

// ------------------------------------------------------------------
// HTTP 传输层适配器（结合 :core:llm 的 ProviderHttpTransport）
// ------------------------------------------------------------------

class HttpToolLoopLlmClient(
    private val transport: ProviderHttpTransport,
    private val baseUrl: String,
    private val apiKey: String,
    private val defaultModel: String
) : ToolLoopLlmClient {

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?
    ): LlmResponse {
        val normalizedUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "${baseUrl.removeSuffix("/")}/chat/completions"
        }

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )

        val body = JSONObject()
        body.put("model", model ?: defaultModel)

        val messagesArr = JSONArray()
        for (m in messages) {
            val mObj = JSONObject()
            mObj.put("role", m.role)
            if (m.content != null) {
                mObj.put("content", m.content)
            }
            if (!m.toolCalls.isNullOrEmpty()) {
                val tcArr = JSONArray()
                for (tc in m.toolCalls) {
                    val tcObj = JSONObject()
                    tcObj.put("id", tc.callId)
                    tcObj.put("type", "function")
                    val fnObj = JSONObject()
                    fnObj.put("name", tc.name)
                    fnObj.put("arguments", tc.arguments)
                    tcObj.put("function", fnObj)
                    tcArr.put(tcObj)
                }
                mObj.put("tool_calls", tcArr)
            }
            if (m.toolCallId != null) {
                mObj.put("tool_call_id", m.toolCallId)
            }
            if (m.name != null) {
                mObj.put("name", m.name)
            }
            messagesArr.put(mObj)
        }
        body.put("messages", messagesArr)

        if (tools.isNotEmpty()) {
            val toolsArr = JSONArray()
            for (t in tools) toolsArr.put(t)
            body.put("tools", toolsArr)
            body.put("tool_choice", "auto")
        }
        body.put("temperature", 0.7)

        val request = ProviderHttpRequest(
            url = normalizedUrl,
            headers = headers,
            jsonBody = body.toString(),
            timeoutMillis = 60000,
            streaming = false
        )

        val t0 = System.currentTimeMillis()
        val result = transport.execute(request)
        val elapsed = System.currentTimeMillis() - t0

        return when (result) {
            is ProviderHttpResult.Response -> {
                if (result.statusCode !in 200..299) {
                    throw IOException("HTTP ${result.statusCode}: ${result.body}")
                }
                parseResponse(result.body, elapsed, model ?: defaultModel)
            }
            is ProviderHttpResult.Timeout -> {
                throw TimeoutException("LLM request timed out")
            }
            is ProviderHttpResult.Failure -> {
                throw IOException("LLM network transport failure")
            }
        }
    }

    private fun parseResponse(body: String, elapsedMs: Long, fallbackModel: String): LlmResponse {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices")
        val choice = choices?.optJSONObject(0)
        val msgObj = choice?.optJSONObject("message")
        val finishReason = choice?.optString("finish_reason", "stop") ?: "stop"
        val content = msgObj?.optString("content", null)

        val toolCalls = mutableListOf<ToolCallRequest>()
        val tcArr = msgObj?.optJSONArray("tool_calls")
        if (tcArr != null) {
            for (i in 0 until tcArr.length()) {
                val tc = tcArr.getJSONObject(i)
                val callId = tc.optString("id", "call_$i")
                val fn = tc.optJSONObject("function")
                val name = fn?.optString("name", "") ?: ""
                val arguments = fn?.optString("arguments", "{}") ?: "{}"
                toolCalls.add(ToolCallRequest(callId, name, arguments))
            }
        }

        val usageObj = root.optJSONObject("usage")
        val usage = if (usageObj != null) {
            LlmUsage(
                promptTokens = usageObj.optInt("prompt_tokens", 0),
                completionTokens = usageObj.optInt("completion_tokens", 0),
                totalTokens = usageObj.optInt("total_tokens", 0)
            )
        } else null

        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
            model = root.optString("model", fallbackModel),
            elapsedMs = elapsedMs,
            usage = usage,
            rawMessage = msgObj
        )
    }
}

// ------------------------------------------------------------------
// 测试用 Mock 客户端（支持脚本化应答与 spike 对齐模拟）
// ------------------------------------------------------------------

class MockToolLoopLlmClient(
    private val customResponses: MutableList<LlmResponse> = mutableListOf()
) : ToolLoopLlmClient {

    fun queueResponse(response: LlmResponse) {
        customResponses.add(response)
    }

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?
    ): LlmResponse {
        if (customResponses.isNotEmpty()) {
            return customResponses.removeAt(0)
        }

        // 默认模拟对齐 spike MockProvider
        val assistantCount = messages.count { it.role == "assistant" && (!it.content.isNullOrEmpty() || !it.toolCalls.isNullOrEmpty()) }
        return when (assistantCount) {
            0 -> LlmResponse(
                toolCalls = listOf(
                    ToolCallRequest(
                        callId = "m0",
                        name = "check_understanding",
                        arguments = JSONObject().apply {
                            put("concept", "二次函数顶点公式")
                            put("student_explanation", "上下文初始化")
                        }.toString()
                    ),
                    ToolCallRequest(
                        callId = "m1",
                        name = "speak_as_confused_student",
                        arguments = JSONObject().apply {
                            put("confusion_point", "为什么顶点公式里是 -b/2a")
                            put("intensity", "moderate")
                        }.toString()
                    )
                ),
                model = "mock-student-v1"
            )
            1 -> LlmResponse(
                toolCalls = listOf(
                    ToolCallRequest(
                        callId = "m2",
                        name = "challenge_teacher_explanation",
                        arguments = JSONObject().apply {
                            put("teacher_statement", "老师的解释")
                            put("challenge_angle", "boundary_case")
                        }.toString()
                    )
                ),
                model = "mock-student-v1"
            )
            else -> LlmResponse(
                content = "老师，我好像有点明白了！那如果 b 是负数呢？",
                model = "mock-student-v1"
            )
        }
    }
}
