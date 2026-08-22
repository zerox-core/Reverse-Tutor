package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionLlmGenerationRuntimeTest {
    @Test
    fun compositeRoutesAllSupportedProtocols() = runBlocking {
        val openAi = RecordingRuntime("openai")
        val anthropic = RecordingRuntime("anthropic")
        val gemini = RecordingRuntime("gemini")
        val runtime = CompositeLlmGenerationRuntime(openAi, anthropic, gemini)

        assertEquals("openai", runtime.generate(request(LlmProviderKind.OpenAiCompatible)).visibleText)
        assertEquals("anthropic", runtime.generate(request(LlmProviderKind.AnthropicCompatible)).visibleText)
        assertEquals("gemini", runtime.generate(request(LlmProviderKind.Gemini)).visibleText)
        assertEquals("openai", runtime.generate(request(LlmProviderKind.DeepSeek)).visibleText)
        assertEquals(2, openAi.callCount)
        assertEquals(1, anthropic.callCount)
        assertEquals(1, gemini.callCount)
    }

    @Test
    fun openAiRuntimeResolvesSecretAtExecutionAndParsesSse() = runBlocking {
        val transport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(
                statusCode = 200,
                body = """
                    data: {"choices":[{"delta":{"content":"Step "}}]}

                    data: {"choices":[{"delta":{"content":"one"}}]}

                    data: [DONE]
                """.trimIndent()
            )
        )
        var resolvedRef: String? = null
        val runtime = ProductionLlmGenerationRuntime(
            protocol = LlmProviderProtocol.OpenAiCompatible,
            transport = transport,
            secretResolver = LlmSecretResolver { ref ->
                resolvedRef = ref
                "resolved-value"
            }
        )

        val result = runtime.generate(
            request(LlmProviderKind.OpenAiCompatible).copy(secretRef = "secret-ref-1")
        )

        assertEquals("Step one", result.visibleText)
        assertEquals("secret-ref-1", resolvedRef)
        assertEquals("Bearer resolved-value", transport.singleRequest().headers["Authorization"])
        assertEquals("https://provider.example/v1/chat/completions", transport.singleRequest().url)
        assertTrue(transport.singleRequest().streaming)
        assertTrue(transport.singleRequest().jsonBody.contains("\"stream\":true"))
        assertFalse(transport.singleRequest().jsonBody.contains("resolved-value"))
    }

    @Test
    fun openAiRuntimeParsesNonStreamingResponse() = runBlocking {
        val transport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(
                statusCode = 200,
                body = """{"choices":[{"message":{"content":"Complete answer"}}]}"""
            )
        )
        val runtime = productionRuntime(LlmProviderProtocol.OpenAiCompatible, transport)

        val result = runtime.generate(
            request(LlmProviderKind.OpenAiCompatible).copy(streaming = false)
        )

        assertEquals(LlmGenerationResult.Success("Complete answer"), result)
        assertFalse(transport.singleRequest().streaming)
        assertTrue(transport.singleRequest().jsonBody.contains("\"stream\":false"))
    }

    @Test
    fun productionRuntimeIncludesSessionPolicyInProviderPayload() = runBlocking {
        val transport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(200, """{"choices":[{"message":{"content":"OK"}}]}""")
        )
        val runtime = productionRuntime(LlmProviderProtocol.OpenAiCompatible, transport)

        runtime.generate(
            request(LlmProviderKind.OpenAiCompatible).copy(
                sessionPolicy = LlmSessionPolicyContext(
                    actionType = "probe",
                    studentRole = "probing_student",
                    knowledgePoint = "factoring",
                    difficulty = 0.7f,
                    processSummary = "ask for a justification",
                    evaluationCorrectness = 0.4f,
                    userEmotion = "engaged",
                    correctionTiming = "summary_only"
                )
            )
        )

        assertTrue(transport.singleRequest().jsonBody.contains("Teaching policy:"))
        assertTrue(transport.singleRequest().jsonBody.contains("Action: probe"))
        assertTrue(transport.singleRequest().jsonBody.contains("Knowledge point: factoring"))
    }

    @Test
    fun anthropicRuntimeBuildsHeadersAndParsesStreamingAndNonStreamingResponses() = runBlocking {
        val streamingTransport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(
                statusCode = 200,
                body = """
                    event: content_block_delta
                    data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Factor "}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"first."}}
                """.trimIndent()
            )
        )
        val streamingRuntime = productionRuntime(
            LlmProviderProtocol.AnthropicCompatible,
            streamingTransport
        )

        assertEquals(
            "Factor first.",
            streamingRuntime.generate(request(LlmProviderKind.AnthropicCompatible)).visibleText
        )
        assertEquals("resolved-value", streamingTransport.singleRequest().headers["x-api-key"])
        assertEquals("2023-06-01", streamingTransport.singleRequest().headers["anthropic-version"])
        assertEquals("https://provider.example/v1/messages", streamingTransport.singleRequest().url)

        val nonStreamingRuntime = productionRuntime(
            LlmProviderProtocol.AnthropicCompatible,
            FakeProviderHttpTransport(
                ProviderHttpResult.Response(
                    statusCode = 200,
                    body = """{"content":[{"type":"text","text":"Complete answer"}]}"""
                )
            )
        )
        assertEquals(
            "Complete answer",
            nonStreamingRuntime.generate(
                request(LlmProviderKind.AnthropicCompatible).copy(streaming = false)
            ).visibleText
        )
    }

    @Test
    fun geminiRuntimeUsesNativeEndpointAndParsesStreamingAndNonStreamingResponses() = runBlocking {
        val streamingTransport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(
                statusCode = 200,
                body = """
                    data: {"candidates":[{"content":{"parts":[{"text":"Use "}]}}]}

                    data: {"candidates":[{"content":{"parts":[{"text":"FOIL."}]}}]}
                """.trimIndent()
            )
        )
        val streamingRuntime = productionRuntime(LlmProviderProtocol.GeminiNative, streamingTransport)

        assertEquals(
            "Use FOIL.",
            streamingRuntime.generate(request(LlmProviderKind.Gemini)).visibleText
        )
        assertEquals("resolved-value", streamingTransport.singleRequest().headers["x-goog-api-key"])
        assertEquals(
            "https://provider.example/v1/models/model-1:streamGenerateContent?alt=sse",
            streamingTransport.singleRequest().url
        )

        val nonStreamingTransport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(
                statusCode = 200,
                body = """{"candidates":[{"content":{"parts":[{"text":"Complete answer"}]}}]}"""
            )
        )
        val nonStreamingRuntime = productionRuntime(
            LlmProviderProtocol.GeminiNative,
            nonStreamingTransport
        )

        assertEquals(
            "Complete answer",
            nonStreamingRuntime.generate(
                request(LlmProviderKind.Gemini).copy(streaming = false)
            ).visibleText
        )
        assertEquals(
            "https://provider.example/v1/models/model-1:generateContent",
            nonStreamingTransport.singleRequest().url
        )
    }

    @Test
    fun missingSecretFailsBeforeTransportExecution() = runBlocking {
        val transport = FakeProviderHttpTransport(
            ProviderHttpResult.Response(200, """{"choices":[]}""")
        )
        val runtime = ProductionLlmGenerationRuntime(
            protocol = LlmProviderProtocol.OpenAiCompatible,
            transport = transport,
            secretResolver = LlmSecretResolver { null }
        )

        val result = runtime.generate(
            request(LlmProviderKind.OpenAiCompatible).copy(secretRef = "missing-ref")
        )

        assertEquals(
            LlmGenerationResult.Failure(
                message = "Provider credential is unavailable.",
                retryable = false
            ),
            result
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun providerErrorsTimeoutsAndMalformedBodiesMapToSafeResults() = runBlocking {
        val cases = listOf(
            ProviderHttpResult.Response(401, """{"error":"credential resolved-value rejected"}""") to
                LlmGenerationResult.Failure("Provider rejected the credential.", retryable = false),
            ProviderHttpResult.Response(403, """{"error":"private response"}""") to
                LlmGenerationResult.Failure("Provider denied access.", retryable = false),
            ProviderHttpResult.Response(404, """{"error":"private response"}""") to
                LlmGenerationResult.Failure("Provider endpoint or model was not found.", retryable = false),
            ProviderHttpResult.Response(429, """{"error":"private response"}""") to
                LlmGenerationResult.Failure("Provider rate limit reached.", retryable = true),
            ProviderHttpResult.Response(503, """{"error":"private response"}""") to
                LlmGenerationResult.Failure("Provider is temporarily unavailable.", retryable = true),
            ProviderHttpResult.Timeout to LlmGenerationResult.Timeout,
            ProviderHttpResult.Failure to
                LlmGenerationResult.Failure("Provider request failed.", retryable = true),
            ProviderHttpResult.Response(200, """{"unexpected":"private response"}""") to
                LlmGenerationResult.Failure("Provider returned an invalid response.", retryable = true)
        )

        cases.forEach { (transportResult, expected) ->
            val runtime = productionRuntime(
                LlmProviderProtocol.OpenAiCompatible,
                FakeProviderHttpTransport(transportResult)
            )
            val actual = runtime.generate(request(LlmProviderKind.OpenAiCompatible))

            assertEquals(expected, actual)
            if (actual is LlmGenerationResult.Failure) {
                assertFalse(actual.message.contains("resolved-value"))
                assertFalse(actual.message.contains("private response"))
                assertFalse(actual.message.contains("provider.example"))
            }
        }
    }

    private fun productionRuntime(
        protocol: LlmProviderProtocol,
        transport: ProviderHttpTransport
    ): ProductionLlmGenerationRuntime =
        ProductionLlmGenerationRuntime(
            protocol = protocol,
            transport = transport,
            secretResolver = LlmSecretResolver { "resolved-value" }
        )

    private fun request(provider: LlmProviderKind): LlmGenerationRequest =
        LlmGenerationRequest(
            sessionId = "session-1",
            userMessageId = "message-1",
            userText = "Explain factoring",
            profileId = "profile-1",
            provider = provider,
            model = "model-1",
            baseUrl = "https://provider.example/v1",
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("token-1"),
            secretRef = "secret-ref-1"
        )
}

private class FakeProviderHttpTransport(
    private val result: ProviderHttpResult
) : ProviderHttpTransport {
    val requests = mutableListOf<ProviderHttpRequest>()

    override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult {
        requests += request
        return result
    }

    fun singleRequest(): ProviderHttpRequest = requests.single()
}

private class RecordingRuntime(
    private val text: String
) : LlmGenerationRuntime {
    var callCount: Int = 0
        private set

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        callCount += 1
        return LlmGenerationResult.Success(text)
    }
}
