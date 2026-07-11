package com.reversetutor.core.domain

import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {
    @Test
    fun oneFailedEnvelopeDoesNotBlockOtherItems() {
        val repository = FakeSyncRepository(
            pending = listOf(envelope("fail"), envelope("ok"))
        )
        val transport = object : SyncTransport {
            override fun push(envelope: SyncEnvelope): SyncPushResult {
                if (envelope.id == "fail") error("network failure")
                return SyncPushResult.Accepted(remoteRevision = 2L)
            }
        }

        val result = SyncCoordinator(repository, transport).pushPending()

        assertEquals(1, result.succeeded.size)
        assertEquals(1, result.failed.size)
        assertEquals(listOf("ok"), repository.succeeded)
        assertEquals(listOf("fail"), repository.failed)
        assertTrue(result.failed.single().retryable)
    }

    private fun envelope(id: String) = SyncEnvelope(
        id = id,
        spaceId = "space-1",
        entityId = "plan-$id",
        entityType = "study_plan",
        ownerId = "owner-1",
        deviceId = "device-1",
        revision = 1L,
        idempotencyKey = "idem-$id",
        ownership = SyncOwnership.Shared
    )
}

private class FakeSyncRepository(
    private val pending: List<SyncEnvelope>
) : SyncRepository {
    val succeeded = mutableListOf<String>()
    val failed = mutableListOf<String>()

    override fun pendingEnvelopes(limit: Int): List<SyncEnvelope> = pending.take(limit)

    override fun markSucceeded(envelopeId: String, remoteRevision: Long) {
        succeeded += envelopeId
    }

    override fun markFailed(envelopeId: String, error: String, retryable: Boolean) {
        failed += envelopeId
    }
}
