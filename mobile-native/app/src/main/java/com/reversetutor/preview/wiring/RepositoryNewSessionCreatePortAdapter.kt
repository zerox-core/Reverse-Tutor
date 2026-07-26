package com.reversetutor.preview.wiring

import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import com.reversetutor.feature.chat.NewSessionCreatePort
import com.reversetutor.feature.chat.NewSessionCreateRequest
import com.reversetutor.feature.chat.NewSessionCreated
import com.reversetutor.feature.chat.SessionListItem

class RepositoryNewSessionCreatePortAdapter(
    private val createSession: suspend (
        input: SessionCreationInput,
        nowEpochMillis: Long,
        sessionId: String
    ) -> SessionListItem,
    private val saveOpeningMessage: suspend (Message) -> Unit,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : NewSessionCreatePort {
    override suspend fun createSession(request: NewSessionCreateRequest): NewSessionCreated {
        val now = nowEpochMillis()
        val sessionId = request.attemptId.toSessionId()
        val item = createSession(request.snapshot.toCoreDraft().toCreationInput(), now, sessionId)
        if (request.snapshot.openingMessage.isNotBlank()) {
            saveOpeningMessage(
                Message(
                    id = "message-$sessionId-opening",
                    spaceId = SessionRepository.defaultSpaceId,
                    sessionId = sessionId,
                    role = MessageRole.Assistant,
                    text = request.snapshot.openingMessage,
                    createdAtEpochMillis = now
                )
            )
        }
        return NewSessionCreated(
            session = item,
            learnerRole = request.snapshot.learnerRole,
            openingMessage = request.snapshot.openingMessage
        )
    }
}

private fun String.toSessionId(): String {
    val safe = lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(72)
    return "session-${safe.ifBlank { hashCode().toUInt().toString(16) }}"
}
