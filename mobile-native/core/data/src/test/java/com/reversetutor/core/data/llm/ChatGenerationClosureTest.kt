package com.reversetutor.core.data.llm

import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route A 最小垂直切片闭包测试。
 *
 * 覆盖简报要求的 7 条断言中在 ChatGenerationRepository 层面的缺口：
 * - Fake Runtime timeout 结果不写入消息（断言 3 补缺）
 * - Session 切换后旧结果不写入当前 session（断言 5）
 * - ChatGenerationRepository 实例重建且复用同一 store 后，已持久化消息仍可读取（断言 6）
 *
 * 已有覆盖（不重复）：
 * - 断言 1 NoModelConfigured → LlmGenerationLifecycleTest
 * - 断言 2 成功 → BackgroundGenerationRepositoryTest.runQueuedCurrentJobPersistsAssistantAndCompletesJob
 * - 断言 3 失败 → BackgroundGenerationRepositoryTest.providerFailureMarksJobFailedWithoutAssistantMessage
 * - 断言 4 Session 删除 → ConversationRunCoordinatorTest.deletedSessionRejectsCompletion
 *   + BackgroundGenerationRepositoryTest.archivedSessionDiscardsJobBeforeWritingAssistantMessage
 * - 断言 7 重试不重复 → ConversationRunCoordinatorTest.duplicateCompletionIsAcceptedOnlyOnce
 */
class ChatGenerationClosureTest {

