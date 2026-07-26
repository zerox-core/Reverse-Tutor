package com.reversetutor.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSessionLifecycleTest {
    @Test
    fun hubHasExactlyThreeTabsAndCustomHasExactlySixSections() {
        assertEquals(listOf("内置预设", "收藏", "自定义"), NewSessionHubTab.entries.map { it.label })
        assertEquals(
            listOf("基本资料", "目标与计划", "对话策略", "世界树", "资料", "自定义栏目"),
            NewSessionSection.entries.map { it.label }
        )
        assertFalse(NewSessionHubTab.entries.any { it.label.contains("草稿") })
    }

    @Test
    fun editingBuiltInCreatesUnfavoritedPersonalCopy() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(persistence)
        val preset = FormalLearningPresets.all.first()

        coordinator.useBuiltInPreset(preset)
        val original = checkNotNull(coordinator.state.currentDraft)
        assertEquals(preset.title, original.configuration.title)
        assertEquals(preset.id, original.originBuiltInPresetId)

        coordinator.updateConfiguration { it.copy(goal = "changed") }
        coordinator.saveBoundary()
        val copy = checkNotNull(coordinator.state.currentDraft)

        assertEquals("${preset.title} · 副本", copy.configuration.title)
        assertEquals("changed", copy.configuration.goal)
        assertNull(copy.originBuiltInPresetId)
        assertNull(copy.favoriteId)
        assertEquals(preset.title, NewSessionConfiguration.fromPreset(preset).title)
    }

    @Test
    fun draftBoxEvictsOldestUnfavoritedAndRestoresDestinationPosition() {
        val favoriteId = "favorite-protected"
        val initial = (1L..20L).map { updated ->
            NewSessionDraftRecord(
                id = "draft-$updated",
                configuration = validConfiguration("Draft $updated"),
                updatedAtEpochMillis = updated,
                favoriteId = favoriteId.takeIf { updated == 1L },
                lastSection = NewSessionSection.WorldTree.takeIf { updated == 20L },
                scrollPositions = if (updated == 20L) {
                    mapOf(NewSessionSection.WorldTree.name to EditorScrollPosition(4, 18))
                } else emptyMap()
            )
        }
        val persistence = MemoryNewSessionPersistence(drafts = initial)
        val coordinator = coordinator(persistence, ids = ArrayDeque(listOf("new")))
        coordinator.load()
        coordinator.startBlankDraft()

        assertTrue(coordinator.saveBoundary())
        assertEquals(20, coordinator.state.drafts.size)
        assertTrue(coordinator.state.drafts.any { it.id == "draft-1" })
        assertFalse(coordinator.state.drafts.any { it.id == "draft-2" })

        assertTrue(coordinator.restoreDraft("draft-20"))
        assertEquals(NewSessionSection.WorldTree, coordinator.state.selectedSection)
        assertEquals(
            EditorScrollPosition(4, 18),
            coordinator.state.currentDraft?.scrollPositions?.get(NewSessionSection.WorldTree.name)
        )
        assertTrue(
            coordinator.state.drafts.zipWithNext().all { (newer, older) ->
                newer.updatedAtEpochMillis >= older.updatedAtEpochMillis
            }
        )
    }

    @Test
    fun randomPreviewApplyAndUndoNeverChangeStorySourcesOrImages() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(
            persistence = persistence,
            randomizer = NewSessionRandomizer {
                it.copy(
                    learnerRole = "random role",
                    goal = "random goal",
                    plan = "random plan",
                    story = "MUST NOT CHANGE",
                    sourceSelections = listOf("MUST NOT CHANGE"),
                    learnerImageRef = "MUST NOT CHANGE",
                    storyImageRef = "MUST NOT CHANGE"
                )
            }
        )
        coordinator.startBlankDraft()
        val original = validConfiguration("Random").copy(
            story = "story",
            sourceSelections = listOf("source-a"),
            learnerImageRef = "learner.png",
            storyImageRef = "story.png"
        )
        coordinator.updateConfiguration { original }

        coordinator.previewRandom()
        val preview = checkNotNull(coordinator.state.randomPreview)
        assertEquals(setOf("学习者角色", "主要目标", "学习计划"), preview.differences.map { it.field }.toSet())
        assertEquals(original.story, preview.after.story)
        assertEquals(original.sourceSelections, preview.after.sourceSelections)
        assertEquals(original.learnerImageRef, preview.after.learnerImageRef)
        assertEquals(original.storyImageRef, preview.after.storyImageRef)

        coordinator.applyRandom()
        assertEquals("random role", coordinator.state.currentDraft?.configuration?.learnerRole)
        coordinator.undoRandom()
        assertEquals(original, coordinator.state.currentDraft?.configuration)
    }

    @Test
    fun favoriteUpdateRequiresDiffConfirmationAndSessionSnapshotStaysIsolated() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(persistence)
        coordinator.startBlankDraft()
        val original = validConfiguration("Favorite").copy(
            sourceSelections = listOf("source-a"),
            customFields = mapOf("level" to "beginner")
        )
        coordinator.updateConfiguration { original }
        coordinator.requestFavoriteCurrent()
        val favorite = coordinator.state.favorites.single()
        assertEquals(original, favorite.configuration)

        persistence.promoteDraft("none", "session-existing", original.copy())
        coordinator.updateConfiguration { it.copy(goal = "updated goal") }
        coordinator.requestFavoriteCurrent()
        assertNotNull(coordinator.state.favoriteUpdatePreview)
        assertEquals(original, coordinator.state.favorites.single().configuration)

        coordinator.confirmFavoriteUpdate()
        assertEquals("updated goal", coordinator.state.favorites.single().configuration.goal)
        assertEquals(original, persistence.loadSessionSnapshot("session-existing"))

        coordinator.removeFavorite(favorite.id)
        assertTrue(coordinator.state.favorites.isEmpty())
        assertEquals("updated goal", coordinator.state.currentDraft?.configuration?.goal)
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
        val favorite = NewSessionFavorite(
            id = "favorite-template",
            name = "Favorite",
            configuration = validConfiguration("Favorite"),
            updatedAtEpochMillis = 100L
        )
        val stored = (1L..20L).map { updated ->
            NewSessionDraftRecord(
                id = "draft-$updated",
                configuration = validConfiguration("Stored $updated"),
                updatedAtEpochMillis = updated,
                favoriteId = "protected-$updated"
            )
        }
        val actions: List<(NewSessionLifecycleCoordinator) -> Boolean> = listOf(
            { it.selectTab(NewSessionHubTab.Favorites) },
            { it.startBlankDraft() },
            { it.restoreDraft("draft-1") },
            { it.useFavorite("favorite-template") }
        )

        actions.forEachIndexed { index, action ->
            val persistence = MemoryNewSessionPersistence(stored, listOf(favorite))
            val coordinator = coordinator(
                persistence,
                ids = ArrayDeque(listOf("current-$index", "next-$index"))
            )
            coordinator.startBlankDraft()
            val unsaved = validConfiguration("Unsaved $index").copy(story = "story-$index")
            coordinator.updateConfiguration { unsaved }
            val currentId = coordinator.state.currentDraft?.id

            assertFalse("action $index must abort", action(coordinator))
            assertEquals(NewSessionHubTab.Custom, coordinator.state.tab)
            assertEquals(currentId, coordinator.state.currentDraft?.id)
            assertEquals(unsaved, coordinator.state.currentDraft?.configuration)
            assertEquals(20, persistence.loadDrafts().size)
            assertNotNull(coordinator.state.persistenceError)
        }
    }

    @Test
    fun customRootBecomesRestoreLocationAfterLeavingEditor() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(persistence, ids = ArrayDeque(listOf("first", "second")))
        coordinator.startBlankDraft()
        coordinator.updateConfiguration { validConfiguration("First") }
        val firstId = checkNotNull(coordinator.state.currentDraft?.id)

        assertTrue(coordinator.openSection(NewSessionSection.WorldTree, EditorScrollPosition(3, 14)))
        assertTrue(coordinator.closeSection(EditorScrollPosition(6, 22)))
        assertNull(coordinator.state.selectedSection)
        assertNull(coordinator.state.currentDraft?.lastSection)
        assertEquals(EditorScrollPosition(3, 14), coordinator.state.currentDraft?.scrollPositions?.get("root"))

        assertTrue(coordinator.startBlankDraft())
        assertTrue(coordinator.restoreDraft(firstId))
        assertNull(coordinator.state.selectedSection)
        assertNull(coordinator.state.currentDraft?.lastSection)
        assertEquals(EditorScrollPosition(3, 14), coordinator.state.currentDraft?.scrollPositions?.get("root"))
        assertEquals(EditorScrollPosition(6, 22), coordinator.state.currentDraft?.scrollPositions?.get(NewSessionSection.WorldTree.name))
    }

    @Test
    fun randomUndoRestoresOnlyOwnedFieldsAfterProtectedFieldEdits() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(
            persistence = persistence,
            randomizer = NewSessionRandomizer {
                it.copy(learnerRole = "random role", goal = "random goal", plan = "random plan")
            }
        )
        coordinator.startBlankDraft()
        val original = validConfiguration("Random protected").copy(
            story = "old story",
            sourceSelections = listOf("old source"),
            learnerImageRef = "old learner",
            storyImageRef = "old story image"
        )
        coordinator.updateConfiguration { original }
        coordinator.previewRandom()
        coordinator.applyRandom()
        coordinator.updateConfiguration {
            it.copy(
                story = "new story",
                sourceSelections = listOf("new source"),
                learnerImageRef = "new learner",
                storyImageRef = "new story image",
                dialogueStrategy = "new dialogue"
            )
        }

        coordinator.undoRandom()
        val result = checkNotNull(coordinator.state.currentDraft?.configuration)
        assertEquals(original.learnerRole, result.learnerRole)
        assertEquals(original.goal, result.goal)
        assertEquals(original.plan, result.plan)
        assertEquals("new story", result.story)
        assertEquals(listOf("new source"), result.sourceSelections)
        assertEquals("new learner", result.learnerImageRef)
        assertEquals("new story image", result.storyImageRef)
        assertEquals("new dialogue", result.dialogueStrategy)
    }

    @Test
    fun randomAndRenameBothCreateUnfavoritedPersonalCopiesOfBuiltIns() {
        val preset = FormalLearningPresets.all.first()
        val randomCoordinator = coordinator(
            MemoryNewSessionPersistence(),
            randomizer = NewSessionRandomizer { it.copy(goal = "randomized") }
        )
        randomCoordinator.useBuiltInPreset(preset)
        randomCoordinator.previewRandom()
        randomCoordinator.applyRandom()
        val randomized = checkNotNull(randomCoordinator.state.currentDraft)
        assertEquals("${preset.title} · 副本", randomized.configuration.title)
        assertNull(randomized.configuration.builtInPresetId)
        assertNull(randomized.originBuiltInPresetId)
        assertNull(randomized.favoriteId)

        val renamePersistence = MemoryNewSessionPersistence()
        val renameCoordinator = coordinator(renamePersistence)
        renameCoordinator.useBuiltInPreset(preset)
        assertTrue(renameCoordinator.saveBoundary())
        val draftId = checkNotNull(renameCoordinator.state.currentDraft?.id)
        assertTrue(renameCoordinator.renameDraft(draftId, "attempted direct rename"))
        val renamed = checkNotNull(renameCoordinator.state.currentDraft)
        assertEquals("${preset.title} · 副本", renamed.configuration.title)
        assertNull(renamed.configuration.builtInPresetId)
        assertNull(renamed.originBuiltInPresetId)
        assertNull(renamed.favoriteId)
    }

    @Test
    fun deletingOnlyActiveSavedDraftCreatesUsableBlankCustomDraft() {
        val persistence = MemoryNewSessionPersistence()
        val coordinator = coordinator(persistence, ids = ArrayDeque(listOf("active", "replacement")))
        coordinator.startBlankDraft()
        coordinator.updateConfiguration { validConfiguration("Active") }
        assertTrue(coordinator.saveBoundary())
        val activeId = checkNotNull(coordinator.state.currentDraft?.id)

        coordinator.deleteDraft(activeId)

        val replacement = checkNotNull(coordinator.state.currentDraft)
        assertNotEquals(activeId, replacement.id)
        assertEquals(NewSessionHubTab.Custom, coordinator.state.tab)
        assertNull(coordinator.state.selectedSection)
        assertTrue(replacement.configuration.title.isBlank())
        assertTrue(persistence.loadDrafts().isEmpty())
    }

    private fun coordinator(
        persistence: MemoryNewSessionPersistence,
        ids: ArrayDeque<String> = ArrayDeque((1..100).map(Int::toString)),
        randomizer: NewSessionRandomizer = DefaultNewSessionRandomizer,
        port: NewSessionCreatePort = NewSessionCreatePort(::created)
    ): NewSessionLifecycleCoordinator {
        var now = 1_000L
        return NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = port,
            randomizer = randomizer,
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
