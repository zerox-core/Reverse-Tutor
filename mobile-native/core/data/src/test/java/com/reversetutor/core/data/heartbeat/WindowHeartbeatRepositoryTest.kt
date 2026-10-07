package com.reversetutor.core.data.heartbeat

import com.reversetutor.core.data.local.dao.WindowHeartbeatDao
import com.reversetutor.core.data.local.entity.WindowHeartbeatEntity
import com.reversetutor.core.domain.EnableWindowHeartbeatCommand
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowHeartbeatRepositoryTest {

    private val taskRoot = WindowRef("root-1", "root-1", null, WindowKind.LEARNING_ROOT)
    private val child = WindowRef("child-1", "root-1", "root-1", WindowKind.CHILD)

    @Test
    fun rootWindowGetsEnabledHeartbeatAtCreation() = runBlocking {
        val dao = FakeWindowHeartbeatDao()
        val repo = WindowHeartbeatRepository(dao, defaultSpaceId = "space-a")
        val schedule = repo.heartbeatFor(taskRoot)
        assertTrue(schedule.enabled)
        assertNotNull(dao.getHeartbeat("root-1"))
        assertTrue(dao.getHeartbeat("root-1")!!.enabled)
    }

    @Test
    fun childWindowStartsDisabledUntilExplicitEnable() = runBlocking {
        val dao = FakeWindowHeartbeatDao()
        val repo = WindowHeartbeatRepository(dao, defaultSpaceId = "space-a")
        assertEquals(false, repo.heartbeatFor(child).enabled)

        val enabled = repo.enableChildHeartbeat(child, EnableWindowHeartbeatCommand("child-1", "user"))
        assertTrue(enabled.enabled)
        assertTrue(dao.getHeartbeat("child-1")!!.enabled)
    }

    @Test
    fun deleteRemovesOnlyTargetSchedule() = runBlocking {
        val dao = FakeWindowHeartbeatDao()
        val repo = WindowHeartbeatRepository(dao, defaultSpaceId = "space-a")
        repo.enableChildHeartbeat(child, EnableWindowHeartbeatCommand("child-1", "user"))
        repo.heartbeatFor(taskRoot)

        repo.deleteHeartbeat("child-1")
        assertNull(dao.getHeartbeat("child-1"))
        assertNotNull(dao.getHeartbeat("root-1"))
        assertTrue(dao.getHeartbeat("root-1")!!.enabled)
    }

    private class FakeWindowHeartbeatDao : WindowHeartbeatDao {
        val heartbeats = mutableMapOf<String, WindowHeartbeatEntity>()

        override suspend fun upsertHeartbeat(heartbeat: WindowHeartbeatEntity) {
            heartbeats[heartbeat.windowId] = heartbeat
        }

        override suspend fun getHeartbeat(windowId: String): WindowHeartbeatEntity? = heartbeats[windowId]

        override suspend fun listBySpace(spaceId: String): List<WindowHeartbeatEntity> =
            heartbeats.values.filter { it.spaceId == spaceId }

        override suspend fun listAll(): List<WindowHeartbeatEntity> = heartbeats.values.toList()

        override suspend fun deleteHeartbeat(windowId: String): Int {
            heartbeats.remove(windowId)
            return 1
        }
    }
}
