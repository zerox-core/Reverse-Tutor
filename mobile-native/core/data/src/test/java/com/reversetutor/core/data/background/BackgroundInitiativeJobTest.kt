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
import com.reversetutor.core.model.BackgroundJobStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundInitiativeJobTest {

    private fun envelope(source: String = "heartbeat") = LlmAssistantTurnEnvelope(
        window = LlmWindowContext(windowId = "session-1", rootId = "root-1", windowKind = "TASK_ROOT", forkRevision = 7L),
        turnPlan = LlmTurnPlan(intent = "check-in", actionType = "observe", studentRole = "companion"),
        initiativeSource = source
    )

    private fun initiativeInput(
        spaceId: String = "space-init",
        source: String = "heartbeat",
        envelope: LlmAssistantTurnEnvelope = envelope()
    ) = BackgroundInitiativeInput(
        spaceId = spaceId,
        targetWindowId = "session-1",
        initiativeSource = source,
        envelope = envelope
    )

    @Test
    fun enqueueInitiativePreservesSpaceIdAndNoPlaceholderUserMessage() = runBlocking {
        val jobDao = InitJobDao()
        val repo = repository(jobDao = jobDao)
        val job = repo.enqueueInitiativeJob(initiativeInput(spaceId = "space-init"), nowEpochMillis = 10L, jobId = "init-1")

        assertEquals("space-init", job.spaceId)
        assertEquals("session-1", job.sessionId)
        assertEquals("Initiative", job.kind)
        assertNull(job.userMessageId)
        assertNull(job.userText)
        assertNull(jobDao.getById("init-1")!!.userMessageId)
        assertNull(jobDao.getById("init-1")!!.userText)
    }

    @Test
    fun initiativeTargetMustMatchImmutableEnvelopeWindow() = runBlocking {
        val repo = repository()
        try {
            repo.enqueueInitiativeJob(
                initiativeInput().copy(targetWindowId = "other-window"),
                nowEpochMillis = 10L,
                jobId = "init-mismatch"
            )
            throw AssertionError("expected target/window mismatch to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("target window"))
        }
    }

    @Test
    fun retryUsesItsPersistedInitiativeSnapshot() = runBlocking {
        val runtime = InitCapturingRuntime()
        val repo = repository(runtime = runtime)
        repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 10L, jobId = "init-1")
        val persisted = repo.getJob("init-1")
        assertEquals("session-1", persisted!!.assistantTurnEnvelope!!.window.windowId)

        repo.runGenerationJob("init-1", nowEpochMillis = 30L)

        assertEquals("session-1", runtime.requests.single().assistantTurnEnvelope!!.window.windowId)
        assertNull(runtime.requests.single().userText)
        assertNull(runtime.requests.single().userMessageId)
    }

    @Test
    fun initiative_snapshot_round_trips_expiry_and_cooldown() = runBlocking {
        val repo = repository()
        val source = envelope().copy(
            turnPlan = envelope().turnPlan!!.copy(
                expiryEpochMillis = 1234L,
                minCooldownMillis = 567L
            )
        )
        repo.enqueueInitiativeJob(
            initiativeInput(envelope = source),
            nowEpochMillis = 10L,
            jobId = "init-timing"
        )

        val persistedPlan = repo.getJob("init-timing")?.assistantTurnEnvelope?.turnPlan
        assertEquals(1234L, persistedPlan?.expiryEpochMillis)
        assertEquals(567L, persistedPlan?.minCooldownMillis)
    }

    @Test
    fun providerFailureMapsToSafeFailure() = runBlocking {
        val messageRepository = MessageRepository(InitMessageDao(), InitMessageAttachmentDao(), InitMessageQuoteDao())
        val repo = repository(runtime = InitStaticRuntime(LlmGenerationResult.Failure("rate limited")), messageRepository = messageRepository)
        repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 10L, jobId = "init-1")

        val outcome = repo.runGenerationJob("init-1", nowEpochMillis = 30L)

        assertEquals(BackgroundGenerationOutcome.Failed("background_generation_failed"), outcome)
        assertEquals(BackgroundJobStatus.Failed, repo.getJob("init-1")?.status)
        assertTrue(repo.getJob("init-1")?.errorMessage?.contains("rate limited") != true)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun initiativeAssistantUsesJobSpaceId() = runBlocking {
        val messageRepository = MessageRepository(InitMessageDao(), InitMessageAttachmentDao(), InitMessageQuoteDao())
        val repo = repository(
            runtime = InitStaticRuntime(LlmGenerationResult.Success("opening")),
            messageRepository = messageRepository
        )
        repo.enqueueInitiativeJob(initiativeInput(spaceId = "space-init"), nowEpochMillis = 10L, jobId = "init-space")
        repo.runGenerationJob("init-space", nowEpochMillis = 20L)
        assertEquals("space-init", messageRepository.listMessages("session-1").single().spaceId)
    }

    @Test
    fun sameJobIdIsIdempotent() = runBlocking {
        val jobDao = InitJobDao()
        val repo = repository(jobDao = jobDao, runtime = InitStaticRuntime(LlmGenerationResult.Success("opening")))
        repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 10L, jobId = "init-1")
        repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 11L, jobId = "init-1")
        assertEquals(1, jobDao.all.size)

        val first = repo.runGenerationJob("init-1", nowEpochMillis = 20L)
        val second = repo.runGenerationJob("init-1", nowEpochMillis = 21L)
        assertEquals(first, second)
        assertEquals(BackgroundJobStatus.Completed, repo.getJob("init-1")?.status)
    }

    @Test
    fun completedInitiativeJobIsNotRequeued() = runBlocking {
        val repo = repository(runtime = InitStaticRuntime(LlmGenerationResult.Success("opening")))
        repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 10L, jobId = "init-completed")
        repo.runGenerationJob("init-completed", nowEpochMillis = 20L)
        val completed = repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 30L, jobId = "init-completed")
        assertEquals(BackgroundJobStatus.Completed, completed.status)
    }

    @Test
    fun initiative_job_id_cannot_collide_with_generation_job() = runBlocking {
        val jobDao = InitJobDao()
        val repo = repository(jobDao = jobDao)
        jobDao.upsert(
            BackgroundJobEntity(
                id = "shared-id",
                spaceId = "space-init",
                kind = "Generation",
                status = BackgroundJobStatus.Completed.name,
                createdAtEpochMillis = 1L,
                sessionId = "session-1",
                userMessageId = "user-1",
                userText = "normal",
                generationToken = "generation-token"
            )
        )

        try {
            repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 2L, jobId = "shared-id")
            throw AssertionError("expected initiative/generation id collision to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("non-initiative"))
        }
    }

    @Test
    fun initiative_token_is_unique_per_job_id() = runBlocking {
        val repo = repository()
        val first = repo.enqueueInitiativeJob(initiativeInput(), nowEpochMillis = 1L, jobId = "init-a")
        val second = repo.enqueueInitiativeJob(
            initiativeInput(source = "manual"),
            nowEpochMillis = 2L,
            jobId = "init-b"
        )
        assertTrue(first.token != second.token)
    }

    @Test
    fun expired_initiative_is_discarded_before_provider_call() = runBlocking {
        val runtime = InitCapturingRuntime()
        val repo = repository(runtime = runtime)
        val expiredEnvelope = envelope().copy(
            turnPlan = envelope().turnPlan!!.copy(expiryEpochMillis = 20L)
        )
        repo.enqueueInitiativeJob(
            initiativeInput(envelope = expiredEnvelope),
            nowEpochMillis = 10L,
            jobId = "init-expired"
        )

        val outcome = repo.runGenerationJob("init-expired", nowEpochMillis = 20L)

        assertEquals(BackgroundGenerationOutcome.Discarded("Initiative plan expired"), outcome)
        assertTrue(runtime.requests.isEmpty())
    }

    private fun repository(
        jobDao: InitJobDao = InitJobDao(),
        runtime: LlmGenerationRuntime = InitStaticRuntime(LlmGenerationResult.Success("opening")),
        messageRepository: MessageRepository = MessageRepository(InitMessageDao(), InitMessageAttachmentDao(), InitMessageQuoteDao())
    ): BackgroundGenerationRepository =
        BackgroundGenerationRepository(
            backgroundJobDao = jobDao,
            sessionDao = InitSessionDao(),
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(InitLlmProfileDao.withActiveProfile(), InitSecretStore()),
            runtime = runtime
        )
}

