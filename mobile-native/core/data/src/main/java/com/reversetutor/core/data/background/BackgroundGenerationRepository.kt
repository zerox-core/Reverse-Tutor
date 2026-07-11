package com.reversetutor.core.data.background

import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.data.local.dao.BackgroundJobDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageAttachment

class BackgroundGenerationRepository(
    private val backgroundJobDao: BackgroundJobDao,
    private val sessionDao: SessionDao,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    runtime: LlmGenerationRuntime,
    private val modelConnectionRepository: ExecutionModelResolver? = null
) {
    private val chatGenerationRepository = ChatGenerationRepository(
        messageRepository = messageRepository,
        llmProfileRepository = llmProfileRepository,
        runtime = runtime,
        modelConnectionRepository = modelConnectionRepository
    )

    suspend fun enqueueGenerationJob(
        input: BackgroundGenerationInput,
        nowEpochMillis: Long,
        jobId: String = "generation-${input.userMessageId}-${input.token.value}"
    ): BackgroundGenerationJob {
        val modelBindingId = snapshotModelBindingId(input)
        val job = BackgroundJobEntity(
            id = jobId,
            spaceId = input.spaceId,
            kind = GenerationKind,
            status = BackgroundJobStatus.Queued.name,
            createdAtEpochMillis = nowEpochMillis,
            sessionId = input.sessionId,
            userMessageId = input.userMessageId,
            userText = input.userText,
            generationToken = input.token.value,
            modelBindingId = modelBindingId,
            quoteExcerpt = input.quoteExcerpt,
            imageAttachmentsPayload = input.imageAttachments.toAttachmentPayload(),
            contextEvidencePayload = input.contextEvidence.toEvidencePayload()
        )
        backgroundJobDao.upsert(job)
        return job.toGenerationJob() ?: error("Persisted generation job is invalid")
    }

    suspend fun getJob(jobId: String): BackgroundGenerationJob? =
        backgroundJobDao.getById(jobId)?.toGenerationJob()

    suspend fun recoverInterruptedGenerationJobs(nowEpochMillis: Long): List<BackgroundGenerationJob> {
        val runnable = backgroundJobDao.listGenerationByStatuses(
            listOf(BackgroundJobStatus.Queued.name, BackgroundJobStatus.Running.name)
        )
        return runnable.mapNotNull { entity ->
            val recovered = if (entity.status == BackgroundJobStatus.Running.name) {
                entity.copy(
                    status = BackgroundJobStatus.Queued.name,
                    startedAtEpochMillis = null,
                    errorMessage = "Recovered after interrupted background execution at $nowEpochMillis"
                )
            } else {
                entity
            }
            if (recovered !== entity) {
                backgroundJobDao.upsert(recovered)
            }
            recovered.toGenerationJob()
        }
    }

    suspend fun cancelSessionGenerationJobs(
        sessionId: String,
        nowEpochMillis: Long
    ): Int {
        val active = backgroundJobDao.listGenerationBySession(sessionId)
            .filter { it.status in ActiveStatuses }
        active.forEach { entity ->
            backgroundJobDao.upsert(
                entity.copy(
                    status = BackgroundJobStatus.Cancelled.name,
                    completedAtEpochMillis = nowEpochMillis,
                    errorMessage = "Generation cancelled for session"
                )
            )
        }
        return active.size
    }

    suspend fun runGenerationJob(
        jobId: String,
        nowEpochMillis: Long
    ): BackgroundGenerationOutcome {
        val stored = backgroundJobDao.getById(jobId)
            ?: return BackgroundGenerationOutcome.MissingJob
        val job = stored.toGenerationJob()
            ?: return discard(stored, nowEpochMillis, InvalidJobReason)

        if (job.status == BackgroundJobStatus.Cancelled) {
            return BackgroundGenerationOutcome.Cancelled
        }
        if (job.status == BackgroundJobStatus.Completed) {
            return BackgroundGenerationOutcome.Completed("assistant-${job.token.value}")
        }
        if (job.status == BackgroundJobStatus.Failed) {
            return BackgroundGenerationOutcome.Failed(job.errorMessage ?: "Generation failed")
        }
        if (job.status == BackgroundJobStatus.Discarded) {
            return BackgroundGenerationOutcome.Discarded(job.errorMessage ?: "Generation was discarded")
        }

        val running = stored.copy(
            status = BackgroundJobStatus.Running.name,
            startedAtEpochMillis = nowEpochMillis,
            errorMessage = null
        )
        backgroundJobDao.upsert(running)

        if (!isSessionAvailable(job.sessionId)) {
            return discard(running, nowEpochMillis, SessionUnavailableReason)
        }
        if (!isGenerationTokenCurrent(job)) {
            return discard(running, nowEpochMillis, StaleTokenReason)
        }

        val outcome = chatGenerationRepository.generateReply(
            input = ChatGenerationInput(
                sessionId = job.sessionId,
                userMessageId = job.userMessageId,
                userText = job.userText,
                token = job.token,
                modelBindingId = job.modelBindingId,
                capabilities = job.capabilities,
                quoteExcerpt = job.quoteExcerpt,
                imageAttachments = job.imageAttachments,
                contextEvidence = job.contextEvidence
            ),
            nowEpochMillis = nowEpochMillis,
            isTokenCurrent = { it == job.token },
            canPersistResult = {
                isSessionAvailable(job.sessionId) && isGenerationTokenCurrent(job)
            }
        )

        return when (outcome) {
            is ChatGenerationOutcome.Generated -> {
                backgroundJobDao.upsert(
                    running.copy(
                        status = BackgroundJobStatus.Completed.name,
                        completedAtEpochMillis = nowEpochMillis
                    )
                )
                BackgroundGenerationOutcome.Completed(outcome.assistantMessageId)
            }
            is ChatGenerationOutcome.ProviderFailed -> fail(running, nowEpochMillis, outcome.message)
            ChatGenerationOutcome.NoModelConfigured -> fail(running, nowEpochMillis, "No model configured")
            ChatGenerationOutcome.UnsupportedVision -> fail(running, nowEpochMillis, "Vision input unsupported")
            ChatGenerationOutcome.BlankPrompt -> fail(running, nowEpochMillis, "Blank prompt")
            ChatGenerationOutcome.Stale -> discard(running, nowEpochMillis, currentDiscardReason(job))
        }
    }

    private suspend fun snapshotModelBindingId(input: BackgroundGenerationInput): String? {
        val requested = input.modelBindingId.normalizedId()
        if (requested != null) return requested
        val sessionBinding = sessionDao.getById(input.sessionId)?.modelBindingId.normalizedId()
        if (sessionBinding != null) return sessionBinding
        return modelConnectionRepository
            ?.resolveForExecution(input.sessionId)
            ?.binding
            ?.id
    }

    private suspend fun isSessionAvailable(sessionId: String): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        return !session.archived
    }

    private suspend fun isGenerationTokenCurrent(job: BackgroundGenerationJob): Boolean {
        val stored = backgroundJobDao.getById(job.id) ?: return false
        return stored.generationToken == job.token.value &&
            stored.status in ActiveStatuses
    }

    private suspend fun currentDiscardReason(job: BackgroundGenerationJob): String =
        when {
            !isSessionAvailable(job.sessionId) -> SessionUnavailableReason
            !isGenerationTokenCurrent(job) -> StaleTokenReason
            else -> "Generation was discarded"
        }

    private suspend fun fail(
        entity: BackgroundJobEntity,
        nowEpochMillis: Long,
        message: String
    ): BackgroundGenerationOutcome {
        backgroundJobDao.upsert(
            entity.copy(
                status = BackgroundJobStatus.Failed.name,
                completedAtEpochMillis = nowEpochMillis,
                errorMessage = message
            )
        )
        return BackgroundGenerationOutcome.Failed(message)
    }

    private suspend fun discard(
        entity: BackgroundJobEntity,
        nowEpochMillis: Long,
        reason: String
    ): BackgroundGenerationOutcome {
        backgroundJobDao.upsert(
            entity.copy(
                status = BackgroundJobStatus.Discarded.name,
                completedAtEpochMillis = nowEpochMillis,
                errorMessage = reason
            )
        )
        return BackgroundGenerationOutcome.Discarded(reason)
    }

    private fun BackgroundJobEntity.toGenerationJob(): BackgroundGenerationJob? {
        if (kind != GenerationKind) return null
        val session = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val messageId = userMessageId?.takeIf { it.isNotBlank() } ?: return null
        val text = userText ?: return null
        val tokenValue = generationToken?.takeIf { it.isNotBlank() } ?: return null
        return BackgroundGenerationJob(
            id = id,
            spaceId = spaceId,
            sessionId = session,
            userMessageId = messageId,
            userText = text,
            token = LlmGenerationToken(tokenValue),
            modelBindingId = modelBindingId,
            status = runCatching { BackgroundJobStatus.valueOf(status) }
                .getOrDefault(BackgroundJobStatus.Failed),
            createdAtEpochMillis = createdAtEpochMillis,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            errorMessage = errorMessage,
            quoteExcerpt = quoteExcerpt,
            imageAttachments = imageAttachmentsPayload.toImageAttachments(),
            contextEvidence = contextEvidencePayload.toContextEvidence()
        )
    }

    private companion object {
        const val GenerationKind = "Generation"
        const val InvalidJobReason = "Invalid generation job"
        const val SessionUnavailableReason = "Session is unavailable"
        const val StaleTokenReason = "Generation token is stale"
        val ActiveStatuses = setOf(BackgroundJobStatus.Queued.name, BackgroundJobStatus.Running.name)
    }
}

