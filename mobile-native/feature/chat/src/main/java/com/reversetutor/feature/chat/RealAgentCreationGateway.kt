package com.reversetutor.feature.chat

import com.reversetutor.core.llm.LlmGenerationPlan
import com.reversetutor.core.llm.LlmGenerationPlanner
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmProfileCapabilityResolver
import com.reversetutor.core.model.LlmProfile

/** 未配置可用模型：协调器按「生成失败」降级（对话与草案保留，可改手动填写）。 */
class AgentCreationNoModelException : IllegalStateException("no llm profile configured")

/**
 * 生成或契约解析失败（协调器会原样重试 1 次）。
 * 消息只进降级文案判断，不进 UI。
 */
class AgentCreationGenerationException(message: String) : IllegalStateException(message)

/**
 * R-B 生产网关：P2' 契约提示词 → core/llm transport（复用 LlmProfile）→ 契约解析。
 *
 * 设计约束（设计方案 v3 · 第十章）：
 * - 不经教学轮 envelope / sessionPolicy——纯 prompt→文本生成；
 * - 非流式（streaming=false），一轮一答，解析失败由协调器重试；
 * - 确定性推进逻辑（追问策略、了解度融合、收敛）全在协调器，
 *   本类只负责把策略快照译成提示词、把返回文本译回契约对象。
 */
class RealAgentCreationGateway(
    private val runtime: LlmGenerationRuntime,
    private val activeProfile: suspend () -> LlmProfile?
) : AgentCreationGateway {

    private var turnSequence = 0

    override suspend fun converse(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?,
        strategy: AgentCreationTurnStrategy
    ): AgentCreationTurnResult {
        val profile = activeProfile()
            ?.takeIf { it.enabled && it.model.isNotBlank() }
            ?: throw AgentCreationNoModelException()
        // 协调器在调用前已把本轮用户输入追加进 history，提示词里历史与本轮输入分开装。
        val trimmed = userText.trim()
        val priorHistory = if (history.lastOrNull()?.isUser == true && history.last().text == trimmed) {
            history.dropLast(1)
        } else {
            history
        }
        val prompt = AgentCreationPrompts.buildTurnPrompt(
            history = priorHistory,
            userText = trimmed,
            currentDraft = currentDraft,
            docAnalysis = docAnalysis,
            strategy = strategy
        )
        val turn = turnSequence++
        val plan = LlmGenerationPlanner.plan(
            sessionId = SESSION_ID,
            userMessageId = "agent-creation-user-$turn",
            userText = prompt,
            profile = profile,
            capabilities = LlmProfileCapabilityResolver.infer(profile),
            token = LlmGenerationToken("agent-creation-turn-$turn")
        )
        val request = (plan as? LlmGenerationPlan.Ready)?.request?.copy(streaming = false)
            ?: throw AgentCreationNoModelException()
        return when (val result = runtime.generate(request)) {
            is LlmGenerationResult.Success ->
                AgentCreationParser.parseTurnResult(result.text)
                    ?: throw AgentCreationGenerationException("contract parse failed")
            is LlmGenerationResult.Streamed ->
                AgentCreationParser.parseTurnResult(result.visibleText)
                    ?: throw AgentCreationGenerationException("contract parse failed")
            is LlmGenerationResult.Failure ->
                throw AgentCreationGenerationException(result.message)
            LlmGenerationResult.Timeout ->
                throw AgentCreationGenerationException("timeout")
        }
    }

    /**
     * P1 文档分析：R-C 接真实解析文本；
     * R-B 沿用脚本化结构，保持文件卡链路可走通。
     */
    override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
        scriptedAgentCreationAnalysis(fileName)

    private companion object {
        const val SESSION_ID = "agent-creation"
    }
}
