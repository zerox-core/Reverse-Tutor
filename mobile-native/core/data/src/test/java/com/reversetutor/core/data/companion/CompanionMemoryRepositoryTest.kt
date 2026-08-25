package com.reversetutor.core.data.companion

import com.reversetutor.core.data.local.dao.CompanionMemoryDao
import com.reversetutor.core.data.local.entity.CompanionMemoryVersionEntity
import com.reversetutor.core.data.local.entity.MemoryObservationEntity
import com.reversetutor.core.domain.ActiveMemoryVersion
import com.reversetutor.core.domain.CompanionMemoryPartition
import com.reversetutor.core.domain.MemoryDomain
import com.reversetutor.core.domain.MemoryObservation
import com.reversetutor.core.domain.MemoryOrigin
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryRepositoryTest {

    private val companionRoot = WindowRef("croot", "croot", null, WindowKind.COMPANION_ROOT)
    private val taskRoot = WindowRef("task", "task", null, WindowKind.LEARNING_ROOT)
    private val child = WindowRef("child", "croot", "croot", WindowKind.CHILD)

    private fun version() = ActiveMemoryVersion(
        partition = CompanionMemoryPartition.STABLE_PREFERENCE,
        value = "socratic, concise",
        origin = MemoryOrigin.INFERRED,
        promotedAtEpochMillis = 10L,
        revision = 1L
    )

    private fun observation() = MemoryObservation(
        domain = MemoryDomain.COMPANION,
        partition = CompanionMemoryPartition.STABLE_PREFERENCE,
        normalizedValue = "prefers worked examples",
        sourceClass = "behavior_observation",
        observedAtEpochMillis = 10L,
        confidence = 0.9f,
        provenanceHandle = "prov-1"
    )

    @Test
    fun companionDomainIsOnlyReadableByCompanionRoot() = runBlocking {
        val dao = FakeCompanionMemoryDao()
        val repo = CompanionMemoryRepository(dao, defaultSpaceId = "space-a")
        repo.saveVersion(companionRoot, version())

        assertNotNull(repo.readVersions(companionRoot))
        assertNull(repo.readVersions(taskRoot))
        assertNull(repo.readVersions(child))
    }

    @Test
    fun taskAndChildCannotWriteCompanionMemory() = runBlocking {
        val dao = FakeCompanionMemoryDao()
        val repo = CompanionMemoryRepository(dao, defaultSpaceId = "space-a")

        assertNull(repo.saveVersion(taskRoot, version()))
        assertFalse(repo.appendObservation(taskRoot, observation()))
        assertNull(repo.readObservations(taskRoot))

        val saved = repo.saveVersion(companionRoot, version())
        assertNotNull(saved)
        assertTrue(repo.appendObservation(companionRoot, observation()))
    }

    @Test
    fun companionRootPersistsVersionsAndObservations() = runBlocking {
        val dao = FakeCompanionMemoryDao()
        val repo = CompanionMemoryRepository(dao, defaultSpaceId = "space-a")
        repo.saveVersion(companionRoot, version())
        repo.appendObservation(companionRoot, observation())

        assertEquals(1, dao.versions.size)
        assertEquals(1, dao.observations.size)
        assertNotNull(repo.readObservations(companionRoot))
    }

    private fun assertEquals(a: Int, b: Int) = org.junit.Assert.assertEquals(a, b)

    private class FakeCompanionMemoryDao : CompanionMemoryDao {
        val versions = mutableListOf<CompanionMemoryVersionEntity>()
        val observations = mutableListOf<MemoryObservationEntity>()

        override suspend fun upsertVersion(version: CompanionMemoryVersionEntity) {
            versions.removeAll { it.id == version.id }
            versions.add(version)
        }

        override suspend fun listVersions(windowId: String): List<CompanionMemoryVersionEntity> =
            versions.filter { it.windowId == windowId }

        override suspend fun getVersion(windowId: String, partition: String): CompanionMemoryVersionEntity? =
            versions.firstOrNull { it.windowId == windowId && it.partition == partition }

        override suspend fun insertObservation(observation: MemoryObservationEntity) {
            observations.add(observation)
        }

        override suspend fun listObservations(windowId: String): List<MemoryObservationEntity> =
            observations.filter { it.windowId == windowId }

        override suspend fun listObservationsBySpace(spaceId: String): List<MemoryObservationEntity> =
            observations.filter { it.spaceId == spaceId }
    }
}
