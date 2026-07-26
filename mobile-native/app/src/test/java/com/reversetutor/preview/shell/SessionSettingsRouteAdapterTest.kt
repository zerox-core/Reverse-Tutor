package com.reversetutor.preview.shell

import com.reversetutor.feature.chat.InMemorySessionSettingsStore
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionHomeViewModel
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatGenerationUiState
import com.reversetutor.feature.chat.buildChatRouteUiState
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSettingsRouteAdapterTest {
    @Test
    fun productionCoordinatorFactoryPersistsAppliedSnapshotThroughRealRouteAdapter() {
        val persistence = RecordingNewSessionPersistence()
        val factory = ProductionSessionSettingsCoordinatorFactory(
            store = InMemorySessionSettingsStore(),
            persistence = persistence
        )
        val coordinator = factory.create(
            sessionId = "session-a",
            snapshot = NewSessionConfiguration(title = "Old", learnerRole = "Student"),
            sources = emptyList(),
            onSourcesChanged = {}
        )
        assertFalse(coordinator.deleteCapability.available)

        coordinator.editProfile { it.copy(title = "New") }
        coordinator.editProfile { it.copy(learnerDisplayName = "小概") }
        coordinator.setLearnerImageRef("content://avatar/new")
        coordinator.commitTextBoundary()

        assertEquals("New", persistence.sessions.getValue("session-a").title)
        val chatState = buildChatRouteUiState(
            sessionTitle = "New",
            records = emptyList(),
            composer = ChatComposerState(""),
            generation = ChatGenerationUiState.Idle,
            learnerRoleFallback = "fallback",
            sessionSnapshot = persistence.sessions.getValue("session-a")
        )
        assertEquals("小概", chatState.learnerName)
        assertEquals("content://avatar/new", chatState.learnerImageRef)

        refreshSessionSettingsSources(coordinator, listOf(source("source-a")))
        assertEquals(1, coordinator.state.sources.size)
        refreshSessionSettingsSources(coordinator, emptyList())
        assertTrue(coordinator.state.sources.isEmpty())
    }

    @Test
    fun task2ADelegateDrivesRealSessionHomeCoordinatorRequestDismissConfirmAndUndo() {
        val port = RecordingSessionHomePort()
        val viewModel = SessionHomeViewModel(port, CoroutineScope(Dispatchers.Unconfined), nowEpochMillis = { 100L })
        var staged = false
        val delegate = createTask2ADeletionDelegate(viewModel, "session-a") { staged = it }

        delegate.request()
        assertEquals("session-a", viewModel.uiState.value.pendingDelete?.id)
        delegate.dismiss()
        assertNull(viewModel.uiState.value.pendingDelete)
        delegate.request()
        delegate.confirm()
        assertTrue(staged)
        assertEquals("session-a", viewModel.uiState.value.undo?.session?.id)
        delegate.undo()
        assertFalse(staged)
        assertNull(viewModel.uiState.value.undo)
        assertEquals(listOf("stage:session-a", "cancel:session-a", "undo:session-a"), port.calls)
    }

    private fun source(id: String) = SessionSource(
        id = id,
        displayName = id,
        managedName = id,
        typeLabel = "Text",
        readState = SourceReadState.Ready,
        currentSessionReferenced = true,
        referenceOwnerIds = listOf("session-a"),
        lastUsedAtEpochMillis = 1L,
        preview = "body",
        bytesRetained = true
    )
}

private class RecordingNewSessionPersistence : NewSessionPersistence {
    val sessions = mutableMapOf<String, NewSessionConfiguration>()
    override fun loadDrafts(): List<NewSessionDraftRecord> = emptyList()
    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) = Unit
    override fun loadFavorites(): List<NewSessionFavorite> = emptyList()
    override fun replaceFavorites(favorites: List<NewSessionFavorite>) = Unit
    override fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration) {
        sessions[sessionId] = snapshot
    }
    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = sessions[sessionId]
    override fun saveSessionSnapshot(sessionId: String, snapshot: NewSessionConfiguration) {
        sessions[sessionId] = snapshot
    }
}

private class RecordingSessionHomePort : SessionHomePort {
    val calls = mutableListOf<String>()
    private val item = SessionListItem("session-a", "Session", 1L, false, "", 0, "S", learnerRole = "Student")
    override suspend fun loadSessionCards(): List<SessionListItem> = listOf(item)
    override suspend fun renameSession(sessionId: String, title: String, nowEpochMillis: Long) = true
    override suspend fun setPinned(sessionId: String, pinned: Boolean, nowEpochMillis: Long) = true
    override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long) = true.also { calls += "stage:$sessionId" }
    override suspend fun undoDelete(sessionId: String) = true.also { calls += "undo:$sessionId" }
    override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long) = true
    override suspend fun cancelScheduledDelete(sessionId: String) { calls += "cancel:$sessionId" }
}
