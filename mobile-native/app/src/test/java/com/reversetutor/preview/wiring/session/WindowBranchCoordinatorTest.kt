package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.EnableWindowHeartbeatCommand
import com.reversetutor.core.domain.HeartbeatScheduleContract
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.feature.chat.CreateWindowBranchRequest
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.WindowBranchMerge
import com.reversetutor.feature.chat.WindowBranchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBranchCoordinatorTest {
    private val root = WindowRef("parent-1", "parent-1", null, WindowKind.TASK_ROOT)
    private val child = WindowRef("child-1", "parent-1", "parent-1", WindowKind.CHILD)
    private val sibling = WindowRef("sibling-1", "parent-1", "parent-1", WindowKind.CHILD)

    @Test
    fun unknownLegacySessionIsBootstrappedOnceAsTaskRoot() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf())

        val first = fake.coordinator().load("legacy-1")
        val second = fake.coordinator().load("legacy-1")

        assertEquals(WindowKind.TASK_ROOT, first.current.kind)
        assertEquals(WindowKind.TASK_ROOT, second.current.kind)
        assertEquals(1, fake.rootCreates)
    }

    @Test
    fun createChildCopiesParentSnapshotAndCapturesLocalRevisionBeforeFork() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(root.id to root))
        val snapshot = NewSessionConfiguration(title = "父会话", learnerRole = "学习者")
        fake.snapshots[root.id] = snapshot
        fake.localCounts[root.id] = 6L

        val result = fake.coordinator().createChild(CreateWindowBranchRequest(root.id, "父会话 · 分支"))

        val created = result as WindowBranchResult.Created
        assertEquals("branch-parent-1-1000", created.child.id)
        assertEquals(snapshot.copy(title = "父会话 · 分支"), fake.snapshots[created.child.id])
        assertEquals(ForkCall(created.child.id, root.id, 6L, 1000L), fake.forks.single())
    }

    @Test
    fun missingParentConfigurationDoesNotCreateAChild() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(root.id to root))

        val result = fake.coordinator().createChild(CreateWindowBranchRequest(root.id, "分支"))

        assertEquals(WindowBranchResult.UnavailableConfiguration, result)
        assertTrue(fake.createdSessions.isEmpty())
    }

    @Test
    fun childHeartbeatCommandsOnlyItsOwnWindow() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(child.id to child))

        assertEquals(WindowBranchResult.HeartbeatUpdated, fake.coordinator().setHeartbeat(child.id, true))
        assertEquals(listOf(child.id), fake.enabledWindowIds)

        assertEquals(WindowBranchResult.HeartbeatUpdated, fake.coordinator().setHeartbeat(child.id, false))
        assertEquals(listOf(child.id), fake.cancelledJobWindowIds)
        assertEquals(listOf(child.id), fake.deletedHeartbeatWindowIds)
    }

    @Test
    fun rootHeartbeatCannotBeDisabled() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(root.id to root))

        assertEquals(WindowBranchResult.InvalidHeartbeatTransition, fake.coordinator().setHeartbeat(root.id, false))
        assertTrue(fake.cancelledJobWindowIds.isEmpty())
        assertTrue(fake.deletedHeartbeatWindowIds.isEmpty())
    }

    @Test
    fun deletingChildReturnsDirectParentAndNeverTargetsSibling() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(root.id to root, child.id to child, sibling.id to sibling))

        val result = fake.coordinator().deleteChild(child.id)

        assertEquals(root.id, (result as WindowBranchResult.Deleted).parentWindowId)
        assertEquals(listOf(child.id), fake.deletedBranches)
        assertFalse(fake.deletedBranches.contains(sibling.id))
        assertFalse(fake.deletedBranches.contains(root.id))
    }

    @Test
    fun loadNeverPretendsThatAnEmptyBranchHasMergeableMemory() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(child.id to child, root.id to root))

        assertEquals(WindowBranchMerge.UnavailableNoDelta, fake.coordinator().load(child.id).merge)
    }

    @Test
    fun deleteIsRejectedWhenLocalMemoryDeleteSeamIsNotPublished() = runBlocking {
        val fake = FakeCoordinator(windows = mutableMapOf(root.id to root, child.id to child))

        assertEquals(WindowBranchResult.Failed, fake.coordinator(deleteAvailable = false).deleteChild(child.id))
        assertTrue(fake.deletedBranches.isEmpty())
    }

    private data class ForkCall(val childId: String, val parentId: String, val revision: Long, val at: Long)

    private class FakeCoordinator(
        val windows: MutableMap<String, WindowRef>,
        val snapshots: MutableMap<String, NewSessionConfiguration> = mutableMapOf(),
        val localCounts: MutableMap<String, Long> = mutableMapOf()
    ) {
        var rootCreates = 0
        val createdSessions = mutableListOf<String>()
        val forks = mutableListOf<ForkCall>()
        val enabledWindowIds = mutableListOf<String>()
        val deletedHeartbeatWindowIds = mutableListOf<String>()
        val cancelledJobWindowIds = mutableListOf<String>()
        val deletedBranches = mutableListOf<String>()

        fun coordinator(deleteAvailable: Boolean = true) = WindowBranchCoordinator(
            sessionExists = { true },
            sessionTitle = { id -> snapshots[id]?.title ?: id },
            readWindow = { id -> windows[id] },
            createTaskRoot = { sessionId, _ ->
                rootCreates += 1
                WindowRef(sessionId, sessionId, null, WindowKind.TASK_ROOT).also { windows[sessionId] = it }
            },
            listWindows = { windows.values.toList() },
            loadSessionSnapshot = { id -> snapshots[id] },
            createSession = { _, id, _ -> createdSessions += id; true },
            saveSessionSnapshot = { id, snapshot -> snapshots[id] = snapshot },
            forkChild = { childId, parentId, revision, at ->
                forks += ForkCall(childId, parentId, revision, at)
                windows[childId] = WindowRef(childId, parentId, parentId, WindowKind.CHILD)
            },
            localMessageCount = { id -> localCounts[id] ?: 0L },
            rollbackChildSession = { id -> createdSessions.remove(id); snapshots.remove(id); windows.remove(id) },
            heartbeatFor = { window -> HeartbeatScheduleContract(window.id, false, 0L) },
            enableChildHeartbeat = { window, command ->
                assertEquals(window.id, command.windowId)
                enabledWindowIds += window.id
            },
            deleteHeartbeat = { id -> deletedHeartbeatWindowIds += id },
            cancelSessionGenerationJobs = { id, _ -> cancelledJobWindowIds += id },
            deleteBranch = { id -> deletedBranches += id },
            deleteAvailable = deleteAvailable,
            nowEpochMillis = { 1000L }
        )
    }
}
