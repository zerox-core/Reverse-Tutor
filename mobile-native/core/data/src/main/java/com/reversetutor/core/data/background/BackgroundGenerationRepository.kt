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
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmSessionPolicyContext
import com.reversetutor.core.llm.LlmTurnPlan
import com.reversetutor.core.llm.LlmWindowContext
import com.reversetutor.core.llm.StructuredTurnOutcome
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageAttachment

class BackgroundGenerationRepository(
    private val backgroundJobDao: BackgroundJobDao,
    private val sessionDao: SessionDao,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    runtime: LlmGenerationRuntime,
    private val modelConnectionRepository: ExecutionModelResolver? = null,
    private val partialStore: GenerationPartialStore = GenerationPartialStore()
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
        val persistedEvidence = input.contextEvidence + input.turnPlan.toGuidedPlanEvidence()
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
            contextEvidencePayload = persistedEvidence.toEvidencePayload(),
            sessionPolicyPayload = input.sessionPolicy.toPayload(),
            assistantTurnEnvelopePayload = input.assistantTurnEnvelope.toEnvelopePayload()
        )
        backgroundJobDao.upsert(job)
        return job.toGenerationJob() ?: error("Persisted generation job is invalid")
    }

    /**
     * Enqueues a plan-driven initiative (heartbeat) job. It carries NO user
     * message and NO user input text; generation is driven by the immutable
     * [InitiativePlan] + envelope. The Worker remains the sole Provider and
     * assistant writer. `jobId` is idempotent: re-enqueueing the same id updates
     * the same row.
     */
    suspend fun enqueueInitiativeJob(
        input: BackgroundInitiativeInput,
        nowEpochMillis: Long,
        jobId: String = "initiative-${input.targetWindowId}-${input.initiativeSource}"
    ): BackgroundGenerationJob {
        val spaceId = input.spaceId.trim()
        val targetWindowId = input.targetWindowId.trim()
        val envelopeWindowId = input.envelope.window.windowId.trim()
        require(spaceId.isNotEmpty()) { "Initiative spaceId must not be blank" }
        require(targetWindowId.isNotEmpty()) { "Initiative targetWindowId must not be blank" }
        require(targetWindowId == envelopeWindowId) {
            "Initiative target window must match its immutable envelope window"
        }
        val existingEntity = backgroundJobDao.getById(jobId)
        require(existingEntity == null || existingEntity.kind == InitiativeKind) {
            "Initiative job id collides with a non-initiative job"
        }
        val existing = existingEntity?.toGenerationJob()
        if (existing != null && existing.status in setOf(BackgroundJobStatus.Running, BackgroundJobStatus.Completed)) {
            return existing
        }
        val tokenValue = "initiative-$jobId"
        val job = BackgroundJobEntity(
            id = jobId,
            spaceId = spaceId,
            kind = InitiativeKind,
            status = BackgroundJobStatus.Queued.name,
            createdAtEpochMillis = nowEpochMillis,
            sessionId = targetWindowId,
            userMessageId = null,
            userText = null,
            generationToken = tokenValue,
            assistantTurnEnvelopePayload = input.envelope.toEnvelopePayload()
        )
        backgroundJobDao.upsert(job)
        return job.toGenerationJob() ?: error("Persisted initiative job is invalid")
    }

    suspend fun getJob(jobId: String): BackgroundGenerationJob? =
        backgroundJobDao.getById(jobId)?.toGenerationJob()

    /**
     * Reattaches a reopened chat screen to its latest still-active job. This is
     * read-only: startup recovery remains responsible for changing an
     * interrupted Running row back to Queued and scheduling its Worker.
     */
    suspend fun findActiveJobForSession(sessionId: String): BackgroundGenerationJob? =
        backgroundJobDao.listGenerationBySession(sessionId.trim())
            .asReversed()
            .firstOrNull { it.status in ActiveStatuses }
            ?.toGenerationJob()

    /**
     * Returns only the bounded, structured plans of this window's completed
     * user turns.  This is deliberately not a general job-history API: caller
     * code never receives user text, assistant text, context evidence, model
     * bindings, or Provider errors.
     */
    suspend fun listCompletedTurnPlans(
        sessionId: String,
        limit: Int = RecentTurnPlanLimit
    ): List<CompletedTurnPlanSnapshot> =
        backgroundJobDao.listGenerationBySession(sessionId.trim())
            .asSequence()
            .filter { entity ->
                entity.kind == GenerationKind &&
                    entity.status == BackgroundJobStatus.Completed.name
            }
            .mapNotNull { entity ->
                val plan = entity.toGenerationJob()?.turnPlan?.normalized() ?: return@mapNotNull null
                val completedAt = entity.completedAtEpochMillis ?: return@mapNotNull null
                CompletedTurnPlanSnapshot(
                    jobId = entity.id,
                    completedAtEpochMillis = completedAt,
                    turnPlan = plan
                )
            }
            .toList()
            .takeLast(limit.coerceIn(0, RecentTurnPlanLimit))

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
            return BackgroundGenerationOutcome.Completed(
                assistantMessageId = "assistant-${job.token.value}",
                structuredOutcome = buildStructuredOutcome(job)
            )
        }
        if (job.status == BackgroundJobStatus.Failed) {
            return BackgroundGenerationOutcome.Failed(job.errorMessage ?: "Generation failed")
        }
        if (job.status == BackgroundJobStatus.Discarded) {
            return BackgroundGenerationOutcome.Discarded(job.errorMessage ?: "Generation was discarded")
        }

        val claimed = backgroundJobDao.claimQueued(
            id = job.id,
            queuedStatus = BackgroundJobStatus.Queued.name,
            runningStatus = BackgroundJobStatus.Running.name,
            startedAtEpochMillis = nowEpochMillis
        )
        if (claimed == 0) {
            val latest = backgroundJobDao.getById(job.id)?.toGenerationJob()
                ?: return BackgroundGenerationOutcome.MissingJob
            return when (latest.status) {
                BackgroundJobStatus.Cancelled -> BackgroundGenerationOutcome.Cancelled
                BackgroundJobStatus.Completed -> BackgroundGenerationOutcome.Completed(
                    assistantMessageId = "assistant-${latest.token.value}",
                    structuredOutcome = buildStructuredOutcome(latest)
                )
                BackgroundJobStatus.Failed -> BackgroundGenerationOutcome.Failed(
                    latest.errorMessage ?: "Generation failed"
                )
                BackgroundJobStatus.Discarded -> BackgroundGenerationOutcome.Discarded(
                    latest.errorMessage ?: "Generation was discarded"
                )
                BackgroundJobStatus.Queued,
                BackgroundJobStatus.Running -> BackgroundGenerationOutcome.AlreadyRunning
            }
        }
        val running = stored.copy(
            status = BackgroundJobStatus.Running.name,
            startedAtEpochMillis = nowEpochMillis,
            errorMessage = null
        )

        if (!isSessionAvailable(job)) {
            return discard(running, nowEpochMillis, SessionUnavailableReason)
        }
        if (!isGenerationTokenCurrent(job)) {
            return discard(running, nowEpochMillis, StaleTokenReason)
        }
        val expiry = job.assistantTurnEnvelope?.turnPlan?.expiryEpochMillis ?: 0L
        if (job.kind == InitiativeKind && expiry > 0L && nowEpochMillis >= expiry) {
            return discard(running, nowEpochMillis, InitiativeExpiredReason)
        }

        val outcome = chatGenerationRepository.generateReply(
            input = ChatGenerationInput(
                sessionId = job.sessionId,
                userMessageId = job.userMessageId,
                userText = job.userText,
                token = job.token,
                spaceId = job.spaceId,
                allowPlanDrivenOpening = job.kind == InitiativeKind,
                modelBindingId = job.modelBindingId,
                capabilities = job.capabilities,
                quoteExcerpt = job.quoteExcerpt,
                imageAttachments = job.imageAttachments,
                contextEvidence = job.contextEvidence,
                sessionPolicy = job.sessionPolicy,
                assistantTurnEnvelope = job.assistantTurnEnvelope,
                turnPlan = job.turnPlan
            ),
            nowEpochMillis = nowEpochMillis,
            isTokenCurrent = { it == job.token },
            canPersistResult = {
                isSessionAvailable(job) && isGenerationTokenCurrent(job)
            },
            onChunk = { chunk ->
                partialStore.append(job.id, job.token.value, chunk)
            }
        )

        return when (outcome) {
            is ChatGenerationOutcome.Generated -> {
                partialStore.clear(job.id, job.token.value)
                backgroundJobDao.upsert(
                    running.copy(
                        status = BackgroundJobStatus.Completed.name,
                        completedAtEpochMillis = nowEpochMillis
                    )
                )
                BackgroundGenerationOutcome.Completed(
                    assistantMessageId = outcome.assistantMessageId,
                    structuredOutcome = buildStructuredOutcome(job),
                    replyEnvelope = outcome.replyEnvelope
                )
            }
            is ChatGenerationOutcome.ProviderFailed -> { partialStore.clear(job.id, job.token.value); fail(running, nowEpochMillis, ProviderFailureReason) }
            ChatGenerationOutcome.NoModelConfigured -> { partialStore.clear(job.id, job.token.value); fail(running, nowEpochMillis, "No model configured") }
            ChatGenerationOutcome.UnsupportedVision -> { partialStore.clear(job.id, job.token.value); fail(running, nowEpochMillis, "Vision input unsupported") }
            ChatGenerationOutcome.BlankPrompt -> { partialStore.clear(job.id, job.token.value); fail(running, nowEpochMillis, "Blank prompt") }
            ChatGenerationOutcome.Stale -> { partialStore.clear(job.id, job.token.value); discard(running, nowEpochMillis, currentDiscardReason(job)) }
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

    private suspend fun isSessionAvailable(job: BackgroundGenerationJob): Boolean {
        val session = sessionDao.getById(job.sessionId) ?: return false
        return !session.archived && session.spaceId == job.spaceId
    }

    private suspend fun isGenerationTokenCurrent(job: BackgroundGenerationJob): Boolean {
        val stored = backgroundJobDao.getById(job.id) ?: return false
        return stored.generationToken == job.token.value &&
            stored.status in ActiveStatuses
    }

    private suspend fun currentDiscardReason(job: BackgroundGenerationJob): String =
        when {
            !isSessionAvailable(job) -> SessionUnavailableReason
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
        if (kind != GenerationKind && kind != InitiativeKind) return null
        val session = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val tokenValue = generationToken?.takeIf { it.isNotBlank() } ?: return null
        val persistedEvidence = contextEvidencePayload.toContextEvidence()
        val guidedTurnPlan = persistedEvidence.firstOrNull { it.kind == GuidedPlanEvidenceKind }
            ?.toGuidedTurnPlan()
        return BackgroundGenerationJob(
            id = id,
            kind = kind,
            spaceId = spaceId,
            sessionId = session,
            userMessageId = userMessageId?.takeIf { it.isNotBlank() },
            userText = userText,
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
            contextEvidence = persistedEvidence.filterNot { it.kind == GuidedPlanEvidenceKind },
            sessionPolicy = sessionPolicyPayload.toSessionPolicy(),
            assistantTurnEnvelope = assistantTurnEnvelopePayload.toEnvelope(),
            turnPlan = guidedTurnPlan
        )
    }

    /**
     * Build a bounded structured turn outcome from the persisted envelope/policy.
     * It never carries raw Provider or user text; malformed/null stays empty.
     */
    private fun buildStructuredOutcome(job: BackgroundGenerationJob): StructuredTurnOutcome {
        val plan = job.assistantTurnEnvelope?.turnPlan
        if (job.assistantTurnEnvelope == null || plan == null) return StructuredTurnOutcome.EMPTY
        return StructuredTurnOutcome(
            windowId = job.assistantTurnEnvelope.window.windowId,
            actionType = plan.actionType,
            studentRole = plan.studentRole,
            knowledgePoint = plan.knowledgePoint,
            correctness = 0f,
            depth = 0f,
            evidenceType = "none",
            evidenceStatus = "none",
            processSummary = plan.intent,
            initiativeSource = job.assistantTurnEnvelope.initiativeSource
        ).normalized()
    }

    suspend fun getGenerationPreview(jobId: String, token: LlmGenerationToken): String? =
        partialStore.get(jobId, token.value)

    private companion object {
        const val GenerationKind = "Generation"
        const val InitiativeKind = "Initiative"
        const val InvalidJobReason = "Invalid generation job"
        const val SessionUnavailableReason = "Session is unavailable"
        const val StaleTokenReason = "Generation token is stale"
        const val InitiativeExpiredReason = "Initiative plan expired"
        const val ProviderFailureReason = "background_generation_failed"
        const val RecentTurnPlanLimit = 8
        val ActiveStatuses = setOf(BackgroundJobStatus.Queued.name, BackgroundJobStatus.Running.name)
    }
}

