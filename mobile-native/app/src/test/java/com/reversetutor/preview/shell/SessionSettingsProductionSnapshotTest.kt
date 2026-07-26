package com.reversetutor.preview.shell

import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSettingsProductionSnapshotTest {
    @Test
    fun appliedSettingsReplaceOnlyTheCurrentSessionSnapshotAndTriggerOwnerRefresh() {
        val persistence = RecordingPersistence().apply {
            sessions["session-b"] = NewSessionConfiguration(title = "其他会话", sourceSelections = listOf("source-old"))
        }
        var refreshes = 0
        val applied = NewSessionConfiguration(
            title = "概率论",
            learnerDisplayName = "小概",
            feedbackIntensity = 5,
            story = "当前会话世界树",
            sourceSelections = listOf("source-new")
        )

        saveAppliedSessionSnapshot(persistence, "session-a", applied) { refreshes += 1 }

        assertEquals(applied, persistence.sessions["session-a"])
        assertEquals(listOf("source-old"), persistence.sessions.getValue("session-b").sourceSelections)
        assertEquals(1, refreshes)
    }
}

private class RecordingPersistence : NewSessionPersistence {
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
