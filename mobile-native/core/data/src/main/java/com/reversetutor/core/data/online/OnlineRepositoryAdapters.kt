package com.reversetutor.core.data.online

import com.reversetutor.core.domain.ActivityRepository
import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.ReleaseMetadata
import com.reversetutor.core.domain.SyncPushResult
import com.reversetutor.core.domain.SyncTransport
import com.reversetutor.core.domain.UpdateRepository
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.remote.OnlineApi
import com.reversetutor.core.remote.OnlineResult
import com.reversetutor.core.remote.OnlineSyncEntityTypes
import com.reversetutor.core.remote.SyncPushRequest

class OnlineActivityRepository(
    private val api: OnlineApi
) : ActivityRepository {
    override suspend fun listCachedActivities(): List<ActivitySummary> =
        when (val result = api.listActivities()) {
            is OnlineResult.Success -> result.value.map {
                ActivitySummary(
                    id = it.id,
                    title = it.title,
                    revision = it.revision,
                    startsAtEpochMillis = it.startsAtEpochMillis,
                    endsAtEpochMillis = it.endsAtEpochMillis
                )
            }
            is OnlineResult.Failure -> emptyList()
        }
}

class OnlineSyncTransport(
    private val api: OnlineApi
) : SyncTransport {
    override suspend fun push(envelope: SyncEnvelope): SyncPushResult {
        if (envelope.ownership != SyncOwnership.Shared) {
            return SyncPushResult.Rejected(
                errorCode = "ownership_not_syncable",
                retryable = false
            )
        }
        if (envelope.entityType !in AllowedSharedEntityTypes) {
            return SyncPushResult.Rejected(
                errorCode = "entity_type_not_syncable",
                retryable = false
            )
        }
        val response = api.pushSync(
            SyncPushRequest(
                userId = envelope.ownerId,
                deviceId = envelope.deviceId,
                items = listOf(envelope)
            )
        )
        return when (response) {
            is OnlineResult.Failure -> SyncPushResult.Rejected(
                errorCode = response.code,
                retryable = response.retryable
            )
            is OnlineResult.Success -> {
                val result = response.value.items.firstOrNull { it.envelopeId == envelope.id }
                    ?: response.value.items.firstOrNull { it.entityId == envelope.entityId }
                val remoteRevision = result?.remoteRevision
                if (result?.accepted == true && remoteRevision != null) {
                    SyncPushResult.Accepted(remoteRevision)
                } else {
                    SyncPushResult.Rejected(
                        errorCode = result?.errorCode ?: "invalid_sync_response",
                        retryable = result?.retryable ?: false
                    )
                }
            }
        }
    }

    companion object {
        val AllowedSharedEntityTypes: Set<String> = OnlineSyncEntityTypes.Allowed
    }
}

class OnlineUpdateRepository(
    private val api: OnlineApi
) : UpdateRepository {
    override suspend fun latestRelease(): ReleaseMetadata? =
        when (val result = api.latestRelease()) {
            is OnlineResult.Success -> ReleaseMetadata(
                versionName = result.value.versionName,
                versionCode = result.value.versionCode,
                minimumSupportedVersionCode = result.value.minimumSupportedVersionCode,
                downloadUrl = result.value.downloadUrl,
                sha256 = result.value.sha256
            )
            is OnlineResult.Failure -> null
        }
}
