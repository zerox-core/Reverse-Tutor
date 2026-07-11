package com.reversetutor.core.domain

import com.reversetutor.core.model.SyncEnvelope

interface SyncTransport {
    fun push(envelope: SyncEnvelope): SyncPushResult
}

sealed interface SyncPushResult {
    data class Accepted(val remoteRevision: Long) : SyncPushResult

    data class Rejected(
        val errorCode: String,
        val retryable: Boolean = false
    ) : SyncPushResult
}

data class SyncItemFailure(
    val envelopeId: String,
    val error: String,
    val retryable: Boolean
)

data class SyncBatchResult(
    val succeeded: List<String>,
    val failed: List<SyncItemFailure>
)

class SyncCoordinator(
    private val repository: SyncRepository,
    private val transport: SyncTransport
) {
    fun pushPending(limit: Int = 100): SyncBatchResult {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<SyncItemFailure>()
        repository.pendingEnvelopes(limit).forEach { envelope ->
            val outcome = runCatching { transport.push(envelope) }
            outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is SyncPushResult.Accepted -> {
                            repository.markSucceeded(envelope.id, result.remoteRevision)
                            succeeded += envelope.id
                        }
                        is SyncPushResult.Rejected -> {
                            repository.markFailed(
                                envelope.id,
                                result.errorCode,
                                result.retryable
                            )
                            failed += SyncItemFailure(
                                envelope.id,
                                result.errorCode,
                                result.retryable
                            )
                        }
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: error::class.java.simpleName
                    repository.markFailed(envelope.id, message, retryable = true)
                    failed += SyncItemFailure(envelope.id, message, retryable = true)
                }
            )
        }
        return SyncBatchResult(succeeded, failed)
    }
}
