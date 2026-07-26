package com.reversetutor.preview.wiring

import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.model.Message
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionCreateRequest
import com.reversetutor.feature.chat.SessionListItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryNewSessionCreatePortAdapterTest {
    @Test
    fun repositoryAdapterUsesStableAttemptAndPersistsConfiguredOpeningMessage() = runBlocking {
        val inputs = mutableListOf<SessionCreationInput>()
        val sessionIds = mutableListOf<String>()
        val messages = mutableListOf<Message>()
        val adapter = RepositoryNewSessionCreatePortAdapter(
            createSession = { input, now, sessionId ->
                inputs += input
                sessionIds += sessionId
                SessionListItem(
                    id = sessionId,
                    title = input.title,
                    updatedAtEpochMillis = now,
                    pinned = false,
                    statusLabel = "created",
                    unreadCount = 0,
                    avatarLabel = input.title.take(1),
                    learnerRole = input.role
                )
            },
            saveOpeningMessage = { messages += it },
            nowEpochMillis = { 123L }
        )
        val request = NewSessionCreateRequest(
            attemptId = "create-ABC_123",
            draftId = "draft-1",
            snapshot = NewSessionConfiguration(
                title = "  Session  ",
                learnerRole = "  Curious learner  ",
                openingMessage = "Please start with an example."
            )
        )

        val first = adapter.createSession(request)
        val retry = adapter.createSession(request)

        assertEquals(sessionIds[0], sessionIds[1])
        assertEquals(first.session.id, retry.session.id)
        assertEquals(2, inputs.size)
        assertEquals("Session", inputs.first().title)
        assertTrue(inputs.first().goal.contains("未填写"))
        assertTrue(inputs.first().profileText.contains("未填写"))
        assertEquals(messages[0].id, messages[1].id)
        assertEquals(request.snapshot.openingMessage, messages.last().text)
        assertEquals(request.snapshot.learnerRole, first.learnerRole)
        assertEquals(request.snapshot.openingMessage, first.openingMessage)
    }

    @Test
    fun blankOpeningMessageDoesNotInsertSyntheticChatMessage() = runBlocking {
        val messages = mutableListOf<Message>()
        val adapter = RepositoryNewSessionCreatePortAdapter(
            createSession = { input, now, sessionId ->
                SessionListItem(
                    id = sessionId,
                    title = input.title,
                    updatedAtEpochMillis = now,
                    pinned = false,
                    statusLabel = "created",
                    unreadCount = 0,
                    avatarLabel = "S",
                    learnerRole = input.role
                )
            },
            saveOpeningMessage = { messages += it },
            nowEpochMillis = { 1L }
        )

        adapter.createSession(
            NewSessionCreateRequest(
                attemptId = "blank-opening",
                draftId = "draft",
                snapshot = NewSessionConfiguration(
                    title = "Session",
                    learnerRole = "Learner",
                    openingMessage = ""
                )
            )
        )

        assertTrue(messages.isEmpty())
    }
}
