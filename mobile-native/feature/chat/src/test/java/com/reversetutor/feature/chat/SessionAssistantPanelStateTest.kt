package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.ErrorReferenceContract
import com.reversetutor.core.domain.MemoryReferenceContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SourceReferenceContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1: Six contract types (READY, NO_MODEL, ERROR/Provider-fail, ERROR/stale-token,
 * deleted-session, empty) all project to safe Chinese labels — never raw error
 * details, source identifiers, URLs, or API keys.
 *
 * B2: Warnings are mapped to fixed Chinese via [toSafeWarningText] —
 * [ContextWarning.source] and [ContextWarning.message] must never appear in
 * the panel state.
 */
class SessionAssistantPanelStateTest {

    // --- B1: 成功 (READY) ---

    @Test
    fun readyContractProjectsAssistantTextEvaluationActionAndContext() {
        val contract = contract(
            sessionId = "session-1",
            turnId = "turn-1",
            state = GenerationState.READY,
            assistantText = "我们来聊聊函数参数。",
            evaluation = SessionEvaluationContract(
                correctness = 0.5f,
                depth = 0.6f,
                entryStatus = "has_entry"
            ),
            action = SessionActionContract(
                type = "ask",
                knowledgePoint = "函数参数"
            ),
            context = ConversationContextContract(
                spaceId = "space-1",
                sessionId = "session-1",
                prerequisiteGaps = listOf("变量作用域"),
                relatedMemory = listOf(
                    MemoryReferenceContract("mem-1", "函数即映射", 0.8f, 1L)
                ),
                sourceEvidence = listOf(
                    SourceReferenceContract("src-1", "Kotlin 文档", "函数定义", "doc", 0.9f)
                ),
                historicalErrors = listOf(
                    ErrorReferenceContract("err-1", "参数缺失", "调用时少传参数", 2L)
                ),
                pendingReviewKnowledgePoints = listOf("返回值"),
                recentMessages = emptyList(),
                warnings = emptyList()
            ),
            events = listOf(
                ConversationUiEvent("retry_turn-1", ConversationUiEventType.RETRY),
                ConversationUiEvent("open_ctx_turn-1", ConversationUiEventType.OPEN_CONTEXT)
            )
        )

        val state = contract.toPanelState()

        assertEquals("session-1", state.sessionId)
        assertEquals("turn-1", state.turnId)
        assertEquals(GenerationState.READY, state.generationState)
        assertNull(state.generationLabel)
        assertEquals("我们来聊聊函数参数。", state.assistantText)
        assertTrue(state.evaluationSummary!!.contains("正确 50%"))
        assertTrue(state.evaluationSummary!!.contains("深度 60%"))
        assertTrue(state.actionLabel!!.contains("提问"))
        assertTrue(state.actionLabel!!.contains("函数参数"))
        assertTrue(state.hasContext)
        assertEquals(listOf("变量作用域"), state.prerequisiteGaps)
        assertEquals(1, state.relatedMemory.size)
        assertEquals(1, state.sourceEvidence.size)
        assertEquals(1, state.historicalErrors.size)
        assertEquals(listOf("返回值"), state.pendingReviewKnowledgePoints)
        assertFalse(state.retryable)
        assertTrue(state.eventInteractions.contains(SessionAssistantInteraction.RETRY))
        assertTrue(state.eventInteractions.contains(SessionAssistantInteraction.OPEN_CONTEXT))
    }

    // --- B1: 无模型 (NO_MODEL) ---

    @Test
    fun noModelContractIsNotRetryable() {
        val contract = contract(state = GenerationState.NO_MODEL, events = emptyList())
        val state = contract.toPanelState()
        assertEquals("未配置模型", state.generationLabel)
        assertFalse(state.retryable)
        assertEquals(listOf(SessionAssistantInteraction.DISMISS), state.eventInteractions)
    }

    // --- B1: Provider 失败 (ERROR) ---

    @Test
    fun errorContractIsRetryable() {
        val contract = contract(
            state = GenerationState.ERROR,
            safeError = "generation_failed",
            events = listOf(ConversationUiEvent("retry_1", ConversationUiEventType.RETRY))
        )
        val state = contract.toPanelState()

        assertEquals(GenerationState.ERROR, state.generationState)
        assertEquals("生成失败", state.generationLabel)
        assertEquals("generation_failed", state.safeError)
        assertTrue(state.retryable)
    }

    // --- B1: 陈旧 token (ERROR with token issue) ---

    @Test
    fun staleTokenContractIsRetryableWithoutLeakingRawDetail() {
        val contract = contract(
            state = GenerationState.ERROR,
            safeError = "token expired",
            events = listOf(ConversationUiEvent("retry_1", ConversationUiEventType.RETRY))
        )
        val state = contract.toPanelState()
        assertTrue(state.retryable)
        assertEquals("token expired", state.safeError)
        // The panel label must be safe Chinese, not the raw error
        assertEquals("生成失败", state.generationLabel)
    }

    // --- B1: 删除会话 (deleted session → ERROR with deleted-session error) ---

    @Test
    fun deletedSessionContractProjectsSafeErrorLabel() {
        val contract = contract(
            sessionId = "session-deleted",
            turnId = null,
            state = GenerationState.ERROR,
            safeError = "session_deleted",
            events = listOf(ConversationUiEvent("retry_del", ConversationUiEventType.RETRY))
        )
        val state = contract.toPanelState()

        assertEquals("session-deleted", state.sessionId)
        assertEquals(GenerationState.ERROR, state.generationState)
        assertEquals("生成失败", state.generationLabel)
        assertTrue(state.retryable)
        // Panel must not show raw "session_deleted" as a label — only as safeError
        assertNotEquals("session_deleted", state.generationLabel)
    }

