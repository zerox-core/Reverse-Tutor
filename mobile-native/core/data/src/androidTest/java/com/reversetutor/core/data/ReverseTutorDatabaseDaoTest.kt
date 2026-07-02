package com.reversetutor.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.local.entity.ExportRecordEntity
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.ImportBatchEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SourceChunkEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseDaoTest {
    private lateinit var database: ReverseTutorDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReverseTutorDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun daoBoundariesPersistSpaceSessionMessageAndOperationalRecords() = runBlocking {
        val space = SpaceEntity(
            id = "space-1",
            name = "Default",
            kind = "Default",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
            sourceImportId = null
        )
        database.spaceDao().upsert(space)
        assertEquals(space, database.spaceDao().getById("space-1"))

        val session = SessionEntity(
            id = "session-1",
            spaceId = "space-1",
            title = "Session",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )
        database.sessionDao().upsert(session)
        assertEquals(listOf(session), database.sessionDao().listBySpace("space-1"))

        val sessionSettings = SessionSettingsEntity(
            id = "settings-session-1",
            spaceId = "space-1",
            sessionId = "session-1",
            systemPrompt = "Role: Tutor\nGoal: Learn"
        )
        database.sessionSettingsDao().upsert(sessionSettings)
        assertEquals(sessionSettings, database.sessionSettingsDao().getBySessionId("session-1"))

        val message = MessageEntity(
            id = "message-1",
            spaceId = "space-1",
            sessionId = "session-1",
            role = "User",
            text = "Hello",
            createdAtEpochMillis = 110L
        )
        database.messageDao().insert(message)
        assertEquals(listOf(message), database.messageDao().listBySession("session-1"))

        val quote = MessageQuoteEntity(
            id = "quote-1",
            spaceId = "space-1",
            messageId = "message-1",
            quotedMessageId = "source-message-1",
            excerpt = "Hello"
        )
        database.messageQuoteDao().insert(quote)
        assertEquals(quote, database.messageQuoteDao().getByMessageId("message-1"))
        assertEquals(1, database.messageQuoteDao().deleteByMessageId("message-1"))
        assertNull(database.messageQuoteDao().getByMessageId("message-1"))
        assertEquals(1, database.messageDao().deleteById("message-1"))
        assertEquals(emptyList<MessageEntity>(), database.messageDao().listBySession("session-1"))

        val source = SourceEntity(
            id = "source-1",
            spaceId = "space-1",
            title = "Source",
            type = "Markdown",
            parserStatus = "FullyLocal",
            createdAtEpochMillis = 120L
        )
        val chunk = SourceChunkEntity(
            id = "chunk-1",
            spaceId = "space-1",
            sourceId = "source-1",
            chunkIndex = 0,
            text = "Chunk"
        )
        database.sourceDao().insertSource(source)
        database.sourceDao().insertChunk(chunk)
        assertEquals(listOf(source), database.sourceDao().listSourcesBySpace("space-1"))
        assertEquals(listOf(chunk), database.sourceDao().listChunksForSource("source-1"))

        val node = GraphNodeEntity(
            id = "node-1",
            spaceId = "space-1",
            label = "Concept",
            kind = "Concept",
            createdAtEpochMillis = 130L
        )
        val edge = GraphEdgeEntity(
            id = "edge-1",
            spaceId = "space-1",
            fromNodeId = "node-1",
            toNodeId = "node-2",
            relation = "relates_to",
            createdAtEpochMillis = 131L
        )
        database.graphDao().insertNode(node)
        database.graphDao().insertEdge(edge)
        assertEquals(listOf(node), database.graphDao().listNodesBySpace("space-1"))
        assertEquals(listOf(edge), database.graphDao().listEdgesBySpace("space-1"))

        val job = BackgroundJobEntity(
            id = "job-1",
            spaceId = "space-1",
            kind = "Generation",
            status = "Queued",
            createdAtEpochMillis = 140L
        )
        database.backgroundJobDao().upsert(job)
        assertEquals(job, database.backgroundJobDao().getById("job-1"))

        val importBatch = ImportBatchEntity(
            id = "import-1",
            spaceId = "space-1",
            sourceFileName = "backup.json",
            sourceSchema = "reverse-tutor.export",
            mode = "NewSpace",
            status = "Completed",
            startedAtEpochMillis = 150L,
            completedAtEpochMillis = 151L
        )
        database.importBatchDao().insert(importBatch)
        assertEquals(importBatch, database.importBatchDao().getById("import-1"))

        val exportRecord = ExportRecordEntity(
            id = "export-1",
            spaceId = "space-1",
            schema = "reverse-tutor.export",
            targetFileName = "backup.json",
            status = "Created",
            createdAtEpochMillis = 160L
        )
        database.exportRecordDao().insert(exportRecord)
        assertEquals(exportRecord, database.exportRecordDao().getById("export-1"))
    }

    @Test
    fun sessionDaoSupportsRenamePinOrderingAndArchiveHiding() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity(
                id = "space-actions",
                name = "Actions",
                kind = "Default",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "older",
                spaceId = "space-actions",
                title = "Older",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 10L
            )
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "newer",
                spaceId = "space-actions",
                title = "Newer",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 20L
            )
        )

        assertEquals(1, database.sessionDao().rename("older", "Renamed", 30L))
        assertEquals(1, database.sessionDao().setPinned("older", true, 40L))
        assertEquals("Renamed", database.sessionDao().getById("older")?.title)
        assertEquals(
            listOf("older", "newer"),
            database.sessionDao().listBySpace("space-actions").map { it.id }
        )

        assertEquals(1, database.sessionDao().archive("older", 50L))
        assertEquals(
            listOf("newer"),
            database.sessionDao().listBySpace("space-actions").map { it.id }
        )
        assertEquals(true, database.sessionDao().getById("older")?.archived)
    }

    @Test
    fun llmProfileDaoSupportsActiveSwitchAndDelete() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity(
                id = "space-llm",
                name = "LLM",
                kind = "Default",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        val first = LlmProfileEntity(
            id = "profile-first",
            spaceId = "space-llm",
            name = "First",
            provider = "OpenAiCompatible",
            model = "gpt-4o-mini",
            secretRef = "llm-secret-profile-first",
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 10L,
            baseUrl = "https://api.openai.com/v1",
            enabled = true
        )
        val second = LlmProfileEntity(
            id = "profile-second",
            spaceId = "space-llm",
            name = "Second",
            provider = "Custom",
            model = "local-model",
            createdAtEpochMillis = 20L,
            updatedAtEpochMillis = 20L,
            baseUrl = "http://localhost:11434",
            enabled = false
        )

        database.llmProfileDao().upsert(first)
        database.llmProfileDao().upsert(second)
        assertEquals(first, database.llmProfileDao().getById("profile-first"))

        database.llmProfileDao().setEnabledForSpace(
            spaceId = "space-llm",
            enabledProfileId = "profile-second",
            updatedAtEpochMillis = 30L
        )

        assertEquals(false, database.llmProfileDao().getById("profile-first")?.enabled)
        assertEquals(true, database.llmProfileDao().getById("profile-second")?.enabled)
        assertEquals(1, database.llmProfileDao().deleteById("profile-first"))
        assertNull(database.llmProfileDao().getById("profile-first"))
    }
}
