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
import com.reversetutor.core.data.local.entity.MemoryItemEntity
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
import com.reversetutor.core.data.model.ModelConnectionRepositoryImpl
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.search.RoomGlobalSearchRepository
import com.reversetutor.core.data.search.SearchDocument
import com.reversetutor.core.data.session.SessionDeletionRepository
import com.reversetutor.core.data.sync.RoomSyncRepository
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.domain.GlobalSearchRepository
import com.reversetutor.core.domain.LearningInsightRepository
import com.reversetutor.core.domain.ModelConnectionRepository
import com.reversetutor.core.domain.PersistTurnCompletion
import com.reversetutor.core.domain.PersistTurnCompletionCommand
import com.reversetutor.core.domain.PersistTurnRetryCommand
import com.reversetutor.core.domain.PersistTurnRunCommand
import com.reversetutor.core.domain.StudyPlanRepository
import com.reversetutor.core.domain.SyncRepository
import com.reversetutor.core.domain.TokenUsageRepository
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ModelAvailability
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.SearchTargetType
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.WeeklySummary
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
    fun graphNodesCanBeQueriedThroughSessionProvenance() = runBlocking {
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-1",
                spaceId = "space-1",
                title = "Session 1",
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L
            )
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-2",
                spaceId = "space-1",
                title = "Session 2",
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L
            )
        )
        database.messageDao().insert(
            MessageEntity(
                id = "message-1",
                spaceId = "space-1",
                sessionId = "session-1",
                role = "user",
                text = "First",
                createdAtEpochMillis = 100L
            )
        )
        database.messageDao().insert(
            MessageEntity(
                id = "message-2",
                spaceId = "space-1",
                sessionId = "session-2",
                role = "user",
                text = "Second",
                createdAtEpochMillis = 100L
            )
        )
        database.memoryDao().insertMemoryItem(
            MemoryItemEntity(
                id = "memory-1",
                spaceId = "space-1",
                kind = "note",
                title = "Memory 1",
                body = "Body",
                createdAtEpochMillis = 100L,
                sourceMessageId = "message-1"
            )
        )
        database.memoryDao().insertMemoryItem(
            MemoryItemEntity(
                id = "memory-2",
                spaceId = "space-1",
                kind = "note",
                title = "Memory 2",
                body = "Body",
                createdAtEpochMillis = 100L,
                sourceMessageId = "message-2"
            )
        )
        val sessionNode = GraphNodeEntity(
            id = "node-1",
            spaceId = "space-1",
            label = "Session 1 node",
            kind = "Concept",
            createdAtEpochMillis = 100L,
            sourceMemoryId = "memory-1"
        )
        database.graphDao().insertNode(sessionNode)
        database.graphDao().insertNode(
            GraphNodeEntity(
                id = "node-2",
                spaceId = "space-1",
                label = "Session 2 node",
                kind = "Concept",
                createdAtEpochMillis = 100L,
                sourceMemoryId = "memory-2"
            )
        )

        assertEquals(
            listOf(sessionNode),
            database.graphDao().listNodesBySession("session-1")
        )
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
        database.sessionSettingsDao().upsert(
            SessionSettingsEntity(
                id = "settings-contract",
                spaceId = "space-contract",
                sessionId = "session-contract"
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

    @Test
    fun domainRepositoryContractsUseTurnAttemptAndPersistSyncRetryState() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity("space-contract", "Contract", "Default", 1L, 1L)
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-contract",
                spaceId = "space-contract",
                title = "Contract",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        database.modelConnectionDao().upsertConnection(
            ProviderConnectionEntity(
                id = "connection-contract",
                spaceId = "space-contract",
                name = "Provider",
                protocol = "OpenAiCompatible",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )

        val modelRepository: ModelConnectionRepository = ModelConnectionRepositoryImpl(database)
        val binding = ModelBinding(
            id = "binding-contract",
            spaceId = "space-contract",
            connectionId = "connection-contract",
            modelId = "model-contract",
            createdAtEpochMillis = 2L,
            updatedAtEpochMillis = 2L
        )
        assertEquals(binding, modelRepository.saveBinding(binding))
        assertEquals(binding, modelRepository.findBinding(binding.id))
        assertEquals(listOf(binding), modelRepository.listBindings("connection-contract"))

        val sessionRepository: com.reversetutor.core.domain.SessionRepository =
            com.reversetutor.core.data.session.SessionRepository(
                database.spaceDao(),
                database.sessionDao(),
                database.sessionSettingsDao()
            )
        assertEquals(true, sessionRepository.setModelBinding("session-contract", binding.id))
        assertEquals(binding.id, database.sessionDao().getById("session-contract")?.modelBindingId)
        assertEquals(
            binding.id,
            database.sessionSettingsDao().getBySessionId("session-contract")?.modelBindingId
        )

        val runData = ConversationRunRepositoryImpl(database)
        val runRepository: ConversationRunRepository = runData
        val parent = runRepository.createRunWithSnapshot(
            persistRunCommand(
                runId = "run-parent-0",
                snapshotId = "snapshot-parent",
                turnId = "turn-parent",
                initialState = TurnRunState.Running
            )
        )
        val waiting = runRepository.createRunWithSnapshot(
            persistRunCommand(
                runId = "run-waiting",
                snapshotId = "snapshot-waiting",
                turnId = "turn-child",
                initialState = TurnRunState.Waiting,
                parentTurnId = "turn-parent"
            )
        )
        assertEquals(1L, parent.sequence)
        assertEquals(2L, waiting.sequence)
        assertEquals(1L, runData.getSnapshot("snapshot-parent")?.maxSequence)

        val replacement = requireNotNull(
            runRepository.retryLatestAttempt(
                PersistTurnRetryCommand(
                    runId = parent.id,
                    replacementRunId = "run-parent-1",
                    initialState = TurnRunState.Running,
                    createdAtEpochMillis = 3L
                )
            )
        )
        assertEquals(1, replacement.attempt)
        assertEquals(parent.sequence, replacement.sequence)
        assertEquals(TurnRunState.Discarded, runRepository.findRun(parent.id)?.state)
        assertNull(
            runRepository.retryLatestAttempt(
                PersistTurnRetryCommand(
                    runId = parent.id,
                    replacementRunId = "run-parent-invalid",
                    initialState = TurnRunState.Running,
                    createdAtEpochMillis = 4L
                )
            )
        )
        assertEquals(
            PersistTurnCompletion.StaleAttempt,
            runRepository.completeCurrentAttempt(
                PersistTurnCompletionCommand(parent.id, 0, "result-stale", 5L)
            )
        )
        val completion = runRepository.completeCurrentAttempt(
            PersistTurnCompletionCommand(replacement.id, 1, "result-current", 6L)
        )
        assertTrue(completion is PersistTurnCompletion.Accepted)
        completion as PersistTurnCompletion.Accepted
        assertEquals(listOf(waiting.id), completion.releasedRuns.map { it.id })
        assertEquals(TurnRunState.Running, runRepository.findRun(waiting.id)?.state)
        assertEquals(
            PersistTurnCompletion.AlreadyTerminal,
            runRepository.completeCurrentAttempt(
                PersistTurnCompletionCommand(replacement.id, 1, "result-repeat", 7L)
            )
        )
        assertEquals("run-parent-1", runRepository.findLatestRun("turn-parent")?.id)
        assertEquals(false, runRepository.isSessionDeleted("session-contract"))

        val learning = LearningRepositoryImpl(database)
        val plans: StudyPlanRepository = learning
        val insights: LearningInsightRepository = learning
        val usage: TokenUsageRepository = learning
        val task = StudyPlanTask(
            id = "plan-contract",
            spaceId = "space-contract",
            title = "Plan",
            state = StudyPlanTaskState.Planned,
            createdAtEpochMillis = 4L,
            updatedAtEpochMillis = 4L
        )
        assertEquals(task, plans.saveTask(task))
        assertEquals(listOf(task), plans.listTasks("space-contract"))
        val summary = WeeklySummary(
            id = "summary-contract",
            spaceId = "space-contract",
            weekStartEpochMillis = 100L,
            sourceRevision = 2L,
            generatorVersion = "v1",
            summary = "Summary"
        )
        assertEquals(summary, insights.saveWeeklySummary(summary))
        assertEquals(
            summary,
            insights.findWeeklySummary("space-contract", 100L, 2L, "v1")
        )
        val tokenUsage = TokenUsageRecord(
            id = "usage-contract",
            spaceId = "space-contract",
            turnId = "turn-parent",
            attempt = 1
        )
        assertEquals(tokenUsage, usage.saveUsage(tokenUsage))

        val detailedSearch = RoomGlobalSearchRepository(database)
        detailedSearch.index(
            SearchDocument(
                id = "search-contract",
                spaceId = "space-contract",
                target = SearchTarget(
                    type = SearchTargetType.Session,
                    entityId = "session-contract",
                    spaceId = "space-contract",
                    sessionId = "session-contract"
                ),
                title = "Contract",
                body = "Search target",
                updatedAtEpochMillis = 5L
            )
        )
        val search: GlobalSearchRepository = detailedSearch
        assertEquals(
            listOf("session-contract"),
            search.search("space-contract", "search").map { it.entityId }
        )
        assertEquals("Contract", detailedSearch.searchResults("space-contract", "search").single().title)

        var now = 1_000L
        val sync: SyncRepository = RoomSyncRepository(database) { now }
        val roomSync = sync as RoomSyncRepository
        roomSync.enqueue(
            SyncEnvelope(
                id = "outbox-contract",
                spaceId = "space-contract",
                entityId = "plan-contract",
                entityType = "study_plan_task",
                ownerId = "owner",
                deviceId = "device",
                revision = 1L,
                idempotencyKey = "outbox-contract-r1",
                ownership = SyncOwnership.Shared,
                updatedAtEpochMillis = now
            )
        )
        assertEquals(listOf("outbox-contract"), sync.pendingEnvelopes().map { it.id })
        sync.markFailed("outbox-contract", "offline", retryable = true)
        assertTrue(sync.pendingEnvelopes().isEmpty())
        val failed = database.syncDao().getOutbox("outbox-contract")
        assertEquals(1, failed?.retryCount)
        assertEquals("Pending", failed?.status)
        now = requireNotNull(failed).nextAttemptAtEpochMillis
        assertEquals(listOf("outbox-contract"), sync.pendingEnvelopes().map { it.id })
        val cursor = SyncCursor(
            id = "cursor-contract",
            spaceId = "space-contract",
            entityType = "study_plan_task",
            cursor = "next",
            revision = 2L,
            updatedAtEpochMillis = now
        )
        assertEquals(cursor, sync.saveCursor(cursor))
        val otherCursor = cursor.copy(
            id = "cursor-other",
            spaceId = "space-other",
            revision = 99L
        )
        sync.saveCursor(otherCursor)
        sync.markSucceeded("outbox-contract", remoteRevision = 2L)
        assertNull(database.syncDao().getOutbox("outbox-contract"))
        assertEquals(2L, sync.readCursor("space-contract", "study_plan_task")?.revision)
        assertEquals(99L, sync.readCursor("space-other", "study_plan_task")?.revision)
    }

    @Test
    fun modelConnectionUpsertsPreserveBindingsAndReferencedRuns() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity("space-upsert", "Upsert", "Default", 1L, 1L)
        )
        val connection = ProviderConnectionEntity(
            id = "connection-upsert",
            spaceId = "space-upsert",
            name = "Original",
            protocol = "OpenAiCompatible",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val binding = ModelBindingEntity(
            id = "binding-upsert",
            spaceId = "space-upsert",
            connectionId = connection.id,
            modelId = "model-upsert",
            displayName = "Model",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        database.modelConnectionDao().upsertConnection(connection)
        database.modelConnectionDao().upsertBinding(binding)

        database.modelConnectionDao().upsertConnection(
            connection.copy(name = "Updated", updatedAtEpochMillis = 2L)
        )

        assertEquals("Updated", database.modelConnectionDao().getConnection(connection.id)?.name)
        assertEquals(binding.id, database.modelConnectionDao().getBinding(binding.id)?.id)

        database.sessionDao().upsert(
            SessionEntity(
                id = "session-upsert",
                spaceId = "space-upsert",
                title = "Session",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                modelBindingId = binding.id
            )
        )
        val run = TurnRun(
            id = "run-upsert",
            spaceId = "space-upsert",
            turnId = "turn-upsert",
            sessionId = "session-upsert",
            userMessageId = "message-upsert",
            sequence = 1L,
            contextVersion = 1L,
            modelBindingId = binding.id,
            state = TurnRunState.Running,
            createdAtEpochMillis = 1L
        )
        ConversationRunRepositoryImpl(database).saveRun(run)

        database.modelConnectionDao().upsertBinding(
            binding.copy(
                availability = ModelAvailability.Available.name,
                updatedAtEpochMillis = 3L
            )
        )

        assertEquals(
            ModelAvailability.Available.name,
            database.modelConnectionDao().getBinding(binding.id)?.availability
        )
        assertEquals(run.id, database.turnRunDao().getById(run.id)?.id)
    }

    @Test
    fun executionModelResolutionUsesRequestedThenSessionThenDefaultAndRejectsDisabled() = runBlocking {
        database.spaceDao().upsert(
            SpaceEntity("space-resolution", "Resolution", "Default", 1L, 1L)
        )
        val connection = ProviderConnectionEntity(
            id = "connection-resolution",
            spaceId = "space-resolution",
            name = "Connection",
            protocol = "OpenAiCompatible",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        database.modelConnectionDao().upsertConnection(connection)
        listOf(
            ModelBindingEntity(
                id = "binding-default",
                spaceId = "space-resolution",
                connectionId = connection.id,
                modelId = "model-default",
                displayName = "Default",
                isDefault = true,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            ),
            ModelBindingEntity(
                id = "binding-session",
                spaceId = "space-resolution",
                connectionId = connection.id,
                modelId = "model-session",
                displayName = "Session",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L
            ),
            ModelBindingEntity(
                id = "binding-requested",
                spaceId = "space-resolution",
                connectionId = connection.id,
                modelId = "model-requested",
                displayName = "Requested",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 3L
            )
        ).forEach { database.modelConnectionDao().upsertBinding(it) }
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-resolution",
                spaceId = "space-resolution",
                title = "Session",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                modelBindingId = "binding-session"
            )
        )
        database.sessionDao().upsert(
            SessionEntity(
                id = "session-default",
                spaceId = "space-resolution",
                title = "Default session",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        val repository = ModelConnectionRepositoryImpl(database)

        assertEquals(
            "binding-requested",
            repository.resolveForExecution("session-resolution", "binding-requested")?.binding?.id
        )
        assertEquals(
            "binding-session",
            repository.resolveForExecution("session-resolution")?.binding?.id
        )
        assertEquals(
            "binding-default",
            repository.resolveForExecution("session-default")?.binding?.id
        )

        database.modelConnectionDao().upsertBinding(
            requireNotNull(database.modelConnectionDao().getBinding("binding-requested"))
                .copy(enabled = false)
        )
        assertNull(repository.resolveForExecution("session-resolution", "binding-requested"))
        assertNull(repository.resolveForExecution("session-resolution", "binding-missing"))

        database.modelConnectionDao().upsertConnection(connection.copy(enabled = false))
        assertNull(repository.resolveForExecution("session-resolution"))
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

    private fun persistRunCommand(
        runId: String,
        snapshotId: String,
        turnId: String,
        initialState: TurnRunState,
        parentTurnId: String? = null
    ): PersistTurnRunCommand =
        PersistTurnRunCommand(
            runId = runId,
            snapshotId = snapshotId,
            spaceId = "space-contract",
            sessionId = "session-contract",
            turnId = turnId,
            userMessageId = "message-$runId",
            modelBindingId = "binding-contract",
            contextVersion = 1L,
            contextMessageIds = listOf("context-1"),
            parentTurnId = parentTurnId,
            initialState = initialState,
            createdAtEpochMillis = 2L
        )
}
