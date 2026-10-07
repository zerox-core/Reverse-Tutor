package com.reversetutor.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.worldtree.RoomWorldTreeRepository
import com.reversetutor.core.model.CreateWorldTreeDraftCommand
import com.reversetutor.core.model.CustomPayload
import com.reversetutor.core.model.LearningGoalPayload
import com.reversetutor.core.model.SourceLibraryPayload
import com.reversetutor.core.model.StudentRolePayload
import com.reversetutor.core.model.WorldTreeMode
import com.reversetutor.core.model.WorldTreeSection
import com.reversetutor.core.model.WorldTreeSectionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldTreeRepositoryInstrumentedTest {
    private lateinit var database: ReverseTutorDatabase
    private lateinit var repository: RoomWorldTreeRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReverseTutorDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomWorldTreeRepository(database)
        database.spaceDao().upsert(
            SpaceEntity("space-1", "Default", "Default", 1, 1)
        )
        database.spaceDao().upsert(
            SpaceEntity("space-2", "Other", "Default", 1, 1)
        )
        database.sessionDao().upsert(
            SessionEntity("session-1", "space-1", "Math", 1, 1)
        )
        database.sourceDao().insertSource(
            SourceEntity(
                id = "source-1",
                spaceId = "space-1",
                title = "错题本",
                type = "Text",
                parserStatus = "FullyLocal",
                createdAtEpochMillis = 1
            )
        )
        database.sourceDao().insertSource(
            SourceEntity(
                id = "source-2",
                spaceId = "space-2",
                title = "Other notes",
                type = "Text",
                parserStatus = "FullyLocal",
                createdAtEpochMillis = 1
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun partialDraftRoundTripsTypedSectionsSourcesAndSessionBinding() = runBlocking {
        val created = repository.createDraft(
            CreateWorldTreeDraftCommand(
                id = "tree-1",
                spaceId = "space-1",
                title = "高三数学讲题冲刺",
                mode = WorldTreeMode.Learning,
                sections = listOf(
                    section("student", WorldTreeSectionType.StudentRole, 0, StudentRolePayload(name = "小岚")),
                    section("sources", WorldTreeSectionType.SourceLibrary, 1, SourceLibraryPayload(listOf("source-1")))
                ),
                sourceIds = listOf("source-1"),
                nowEpochMillis = 10
            )
        )

        assertEquals("小岚", (created.sections[0].payload as StudentRolePayload).name)
        assertEquals(listOf("source-1"), created.sourceIds)
        assertNull(created.sessionId)

        val attached = repository.attachToSession("tree-1", "session-1", 20)
        assertEquals("session-1", attached.sessionId)
        assertEquals(attached, repository.observeDraft("tree-1").first())
    }

    @Test
    fun sectionMutationValidatesPayloadOrderAndCustomRemoval() = runBlocking {
        repository.createDraft(
            CreateWorldTreeDraftCommand(
                id = "tree-2",
                spaceId = "space-1",
                title = "Draft",
                mode = WorldTreeMode.Learning,
                sections = listOf(
                    section("goal", WorldTreeSectionType.LearningGoal, 0, LearningGoalPayload("Explain", emptyList())),
                    section("custom", WorldTreeSectionType.Custom, 1, CustomPayload("Context"))
                ),
                nowEpochMillis = 10
            )
        )

        val reordered = repository.reorderSections("tree-2", listOf("custom", "goal"), 20)
        assertEquals(listOf("custom", "goal"), reordered.sections.map { it.id })

        val withoutCustom = repository.removeCustomSection("tree-2", "custom", 30)
        assertEquals(listOf("goal"), withoutCustom.sections.map { it.id })

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertSection(
                    "tree-2",
                    section("bad", WorldTreeSectionType.LearningGoal, 1, CustomPayload("wrong")),
                    40
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.reorderSections("tree-2", listOf("goal", "goal"), 40) }
        }
        assertEquals(
            listOf("goal"),
            repository.getDraft("tree-2")?.sections?.map { it.id }
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.removeCustomSection("tree-2", "goal", 50) }
        }
    }

    @Test
    fun rejectsCrossSpaceSourcesAndDuplicateSessionBindingWithoutMutation() = runBlocking {
        repository.createDraft(
            CreateWorldTreeDraftCommand(
                id = "tree-3",
                spaceId = "space-1",
                title = "First",
                mode = WorldTreeMode.Learning,
                nowEpochMillis = 10
            )
        )
        repository.createDraft(
            CreateWorldTreeDraftCommand(
                id = "tree-4",
                spaceId = "space-1",
                title = "Second",
                mode = WorldTreeMode.Review,
                nowEpochMillis = 10
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.replaceSourceLinks("tree-3", listOf("source-2"), 20) }
        }
        assertTrue(repository.getDraft("tree-3")?.sourceIds.orEmpty().isEmpty())

        repository.attachToSession("tree-3", "session-1", 30)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.attachToSession("tree-4", "session-1", 40) }
        }
        assertNull(repository.getDraft("tree-4")?.sessionId)

        val recreated = RoomWorldTreeRepository(database)
        assertEquals("session-1", recreated.getDraft("tree-3")?.sessionId)
    }

    private fun section(
        id: String,
        type: WorldTreeSectionType,
        order: Int,
        payload: com.reversetutor.core.model.WorldTreeSectionPayload
    ): WorldTreeSection = WorldTreeSection(
        id = id,
        type = type,
        title = id,
        order = order,
        payload = payload,
        required = false,
        completed = true
    )
}
