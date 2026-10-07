package com.reversetutor.preview.wiring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.SessionSettingsDocument
import com.reversetutor.feature.chat.SessionSettingsStoredState
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SessionSettingsPersistenceDeviceTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("new_session_feature_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("session_settings_feature_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun newSessionAndSettingsStoresRoundTripTheCompleteIndependentSnapshot() {
        val snapshot = NewSessionConfiguration(
            title = "概率论",
            learnerDisplayName = "小概",
            avatarVisible = false,
            deadline = "2026-09-01",
            feedbackIntensity = 5,
            story = "当前会话世界树",
            sourceSelections = listOf("source-a")
        )
        val newSessionStore = SharedPreferencesNewSessionPersistence(context)
        newSessionStore.saveSessionSnapshot("session-a", snapshot)

        assertEquals(snapshot, SharedPreferencesNewSessionPersistence(context).loadSessionSnapshot("session-a"))

        val document = SessionSettingsDocument.fromSnapshot(snapshot)
        val source = SessionSource(
            id = "source-a",
            displayName = "我的讲义",
            managedName = "概率论讲义",
            typeLabel = "PDF",
            readState = SourceReadState.Ready,
            currentSessionReferenced = true,
            referenceOwnerIds = listOf("session-a", "session-b"),
            lastUsedAtEpochMillis = 42L,
            preview = "条件概率",
            bytesRetained = true
        )
        val settingsStore = SharedPreferencesSessionSettingsStore(context)
        settingsStore.save(
            "session-a",
            SessionSettingsStoredState(document, document, listOf(source), sourceLastUsedAt = mapOf("source-a" to 99L))
        )

        val restored = SharedPreferencesSessionSettingsStore(context).load("session-a")!!
        assertEquals(snapshot, restored.applied.snapshot)
        assertEquals(listOf("session-a", "session-b"), restored.sources.single().referenceOwnerIds)
        assertEquals("我的讲义", restored.sources.single().displayName)
        assertEquals(99L, restored.sourceLastUsedAt["source-a"])
    }
}
