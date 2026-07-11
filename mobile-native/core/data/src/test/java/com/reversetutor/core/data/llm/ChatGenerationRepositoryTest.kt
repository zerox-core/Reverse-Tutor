package com.reversetutor.core.data.llm

import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.model.ExecutionModelConfiguration
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.llm.FakeLlmGenerationRuntime
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGenerationRepositoryTest {
    @Test
    fun requestedBindingBuildsExecutionProfileBeforeLegacyFallback() = runBlocking {
        val runtime = RecordingGenerationRuntime()
        val resolver = FixedExecutionModelResolver(
            ExecutionModelConfiguration(
                connection = ProviderConnection(
                    id = "connection-1",
                    spaceId = "space-1",
                    name = "Anthropic",
                    protocol = ModelProtocol.AnthropicCompatible,
                    baseUrl = "https://anthropic.example/v1",
                    secretRef = "secret-1"
                ),
                binding = ModelBinding(
                    id = "binding-1",
                    spaceId = "space-1",
                    connectionId = "connection-1",
                    modelId = "claude-test",
                    displayName = "Claude"
                )
            )
        )
        val repository = ChatGenerationRepository(
            messageRepository = MessageRepository(
                FakeMessageDao(),
                FakeMessageAttachmentDao(),
                FakeMessageQuoteDao()
            ),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = runtime,
            modelConnectionRepository = resolver
        )

        val outcome = repository.generateReply(
            input = input("token-binding").copy(modelBindingId = "binding-1"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-binding"), outcome)
        assertEquals("binding-1", resolver.requestedBindingIds.single())
        assertEquals("claude-test", runtime.requests.single().model)
        assertEquals("https://anthropic.example/v1", runtime.requests.single().baseUrl)
        assertEquals("secret-1", runtime.requests.single().secretRef)
    }

    @Test
    fun legacyProfileFallbackOnlyRunsWhenNoNewConfigurationExists() = runBlocking {
        val fallbackRuntime = RecordingGenerationRuntime()
        val fallbackRepository = ChatGenerationRepository(
            messageRepository = MessageRepository(
                FakeMessageDao(),
                FakeMessageAttachmentDao(),
                FakeMessageQuoteDao()
            ),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = fallbackRuntime,
            modelConnectionRepository = FixedExecutionModelResolver(
                configuration = null,
                hasNewConfiguration = false
            )
        )
        val blockedRuntime = RecordingGenerationRuntime()
        val blockedRepository = ChatGenerationRepository(
            messageRepository = MessageRepository(
                FakeMessageDao(),
                FakeMessageAttachmentDao(),
                FakeMessageQuoteDao()
            ),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = blockedRuntime,
            modelConnectionRepository = FixedExecutionModelResolver(
                configuration = null,
                hasNewConfiguration = true
            )
        )

        val fallback = fallbackRepository.generateReply(
            input = input("token-legacy-fallback"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )
        val blocked = blockedRepository.generateReply(
            input = input("token-new-config-invalid"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-legacy-fallback"), fallback)
        assertEquals("gpt-4o-mini", fallbackRuntime.requests.single().model)
        assertEquals(ChatGenerationOutcome.NoModelConfigured, blocked)
        assertTrue(blockedRuntime.requests.isEmpty())
    }

    @Test
    fun generateReplyPersistsAssistantMessageForCurrentToken() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val profileRepository = LlmProfileRepository(
            ChatGenerationFakeLlmProfileDao.withActiveProfile(),
            ChatGenerationFakeSecretStore()
        )
        val runtime = FakeLlmGenerationRuntime(
            defaultResult = LlmGenerationResult.Success("Mock assistant reply")
        )
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = profileRepository,
            runtime = runtime
        )

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Explain",
                token = LlmGenerationToken("token-current"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { it == LlmGenerationToken("token-current") }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-current"), outcome)
        val messages = messageRepository.listMessages("session-1")
        assertEquals(listOf(MessageRole.Assistant), messages.map { it.role })
        assertEquals("Mock assistant reply", messages.single().text)
        assertEquals(0, runtime.realProviderCallCount)
    }

    @Test
    fun streamedReplyPersistsAggregatedAssistantText() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(
                defaultResult = LlmGenerationResult.Streamed(
                    chunks = listOf("Step 1", ": factor first.")
                )
            )
        )

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Explain",
                token = LlmGenerationToken("token-stream"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-stream"), outcome)
        assertEquals("Step 1: factor first.", messageRepository.listMessages("session-1").single().text)
    }

    @Test
    fun staleGenerationTokenDoesNotPersistAssistantMessage() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Success("Late reply"))
        )

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Explain",
                token = LlmGenerationToken("token-old"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { false }
        )

        assertEquals(ChatGenerationOutcome.Stale, outcome)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun staleTokenAfterRuntimeDoesNotPersistAssistantMessage() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Success("Late reply"))
        )
        var checks = 0

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Explain",
                token = LlmGenerationToken("token-late"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = {
                checks += 1
                checks == 1
            }
        )

        assertEquals(ChatGenerationOutcome.Stale, outcome)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun providerFailuresAndTimeoutsMapToDiagnosticsWithoutPersistence() = runBlocking {
        val failureRepository = ChatGenerationRepository(
            messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao()),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Failure("Rate limited"))
        )
        val timeoutRepository = ChatGenerationRepository(
            messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao()),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Timeout)
        )

        val failure = failureRepository.generateReply(
            input = input("token-failure"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )
        val timeout = timeoutRepository.generateReply(
            input = input("token-timeout"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.ProviderFailed("Rate limited"), failure)
        assertEquals(ChatGenerationOutcome.ProviderFailed("Timeout"), timeout)
    }

    @Test
    fun imageAttachmentUsesInferredVisionCapabilityWhenInputOmitsCapabilities() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Success("I can inspect the image."))
        )

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "",
                token = LlmGenerationToken("token-image"),
                imageAttachments = listOf(imageAttachment())
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-image"), outcome)
        assertEquals("I can inspect the image.", messageRepository.listMessages("session-1").single().text)
    }

    @Test
    fun generatedReplyIncludesVisibleCitationFooterWhenEvidenceExists() = runBlocking {
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Success("Use factoring."))
        )

        val outcome = repository.generateReply(
            input = input("token-context").copy(
                contextEvidence = listOf(contextEvidence())
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.Generated("assistant-token-context"), outcome)
        val text = messageRepository.listMessages("session-1").single().text
        assertTrue(text.contains("Use factoring."))
        assertTrue(text.contains("Sources:"))
        assertTrue(text.contains("[1] Algebra note (message-1, source-1)"))
    }

    @Test
    fun unsupportedVisionAndBlankPromptReturnBlockedOutcomes() = runBlocking {
        val repository = ChatGenerationRepository(
            messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao()),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao.withActiveProfile(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime(defaultResult = LlmGenerationResult.Success("Should not persist"))
        )

        val unsupportedVision = repository.generateReply(
            input = input("token-vision").copy(
                imageAttachments = listOf(imageAttachment()),
                capabilities = LlmCapabilities(supportsVision = false)
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )
        val blank = repository.generateReply(
            input = input("token-blank").copy(userText = "   "),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.UnsupportedVision, unsupportedVision)
        assertEquals(ChatGenerationOutcome.BlankPrompt, blank)
    }

    @Test
    fun noActiveProfileReturnsNoModelOutcome() = runBlocking {
        val repository = ChatGenerationRepository(
            messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao()),
            llmProfileRepository = LlmProfileRepository(
                ChatGenerationFakeLlmProfileDao(),
                ChatGenerationFakeSecretStore()
            ),
            runtime = FakeLlmGenerationRuntime()
        )

        val outcome = repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Explain",
                token = LlmGenerationToken("token-no-model"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertEquals(ChatGenerationOutcome.NoModelConfigured, outcome)
    }

    private fun input(token: String): ChatGenerationInput =
        ChatGenerationInput(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Explain",
            token = LlmGenerationToken(token),
            capabilities = LlmCapabilities()
        )

    private fun imageAttachment(): MessageAttachment =
        MessageAttachment(
            id = "attachment-user-1-0",
            spaceId = "default-space",
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

private class FakeMessageDao : MessageDao {
    private val messages = linkedMapOf<String, MessageEntity>()

    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values
            .filter { it.sessionId == sessionId }
            .sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int =
        if (messages.remove(id) == null) 0 else 1
}

private class FakeMessageAttachmentDao : MessageAttachmentDao {
    private val attachments = linkedMapOf<String, MessageAttachmentEntity>()

    override suspend fun insert(attachment: MessageAttachmentEntity) {
        attachments[attachment.id] = attachment
    }

    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> =
        attachments.values.filter { it.messageId == messageId }.sortedBy { it.name }

    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> =
        attachments.values
            .filter { it.messageId in messageIds }
            .sortedWith(compareBy<MessageAttachmentEntity> { it.messageId }.thenBy { it.name })

    override suspend fun deleteByMessageId(messageId: String): Int {
        val matches = attachments.filterValues { it.messageId == messageId }.keys
        matches.forEach { attachments.remove(it) }
        return matches.size
    }
}

private class FakeMessageQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit

    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null

    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class ChatGenerationFakeLlmProfileDao : LlmProfileDao {
    private val entities = linkedMapOf<String, LlmProfileEntity>()

    override suspend fun upsert(profile: LlmProfileEntity) {
        entities[profile.id] = profile
    }

    override suspend fun getById(id: String): LlmProfileEntity? = entities[id]

    override suspend fun listBySpace(spaceId: String): List<LlmProfileEntity> =
        entities.values.filter { it.spaceId == spaceId }

    override suspend fun listAll(): List<LlmProfileEntity> =
        entities.values.toList()

    override suspend fun setEnabledForSpace(spaceId: String, enabledProfileId: String, updatedAtEpochMillis: Long) = Unit

    override suspend fun deleteById(id: String): Int = if (entities.remove(id) == null) 0 else 1

    companion object {
        fun withActiveProfile(): ChatGenerationFakeLlmProfileDao =
            ChatGenerationFakeLlmProfileDao().also { dao ->
                dao.entities["profile-1"] = LlmProfileEntity(
                    id = "profile-1",
                    spaceId = "default-space",
                    name = "Work model",
                    provider = "OpenAiCompatible",
                    model = "gpt-4o-mini",
                    secretRef = "llm-secret-profile-1",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    baseUrl = "https://api.example.test/v1",
                    enabled = true
                )
            }
    }
}

private class ChatGenerationFakeSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}

private class FixedExecutionModelResolver(
    private val configuration: ExecutionModelConfiguration?,
    private val hasNewConfiguration: Boolean = true
) : ExecutionModelResolver {
    val requestedBindingIds = mutableListOf<String?>()

    override suspend fun resolveForExecution(
        sessionId: String,
        requestedBindingId: String?
    ): ExecutionModelConfiguration? {
        requestedBindingIds += requestedBindingId
        return configuration
    }

    override suspend fun hasNewConfigurationForSession(sessionId: String): Boolean =
        hasNewConfiguration
}

private class RecordingGenerationRuntime : LlmGenerationRuntime {
    val requests = mutableListOf<LlmGenerationRequest>()

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        requests += request
        return LlmGenerationResult.Success("Bound model reply")
    }
}