data class BackgroundGenerationInput(
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val allowPlanDrivenOpening: Boolean = false,
    val token: LlmGenerationToken,
    val modelBindingId: String? = null,
    val capabilities: LlmCapabilities? = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList(),
    val sessionPolicy: LlmSessionPolicyContext? = null,
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
    val turnPlan: TurnPlan? = null
)

/**
 * A plan-driven initiative/heartbeat generation. It carries no user message and
 * no user input text; generation is driven by the [LlmAssistantTurnEnvelope]
 * (which projects the [InitiativePlan] into [LlmTurnPlan]).
 */
data class BackgroundInitiativeInput(
    val spaceId: String,
    val targetWindowId: String,
    val initiativeSource: String,
    val envelope: LlmAssistantTurnEnvelope
)

data class BackgroundGenerationJob(
    val id: String,
    val kind: String = "Generation",
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String?,
    val userText: String?,
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
    val contextEvidence: List<LlmContextEvidence> = emptyList(),
    val sessionPolicy: LlmSessionPolicyContext? = null,
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
    val turnPlan: TurnPlan? = null
)

/**
 * Privacy-safe historical plan observation used by the guided-learning
 * coordinator. It intentionally omits every transcript and Provider field.
 */
data class CompletedTurnPlanSnapshot(
    val jobId: String,
    val completedAtEpochMillis: Long,
    val turnPlan: TurnPlan
)