    @Test
    fun noProfileReturnsNoModelConfigured() = runBlocking {
        val fixture = fixture(profileDao = RouteAFakeLlmProfileDao())
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-1"),
            nowEpochMillis = 100L,
            isTokenCurrent = { true }
        )
        assertEquals(ChatGenerationOutcome.NoModelConfigured, outcome)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun fakeRuntimeSuccessPersistsAssistantMessage() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Success("Use common factors first."))
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-success"),
            nowEpochMillis = 200L,
            isTokenCurrent = { true }
        )
        assertTrue(outcome is ChatGenerationOutcome.Generated)
        assertEquals("assistant-token-success", (outcome as ChatGenerationOutcome.Generated).assistantMessageId)
        val messages = fixture.messageRepository.listMessages("session-1")
        assertEquals(1, messages.size)
        assertEquals(MessageRole.Assistant, messages[0].role)
        assertEquals("Use common factors first.", messages[0].text)
        assertEquals("session-1", messages[0].sessionId)
    }

    @Test
    fun fakeRuntimeFailureReturnsProviderFailedWithoutMessage() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Failure("Rate limited"))
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-failure"),
            nowEpochMillis = 300L,
            isTokenCurrent = { true }
        )
        assertTrue(outcome is ChatGenerationOutcome.ProviderFailed)
        assertEquals("Rate limited", (outcome as ChatGenerationOutcome.ProviderFailed).message)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun fakeRuntimeTimeoutReturnsProviderFailedWithoutMessage() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Timeout)
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-timeout"),
            nowEpochMillis = 400L,
            isTokenCurrent = { true }
        )
        assertTrue(outcome is ChatGenerationOutcome.ProviderFailed)
        assertEquals(LlmGenerationResult.Timeout.message, (outcome as ChatGenerationOutcome.ProviderFailed).message)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun staleTokenPreventsPersistenceAndReturnsStale() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Success("Late reply"))
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-stale"),
            nowEpochMillis = 500L,
            isTokenCurrent = { false }
        )
        assertEquals(ChatGenerationOutcome.Stale, outcome)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun canPersistResultFalsePreventsPersistenceAndReturnsStale() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Success("Late reply"))
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-current"),
            nowEpochMillis = 600L,
            isTokenCurrent = { true },
            canPersistResult = { false }
        )
        assertEquals(ChatGenerationOutcome.Stale, outcome)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun messagesRemainReadableWhenChatGenerationRepositoryIsRecreatedWithSharedStore() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(LlmGenerationResult.Success("First reply"))
        )
        fixture.repository.generateReply(
            input = input(token = "token-1"),
            nowEpochMillis = 700L,
            isTokenCurrent = { true }
        )
        assertEquals(1, fixture.messageRepository.listMessages("session-1").size)

        // 用同一批 DAO 构造新的 Repository 实例，验证共享 store 中的消息仍可读取。
        val recreatedRepository = ChatGenerationRepository(
            messageRepository = fixture.messageRepository,
            llmProfileRepository = fixture.llmProfileRepository,
            runtime = RouteAStaticRuntime(LlmGenerationResult.Success("Second reply"))
        )
        recreatedRepository.generateReply(
            input = input(token = "token-2"),
            nowEpochMillis = 800L,
            isTokenCurrent = { true }
        )

        val recovered = fixture.messageRepository.listMessages("session-1")
        assertEquals(2, recovered.size)
        assertEquals("First reply", recovered[0].text)
        assertEquals("Second reply", recovered[1].text)
        assertEquals(MessageRole.Assistant, recovered[0].role)
        assertEquals(MessageRole.Assistant, recovered[1].role)
    }

    @Test
    fun staleSession1GenerationDoesNotWriteAfterSessionSwitch() = runBlocking {
        val oldRuntime = RouteASuspendedRuntime(LlmGenerationResult.Success("Late reply for session-1"))
        val fixture = fixture(
            runtime = oldRuntime
        )
        var currentToken = LlmGenerationToken("token-session-1")
        val oldGeneration = async {
            fixture.repository.generateReply(
            input = ChatGenerationInput(
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "Help with session 1",
                token = LlmGenerationToken("token-session-1"),
                capabilities = LlmCapabilities()
            ),
            nowEpochMillis = 900L,
                isTokenCurrent = { it == currentToken }
            )
        }
        oldRuntime.started.await()

        currentToken = LlmGenerationToken("token-session-2")
        try {
            val newSessionRepository = ChatGenerationRepository(
                messageRepository = fixture.messageRepository,
                llmProfileRepository = fixture.llmProfileRepository,
                runtime = RouteAStaticRuntime(LlmGenerationResult.Success("Reply for session-2"))
            )
            val newOutcome = newSessionRepository.generateReply(
                input = ChatGenerationInput(
                    sessionId = "session-2",
                    userMessageId = "user-2",
                    userText = "Help with session 2",
                    token = LlmGenerationToken("token-session-2"),
                    capabilities = LlmCapabilities()
                ),
                nowEpochMillis = 910L,
                isTokenCurrent = { it == currentToken }
            )
            val session2Messages = fixture.messageRepository.listMessages("session-2")
            assertTrue(newOutcome is ChatGenerationOutcome.Generated)
            assertEquals("assistant-token-session-2", (newOutcome as ChatGenerationOutcome.Generated).assistantMessageId)
            assertEquals(1, session2Messages.size)
            assertEquals("Reply for session-2", session2Messages[0].text)
            assertEquals("assistant-token-session-2", session2Messages[0].id)
            assertEquals("session-2", session2Messages[0].sessionId)
        } finally {
            oldRuntime.release()
        }
        val oldOutcome = oldGeneration.await()

        val session1Messages = fixture.messageRepository.listMessages("session-1")
        assertEquals(ChatGenerationOutcome.Stale, oldOutcome)
        assertTrue(session1Messages.isEmpty())
    }

    @Test
    fun noModelConfigurationNeverInvokesRuntime() = runBlocking {
        val countingRuntime = RouteACountingRuntime(LlmGenerationResult.Success("Should never be generated"))
        val fixture = fixture(
            profileDao = RouteAFakeLlmProfileDao(),
            runtime = countingRuntime
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-no-model"),
            nowEpochMillis = 1000L,
            isTokenCurrent = { true }
        )
        assertEquals(ChatGenerationOutcome.NoModelConfigured, outcome)
        assertEquals(0, countingRuntime.callCount)
        assertTrue(fixture.messageDao.messages.isEmpty())
    }

    @Test
    fun richReplyKeepsEvidenceOutOfPersistedAssistantText() = runBlocking {
        val fixture = fixture(
            runtime = RouteAStaticRuntime(
                LlmGenerationResult.Success(
                    """{"version":"v1","blocks":[{"type":"paragraph","text":"先看定义域。"}],"evidenceReferenceIds":["source-1"],"toolCalls":[],"outcome":{}}"""
                )
            )
        )
        val outcome = fixture.repository.generateReply(
            input = input(token = "token-rich").copy(
                contextEvidence = listOf(
                    LlmContextEvidence(
                        id = "source-1",
                        title = "定义域提示",
                        body = "先检查变量取值范围。",
                        kind = "Source"
                    )
                )
            ),
            nowEpochMillis = 1100L,
            isTokenCurrent = { true }
        )

        assertTrue(outcome is ChatGenerationOutcome.Generated)
        assertEquals(
            listOf("source-1"),
            (outcome as ChatGenerationOutcome.Generated).replyEnvelope!!.evidenceReferenceIds
        )
        val persisted = fixture.messageRepository.listMessages("session-1").single().text
        assertEquals("先看定义域。", persisted)
        assertFalse(persisted.contains("Sources:"))
    }

    private fun fixture(
        profileDao: RouteAFakeLlmProfileDao = RouteAFakeLlmProfileDao.withActiveProfile(),
        runtime: LlmGenerationRuntime = RouteAStaticRuntime(LlmGenerationResult.Success("Mock generation ready"))
    ): RouteAFixture {
        val messageDao = RouteAFakeMessageDao()
        val messageAttachmentDao = RouteAFakeMessageAttachmentDao()
        val messageQuoteDao = RouteAFakeMessageQuoteDao()
        val messageRepository = MessageRepository(messageDao, messageAttachmentDao, messageQuoteDao)
        val secretStore = RouteAFakeSecretStore()
        val llmProfileRepository = LlmProfileRepository(profileDao, secretStore)
        val repository = ChatGenerationRepository(
            messageRepository = messageRepository,
            llmProfileRepository = llmProfileRepository,
            runtime = runtime
        )
        return RouteAFixture(messageDao, messageRepository, llmProfileRepository, repository)
    }

    private fun input(token: String): ChatGenerationInput =
        ChatGenerationInput(
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Explain factoring",
            token = LlmGenerationToken(token),
            capabilities = LlmCapabilities()
        )
}

