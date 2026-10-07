package com.reversetutor.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Expression-loop slice 5: token-usage parsing for the latency / cost
 * baseline. parseLlmTokenUsage must understand the three usage dialects
 * seen across candidate providers (OpenAI, DeepSeek, Anthropic style)
 * and stay null-safe on payloads without usage.
 */
class LlmTokenUsageParseTest {
    @Test
    fun parsesOpenAiStyleUsageWithCachedTokens() {
        val json = """
            {"id":"chatcmpl-1","choices":[],"usage":{
                "prompt_tokens":1234,
                "completion_tokens":210,
                "prompt_tokens_details":{"cached_tokens":900}
            }}
        """.trimIndent()

        val usage = parseLlmTokenUsage(json)

        assertEquals(1234L, usage?.promptTokens)
        assertEquals(210L, usage?.completionTokens)
        assertEquals(900L, usage?.cachedPromptTokens)
    }

    @Test
    fun parsesDeepSeekStyleUsageWithCacheHitTokens() {
        val json = """
            {"id":"chatcmpl-2","choices":[],"usage":{
                "prompt_tokens":1234,
                "completion_tokens":210,
                "prompt_cache_hit_tokens":1024,
                "prompt_cache_miss_tokens":210
            }}
        """.trimIndent()

        val usage = parseLlmTokenUsage(json)

        assertEquals(1234L, usage?.promptTokens)
        assertEquals(210L, usage?.completionTokens)
        assertEquals(1024L, usage?.cachedPromptTokens)
    }

    @Test
    fun parsesAnthropicStyleUsageWithCacheRead() {
        val json = """
            {"id":"msg_1","content":[],"usage":{
                "input_tokens":800,
                "output_tokens":150,
                "cache_read_input_tokens":512
            }}
        """.trimIndent()

        val usage = parseLlmTokenUsage(json)

        assertEquals(800L, usage?.promptTokens)
        assertEquals(150L, usage?.completionTokens)
        assertEquals(512L, usage?.cachedPromptTokens)
    }

    @Test
    fun returnsNullWhenPayloadHasNoUsageBlock() {
        val json = """{"id":"chatcmpl-3","choices":[{"delta":{"content":"hi"}}]}"""

        assertNull(parseLlmTokenUsage(json))
    }

    @Test
    fun returnsNullOnMalformedJson() {
        assertNull(parseLlmTokenUsage("{not-json"))
        assertNull(parseLlmTokenUsage(""))
    }

    @Test
    fun usageDefaultsThroughGenerationResultSubtypes() {
        // Failure / Timeout subtypes must not need explicit usage wiring.
        val failure = LlmGenerationResult.Failure("boom", retryable = false)
        val timeout = LlmGenerationResult.Timeout
        assertNull(failure.usage)
        assertNull(timeout.usage)

        val streamed = LlmGenerationResult.Streamed(
            listOf("he", "llo"),
            LlmTokenUsage(promptTokens = 10, completionTokens = 5)
        )
        assertEquals(10L, streamed.usage?.promptTokens)
        assertEquals(5L, streamed.usage?.completionTokens)
        assertNull(streamed.usage?.cachedPromptTokens)
    }
}