sealed interface BackgroundGenerationOutcome {
    data class Completed(
        val assistantMessageId: String,
        val structuredOutcome: StructuredTurnOutcome = StructuredTurnOutcome.EMPTY,
        /** Ephemeral P3 output; durable artifacts are added only by the P6 slice. */
        val replyEnvelope: LlmAssistantReplyEnvelope? = null
    ) : BackgroundGenerationOutcome
    data class Failed(val message: String) : BackgroundGenerationOutcome
    data class Discarded(val reason: String) : BackgroundGenerationOutcome
    object Cancelled : BackgroundGenerationOutcome
    /** Another Worker owns the queued row; no Provider call or notification is produced. */
    object AlreadyRunning : BackgroundGenerationOutcome
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

private fun LlmSessionPolicyContext?.toPayload(): String? =
    this?.normalized()?.let { policy ->
        listOf(
            policy.actionType,
            policy.studentRole,
            policy.knowledgePoint,
            policy.difficulty.toString(),
            policy.processSummary,
            policy.evaluationCorrectness.toString(),
            policy.userEmotion,
            policy.correctionTiming
        ).joinToString(separator = "\t") { it.encodePayloadField() }
    }

private const val GuidedPlanEvidenceKind = "GuidedTurnPlan"

private fun TurnPlan?.toGuidedPlanEvidence(): List<LlmContextEvidence> {
    val plan = this?.normalized() ?: return emptyList()
    val body = listOf(
        plan.actionType.name,
        plan.secondaryAction?.name.orEmpty(),
        plan.learningObjective,
        plan.conceptKey,
        plan.expectedUserMove,
        plan.responseFormat.name,
        plan.hintLevel.toString(),
        plan.evidenceRequirement.name
    ).joinToString("|") { it.encodePayloadField() }
    return listOf(LlmContextEvidence("guided-turn-plan", "Guided turn plan", body, GuidedPlanEvidenceKind))
}

private fun LlmContextEvidence.toGuidedTurnPlan(): TurnPlan? {
    if (kind != GuidedPlanEvidenceKind) return null
    val fields = body.split('|').map { it.decodePayloadField() }
    if (fields.size != 8) return null
    val action = runCatching { com.reversetutor.core.domain.TeachingAction.valueOf(fields[0]) }.getOrNull()
        ?: return null
    val secondary = fields[1].takeIf { it.isNotBlank() }?.let {
        runCatching { com.reversetutor.core.domain.TeachingAction.valueOf(it) }.getOrNull()
    }
    val format = runCatching { com.reversetutor.core.domain.ResponseFormat.valueOf(fields[5]) }
        .getOrDefault(com.reversetutor.core.domain.ResponseFormat.Plain)
    val evidence = runCatching { com.reversetutor.core.domain.EvidenceRequirement.valueOf(fields[7]) }
        .getOrDefault(com.reversetutor.core.domain.EvidenceRequirement.None)
    return TurnPlan(
        actionType = action,
        secondaryAction = secondary,
        learningObjective = fields[2],
        conceptKey = fields[3],
        expectedUserMove = fields[4],
        responseFormat = format,
        hintLevel = fields[6].toIntOrNull() ?: 0,
        evidenceRequirement = evidence
    ).normalized()
}

private fun String?.toSessionPolicy(): LlmSessionPolicyContext? {
    val fields = this?.split('\t')?.map { it.decodePayloadField() } ?: return null
    if (fields.size != 8) return null
    val difficulty = fields[3].toFloatOrNull() ?: return null
    val evaluationCorrectness = fields[5].toFloatOrNull() ?: return null
    return LlmSessionPolicyContext(
        actionType = fields[0],
        studentRole = fields[1],
        knowledgePoint = fields[2],
        difficulty = difficulty,
        processSummary = fields[4],
        evaluationCorrectness = evaluationCorrectness,
        userEmotion = fields[6],
        correctionTiming = fields[7]
    ).normalized()
}

private fun LlmAssistantTurnEnvelope?.toEnvelopePayload(): String? =
    this?.normalized()?.let { envelope ->
        val window = envelope.window
        val plan = envelope.turnPlan
        val tone = plan?.toneConstraints?.joinToString(";") { it.encodePayloadField() } ?: ""
        listOf(
            window.windowId,
            window.rootId,
            window.parentId.orEmpty(),
            window.windowKind,
            window.forkRevision.toString(),
            plan?.intent.orEmpty(),
            plan?.actionType.orEmpty(),
            plan?.studentRole.orEmpty(),
            plan?.knowledgePoint.orEmpty(),
            plan?.difficulty?.toString().orEmpty(),
            plan?.studyMethod.orEmpty(),
            tone,
            envelope.initiativeSource.orEmpty(),
            plan?.expiryEpochMillis?.toString().orEmpty(),
            plan?.minCooldownMillis?.toString().orEmpty()
        ).joinToString(separator = "\t") { it.encodePayloadField() }
    }

private fun String?.toEnvelope(): LlmAssistantTurnEnvelope? {
    val fields = this?.split('\t')?.map { it.decodePayloadField() } ?: return null
    if (fields.size != 13 && fields.size != 15) return null
    val forkRevision = fields[4].toLongOrNull() ?: return null
    val plan = if (fields[5].isNotEmpty() && fields[6].isNotEmpty() && fields[7].isNotEmpty()) {
        val difficulty = fields[9].toFloatOrNull() ?: return null
        LlmTurnPlan(
            intent = fields[5],
            actionType = fields[6],
            studentRole = fields[7],
            knowledgePoint = fields[8],
            difficulty = difficulty,
            studyMethod = fields[10],
            toneConstraints = fields[11].split(';').filter { it.isNotEmpty() },
            expiryEpochMillis = fields.getOrNull(13)?.toLongOrNull() ?: 0L,
            minCooldownMillis = fields.getOrNull(14)?.toLongOrNull() ?: 0L
        ).normalized()
    } else {
        null
    }
    val window = LlmWindowContext(
        windowId = fields[0],
        rootId = fields[1],
        parentId = fields[2].ifBlank { null },
        windowKind = fields[3],
        forkRevision = forkRevision
    ).normalized() ?: return null
    return LlmAssistantTurnEnvelope(
        window = window,
        turnPlan = plan,
        initiativeSource = fields[12].ifBlank { null }
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
