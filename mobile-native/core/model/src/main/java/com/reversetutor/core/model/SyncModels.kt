package com.reversetutor.core.model

data class SyncEnvelope(
    val id: String,
    val spaceId: String,
    val entityId: String,
    val entityType: String,
    val ownerId: String,
    val deviceId: String,
    val revision: Long,
    val idempotencyKey: String,
    val ownership: SyncOwnership,
    val operation: SyncOperation = SyncOperation.Upsert,
    val payload: String? = null,
    val updatedAtEpochMillis: Long = 0L,
    val deletedAtEpochMillis: Long? = null
)

enum class SyncOwnership {
    Local,
    Server,
    Shared
}

enum class SyncOperation {
    Upsert,
    Delete
}

data class SyncCursor(
    val id: String,
    val spaceId: String,
    val entityType: String,
    val cursor: String? = null,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L
)

data class SyncConflict(
    val id: String,
    val spaceId: String,
    val entityId: String,
    val entityType: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localPayload: String? = null,
    val remotePayload: String? = null,
    val state: SyncConflictState = SyncConflictState.Pending,
    val resolution: SyncConflictResolution? = null,
    val createdAtEpochMillis: Long = 0L,
    val resolvedAtEpochMillis: Long? = null
)

enum class SyncConflictState {
    Pending,
    Resolved,
    Dismissed
}

enum class SyncConflictResolution {
    KeepLocal,
    KeepRemote
}
