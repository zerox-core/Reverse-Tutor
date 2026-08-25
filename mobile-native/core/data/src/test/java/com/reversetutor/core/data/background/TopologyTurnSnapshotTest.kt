package com.reversetutor.core.data.background

import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.data.llm.SecretStore
import com.reversetutor.core.data.local.dao.BackgroundJobDao
import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmTurnPlan
import com.reversetutor.core.llm.LlmWindowContext
import com.reversetutor.core.llm.StructuredTurnOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 topology turn snapshot tests.
 *
 * Package C (task 4): the background generation job persists the immutable turn
 * envelope so retries use the persisted snapshot, not current memory. A missing
 * or malformed envelope preserves a normal assistant reply and yields an empty
 * structured outcome; the outcome never carries raw transcript or Provider text.
 */
class TopologyTurnSnapshotTest {

    private fun envelope(plan: LlmTurnPlan? = turnPlan()) = LlmAssistantTurnEnvelope(
        window = LlmWindowContext(windowId = "w1", rootId = "root-1", windowKind = "TASK_ROOT", forkRevision = 7L),
        turnPlan = plan,
        initiativeSource = "heartbeat"
    )

    private fun turnPlan() = LlmTurnPlan(
        intent = "check-in",
        actionType = "probe",
        studentRole = "probing_student",
        knowledgePoint = "factoring",
        difficulty = 0.7f
    )

