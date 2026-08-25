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
import com.reversetutor.core.data.model.ExecutionModelConfiguration
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGenerationRequest
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmSessionPolicyContext
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelProtocol
import com.reversetutor.core.model.ProviderConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundGenerationRepositoryTest {
    @Test
    fun queuedPolicySnapshotIsForwardedToRecoveredGenerationRequest() = runBlocking {
        val runtime = CapturingRuntime()
        val jobDao = FakeBackgroundJobDao()
        val firstRepository = repository(jobDao = jobDao, runtime = runtime)

        firstRepository.enqueueGenerationJob(
            input(token = "token-policy").copy(sessionPolicy = policy()),
            nowEpochMillis = 10L,
            jobId = "job-policy"
        )

        val recoveredRepository = repository(jobDao = jobDao, runtime = runtime)
        recoveredRepository.recoverInterruptedGenerationJobs(nowEpochMillis = 20L)
        recoveredRepository.runGenerationJob("job-policy", nowEpochMillis = 30L)

        assertEquals(policy().normalized(), runtime.requests.single().sessionPolicy)
    }

    @Test
    fun legacyJobWithoutPolicyForwardsNullToGenerationRequest() = runBlocking {
        val runtime = CapturingRuntime()
        val repository = repository(runtime = runtime)

        repository.enqueueGenerationJob(
            input(token = "token-legacy"),
            nowEpochMillis = 10L,
            jobId = "job-legacy"
        )
        repository.runGenerationJob("job-legacy", nowEpochMillis = 20L)

        assertNull(runtime.requests.single().sessionPolicy)
    }

    @Test
    fun defaultSwitchAndProcessRecoveryKeepEnqueueModelSnapshot() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val sessionDao = FakeSessionDao()
        val resolver = MutableExecutionModelResolver(defaultBindingId = "binding-a")
        val runtime = CapturingRuntime()
        val firstRepository = repository(
            jobDao = jobDao,
            sessionDao = sessionDao,
            runtime = runtime,
            modelConnectionRepository = resolver
        )

        val queued = firstRepository.enqueueGenerationJob(
            input(token = "token-snapshot"),
            nowEpochMillis = 10L,
            jobId = "job-snapshot"
        )
        assertEquals("binding-a", queued.modelBindingId)

        resolver.defaultBindingId = "binding-b"
        val restoredRepository = repository(
            jobDao = jobDao,
            sessionDao = sessionDao,
            runtime = runtime,
            modelConnectionRepository = resolver
        )
        restoredRepository.recoverInterruptedGenerationJobs(nowEpochMillis = 20L)
        val outcome = restoredRepository.runGenerationJob("job-snapshot", nowEpochMillis = 30L)

        assertEquals(BackgroundGenerationOutcome.Completed("assistant-token-snapshot"), outcome)
        assertEquals("binding-a", restoredRepository.getJob("job-snapshot")?.modelBindingId)
        assertEquals("model-a", runtime.requests.single().model)
        assertEquals("binding-a", resolver.requestedBindingIds.last())
    }

    @Test
    fun enqueuePersistsQueuedJobBeforeExecution() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val repository = repository(jobDao = jobDao)

        val job = repository.enqueueGenerationJob(input(token = "token-current"), nowEpochMillis = 10L, jobId = "job-1")

        assertEquals("job-1", job.id)
        assertEquals(BackgroundJobStatus.Queued, job.status)
        assertEquals("session-1", job.sessionId)
        assertEquals("user-1", job.userMessageId)
        assertEquals(LlmGenerationToken("token-current"), job.token)
        assertEquals("Explain factoring", job.userText)
        assertEquals("Queued", jobDao.getById("job-1")?.status)
    }

    @Test
    fun runQueuedCurrentJobPersistsAssistantAndCompletesJob() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = repository(
            jobDao = jobDao,
            messageRepository = messageRepository,
            runtime = StaticRuntime(LlmGenerationResult.Success("Use common factors first."))
        )
        repository.enqueueGenerationJob(input(token = "token-current"), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repository.runGenerationJob("job-1", nowEpochMillis = 20L)

        assertEquals(BackgroundGenerationOutcome.Completed("assistant-token-current"), outcome)
        val persisted = repository.getJob("job-1")
        assertEquals(BackgroundJobStatus.Completed, persisted?.status)
        assertEquals(20L, persisted?.startedAtEpochMillis)
        assertEquals(20L, persisted?.completedAtEpochMillis)
        val messages = messageRepository.listMessages("session-1")
        assertEquals(listOf(MessageRole.Assistant), messages.map { it.role })
        assertEquals("Use common factors first.", messages.single().text)
    }

    @Test
    fun providerFailureMarksJobFailedWithoutAssistantMessage() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = repository(
            jobDao = jobDao,
            messageRepository = messageRepository,
            runtime = StaticRuntime(LlmGenerationResult.Failure("Rate limited"))
        )
        repository.enqueueGenerationJob(input(token = "token-failure"), nowEpochMillis = 10L, jobId = "job-1")

        val outcome = repository.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertEquals(BackgroundGenerationOutcome.Failed("background_generation_failed"), outcome)
        val persisted = repository.getJob("job-1")
        assertEquals(BackgroundJobStatus.Failed, persisted?.status)
        assertEquals("background_generation_failed", persisted?.errorMessage)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun recoveryRequeuesRunningJobsAndCancellationPreventsExecution() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = repository(jobDao = jobDao, messageRepository = messageRepository)
        repository.enqueueGenerationJob(input(token = "token-current"), nowEpochMillis = 10L, jobId = "job-1")
        jobDao.forceStatus("job-1", "Running", startedAtEpochMillis = 11L)

        val recovered = repository.recoverInterruptedGenerationJobs(nowEpochMillis = 20L)

        assertEquals(listOf("job-1"), recovered.map { it.id })
        assertEquals(BackgroundJobStatus.Queued, repository.getJob("job-1")?.status)

        assertEquals(1, repository.cancelSessionGenerationJobs("session-1", nowEpochMillis = 21L))
        val outcome = repository.runGenerationJob("job-1", nowEpochMillis = 22L)

        assertEquals(BackgroundGenerationOutcome.Cancelled, outcome)
        assertEquals(BackgroundJobStatus.Cancelled, repository.getJob("job-1")?.status)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun archivedSessionDiscardsJobBeforeWritingAssistantMessage() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val sessionDao = FakeSessionDao()
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val repository = repository(
            jobDao = jobDao,
            sessionDao = sessionDao,
            messageRepository = messageRepository,
            runtime = StaticRuntime(LlmGenerationResult.Success("Late reply"))
        )
        repository.enqueueGenerationJob(input(token = "token-current"), nowEpochMillis = 10L, jobId = "job-1")
        sessionDao.archive("session-1", updatedAtEpochMillis = 11L)

        val outcome = repository.runGenerationJob("job-1", nowEpochMillis = 30L)

        assertEquals(BackgroundGenerationOutcome.Discarded("Session is unavailable"), outcome)
        assertEquals(BackgroundJobStatus.Discarded, repository.getJob("job-1")?.status)
        assertTrue(messageRepository.listMessages("session-1").isEmpty())
    }

    @Test
    fun cross_space_session_discards_job_before_provider_call() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val sessionDao = FakeSessionDao()
        val runtime = CapturingRuntime()
        val repository = repository(
            jobDao = jobDao,
            sessionDao = sessionDao,
            runtime = runtime
        )
        repository.enqueueGenerationJob(
            input(token = "token-cross-space").copy(spaceId = "space-a"),
            nowEpochMillis = 10L,
            jobId = "job-cross-space"
        )
        sessionDao.setSpace("session-1", "space-b")

        val outcome = repository.runGenerationJob("job-cross-space", nowEpochMillis = 20L)

        assertEquals(BackgroundGenerationOutcome.Discarded("Session is unavailable"), outcome)
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun second_claim_does_not_invoke_provider_again() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val runtime = CapturingRuntime()
        val repository = repository(jobDao = jobDao, runtime = runtime)
        repository.enqueueGenerationJob(input(token = "token-claim"), nowEpochMillis = 10L, jobId = "job-claim")
        jobDao.forceStatus("job-claim", BackgroundJobStatus.Running.name, startedAtEpochMillis = 11L)

        val outcome = repository.runGenerationJob("job-claim", nowEpochMillis = 20L)

        assertEquals(BackgroundGenerationOutcome.AlreadyRunning, outcome)
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun newerIndependentJobDoesNotInvalidateAnOlderActiveJobInTheSameSession() = runBlocking {
        val jobDao = FakeBackgroundJobDao()
        val messageRepository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        val runtime = CallbackRuntime {
            jobDao.forceUpsert(
                BackgroundJobEntity(
                    id = "job-newer",
                    spaceId = "default-space",
                    kind = "Generation",
                    status = "Queued",
                    createdAtEpochMillis = 11L,
                    sessionId = "session-1",
                    userMessageId = "user-2",
                    userText = "Newer prompt",
                    generationToken = "token-newer"
                )
            )
            LlmGenerationResult.Success("Old late reply")
        }
        val repository = repository(
            jobDao = jobDao,
            messageRepository = messageRepository,
            runtime = runtime
        )
        repository.enqueueGenerationJob(input(token = "token-old"), nowEpochMillis = 10L, jobId = "job-old")

        val outcome = repository.runGenerationJob("job-old", nowEpochMillis = 30L)

        assertEquals(BackgroundGenerationOutcome.Completed("assistant-token-old"), outcome)
        assertEquals(BackgroundJobStatus.Completed, repository.getJob("job-old")?.status)
        assertEquals(listOf("Old late reply"), messageRepository.listMessages("session-1").map { it.text })
    }

    private fun repository(
        jobDao: FakeBackgroundJobDao = FakeBackgroundJobDao(),
        sessionDao: FakeSessionDao = FakeSessionDao(),
        messageRepository: MessageRepository = MessageRepository(
            FakeMessageDao(),
            FakeMessageAttachmentDao(),
            FakeMessageQuoteDao()
        ),
        runtime: LlmGenerationRuntime = StaticRuntime(LlmGenerationResult.Success("Mock generation ready")),
        modelConnectionRepository: ExecutionModelResolver? = null
    ): BackgroundGenerationRepository =
        BackgroundGenerationRepository(
            backgroundJobDao = jobDao,
            sessionDao = sessionDao,
            messageRepository = messageRepository,
            llmProfileRepository = LlmProfileRepository(FakeLlmProfileDao.withActiveProfile(), FakeSecretStore()),
            runtime = runtime,
            modelConnectionRepository = modelConnectionRepository
        )

    private fun input(token: String): BackgroundGenerationInput =
        BackgroundGenerationInput(
            spaceId = "default-space",
            sessionId = "session-1",
            userMessageId = "user-1",
            userText = "Explain factoring",
            token = LlmGenerationToken(token),
            capabilities = LlmCapabilities()
        )

    private fun policy() = LlmSessionPolicyContext(
        actionType = "probe",
        studentRole = "probing_student",
        knowledgePoint = "factoring",
        difficulty = 0.7f,
        processSummary = "要求学习者说明依据",
        evaluationCorrectness = 0.4f,
        userEmotion = "neutral",
        correctionTiming = "immediate"
    )
}

