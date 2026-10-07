package com.reversetutor.core.data.window

import com.reversetutor.core.data.local.dao.WindowTopologyDao
import com.reversetutor.core.data.local.entity.MergeCommitEntity
import com.reversetutor.core.data.local.entity.WindowDeltaEntity
import com.reversetutor.core.data.local.entity.WindowEntity
import com.reversetutor.core.data.local.entity.WindowSnapshotEntity
import com.reversetutor.core.domain.BranchDeletionEffect
import com.reversetutor.core.domain.MergeDenial
import com.reversetutor.core.domain.WindowKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowTopologyRepositoryTest {

    @Test
    fun rootSessionCreatesWindowOfTaskRoot() = runBlocking {
        val repo = repository()
        val window = repo.createRootWindow("s1", WindowKind.TASK_ROOT, nowEpochMillis = 0L)
        assertEquals("s1", window.id)
        assertEquals("s1", window.rootId)
        assertNull(window.parentId)
        assertEquals(WindowKind.TASK_ROOT, window.kind)
    }

    @Test
    fun childSnapshotKeepsForkRevisionAfterParentChanges() = runBlocking {
        val repo = repository()
        repo.createRootWindow("p1", WindowKind.LEARNING_ROOT, nowEpochMillis = 0L)
        val snapshot = repo.forkChild("c1", "p1", ancestorRevision = 5L, nowEpochMillis = 100L)
        assertEquals(5L, snapshot.ancestorRevision)
        assertEquals(100L, snapshot.forkedAtEpochMillis)

        repo.commitDelta("p1", "parent-delta", "handle-p", 6L)
        val stored = repo.getSnapshot("c1")
        assertEquals(5L, stored!!.ancestorRevision)
        assertEquals(100L, stored.forkedAtEpochMillis)
    }

    @Test
    fun directParentMergeIsIdempotent() = runBlocking {
        val dao = FakeWindowTopologyDao()
        val repo = repository(dao)
        repo.createRootWindow("p1", WindowKind.LEARNING_ROOT, nowEpochMillis = 0L)
        repo.forkChild("c1", "p1", ancestorRevision = 3L, nowEpochMillis = 10L)

        val first = repo.merge("c1", "p1", "delta-1", 9L)
        val second = repo.merge("c1", "p1", "delta-1", 9L)

        assertTrue(first is MergeResult.Committed)
        assertTrue(second is MergeResult.Committed)
        val a = (first as MergeResult.Committed).commit
        val b = (second as MergeResult.Committed).commit
        assertEquals(a, b)
        assertEquals(1, dao.commits.size)
    }

    @Test
    fun siblingMergeIsRejected() = runBlocking {
        val dao = FakeWindowTopologyDao()
        val repo = repository(dao)
        repo.createRootWindow("p1", WindowKind.LEARNING_ROOT, nowEpochMillis = 0L)
        repo.forkChild("c1", "p1", ancestorRevision = 3L, nowEpochMillis = 10L)
        repo.forkChild("c2", "p1", ancestorRevision = 3L, nowEpochMillis = 11L)

        val result = repo.merge("c1", "c2", "delta-1", 9L)
        assertTrue(result is MergeResult.Rejected)
        assertEquals(MergeDenial.SIBLING_TARGET, (result as MergeResult.Rejected).reason)
        assertEquals(0, dao.commits.size)
    }

    @Test
    fun deleteChildKeepsCommittedParentMergeReceipt() = runBlocking {
        val dao = FakeWindowTopologyDao()
        val repo = repository(dao)
        repo.createRootWindow("p1", WindowKind.LEARNING_ROOT, nowEpochMillis = 0L)
        repo.forkChild("c1", "p1", ancestorRevision = 3L, nowEpochMillis = 10L)
        val committed = repo.merge("c1", "p1", "delta-1", 9L)
        val commit = (committed as MergeResult.Committed).commit

        val effect: BranchDeletionEffect = repo.deleteBranch("c1")
        assertTrue(effect.preservesCommittedParentMerges)
        assertTrue(effect.preservesGlobalLearningReceipts)
        assertNull(dao.getWindow("c1"))
        assertEquals(commit, dao.getMergeCommit(commit.id)?.toDomainCompat())
    }

    private fun repository(dao: WindowTopologyDao = FakeWindowTopologyDao()) =
        WindowTopologyRepository(dao, defaultSpaceId = "default-space")

    private class FakeWindowTopologyDao : WindowTopologyDao {
        val windows = mutableMapOf<String, WindowEntity>()
        val snapshots = mutableMapOf<String, WindowSnapshotEntity>()
        val deltas = mutableMapOf<String, WindowDeltaEntity>()
        val commits = mutableMapOf<String, MergeCommitEntity>()

        override suspend fun insertWindow(window: WindowEntity) {
            windows[window.sessionId] = window
        }

        override suspend fun getWindow(sessionId: String): WindowEntity? = windows[sessionId]

        override suspend fun listWindows(spaceId: String): List<WindowEntity> =
            windows.values.filter { it.spaceId == spaceId }.sortedBy { it.createdAtEpochMillis }

        override suspend fun deleteWindow(sessionId: String): Int {
            windows.remove(sessionId)
            return 1
        }

        override suspend fun upsertSnapshot(snapshot: WindowSnapshotEntity) {
            snapshots[snapshot.windowId] = snapshot
        }

        override suspend fun getSnapshot(windowId: String): WindowSnapshotEntity? = snapshots[windowId]

        override suspend fun deleteSnapshots(windowId: String): Int {
            snapshots.remove(windowId)
            return 1
        }

        override suspend fun insertDelta(delta: WindowDeltaEntity) {
            deltas[delta.id] = delta
        }

        override suspend fun deleteDeltas(windowId: String): Int {
            deltas.entries.removeAll { it.value.windowId == windowId }
            return 1
        }

        override suspend fun insertMergeCommit(commit: MergeCommitEntity): Long {
            commits[commit.id] = commit
            return 1
        }

        override suspend fun getMergeCommit(id: String): MergeCommitEntity? = commits[id]

        override suspend fun listMergeCommitsForChild(childId: String): List<MergeCommitEntity> =
            commits.values.filter { it.childId == childId }
    }
}

private fun MergeCommitEntity.toDomainCompat() =
    com.reversetutor.core.domain.MergeCommit(id, childId, parentId, deltaId, sourceRevision)
