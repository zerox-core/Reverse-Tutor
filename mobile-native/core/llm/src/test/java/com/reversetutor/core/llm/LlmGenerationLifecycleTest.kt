package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmGenerationLifecycleTest {
    @Test
    fun plannerBuildsProfileBoundRequestAndRejectsUnsupportedImages() {
        val profile = profile(
            provider = LlmProviderKind.OpenAiCompatible,
            secretRef = "llm-secret-profile-1"
        )

        val ready = LlmGenerationPlanner.plan(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "  Explain factoring  ",
            profile = profile,
            capabilities = LlmCapabilities(supportsVision = true, supportsJsonMode = true),
            token = LlmGenerationToken("token-1"),
            quoteExcerpt = "x^2 - 4",
            imageAttachments = listOf(imageAttachment()),
            contextEvidence = listOf(contextEvidence())
        )

        assertTrue(ready is LlmGenerationPlan.Ready)
        val request = (ready as LlmGenerationPlan.Ready).request
        assertEquals("session-1", request.sessionId)
        assertEquals("user-1", request.userMessageId)
        assertEquals("Explain factoring", request.userText)
        assertEquals("profile-1", request.profileId)
        assertEquals(LlmProviderKind.OpenAiCompatible, request.provider)
        assertEquals("gpt-4o-mini", request.model)
        assertEquals("https://api.example.test/v1", request.baseUrl)
        assertEquals("llm-secret-profile-1", request.secretRef)
        assertEquals(LlmCapabilities(supportsVision = true, supportsJsonMode = true), request.capabilities)
        assertEquals("x^2 - 4", request.quoteExcerpt)
        assertEquals(listOf("question.png"), request.imageAttachments.map { it.name })
        assertEquals(listOf("Algebra note"), request.contextEvidence.map { it.title })
        assertEquals(LlmGenerationToken("token-1"), request.token)

        val blocked = LlmGenerationPlanner.plan(
            sessionId = "session-1",
            userMessageId = "user-2",
            userText = "Describe this image",
            profile = profile,
            capabilities = LlmCapabilities(supportsVision = false),
            token = LlmGenerationToken("token-2"),
            imageAttachments = listOf(imageAttachment())
        )

        assertEquals(
            LlmGenerationPlan.Blocked(LlmGenerationBlockReason.UnsupportedVision),
            blocked
        )
    }

    @Test
    fun plannerReturnsNoModelWhenNoProfileIsActive() {
        val plan = LlmGenerationPlanner.plan(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Help",
            profile = null,
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("token-1")
        )

        assertEquals(
            LlmGenerationPlan.Blocked(LlmGenerationBlockReason.NoModelConfigured),
            plan
        )
    }

    @Test
    fun fakeRuntimeAggregatesStreamingChunksAndNeverCallsRealProviders() {
        val request = LlmGenerationRequest(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Help",
            profileId = "profile-1",
            provider = LlmProviderKind.AnthropicCompatible,
            model = "claude-3-5-haiku-latest",
            baseUrl = "https://api.anthropic.com/v1",
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("token-stream")
        )
        val runtime = FakeLlmGenerationRuntime(
            outcomes = mapOf(
                LlmGenerationToken("token-stream") to LlmGenerationResult.Streamed(
                    chunks = listOf("Step 1", ": factor first.")
                )
            )
        )

        val result = runtime.generate(request)

        assertEquals("Step 1: factor first.", result.visibleText)
        assertEquals(0, runtime.realProviderCallCount)
    }

    @Test
    fun fakeRuntimeReturnsFailureAndTimeoutOutcomesWithoutProviderCalls() {
        val failureToken = LlmGenerationToken("token-failure")
        val timeoutToken = LlmGenerationToken("token-timeout")
        val runtime = FakeLlmGenerationRuntime(
            outcomes = mapOf(
                failureToken to LlmGenerationResult.Failure("Rate limited"),
                timeoutToken to LlmGenerationResult.Timeout
            )
        )

        val failure = runtime.generate(request(LlmProviderKind.OpenAiCompatible).copy(token = failureToken))
        val timeout = runtime.generate(request(LlmProviderKind.OpenAiCompatible).copy(token = timeoutToken))

        assertEquals(LlmGenerationResult.Failure("Rate limited"), failure)
        assertEquals(LlmGenerationResult.Timeout, timeout)
        assertEquals(0, runtime.realProviderCallCount)
    }

    @Test
    fun providerStrategiesBuildProtocolSpecificPayloadsWithoutNetworkCalls() {
        val openAi = OpenAiCompatibleGenerationRuntime()
        val anthropic = AnthropicCompatibleGenerationRuntime()

        assertEquals(
            LlmProviderProtocol.OpenAiCompatible,
            openAi.buildPayload(request(LlmProviderKind.OpenAiCompatible)).protocol
        )
        assertEquals(
            LlmProviderProtocol.AnthropicCompatible,
            anthropic.buildPayload(request(LlmProviderKind.AnthropicCompatible)).protocol
        )
        assertEquals(0, openAi.realProviderCallCount)
        assertEquals(0, anthropic.realProviderCallCount)
    }

    @Test
    fun providerPayloadsCarryQuoteAndImageDraftContextWithoutNetworkCalls() {
        val runtime = OpenAiCompatibleGenerationRuntime()
        val payload = runtime.buildPayload(
            request(LlmProviderKind.OpenAiCompatible).copy(
                quoteExcerpt = "x^2 - 4",
                imageAttachments = listOf(imageAttachment())
            )
        )
        val messages = payload.body["messages"] as List<*>
        val userMessage = messages.single() as Map<*, *>
        val content = userMessage["content"] as List<*>
        val textPart = content.first() as Map<*, *>
        val imagePart = content.last() as Map<*, *>

        assertEquals("Quote: x^2 - 4\n\nHelp", textPart["text"])
        assertEquals("image_url", imagePart["type"])
        assertEquals(0, runtime.realProviderCallCount)
    }

    @Test
    fun providerPayloadsCarryMemoryAndSourceContextWithoutNetworkCalls() {
        val runtime = OpenAiCompatibleGenerationRuntime()
        val payload = runtime.buildPayload(
            request(LlmProviderKind.OpenAiCompatible).copy(
                contextEvidence = listOf(contextEvidence())
            )
        )
        val messages = payload.body["messages"] as List<*>
        val userMessage = messages.single() as Map<*, *>
        val content = userMessage["content"] as String

        assertTrue(content.contains("Context evidence:"))
        assertTrue(content.contains("[1] Note - Algebra note: Remember difference of squares."))
        assertEquals(0, runtime.realProviderCallCount)
    }

    private fun request(provider: LlmProviderKind): LlmGenerationRequest =
        LlmGenerationRequest(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Help",
            profileId = "profile-1",
            provider = provider,
            model = "model",
            baseUrl = "https://api.example.test/v1",
            capabilities = LlmCapabilities(),
            token = LlmGenerationToken("token-$provider")
        )

    private fun profile(
        provider: LlmProviderKind,
        secretRef: String?
    ): LlmProfile = LlmProfile(
        id = "profile-1",
        spaceId = "space-1",
        name = "Work model",
        provider = provider,
        model = "gpt-4o-mini",
        secretRef = secretRef,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        baseUrl = "https://api.example.test/v1",
        enabled = true
    )

    private fun imageAttachment(): MessageAttachment =
        MessageAttachment(
            id = "attachment-user-1-0",
            spaceId = "space-1",
            messageId = "user-1",
            name = "question.png",
            mimeType = "image/png",
            uri = "content://images/question.png",
            sourceId = "source-question"
        )

    private fun contextEvidence(): LlmContextEvidence =
        LlmContextEvidence(
            id = "memory-note-1",
            title = "Algebra note",
            body = "Remember difference of squares.",
            kind = "Note",
            sourceMessageId = "message-1",
            sourceId = "source-1"
        )
}