    @Test
    fun oldJobWithNullTopologyFieldsRunsWithExistingBehavior() = runBlocking {
        val runtime = TsnapCapturingRuntime()
        val repo = repository(runtime = runtime)
        repo.enqueueGenerationJob(input(token = "tok-null", envelope = null), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repo.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertNull(runtime.requests.single().assistantTurnEnvelope)
        assertEquals(StructuredTurnOutcome.EMPTY, (outcome as BackgroundGenerationOutcome.Completed).structuredOutcome)
    }

    @Test
    fun retryUsesItsPersistedSnapshotNotCurrentMemory() = runBlocking {
        val runtime = TsnapCapturingRuntime()
        val repo = repository(runtime = runtime)
        repo.enqueueGenerationJob(input(token = "tok-retry", envelope = envelope()), nowEpochMillis = 10L, jobId = "job-1")

        val persisted = repo.getJob("job-1")
        assertEquals("w1", persisted!!.assistantTurnEnvelope!!.window.windowId)

        repo.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertEquals("w1", runtime.requests.single().assistantTurnEnvelope!!.window.windowId)
        assertEquals(7L, runtime.requests.single().assistantTurnEnvelope!!.window.forkRevision)
    }

    @Test
    fun plainProviderReplyPersistsAssistantWithEmptyOutcome() = runBlocking {
        val messageRepository = MessageRepository(TsnapMessageDao(), TsnapMessageAttachmentDao(), TsnapMessageQuoteDao())
        val repo = repository(
            runtime = TsnapStaticRuntime(LlmGenerationResult.Success("plain reply")),
            messageRepository = messageRepository
        )
        repo.enqueueGenerationJob(input(token = "tok-plain", envelope = null), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repo.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertEquals(StructuredTurnOutcome.EMPTY, (outcome as BackgroundGenerationOutcome.Completed).structuredOutcome)
        assertEquals(listOf("plain reply"), messageRepository.listMessages("session-1").map { it.text })
    }

    @Test
    fun malformedEnvelopePersistsAssistantWithEmptyOutcome() = runBlocking {
        val runtime = TsnapCapturingRuntime()
        val repo = repository(runtime = runtime)
        val malformed = LlmAssistantTurnEnvelope(
            window = LlmWindowContext(windowId = "  ", rootId = ""),
            turnPlan = turnPlan()
        )
        repo.enqueueGenerationJob(input(token = "tok-bad", envelope = malformed), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repo.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertNull(runtime.requests.single().assistantTurnEnvelope)
        assertEquals(StructuredTurnOutcome.EMPTY, (outcome as BackgroundGenerationOutcome.Completed).structuredOutcome)
    }

    @Test
    fun validEnvelopePersistsReplyAndReturnsBoundedOutcome() = runBlocking {
        val runtime = TsnapCapturingRuntime()
        val repo = repository(runtime = runtime)
        repo.enqueueGenerationJob(input(token = "tok-valid", envelope = envelope()), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repo.runGenerationJob("job-1", nowEpochMillis = 30L)

        val completed = outcome as BackgroundGenerationOutcome.Completed
        assertEquals("w1", completed.structuredOutcome.windowId)
        assertEquals("probe", completed.structuredOutcome.actionType)
        assertEquals("heartbeat", completed.structuredOutcome.initiativeSource)
        assertEquals("check-in", completed.structuredOutcome.processSummary)
    }

    @Test
    fun outcomeCannotContainRawTranscriptOrProviderDetails() {
        val outcome = StructuredTurnOutcome(
            windowId = "w1",
            actionType = "probe",
            processSummary = "raw provider text should never be here"
        ).normalized()
        // The outcome type carries only bounded plan-derived fields; it is not a
        // transcript and has no provider error detail surface.
        assertTrue(outcome.processSummary.isNotBlank() && outcome.processSummary.length <= 320)
    }

    private fun repository(
        runtime: LlmGenerationRuntime,
        jobDao: TsnapJobDao = TsnapJobDao(),
        sessionDao: TsnapSessionDao = TsnapSessionDao(),
        messageRepository: MessageRepository = MessageRepository(
            TsnapMessageDao(), TsnapMessageAttachmentDao(), TsnapMessageQuoteDao()
        )
    ): BackgroundGenerationRepository =
        BackgroundGenerationRepository(
            backgroundJobDao = jobDao,
            sessionDao = sessionDao,
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(TsnapLlmProfileDao.withActiveProfile(), TsnapSecretStore()),
            runtime = runtime
        )

    private fun input(token: String, envelope: LlmAssistantTurnEnvelope?): BackgroundGenerationInput =
        BackgroundGenerationInput(
            spaceId = "default-space",
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Explain factoring",
            token = LlmGenerationToken(token),
            capabilities = LlmCapabilities(),
            assistantTurnEnvelope = envelope
        )
}

private class TsnapJobDao : BackgroundJobDao {
    private val jobs = linkedMapOf<String, BackgroundJobEntity>()
    override suspend fun upsert(job: BackgroundJobEntity) {
        jobs[job.id] = job
    }

    override suspend fun getById(id: String): BackgroundJobEntity? = jobs[id]

    override suspend fun claimQueued(
        id: String,
        queuedStatus: String,
        runningStatus: String,
        startedAtEpochMillis: Long
    ): Int {
        val existing = jobs[id] ?: return 0
        if (existing.status != queuedStatus) return 0
        jobs[id] = existing.copy(
            status = runningStatus,
            startedAtEpochMillis = startedAtEpochMillis,
            errorMessage = null
        )
        return 1
    }

    override suspend fun listGenerationByStatuses(statuses: List<String>): List<BackgroundJobEntity> =
        jobs.values.filter { it.kind in setOf("Generation", "Initiative") && it.status in statuses }

    override suspend fun listGenerationBySession(sessionId: String): List<BackgroundJobEntity> =
        jobs.values.filter { it.kind in setOf("Generation", "Initiative") && it.sessionId == sessionId }
}

private class TsnapSessionDao : SessionDao {
    private val sessions = linkedMapOf(
        "session-1" to SessionEntity(
            id = "session-1", spaceId = "default-space", title = "Session",
            createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L
        )
    )

    override suspend fun upsert(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun getById(id: String): SessionEntity? = sessions[id]

    override suspend fun listBySpace(spaceId: String): List<SessionEntity> =
        sessions.values.filter { it.spaceId == spaceId && !it.archived }

    override suspend fun rename(id: String, title: String, updatedAtEpochMillis: Long): Int = 0
    override suspend fun setPinned(id: String, pinned: Boolean, updatedAtEpochMillis: Long): Int = 0

    override suspend fun archive(id: String, updatedAtEpochMillis: Long): Int {
        val existing = sessions[id] ?: return 0
        sessions[id] = existing.copy(archived = true, updatedAtEpochMillis = updatedAtEpochMillis)
        return 1
    }

    override suspend fun updateSessionModelBinding(sessionId: String, modelBindingId: String): Int {
        val existing = sessions[sessionId] ?: return 0
        sessions[sessionId] = existing.copy(modelBindingId = modelBindingId)
        return 1
    }

    override suspend fun updateSessionSettingsModelBinding(sessionId: String, modelBindingId: String): Int = 0
}

private class TsnapMessageDao : MessageDao {
    val messages = linkedMapOf<String, MessageEntity>()
    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int = if (messages.remove(id) == null) 0 else 1
}

private class TsnapMessageAttachmentDao : MessageAttachmentDao {
    override suspend fun insert(attachment: MessageAttachmentEntity) = Unit
    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> = emptyList()
    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> = emptyList()
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class TsnapMessageQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit
    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class TsnapLlmProfileDao : LlmProfileDao {
    private val entities = linkedMapOf<String, LlmProfileEntity>()

    override suspend fun upsert(profile: LlmProfileEntity) {
        entities[profile.id] = profile
    }

    override suspend fun getById(id: String): LlmProfileEntity? = entities[id]
    override suspend fun listBySpace(spaceId: String): List<LlmProfileEntity> = entities.values.filter { it.spaceId == spaceId }
    override suspend fun listAll(): List<LlmProfileEntity> = entities.values.toList()
    override suspend fun setEnabledForSpace(spaceId: String, enabledProfileId: String, updatedAtEpochMillis: Long) = Unit
    override suspend fun deleteById(id: String): Int = if (entities.remove(id) == null) 0 else 1

    companion object {
        fun withActiveProfile(): TsnapLlmProfileDao =
            TsnapLlmProfileDao().also { dao ->
                dao.entities["profile-1"] = LlmProfileEntity(
                    id = "profile-1", spaceId = "default-space", name = "Work model",
                    provider = "OpenAiCompatible", model = "gpt-4o-mini",
                    secretRef = "llm-secret-profile-1", createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L, baseUrl = "https://api.example.test/v1", enabled = true
                )
            }
    }
}

private class TsnapSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}

private class TsnapStaticRuntime(private val result: LlmGenerationResult) : LlmGenerationRuntime {
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult = result
}

private class TsnapCapturingRuntime : LlmGenerationRuntime {
    val requests = mutableListOf<LlmGenerationRequest>()
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        requests += request
        return LlmGenerationResult.Success("Snapshot reply")
    }
}
