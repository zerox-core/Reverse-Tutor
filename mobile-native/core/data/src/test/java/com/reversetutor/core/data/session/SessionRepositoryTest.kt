package com.reversetutor.core.data.session

import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.dao.SessionSettingsDao
import com.reversetutor.core.data.local.dao.SpaceDao
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.model.TutorSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun listSessionsOrdersPinnedFirstThenRecentlyUpdatedAndHidesArchived() = runBlocking {
        val sessionDao = FakeSessionDao()
        val repository = SessionRepository(FakeSpaceDao(), sessionDao, FakeSessionSettingsDao())
        sessionDao.upsert(session(id = "old", title = "Old", updatedAt = 10L))
        sessionDao.upsert(session(id = "new", title = "New", updatedAt = 30L))
        sessionDao.upsert(session(id = "pinned", title = "Pinned", updatedAt = 20L, pinned = true))
        sessionDao.upsert(session(id = "archived", title = "Archived", updatedAt = 40L, archived = true))

        val sessions = repository.listSessions(spaceId = "space-1")

        assertEquals(listOf("pinned", "new", "old"), sessions.map { it.id })
    }

    @Test
    fun renamePinAndArchiveMutationsPersistThroughDao() = runBlocking {
        val repository = SessionRepository(FakeSpaceDao(), FakeSessionDao(), FakeSessionSettingsDao())
        repository.saveSession(
            TutorSession(
                id = "session-1",
                spaceId = "space-1",
                title = "Original",
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L
            )
        )

        assertTrue(repository.renameSession("session-1", "Renamed", updatedAtEpochMillis = 200L))
        assertTrue(repository.setPinned("session-1", pinned = true, updatedAtEpochMillis = 210L))
        val renamed = repository.getSession("session-1")
        assertEquals("Renamed", renamed?.title)
        assertTrue(renamed?.pinned == true)
        assertEquals(210L, renamed?.updatedAtEpochMillis)

        assertTrue(repository.archiveSession("session-1", updatedAtEpochMillis = 220L))
        assertEquals(emptyList<TutorSession>(), repository.listSessions("space-1"))
        assertTrue(repository.getSession("session-1")?.archived == true)
    }

    @Test
    fun mutationsReturnFalseWhenSessionDoesNotExist() = runBlocking {
        val repository = SessionRepository(FakeSpaceDao(), FakeSessionDao(), FakeSessionSettingsDao())

        assertFalse(repository.renameSession("missing", "Ignored", updatedAtEpochMillis = 1L))
        assertFalse(repository.setPinned("missing", pinned = true, updatedAtEpochMillis = 1L))
        assertFalse(repository.archiveSession("missing", updatedAtEpochMillis = 1L))
    }

    @Test
    fun createCustomSessionPersistsProfileInSessionSettings() = runBlocking {
        val repository = SessionRepository(FakeSpaceDao(), FakeSessionDao(), FakeSessionSettingsDao())

        val created = repository.createSession(
            input = SessionCreationInput(
                title = "  Exam sprint  ",
                role = "  Socratic coach ",
                goal = "  pass algebra quiz ",
                profileText = "  concise but warm ",
                sourceHandoffRequested = true
            ),
            nowEpochMillis = 1_000L,
            sessionId = "session-custom"
        )

        assertEquals("session-custom", created.session.id)
        assertEquals("Exam sprint", created.session.title)
        assertEquals("settings-session-custom", created.session.settingsId)
        assertEquals(listOf("session-custom"), repository.listSessions("default-space").map { it.id })

        val settings = repository.getSessionSettings("session-custom")
        assertEquals("settings-session-custom", settings?.id)
        assertTrue(settings?.systemPrompt?.contains("Role: Socratic coach") == true)
        assertTrue(settings?.systemPrompt?.contains("Goal: pass algebra quiz") == true)
        assertTrue(settings?.systemPrompt?.contains("Profile: concise but warm") == true)
        assertTrue(settings?.systemPrompt?.contains("Initial source: Deferred handoff") == true)
    }

    private fun session(
        id: String,
        title: String,
        updatedAt: Long,
        pinned: Boolean = false,
        archived: Boolean = false
    ): SessionEntity = SessionEntity(
        id = id,
        spaceId = "space-1",
        title = title,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = updatedAt,
        pinned = pinned,
        archived = archived
    )
}

private class FakeSpaceDao : SpaceDao {
    private val spaces = linkedMapOf<String, SpaceEntity>()

    override suspend fun upsert(space: SpaceEntity) {
        spaces[space.id] = space
    }

    override suspend fun getById(id: String): SpaceEntity? = spaces[id]

    override suspend fun listAll(): List<SpaceEntity> =
        spaces.values.sortedByDescending { it.updatedAtEpochMillis }
}

private class FakeSessionDao : SessionDao {
    private val sessions = linkedMapOf<String, SessionEntity>()

    override suspend fun upsert(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun getById(id: String): SessionEntity? = sessions[id]

    override suspend fun listBySpace(spaceId: String): List<SessionEntity> =
        sessions.values
            .filter { it.spaceId == spaceId && !it.archived }
            .sortedWith(compareByDescending<SessionEntity> { it.pinned }.thenByDescending { it.updatedAtEpochMillis })

    override suspend fun rename(id: String, title: String, updatedAtEpochMillis: Long): Int {
        val existing = sessions[id] ?: return 0
        sessions[id] = existing.copy(title = title, updatedAtEpochMillis = updatedAtEpochMillis)
        return 1
    }

    override suspend fun setPinned(id: String, pinned: Boolean, updatedAtEpochMillis: Long): Int {
        val existing = sessions[id] ?: return 0
        sessions[id] = existing.copy(pinned = pinned, updatedAtEpochMillis = updatedAtEpochMillis)
        return 1
    }

    override suspend fun archive(id: String, updatedAtEpochMillis: Long): Int {
        val existing = sessions[id] ?: return 0
        sessions[id] = existing.copy(archived = true, updatedAtEpochMillis = updatedAtEpochMillis)
        return 1
    }
}

private class FakeSessionSettingsDao : SessionSettingsDao {
    private val settingsById = linkedMapOf<String, SessionSettingsEntity>()

    override suspend fun upsert(settings: SessionSettingsEntity) {
        settingsById[settings.id] = settings
    }

    override suspend fun getBySessionId(sessionId: String): SessionSettingsEntity? =
        settingsById.values.firstOrNull { it.sessionId == sessionId }
}