private data class RouteAFixture(
    val messageDao: RouteAFakeMessageDao,
    val messageRepository: MessageRepository,
    val llmProfileRepository: LlmProfileRepository,
    val repository: ChatGenerationRepository
)

private class RouteAFakeMessageDao : MessageDao {
    val messages = linkedMapOf<String, MessageEntity>()

    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int =
        if (messages.remove(id) == null) 0 else 1
}

private class RouteAFakeMessageAttachmentDao : MessageAttachmentDao {
    override suspend fun insert(attachment: MessageAttachmentEntity) = Unit
    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> = emptyList()
    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> = emptyList()
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class RouteAFakeMessageQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit
    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class RouteAFakeLlmProfileDao : LlmProfileDao {
    val entities = linkedMapOf<String, LlmProfileEntity>()

    override suspend fun upsert(profile: LlmProfileEntity) {
        entities[profile.id] = profile
    }

    override suspend fun getById(id: String): LlmProfileEntity? = entities[id]

    override suspend fun listBySpace(spaceId: String): List<LlmProfileEntity> =
        entities.values.filter { it.spaceId == spaceId }

    override suspend fun listAll(): List<LlmProfileEntity> = entities.values.toList()

    override suspend fun setEnabledForSpace(spaceId: String, enabledProfileId: String, updatedAtEpochMillis: Long) = Unit

    override suspend fun deleteById(id: String): Int = if (entities.remove(id) == null) 0 else 1

    companion object {
        fun withActiveProfile(): RouteAFakeLlmProfileDao =
            RouteAFakeLlmProfileDao().also { dao ->
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

private class RouteAFakeSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}

private class RouteAStaticRuntime(
    private val result: LlmGenerationResult
) : LlmGenerationRuntime {
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult = result
}

private class RouteACountingRuntime(
    private val result: LlmGenerationResult
) : LlmGenerationRuntime {
    var callCount = 0
        private set

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        callCount += 1
        return result
    }
}

private class RouteASuspendedRuntime(
    private val result: LlmGenerationResult
) : LlmGenerationRuntime {
    val started = CompletableDeferred<Unit>()
    private val continuation = CompletableDeferred<Unit>()

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        started.complete(Unit)
        continuation.await()
        return result
    }

    fun release() {
        continuation.complete(Unit)
    }
}
