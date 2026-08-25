package com.reversetutor.core.data.window

import com.reversetutor.core.data.local.dao.WindowTopologyDao
import com.reversetutor.core.data.local.entity.MergeCommitEntity
import com.reversetutor.core.data.local.entity.WindowDeltaEntity
import com.reversetutor.core.data.local.entity.WindowEntity
import com.reversetutor.core.data.local.entity.WindowSnapshotEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.BranchDeletionEffect
import com.reversetutor.core.domain.MergeCommit
import com.reversetutor.core.domain.MergeDenial
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowMergeDecision
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.domain.WindowSnapshotRef
import com.reversetutor.core.domain.WindowTopologyPolicy

/** Outcome of a merge proposal against the persisted topology. */
sealed interface MergeResult {
    data class Committed(val commit: MergeCommit) : MergeResult
    data class Rejected(val reason: MergeDenial) : MergeResult
    data object NotFound : MergeResult
}

/**
 * Window topology repository. Exposes only domain-safe `core:domain` contracts
 * (never entities or DAOs) and enforces the pure [WindowTopologyPolicy]: a child
 * reads a fork-time snapshot only, only a direct child may merge into its direct
 * parent, a merge is idempotent, and branch deletion never rewinds a committed
 * parent merge or a global learning receipt.
 */
class WindowTopologyRepository(
    private val dao: WindowTopologyDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {

    suspend fun createRootWindow(
        sessionId: String,
        kind: WindowKind,
        spaceId: String = defaultSpaceId,
        nowEpochMillis: Long
    ): WindowRef {
        dao.insertWindow(WindowEntity(sessionId, spaceId, sessionId, null, kind.name, nowEpochMillis))
        return dao.getWindow(sessionId)!!.toDomain()
    }

    suspend fun getWindow(sessionId: String): WindowRef? = dao.getWindow(sessionId)?.toDomain()

    suspend fun listWindows(spaceId: String = defaultSpaceId): List<WindowRef> =
        dao.listWindows(spaceId).map { it.toDomain() }

    suspend fun forkChild(
        childId: String,
        parentId: String,
        ancestorRevision: Long,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId
    ): WindowSnapshotRef {
        val parent = dao.getWindow(parentId) ?: error("parent window $parentId not found")
        dao.insertWindow(WindowEntity(childId, spaceId, parent.rootId, parentId, WindowKind.CHILD.name, nowEpochMillis))
        val snapshot = WindowSnapshotRef(ancestorRevision, nowEpochMillis)
        dao.upsertSnapshot(WindowSnapshotEntity(childId, ancestorRevision, nowEpochMillis))
        return snapshot
    }

    suspend fun getSnapshot(windowId: String): WindowSnapshotRef? = dao.getSnapshot(windowId)?.toDomain()

    suspend fun commitDelta(windowId: String, deltaId: String, payloadHandle: String, sourceRevision: Long) {
        dao.insertDelta(WindowDeltaEntity("$windowId::$deltaId", windowId, deltaId, payloadHandle, sourceRevision))
    }

    suspend fun merge(childId: String, parentId: String, deltaId: String, sourceRevision: Long): MergeResult {
        val childEntity = dao.getWindow(childId) ?: return MergeResult.NotFound
        val parentEntity = dao.getWindow(parentId) ?: return MergeResult.NotFound
        return when (val decision = WindowTopologyPolicy.mergeDecision(
            childEntity.toDomain(),
            parentEntity.toDomain(),
            deltaId,
            sourceRevision
        )) {
            is WindowMergeDecision.Allowed -> {
                val existing = dao.getMergeCommit(decision.commit.id)
                val commit = existing?.toDomain() ?: run {
                    dao.insertMergeCommit(decision.commit.toEntity(childEntity.spaceId))
                    decision.commit
                }
                MergeResult.Committed(commit)
            }
            is WindowMergeDecision.Denied -> MergeResult.Rejected(decision.reason)
        }
    }

    /** Delete only the branch's local rows; committed parent merges and global receipts remain. */
    suspend fun deleteBranch(childId: String): BranchDeletionEffect {
        dao.deleteDeltas(childId)
        dao.deleteSnapshots(childId)
        dao.deleteWindow(childId)
        return WindowTopologyPolicy.branchDeletionEffect(childId)
    }
}

private fun WindowEntity.toDomain() =
    WindowRef(id = sessionId, rootId = rootId, parentId = parentId, kind = WindowKind.valueOf(kind))

private fun WindowSnapshotEntity.toDomain() = WindowSnapshotRef(ancestorRevision, forkedAtEpochMillis)

private fun MergeCommitEntity.toDomain() = MergeCommit(id, childId, parentId, deltaId, sourceRevision)

private fun MergeCommit.toEntity(spaceId: String) =
    MergeCommitEntity(id, childId, parentId, deltaId, sourceRevision, spaceId)