data class BackgroundGenerationInput(
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: LlmGenerationToken,
    val modelBindingId: String? = null,
    val capabilities: LlmCapabilities? = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList()
)

data class BackgroundGenerationJob(
    val id: String,
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: LlmGenerationToken,
    val modelBindingId: String? = null,
    val status: BackgroundJobStatus,
    val createdAtEpochMillis: Long,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val errorMessage: String? = null,
    val capabilities: LlmCapabilities? = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList()
)

sealed interface BackgroundGenerationOutcome {
    data class Completed(val assistantMessageId: String) : BackgroundGenerationOutcome
    data class Failed(val message: String) : BackgroundGenerationOutcome
    data class Discarded(val reason: String) : BackgroundGenerationOutcome
    object Cancelled : BackgroundGenerationOutcome
    object MissingJob : BackgroundGenerationOutcome
}

private fun List<MessageAttachment>.toAttachmentPayload(): String =
    joinToString(separator = "\n") { attachment ->
        listOf(
            attachment.id,
            attachment.spaceId,
            attachment.messageId,
            attachment.name,
            attachment.mimeType.orEmpty(),
            attachment.uri.orEmpty(),
            attachment.sourceId.orEmpty()
        ).joinToString(separator = "\t") { it.encodePayloadField() }
    }

