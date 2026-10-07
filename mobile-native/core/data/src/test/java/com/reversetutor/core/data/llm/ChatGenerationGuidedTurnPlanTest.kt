package com.reversetutor.core.data.llm

import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.domain.TeachingAction
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.llm.FakeLlmGenerationRuntime
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.StructuredTurnOutcome
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 Task 2.4 red tests for [ChatGenerationRepository]:
 * the domain [TurnPlan] is forwarded to the runtime as bounded structured
 * context; the model's own self-assessment never becomes a learning fact in
 * this layer; and provider diagnostics are surfaced only as safe codes.
 */
class ChatGenerationGuidedTurnPlanTest {

    private val domainPlan = TurnPlan(
        actionType = TeachingAction.Diagnose,
        secondaryAction = TeachingAction.WorkedExample,
        learningObjective = "先定位跳步骤的具体缺口",
        conceptKey = "函数单调性",
        expectedUserMove = "回答一个最小诊断问题",
        responseFormat = com.reversetutor.core.domain.ResponseFormat.Steps,
        hintLevel = 1,
        evidenceRequirement = com.reversetutor.core.domain.EvidenceRequirement.UserAnswer,
        nextActionOnSuccess = TeachingAction.SocraticQuestion,
        nextActionOnFailure = TeachingAction.WorkedExample
    )

    private fun repository(
        runtime: LlmGenerationRuntime,
        messages: MessageRepository = MessageRepository(
            GuidedFakeMessageDao(), GuidedFakeAttachmentDao(), GuidedFakeQuoteDao()
        )
    ): Pair<ChatGenerationRepository, MessageRepository> {
        val repository = ChatGenerationRepository(
            messageRepository = messages,
            llmProfileRepository = LlmProfileRepository(
                GuidedFakeProfileDao.withActiveProfile(),
                GuidedFakeSecretStore()
            ),
            runtime = runtime
        )
        return repository to messages
    }

    private fun input(token: String) = ChatGenerationInput(
        sessionId = "session-1",
        userMessageId = "user-1",
        userText = "我刚才那步对不对？",
        token = LlmGenerationToken(token),
        capabilities = LlmCapabilities()
    )