    // --- B1: 空会话 (empty contract → IDLE) ---

    @Test
    fun emptyContractProjectsIdleState() {
        val contract = SessionConversationContract.empty("session-empty")
        val state = contract.toPanelState()
        assertEquals(GenerationState.IDLE, state.generationState)
        assertNull(state.generationLabel)
        assertNull(state.evaluationSummary)
        assertNull(state.actionLabel)
        assertFalse(state.hasContext)
        assertFalse(state.retryable)
    }

    // --- B1: 暂不支持 (UNSUPPORTED) ---

    @Test
    fun unsupportedContractProjectsSafeLabel() {
        val contract = contract(state = GenerationState.UNSUPPORTED)
        val state = contract.toPanelState()
        assertEquals("暂不支持该输入", state.generationLabel)
        assertFalse(state.retryable)
    }

    // --- B1: 空白输入 (BLANK) ---

    @Test
    fun blankContractProjectsBlankLabel() {
        val contract = contract(state = GenerationState.BLANK)
        val state = contract.toPanelState()
        assertEquals("请输入内容后再发送", state.generationLabel)
    }

    // --- B2: 安全警告映射 — 不泄露 source 和 message ---

    @Test
    fun warningsMapToSafeChineseWithoutLeakingSourceOrMessage() {
        val contract = contract(
            context = ConversationContextContract(
                spaceId = "space-1",
                sessionId = "session-1",
                prerequisiteGaps = emptyList(),
                relatedMemory = emptyList(),
                sourceEvidence = emptyList(),
                historicalErrors = emptyList(),
                pendingReviewKnowledgePoints = emptyList(),
                recentMessages = emptyList(),
                warnings = listOf(
                    ContextWarning(
                        source = "memory",
                        message = "https://api.openai.com/v1/chat completions failed: sk-abc123 401 Unauthorized"
                    ),
                    ContextWarning(
                        source = "graph",
                        message = "NullPointerException at com.reversetutor.core.data.GraphRepository"
                    ),
                    ContextWarning(
                        source = "unknown_provider",
                        message = "Bearer token xyz expired"
                    )
                )
            )
        )
        val state = contract.toPanelState()

        // Warnings must be non-empty
        assertTrue(state.warnings.isNotEmpty())
        // Must contain safe Chinese labels, not raw source/message
        state.warnings.forEach { warning ->
            // No URLs
            assertFalse("Warning leaked URL: $warning", warning.contains("https://"))
            assertFalse("Warning leaked URL: $warning", warning.contains("api.openai.com"))
            // No API keys
            assertFalse("Warning leaked API key: $warning", warning.contains("sk-"))
            assertFalse("Warning leaked bearer: $warning", warning.contains("Bearer"))
            // No raw source identifiers
            assertFalse("Warning leaked source: $warning", warning.contains("unknown_provider"))
            // No stack traces
            assertFalse("Warning leaked stack trace: $warning", warning.contains("NullPointerException"))
            assertFalse("Warning leaked class path: $warning", warning.contains("com.reversetutor"))
            // Must be Chinese (not raw English error text)
            assertFalse("Warning leaked raw error code: $warning", warning.contains("401"))
        }
        // Known sources map to specific safe labels
        assertTrue(state.warnings.contains("记忆检索部分不可用"))
        assertTrue(state.warnings.contains("知识点图谱部分不可用"))
        // Unknown source maps to generic safe message
        assertTrue(state.warnings.contains("部分学习数据暂不可用"))
    }

    @Test
    fun emptyWarningsProduceEmptyList() {
        val contract = contract()
        val state = contract.toPanelState()
        assertTrue(state.warnings.isEmpty())
    }

    // --- B1: 会话切换产生独立状态 ---

    @Test
    fun sessionSwitchProducesDistinctState() {
        val first = contract(
            sessionId = "session-A",
            turnId = "turn-A",
            state = GenerationState.READY,
            assistantText = "first"
        ).toPanelState()
        val second = contract(
            sessionId = "session-B",
            turnId = "turn-B",
            state = GenerationState.READY,
            assistantText = "second"
        ).toPanelState()

        assertEquals("session-A", first.sessionId)
        assertEquals("session-B", second.sessionId)
        assertEquals("turn-A", first.turnId)
        assertEquals("turn-B", second.turnId)
        assertEquals("first", first.assistantText)
        assertEquals("second", second.assistantText)
    }

    // --- Helper ---

    private fun contract(
        sessionId: String = "session-1",
        turnId: String? = "turn-1",
        state: GenerationState = GenerationState.IDLE,
        assistantText: String? = null,
        safeError: String? = null,
        evaluation: SessionEvaluationContract? = null,
        action: SessionActionContract? = null,
        context: ConversationContextContract = ConversationContextContract.empty("space-1", sessionId),
        events: List<ConversationUiEvent> = emptyList()
    ): SessionConversationContract = SessionConversationContract(
        sessionId = sessionId,
        turnId = turnId,
        messages = emptyList(),
        generation = GenerationUiContract(
            state = state,
            assistantMessageId = null,
            assistantText = assistantText,
            safeError = safeError
        ),
        evaluation = evaluation,
        action = action,
        processSummary = null,
        currentKnowledgePoint = action?.knowledgePoint,
        nextStep = action?.let {
            NextStepContract(hint = "hint", knowledgePoint = it.knowledgePoint, actionType = it.type)
        },
        context = context,
        events = events
    )

    private fun <T> assertNotEquals(expected: T, actual: T) {
        assertFalse("Expected '$actual' to not equal '$expected'", expected == actual)
    }
}
