package com.reversetutor.preview.background

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.session.SessionDeletionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundGenerationSessionDeletionDeviceTest {
    private lateinit var database: ReverseTutorDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReverseTutorDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingSessionRemovesQueuedGenerationBeforeAnyLateWorkerCanWrite() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity("space-1", "Space", "Default", 1L, 1L)
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-1",
                spaceId = "space-1",
                title = "Session",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        database.backgroundJobDao().upsert(
            BackgroundJobEntity(
                id = "job-1",
                spaceId = "space-1",
                kind = "Generation",
                status = "Queued",
                createdAtEpochMillis = 2L,
                sessionId = "session-1",
                userMessageId = "message-1",
                userText = "Late generation",
                generationToken = "token-1"
            )
        )

        val deleted = SessionDeletionRepository(database).deleteSession(
            sessionId = "session-1",
            deletedAtEpochMillis = 3L,
            revision = 1L
        )

        assertTrue(deleted)
        assertNull(database.sessionDao().getById("session-1"))
        assertNull(database.backgroundJobDao().getById("job-1"))
        assertTrue(database.messageDao().listBySession("session-1").isEmpty())
    }
}
