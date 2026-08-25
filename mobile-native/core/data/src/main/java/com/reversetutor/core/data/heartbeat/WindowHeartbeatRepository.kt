package com.reversetutor.core.data.heartbeat

import com.reversetutor.core.data.local.dao.WindowHeartbeatDao
import com.reversetutor.core.data.local.entity.WindowHeartbeatEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.EnableWindowHeartbeatCommand
import com.reversetutor.core.domain.HeartbeatScheduleContract
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.domain.WindowTopologyPolicy
import java.util.UUID

/**
 * Window heartbeat repository. Exposes only domain-safe `core:domain` contracts.
 *
 * A root window is heartbeat-enabled by default; a child starts disabled and
 * becomes enabled only after an explicit enable command. A heartbeat schedule is
 * keyed by its own window and is never transferred by a merge, delete, or parent
 * change.
 */
class WindowHeartbeatRepository(
    private val dao: WindowHeartbeatDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {

    suspend fun heartbeatFor(window: WindowRef, spaceId: String = defaultSpaceId): HeartbeatScheduleContract {
        val stored = dao.getHeartbeat(window.id)
        if (stored != null) return stored.toDomain()
        val fresh = defaultFor(window)
        dao.upsertHeartbeat(fresh.toEntity(window.id, spaceId))
        return fresh
    }

    suspend fun enableChildHeartbeat(
        window: WindowRef,
        command: EnableWindowHeartbeatCommand,
        spaceId: String = defaultSpaceId
    ): HeartbeatScheduleContract {
        val current = dao.getHeartbeat(window.id)?.toDomain() ?: defaultFor(window)
        val enabled = if (window.kind == WindowKind.CHILD && current.windowId == command.windowId) {
            current.copy(enabled = true)
        } else {
            current
        }
        dao.upsertHeartbeat(enabled.toEntity(window.id, spaceId))
        return enabled
    }

    suspend fun deleteHeartbeat(windowId: String) {
        dao.deleteHeartbeat(windowId)
    }

    suspend fun scheduleFor(windowId: String): HeartbeatScheduleContract? = dao.getHeartbeat(windowId)?.toDomain()

    private fun defaultFor(window: WindowRef): HeartbeatScheduleContract = HeartbeatScheduleContract(
        windowId = window.id,
        enabled = WindowTopologyPolicy.defaultHeartbeatState(window.kind) != WindowHeartbeatState.ChildDisabled,
        minCooldownMillis = 0L
    )
}

private fun HeartbeatScheduleContract.toEntity(windowId: String, spaceId: String) = WindowHeartbeatEntity(
    windowId = windowId,
    spaceId = spaceId,
    enabled = enabled,
    minCooldownMillis = minCooldownMillis,
    cooldownUntilEpochMillis = 0L,
    pendingJobId = null,
    updatedAtEpochMillis = 0L
)

private fun WindowHeartbeatEntity.toDomain() = HeartbeatScheduleContract(
    windowId = windowId,
    enabled = enabled,
    minCooldownMillis = minCooldownMillis
)