private fun String?.toImageAttachments(): List<MessageAttachment> =
    decodePayloadRows(expectedFields = 7).map { fields ->
        MessageAttachment(
            id = fields[0],
            spaceId = fields[1],
            messageId = fields[2],
            name = fields[3],
            mimeType = fields[4].ifBlank { null },
            uri = fields[5].ifBlank { null },
            sourceId = fields[6].ifBlank { null }
        )
    }

private fun List<LlmContextEvidence>.toEvidencePayload(): String =
    joinToString(separator = "\n") { evidence ->
        listOf(
            evidence.id,
            evidence.title,
            evidence.body,
            evidence.kind,
            evidence.sourceMessageId.orEmpty(),
            evidence.sourceId.orEmpty()
        ).joinToString(separator = "\t") { it.encodePayloadField() }
    }

private fun String?.toContextEvidence(): List<LlmContextEvidence> =
    decodePayloadRows(expectedFields = 6).mapNotNull { fields ->
        LlmContextEvidence(
            id = fields[0],
            title = fields[1],
            body = fields[2],
            kind = fields[3],
            sourceMessageId = fields[4].ifBlank { null },
            sourceId = fields[5].ifBlank { null }
        ).normalized()
    }

private fun String?.decodePayloadRows(expectedFields: Int): List<List<String>> {
    val payload = this?.takeIf { it.isNotBlank() } ?: return emptyList()
    return payload.lineSequence()
        .map { row -> row.split('\t').map { it.decodePayloadField() } }
        .filter { it.size == expectedFields }
        .toList()
}

private fun String.encodePayloadField(): String =
    buildString {
        this@encodePayloadField.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }

private fun String.decodePayloadField(): String =
    buildString {
        var escaping = false
        this@decodePayloadField.forEach { char ->
            if (escaping) {
                append(
                    when (char) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> char
                    }
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                append(char)
            }
        }
        if (escaping) {
            append('\\')
        }
    }

private fun String?.normalizedId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
