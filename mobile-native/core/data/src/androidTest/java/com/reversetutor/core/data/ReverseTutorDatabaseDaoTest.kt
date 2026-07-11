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
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SearchDocumentEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SourceChunkEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.session.SessionDeletionRepository
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun hybridRepositoriesKeepConcurrentRunsAndDeleteSessionTransactionally() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity("space-hybrid", "Hybrid", "Default", 1L, 1L)
        )
        database.modelConnectionDao().upsertConnection(
            ProviderConnectionEntity(
                id = "connection-1",
                spaceId = "space-hybrid",
                name = "Provider",
                protocol = "OpenAiCompatible",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        database.modelConnectionDao().upsertBinding(
            ModelBindingEntity(
                id = "binding-1",
                spaceId = "space-hybrid",
                connectionId = "connection-1",
                modelId = "model-1",
                displayName = "Model",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-hybrid",
                spaceId = "space-hybrid",
                title = "Hybrid",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                modelBindingId = "binding-1"
            )
        )
        database.messageDao().insert(
            MessageEntity(
                id = "message-1",
                spaceId = "space-hybrid",
                sessionId = "session-hybrid",
                role = "User",
                text = "First",
                createdAtEpochMillis = 2L
            )
        )
        database.searchDocumentDao().upsert(
            SearchDocumentEntity(
                id = "search-session",
                spaceId = "space-hybrid",
                entityType = "Session",
                entityId = "session-hybrid",
                sessionId = "session-hybrid",
                title = "Hybrid",
                body = "Hybrid session",
                normalizedText = "hybrid session",
                updatedAtEpochMillis = 2L
            )
        )

        val runs = ConversationRunRepositoryImpl(database)
        runs.save(run("run-1", "turn-1", 1L))
        runs.save(run("run-2", "turn-2", 2L))
        assertEquals(listOf("run-1", "run-2"), runs.listActiveBySession("session-hybrid").map { it.id })

        val learning = LearningRepositoryImpl(database)
        learning.savePlanTask(
            task = StudyPlanTask(
                id = "plan-1",
                spaceId = "space-hybrid",
                title = "Review",
                state = StudyPlanTaskState.Planned,
                revision = 1L,
                createdAtEpochMillis = 3L,
                updatedAtEpochMillis = 3L
            ),
            outbox = SyncEnvelope(
                id = "outbox-1",
                spaceId = "space-hybrid",
                entityId = "plan-1",
                entityType = "study_plan_task",
                ownerId = "owner-1",
                deviceId = "device-1",
                revision = 1L,
                idempotencyKey = "plan-1-r1",
                ownership = SyncOwnership.Shared,
                updatedAtEpochMillis = 3L
            )
        )
        assertEquals(listOf("plan-1"), learning.listPlanTasks("space-hybrid").map { it.id })
        assertEquals(listOf("outbox-1"), database.syncDao().listReadyOutbox(3L, 10).map { it.id })

        assertEquals(
            true,
            SessionDeletionRepository(database).deleteSession(
                sessionId = "session-hybrid",
                deletedAtEpochMillis = 10L,
                revision = 2L,
                idempotencyKey = "delete-session-hybrid"
            )
        )
        assertNull(database.sessionDao().getById("session-hybrid"))
        assertEquals(emptyList<TurnRun>(), runs.listBySession("session-hybrid"))
        assertTrue(database.searchDocumentDao().search("space-hybrid", "hybrid", 10).isEmpty())
        assertEquals(
            "session-hybrid",
            database.syncDao().getTombstone("session", "session-hybrid")?.entityId
        )
    }

    private fun run(id: String, turnId: String, sequence: Long): TurnRun =
        TurnRun(
            id = id,
            spaceId = "space-hybrid",
            turnId = turnId,
            sessionId = "session-hybrid",
            userMessageId = "message-1",
            sequence = sequence,
            contextVersion = sequence,
            modelBindingId = "binding-1",
            state = TurnRunState.Running,
            createdAtEpochMillis = sequence
        )
}