private class FakeBackgroundJobDao : BackgroundJobDao {
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
        jobs.values
            .filter { it.kind in setOf("Generation", "Initiative") && it.status in statuses }
            .sortedBy { it.createdAtEpochMillis }

    override suspend fun listGenerationBySession(sessionId: String): List<BackgroundJobEntity> =
        jobs.values
            .filter { it.kind in setOf("Generation", "Initiative") && it.sessionId == sessionId }
            .sortedBy { it.createdAtEpochMillis }

    fun forceStatus(id: String, status: String, startedAtEpochMillis: Long? = null) {
        jobs[id]?.let { existing ->
            jobs[id] = existing.copy(status = status, startedAtEpochMillis = startedAtEpochMillis)
        }
    }

    fun forceUpsert(job: BackgroundJobEntity) {
        jobs[job.id] = job
    }
}

private class FakeSessionDao : SessionDao {
    private val sessions = linkedMapOf(
        "session-1" to SessionEntity(
            id = "session-1",
            spaceId = "default-space",
            title = "Session",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
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

    override suspend fun updateSessionSettingsModelBinding(
        sessionId: String,
        modelBindingId: String
    ): Int = 0

    fun setSpace(sessionId: String, spaceId: String) {
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(spaceId = spaceId) }
    }
}

private class FakeMessageDao : MessageDao {
    private val messages = linkedMapOf<String, MessageEntity>()

    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int =
        if (messages.remove(id) == null) 0 else 1
}

private class FakeMessageAttachmentDao : MessageAttachmentDao {
    override suspend fun insert(attachment: MessageAttachmentEntity) = Unit

    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> = emptyList()

    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> = emptyList()

    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class FakeMessageQuoteDao : MessageQuoteDao {
    override suspend fun insert(quote: MessageQuoteEntity) = Unit

    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? = null

    override suspend fun deleteByMessageId(messageId: String): Int = 0
}

private class FakeLlmProfileDao : LlmProfileDao {
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
        fun withActiveProfile(): FakeLlmProfileDao =
            FakeLlmProfileDao().also { dao ->
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

private class FakeSecretStore : SecretStore {
    override suspend fun put(ref: String, secret: String) = Unit
    override suspend fun get(ref: String): String? = null
    override suspend fun delete(ref: String) = Unit
}

private class StaticRuntime(
    private val result: LlmGenerationResult
) : LlmGenerationRuntime {
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult = result
}

private class CallbackRuntime(
    private val block: () -> LlmGenerationResult
) : LlmGenerationRuntime {
    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult = block()
}

private class CapturingRuntime : LlmGenerationRuntime {
    val requests = mutableListOf<LlmGenerationRequest>()

    override suspend fun generate(request: LlmGenerationRequest): LlmGenerationResult {
        requests += request
        return LlmGenerationResult.Success("Snapshot reply")
    }
}

private class MutableExecutionModelResolver(
    var defaultBindingId: String
) : ExecutionModelResolver {
    val requestedBindingIds = mutableListOf<String?>()

    override suspend fun resolveForExecution(
        sessionId: String,
        requestedBindingId: String?
    ): ExecutionModelConfiguration? {
        requestedBindingIds += requestedBindingId
        return configuration(requestedBindingId ?: defaultBindingId)
    }

    override suspend fun hasNewConfigurationForSession(sessionId: String): Boolean = true

    private fun configuration(bindingId: String): ExecutionModelConfiguration {
        val suffix = bindingId.substringAfterLast('-')
        val connectionId = "connection-$suffix"
        return ExecutionModelConfiguration(
            connection = ProviderConnection(
                id = connectionId,
                spaceId = "default-space",
                name = "Connection $suffix",
                protocol = ModelProtocol.OpenAiCompatible,
                baseUrl = "https://provider-$suffix.example/v1",
                secretRef = "secret-$suffix"
            ),
            binding = ModelBinding(
                id = bindingId,
                spaceId = "default-space",
                connectionId = connectionId,
                modelId = "model-$suffix",
                displayName = "Model $suffix",
                isDefault = bindingId == defaultBindingId
            )
        )
    }
}