    // 1+3. Domain TurnPlan reaches the request as structured context, and the
    //      original user message is untouched.
    @Test
    fun domainTurnPlanIsForwardedAsBoundedStructuredContext() = runBlocking {
        val runtime = GuidedRecordingRuntime()
        val (repo, _) = repository(runtime)

        repo.generateReply(
            input = input("t-plan").copy(turnPlan = domainPlan),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        val request: LlmGenerationRequest = runtime.requests.single()
        assertEquals("我刚才那步对不对？", request.userText)
        val carried = request.guidedTurnPlan
        assertEquals("diagnose", carried?.actionType)
        assertEquals("worked_example", carried?.secondaryAction)
        assertEquals("函数单调性", carried?.conceptKey)
        assertEquals(1, carried?.hintLevel)
        assertEquals("user_answer", carried?.evidenceRequirement)
    }

    // 2. No plan → legacy request shape, fully backward compatible.
    @Test
    fun absentPlanKeepsLegacyRequestShape() = runBlocking {
        val runtime = GuidedRecordingRuntime()
        val (repo, _) = repository(runtime)

        repo.generateReply(
            input = input("t-none"),
            nowEpochMillis = 20L,
            isTokenCurrent = { true }
        )

        assertNull(runtime.requests.single().guidedTurnPlan)
    }

    // 4. A model self-assessed reply never turns into a learning fact here:
    //    `mastery` is not an accepted outcome field, so the outcome snapshot
    //    collapses to EMPTY, and only the plain reply text persists.
    @Test
    fun modelSelfAssessmentNeverBecomesLearningFact() = runBlocking {
        val (repo, messages) = repository(
            FakeLlmGenerationRuntime(
                defaultResult = LlmGenerationResult.Success(
                    """{"version":"v1","blocks":[{"type":"paragraph","text":"你离目标还差一步"}],""" +
                        """"evidenceReferenceIds":[],"toolCalls":[],"outcome":{"mastery":1.0,"correctness":1.0,"evidenceStatus":"passed"}}"""
                )
            )
        )

        val outcome = repo.generateReply(
            input = input("t-self").copy(turnPlan = domainPlan),
            nowEpochMillis = 30L,
            isTokenCurrent = { true }
        )

        val generated = outcome as ChatGenerationOutcome.Generated
        val carriedOutcome = generated.replyEnvelope?.outcome
        // self-assessment fields poison the outcome → untrusted EMPTY candidate
        assertEquals(StructuredTurnOutcome.EMPTY, carriedOutcome)
        assertEquals("none", carriedOutcome?.evidenceStatus)
        // only the plain reply text persists; no structured fact leaves this layer
        val saved = messages.listMessages("session-1")
        assertEquals(listOf(MessageRole.Assistant), saved.map { it.role })
        assertEquals("你离目标还差一步", saved.single().text)
        assertFalse(saved.single().text.contains("mastery"))
    }

    // 7. Raw provider diagnostics are mapped to safe codes and never persisted.
    @Test
    fun rawProviderDiagnosticIsMappedToSafeCode() = runBlocking {
        val (repo, messages) = repository(
            FakeLlmGenerationRuntime(
                defaultResult = LlmGenerationResult.Failure(
                    "HTTP 500 from https://sk-abcdef:8443/v1 Authorization: Bearer sk-secretvalue leaked"
                )
            )
        )

        val outcome = repo.generateReply(
            input = input("t-fail").copy(turnPlan = domainPlan),
            nowEpochMillis = 40L,
            isTokenCurrent = { true }
        )

        val failed = outcome as ChatGenerationOutcome.ProviderFailed
        val lower = failed.message.lowercase()
        assertFalse("不得转发原始异常细节", failed.message.startsWith("HTTP 500"))
        assertFalse(lower.contains("sk-"))
        assertFalse(lower.contains("http"))
        assertFalse(lower.contains("authorization"))
        assertTrue(messages.listMessages("session-1").isEmpty())
    }

    @Test
    fun credentialRejectionMapsToSafeAuthCode() = runBlocking {
        val (repo, _) = repository(
            FakeLlmGenerationRuntime(
                defaultResult = LlmGenerationResult.Failure("Provider rejected the credential.")
            )
        )
        val outcome = repo.generateReply(
            input = input("t-auth"),
            nowEpochMillis = 50L,
            isTokenCurrent = { true }
        )
        assertEquals(
            ChatGenerationOutcome.ProviderFailed("llm_provider_unauthorized"),
            outcome
        )
    }
}

private class GuidedRecordingRuntime : LlmGenerationRuntime {
    val requests = mutableListOf<LlmGenerationRequest>()

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        requests += request
        return LlmGenerationResult.Success("好的，我们一步步来。")
    }
}

private class GuidedFakeMessageDao : MessageDao {
    private val messages = linkedMapOf<String, MessageEntity>()

    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int =
        if (messages.remove(id) == null) 0 else 1
}

private class GuidedFakeAttachmentDao : MessageAttachmentDao {
    override suspend fun insert(attachment: MessageAttachmentEntity) = Unit
    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> = emptyList()
    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> = emptyList()
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class GuidedFakeQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit
    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class GuidedFakeProfileDao : LlmProfileDao {
    private val entities = linkedMapOf<String, LlmProfileEntity>()

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
        fun withActiveProfile(): GuidedFakeProfileDao =
            GuidedFakeProfileDao().also { dao ->
                dao.entities["profile-1"] = LlmProfileEntity(
                    id = "profile-1",
                    spaceId = "default-space",
                    name = "Guided test model",
                    provider = "OpenAiCompatible",
                    model = "qwen-test",
                    secretRef = "llm-secret-profile-1",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    baseUrl = "https://provider.invalid/v1",
                    enabled = true
                )
            }
    }
}

private class GuidedFakeSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}