private class InitJobDao : BackgroundJobDao {
    val all = mutableMapOf<String, BackgroundJobEntity>()
    override suspend fun upsert(job: BackgroundJobEntity) {
        all[job.id] = job
    }

    override suspend fun getById(id: String): BackgroundJobEntity? = all[id]

    override suspend fun claimQueued(
        id: String,
        queuedStatus: String,
        runningStatus: String,
        startedAtEpochMillis: Long
    ): Int {
        val existing = all[id] ?: return 0
        if (existing.status != queuedStatus) return 0
        all[id] = existing.copy(
            status = runningStatus,
            startedAtEpochMillis = startedAtEpochMillis,
            errorMessage = null
        )
        return 1
    }

    override suspend fun listGenerationByStatuses(statuses: List<String>): List<BackgroundJobEntity> =
        all.values.filter { it.kind in setOf("Generation", "Initiative") && it.status in statuses }

    override suspend fun listGenerationBySession(sessionId: String): List<BackgroundJobEntity> =
        all.values.filter { it.kind in setOf("Generation", "Initiative") && it.sessionId == sessionId }
}

private class InitSessionDao : SessionDao {
    private val sessions = linkedMapOf(
        "session-1" to SessionEntity(id = "session-1", spaceId = "space-init", title = "S", createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L)
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
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(modelBindingId = modelBindingId) }
        return 1
    }

    override suspend fun updateSessionSettingsModelBinding(sessionId: String, modelBindingId: String): Int = 0
}

private class InitMessageDao : MessageDao {
    val messages = linkedMapOf<String, MessageEntity>()
    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int = if (messages.remove(id) == null) 0 else 1
}

private class InitMessageAttachmentDao : MessageAttachmentDao {
    override suspend fun insert(attachment: MessageAttachmentEntity) = Unit
    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> = emptyList()
    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> = emptyList()
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class InitMessageQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit
    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null
    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class InitLlmProfileDao : LlmProfileDao {
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
        fun withActiveProfile(): InitLlmProfileDao =
            InitLlmProfileDao().also { dao ->
                dao.entities["profile-1"] = LlmProfileEntity(
                    id = "profile-1", spaceId = "default-space", name = "Work", provider = "OpenAiCompatible",
                    model = "gpt-4o-mini", secretRef = "llm-secret-profile-1", createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L, baseUrl = "https://api.example.test/v1", enabled = true
                )
            }
    }
}

private class InitSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}

private class InitStaticRuntime(private val result: LlmGenerationResult) : LlmGenerationRuntime {
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult = result
}

private class InitCapturingRuntime : LlmGenerationRuntime {
    val requests = mutableListOf<LlmGenerationRequest>()
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        requests += request
        return LlmGenerationResult.Success("Opening reply")
    }
}
