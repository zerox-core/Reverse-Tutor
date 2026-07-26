package com.reversetutor.preview.shell

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionListItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormalPersonalizationAvatarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun avatarSwitchCallsSessionHomeViewModelAndPersistedPortPath() {
        val port = FakeAvatarSessionHomePort()
        composeRule.setContent {
            SessionSettingsRoute(
                destination = AppDestination.SessionSettingsPersonalization,
                sessionId = SessionId,
                sessionTitle = "Session",
                sessionHomePort = port,
                onSelectDestination = {},
                onOpenBrain = {},
                onBack = {},
            )
        }
        composeRule.waitUntil(2_000L) { port.loadCount > 0 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("session-avatar-visibility-switch")
            .assertIsOn()
            .performClick()
            .assertIsOff()
        composeRule.runOnIdle {
            assertEquals(listOf(SessionId to false), port.avatarChanges)
        }
    }

    private class FakeAvatarSessionHomePort : SessionHomePort {
        var loadCount = 0
        val avatarChanges = mutableListOf<Pair<String, Boolean>>()

        override suspend fun loadSessionCards(): List<SessionListItem> {
            loadCount += 1
            return listOf(
                SessionListItem(
                    id = SessionId,
                    title = "Session",
                    updatedAtEpochMillis = 1L,
                    pinned = false,
                    statusLabel = "Ready",
                    unreadCount = 0,
                    avatarLabel = "S"
                )
            )
        }

        override suspend fun renameSession(
            sessionId: String,
            title: String,
            nowEpochMillis: Long
        ): Boolean = true

        override suspend fun setPinned(
            sessionId: String,
            pinned: Boolean,
            nowEpochMillis: Long
        ): Boolean = true

        override suspend fun setAvatarVisible(sessionId: String, visible: Boolean): Boolean {
            avatarChanges += sessionId to visible
            return true
        }

        override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long): Boolean = true
        override suspend fun undoDelete(sessionId: String): Boolean = true
        override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long): Boolean = true
    }

    private companion object {
        const val SessionId = "session"
    }
}
