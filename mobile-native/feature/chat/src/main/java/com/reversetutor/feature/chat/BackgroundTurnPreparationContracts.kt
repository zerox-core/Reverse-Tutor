package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageAttachment

/**
 * Compose-free port for preparing a background turn after one user message is persisted.
 * The port assembles bounded context, produces a study-policy snapshot, enqueues exactly
 * one background generation job, and returns a queued contract projection. It never
 * calls ChatGenerationRepository, Worker, or any message-write API directly.
 */
fun interface BackgroundTurnPreparationPort {
    suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest): BackgroundTurnPreparationResult

    data object Unavailable : BackgroundTurnPreparationPort {
        override suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest) =
            BackgroundTurnPreparationResult.Unavailable
    }
}

/**
 * Input to background turn preparation. Only [MessageAttachment] is imported from core:model;
 * no Repository, DAO, Entity, Database, protocol DTO, or Compose type.
 */
data class BackgroundTurnPreparationRequest(
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: String,
    val quoteExcerpt: String?,
    val imageAttachments: List<MessageAttachment>,
    val sessionSnapshot: NewSessionConfiguration?
)

/**
 * Result of background turn preparation.
 * - [Queued]: job enqueued, contract projected as LOADING.
 * - [BlankInput]: user text was blank.
 * - [SessionUnavailable]: session was deleted.
 * - [Unavailable]: port not wired (e.g. missing repository).
 * - [Failed]: unexpected error during preparation.
 */
sealed interface BackgroundTurnPreparationResult {
    data class Queued(val jobId: String, val contract: SessionConversationContract) : BackgroundTurnPreparationResult
    data object BlankInput : BackgroundTurnPreparationResult
    data object SessionUnavailable : BackgroundTurnPreparationResult
    data object Unavailable : BackgroundTurnPreparationResult
    data object Failed : BackgroundTurnPreparationResult
}
