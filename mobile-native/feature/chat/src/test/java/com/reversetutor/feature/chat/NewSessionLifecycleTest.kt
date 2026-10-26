package com.reversetutor.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSessionLifecycleTest {
    @Test
    fun hubHasExactlyTwoTabsSinceFormEntryWasRemoved() {
        // 2026-10-23 方案B：六栏表单与「自定义」页删除，只剩两个取材入口。
        assertEquals(listOf("内置预设", "收藏"), NewSessionHubTab.entries.map { it.label })
        assertFalse(NewSessionHubTab.entries.any { it.label.contains("自定义") })
        assertFalse(NewSessionHubTab.entries.any { it.label.contains("草稿") })
    }

    @Test
    fun saveBoundaryEvictsOldestUnfavoritedDraft() {
        val favoriteId = "favorite-protected"
        val initial = (1L..20L).map { updated ->
            NewSessionDraftRecord(
                id = "draft-$updated",
                configuration = validConfiguration("Draft $updated"),
                updatedAtEpochMillis = updated,
                favoriteId = favoriteId.takeIf { updated == 1L }
            )
        }
        val persistence = MemoryNewSessionPersistence(drafts = initial)
        val coordinator = coordinator(persistence, ids = ArrayDeque(listOf("new")))
        coordinator.load()
        coordinator.startBlankDraft()

        assertTrue(coordinator.saveBoundary())
        assertEquals(20, coordinator.state.drafts.size)
        // 被收藏的 draft-1 受保护，淘汰最旧的未收藏 draft-2。
        assertTrue(coordinator.state.drafts.any { it.id == "draft-1" })
        assertFalse(coordinator.state.drafts.any { it.id == "draft-2" })

        assertTrue(coordinator.restoreDraft("draft-20"))
        assertEquals("draft-20", coordinator.state.currentDraft?.id)
        assertTrue(
            coordinator.state.drafts.zipWithNext().all { (newer, older) ->
                newer.updatedAtEpochMillis >= older.updatedAtEpochMillis
            }
        )
    }

    @Test
    fun createValidationRequiresOnlyTitleAndLearnerRole() {
        val incomplete = NewSessionConfiguration(title = "  Session  ", learnerRole = "  Learner  ")

        assertTrue(incomplete.validationErrors().isEmpty())
        val input = incomplete.toCoreDraft().toCreationInput()
        assertEquals("Session", input.title)
        assertEquals("Learner", input.role)
        assertEquals("未填写", input.goal)
        assertTrue(input.profileText.contains("未填写"))
        assertEquals(listOf("请填写会话名称。"), incomplete.copy(title = "").validationErrors())
        assertEquals(listOf("请填写学习者角色。"), incomplete.copy(learnerRole = "").validationErrors())
    }

    @Test
    fun duplicateCreateIsRejectedWhileFirstSubmissionIsRunning() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(
            persistence = persistence,
            port = NewSessionCreatePort { request ->
                calls += 1
                entered.complete(Unit)
                release.await()
                created(request)
            }
        )
        coordinator.startBlankDraft()
        coordinator.updateConfiguration { validConfiguration("Guard") }

        val first = async { coordinator.createSession() }
        entered.await()
        val duplicate = coordinator.createSession()
        release.complete(Unit)

        assertEquals(CreateSessionOutcome.Duplicate, duplicate)
        assertTrue(first.await() is CreateSessionOutcome.Success)
        assertEquals(1, calls)
    }

    @Test
    fun failureRetainsEveryEditAndRetryPromotesSnapshotWithOpeningMessage() = runTest {
        var calls = 0
        val attempts = mutableListOf<String>()
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(
            persistence = persistence,
            port = NewSessionCreatePort { request ->
                calls += 1
                attempts += request.attemptId
                if (calls == 1) error("temporary")
                created(request)
            }
        )
        coordinator.startBlankDraft()
        val configuration = validConfiguration("Retry").copy(
            openingMessage = "请先向我解释这个概念。",
            story = "retained story",
            sourceSelections = listOf("retained source")
        )
        coordinator.updateConfiguration { configuration }
        coordinator.saveBoundary()

        val failure = coordinator.createSession()
        assertTrue(failure is CreateSessionOutcome.Failure)
        assertEquals(configuration, coordinator.state.currentDraft?.configuration)
        assertNotNull(coordinator.state.createError)

        val success = coordinator.createSession()
        assertTrue(success is CreateSessionOutcome.Success)
        assertEquals(attempts[0], attempts[1])
        val created = (success as CreateSessionOutcome.Success).created
        assertEquals(configuration.learnerRole, created.learnerRole)
        assertEquals(configuration.openingMessage, created.openingMessage)
        assertEquals(configuration, persistence.loadSessionSnapshot(created.session.id))
        assertTrue(persistence.loadDrafts().isEmpty())
    }

    @Test
    fun restoringExactOldestEvictionCandidateProtectsCurrentAndDestination() {
        val stored = (1L..20L).map { updated ->
            NewSessionDraftRecord(
                id = "draft-$updated",
                configuration = validConfiguration("Stored $updated"),
                updatedAtEpochMillis = updated
            )
        }
        val persistence = MemoryNewSessionPersistence(drafts = stored)
        val coordinator = coordinator(persistence, ids = ArrayDeque(listOf("current")))
        coordinator.startBlankDraft()
        val unsaved = validConfiguration("Unsaved current").copy(story = "must survive")
        coordinator.updateConfiguration { unsaved }

        assertTrue(coordinator.restoreDraft("draft-1"))
        assertEquals("draft-1", coordinator.state.currentDraft?.id)
        assertTrue(coordinator.state.drafts.any { it.id == "draft-current" && it.configuration == unsaved })
        assertTrue(coordinator.state.drafts.any { it.id == "draft-1" })
        assertFalse(coordinator.state.drafts.any { it.id == "draft-2" })
    }

    @Test
    fun allFavoritedCapacityRefusalAbortsEveryBoundaryDestinationSwitch() {
        val stored = (1L..20L).map { updated ->
            NewSessionDraftRecord(
                id = "draft-$updated",
                configuration = validConfiguration("Stored $updated"),
                updatedAtEpochMillis = updated,
                favoriteId = "protected-$updated"
            )
        }
        // selectTab 不再触发草稿边界；只剩真正会写草稿的入口参与容量拒绝。
        val actions: List<(NewSessionLifecycleCoordinator) -> Boolean> = listOf(
            { it.startBlankDraft() },
            { it.restoreDraft("draft-1") }
        )

        actions.forEachIndexed { index, action ->
            val persistence = MemoryNewSessionPersistence(stored, emptyList())
            val coordinator = coordinator(
                persistence,
                ids = ArrayDeque(listOf("current-$index", "next-$index"))
            )
            coordinator.startBlankDraft()
            val unsaved = validConfiguration("Unsaved $index").copy(story = "story-$index")
            coordinator.updateConfiguration { unsaved }
            val currentId = coordinator.state.currentDraft?.id

            assertFalse("action $index must abort", action(coordinator))
            assertEquals(currentId, coordinator.state.currentDraft?.id)
            assertEquals(unsaved, coordinator.state.currentDraft?.configuration)
            assertEquals(20, persistence.loadDrafts().size)
            assertNotNull(coordinator.state.persistenceError)
        }
    }

    @Test
    fun selectingTabOnlyMovesTheTabAndKeepsTheDraft() {
        val coordinator = coordinator(MemoryNewSessionPersistence())
        coordinator.startBlankDraft()
        val draftId = coordinator.state.currentDraft?.id

        coordinator.selectTab(NewSessionHubTab.Favorites)
        assertEquals(NewSessionHubTab.Favorites, coordinator.state.tab)
        assertEquals(draftId, coordinator.state.currentDraft?.id)

        coordinator.selectTab(NewSessionHubTab.BuiltIn)
        assertEquals(NewSessionHubTab.BuiltIn, coordinator.state.tab)
        assertEquals(draftId, coordinator.state.currentDraft?.id)
    }

    @Test
    fun favoritesCanBeToggledFromBuiltInPresetsAndRemoved() {
        val coordinator = coordinator(MemoryNewSessionPersistence(), ids = ArrayDeque(listOf("fav")))
        val preset = FormalLearningPresets.all.first()

        coordinator.toggleBuiltInFavorite(preset)
        val favorite = coordinator.state.favorites.single()
        assertEquals("favorite-fav", favorite.id)
        assertEquals(preset.title, favorite.name)
        assertEquals(preset.id, favorite.originBuiltInPresetId)

        coordinator.toggleBuiltInFavorite(preset)
        assertTrue(coordinator.state.favorites.isEmpty())
    }

    @Test
    fun removeFavoriteDetachesLinkedDraftWithoutDroppingIt() {
        val draft = NewSessionDraftRecord(
            id = "draft-linked",
            configuration = validConfiguration("Linked"),
            updatedAtEpochMillis = 10L,
            favoriteId = "favorite-x"
        )
        val favorite = NewSessionFavorite(
            id = "favorite-x",
            name = "Linked",
            configuration = validConfiguration("Linked"),
            updatedAtEpochMillis = 10L
        )
        val persistence = MemoryNewSessionPersistence(listOf(draft), listOf(favorite))
        val coordinator = coordinator(persistence)
        coordinator.restoreDraft("draft-linked")

        coordinator.removeFavorite("favorite-x")

        assertTrue(coordinator.state.favorites.isEmpty())
        assertNull(coordinator.state.currentDraft?.favoriteId)
        assertEquals("draft-linked", coordinator.state.currentDraft?.id)
        assertTrue(persistence.loadDrafts().any { it.id == "draft-linked" })
    }

    private fun coordinator(
        persistence: MemoryNewSessionPersistence,
        ids: ArrayDeque<String> = ArrayDeque((1..100).map(Int::toString)),
        port: NewSessionCreatePort = NewSessionCreatePort(::created)
    ): NewSessionLifecycleCoordinator {
        var now = 1_000L
        return NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = port,
            nowEpochMillis = { ++now },
            idFactory = { ids.removeFirst() }
        ).also { it.load() }
    }

    private companion object {
        fun validConfiguration(title: String) = NewSessionConfiguration(
            title = title,
            learnerRole = "curious learner",
            learnerProfile = "careful",
            goal = "explain clearly",
            plan = "three steps",
            dialogueStrategy = "ask why",
            openingMessage = "Start teaching me."
        )

        fun created(request: NewSessionCreateRequest): NewSessionCreated = NewSessionCreated(
            session = SessionListItem(
                id = "session-${request.attemptId}",
                title = request.snapshot.title,
                updatedAtEpochMillis = 1L,
                pinned = false,
                statusLabel = request.snapshot.openingMessage,
                unreadCount = 0,
                avatarLabel = request.snapshot.title.take(1),
                learnerRole = request.snapshot.learnerRole
            ),
            learnerRole = request.snapshot.learnerRole,
            openingMessage = request.snapshot.openingMessage
        )
    }
}

private class MemoryNewSessionPersistence(
    drafts: List<NewSessionDraftRecord> = emptyList(),
    favorites: List<NewSessionFavorite> = emptyList()
) : NewSessionPersistence {
    private var storedDrafts = drafts.map { it.copy(configuration = it.configuration.copy()) }
    private var storedFavorites = favorites.map { it.copy(configuration = it.configuration.copy()) }
    private val sessions = mutableMapOf<String, NewSessionConfiguration>()

    override fun loadDrafts(): List<NewSessionDraftRecord> =
        storedDrafts.map { it.copy(configuration = it.configuration.copy()) }

    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) {
        storedDrafts = drafts.map { it.copy(configuration = it.configuration.copy()) }
    }

    override fun loadFavorites(): List<NewSessionFavorite> =
        storedFavorites.map { it.copy(configuration = it.configuration.copy()) }

    override fun replaceFavorites(favorites: List<NewSessionFavorite>) {
        storedFavorites = favorites.map { it.copy(configuration = it.configuration.copy()) }
    }

    override fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration) {
        storedDrafts = storedDrafts.filterNot { it.id == draftId }
        sessions[sessionId] = snapshot.copy()
    }

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? =
        sessions[sessionId]?.copy()
}
