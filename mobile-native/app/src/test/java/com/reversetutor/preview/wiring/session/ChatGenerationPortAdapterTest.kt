package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.domain.GenerationOutcome
import com.reversetutor.core.domain.GenerationRequest
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SessionPolicyOutput
import com.reversetutor.core.domain.SessionTurnContracts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM tests for [ChatGenerationPortAdapter]. Covers NATIVE-P2-007 scenarios:
 * 1. No-model configuration sends no network request (NoModelConfigured mapped
 *    faithfully, no assistant text fabricated).
 * 2. Fake Runtime success path (Generated mapped with assistant text + context
 *    evidence).
 * 4. Provider error always maps to a safe code — no key, Authorization, or URL
 *    leak.
 */
class ChatGenerationPortAdapterTest {

    private fun request(
        contextEvidence: List<String> = listOf("gap A", "review B"),
        token: String = "turn-token-1"
    ): GenerationRequest = GenerationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        turnId = "turn-1",
        userMessageId = "user-1",
        userText = "What is photosynthesis?",
        token = token,
        contextEvidence = contextEvidence,
        policy = SessionPolicyOutput(
            evaluation = SessionEvaluationContract(),
            action = SessionActionContract(),
            processSummary = "test"
        )
    )

    // -- Scenario 1: 无模型配置不发网络请求 ----------------------------------

    @Test
    fun `no model configured maps to NoModelConfigured without fabricating assistant text`() = runTest {
        var readAssistantCalled = false
        val adapter = ChatGenerationPortAdapter(
            generate = { _, _, _, _ -> ChatGenerationOutcome.NoModelConfigured },
            readAssistantText = { _, _ -> readAssistantCalled = true; "" }
        )
        val outcome = adapter.generateReply(request(), 1000L) { true }
        assertTrue(outcome is GenerationOutcome.NoModelConfigured)
        assertFalse(
            "readAssistantText must not be called when no model is configured",
            readAssistantCalled
        )

        // The wired input carries no model binding → the frozen layer short-circuits before network.
        val input = adapter.buildInput(request())
        assertNull("buildInput must not fabricate a model binding id", input.modelBindingId)
    }

    @Test
    fun `stale preflight skips frozen generation and returns Stale`() = runTest {
        var generationCalled = false
        val adapter = ChatGenerationPortAdapter(
            generate = { _, _, _, _ ->
                generationCalled = true
                ChatGenerationOutcome.Generated(assistantMessageId = "must-not-exist")
            }
        )

        val outcome = adapter.generateReply(request(), 1000L) { false }

        assertEquals(GenerationOutcome.Stale, outcome)
        assertFalse("an already-stale turn must not start frozen generation", generationCalled)
    }

    // -- Scenario 2: Fake Runtime 成功路径 ------------------------------------

    @Test
    fun `fake runtime success path maps Generated with assistant text and context evidence`() = runTest {
        val adapter = ChatGenerationPortAdapter(
            generate = { _, _, _, _ -> ChatGenerationOutcome.Generated(assistantMessageId = "asst-1") },
            readAssistantText = { sessionId, assistantMessageId ->
                assertEquals("session-1", sessionId)
                assertEquals("asst-1", assistantMessageId)
                "Hello world"
            }
        )
        val outcome = adapter.generateReply(request(), 1000L) { true }
        assertTrue(outcome is GenerationOutcome.Generated)
        val generated = outcome as GenerationOutcome.Generated
        assertEquals("asst-1", generated.assistantMessageId)
        assertEquals("Hello world", generated.assistantText)

        // Context-evidence strings are mapped to structured LlmContextEvidence with the real text as body.
        val input = adapter.buildInput(request(contextEvidence = listOf("gap A", "review B")))
        assertEquals(2, input.contextEvidence.size)
        assertEquals("gap A", input.contextEvidence[0].body)
        assertEquals("review B", input.contextEvidence[1].body)
        // No policy field leaks into the frozen input.
        assertNull(input.modelBindingId)
    }

    // -- Scenario 4: Provider 错误始终映射 generation_failed，不泄露 -----------

    @Test
    fun `provider failure maps to safe code without leaking key authorization or url`() = runTest {
        val sensitive =
            "Authorization: Bearer sk-live-abc123 at https://api.openai.com/v1/chat"
        val adapter = ChatGenerationPortAdapter(
            generate = { _, _, _, _ -> ChatGenerationOutcome.ProviderFailed(message = sensitive) }
        )
        val outcome = adapter.generateReply(request(), 1000L) { true }
        assertTrue(outcome is GenerationOutcome.ProviderFailed)
        val failed = outcome as GenerationOutcome.ProviderFailed
        val safeError = failed.safeError

        // The safe error must be the sanitized constant, never the raw provider message.
        assertEquals(SessionTurnContracts.safeGenerationFailureCode(sensitive), safeError)
        assertNotEquals(sensitive, safeError)
        // No secret material reaches the domain/UI.
        assertFalse("safeError must not leak the API key", safeError.contains("sk-live-abc123"))
        assertFalse("safeError must not leak the Authorization scheme", safeError.contains("Bearer"))
        assertFalse("safeError must not leak the header name", safeError.contains("Authorization"))
        assertFalse("safeError must not leak the provider URL host", safeError.contains("openai.com"))
        assertFalse("safeError must not leak the provider URL scheme", safeError.contains("https://"))
    }
}
