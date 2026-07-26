package com.reversetutor.feature.chat

import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatFix1ContractsTest {
    @Test
    fun scopeChangeRefreshesImmediatelyAndStaleReverseResponseCannotOverwrite() = runTest {
        val requests = mutableListOf<PendingQuery>()
        val port = ChatReferenceQueryPort { query, scope, _ ->
            val pending = PendingQuery(query, scope)
            requests += pending
            withContext(NonCancellable) { pending.response.await() }
        }
        val coordinator = ChatReferenceQueryCoordinator(
            sessionId = "session-1",
            queryPort = port,
            scope = this
        )

        coordinator.onQueryChange("函数")
        coordinator.submit()
        runCurrent()
        val firstToken = coordinator.requestToken
        assertEquals(ChatQueryLoadState.Loading, coordinator.state.loadState)

        coordinator.onScopeChange(ChatQueryScope.AllSessions)
        runCurrent()
        assertEquals("函数", coordinator.state.query)
        assertEquals(ChatQueryScope.AllSessions, coordinator.state.scope)
        assertEquals(ChatQueryLoadState.Loading, coordinator.state.loadState)
        assertTrue(coordinator.requestToken > firstToken)
        assertEquals(2, requests.size)

        requests[1].response.complete(
            ChatQueryResponse(listOf(queryResult("all", "all sessions")))
        )
        runCurrent()
        assertEquals(listOf("all"), coordinator.state.results.map { it.id })

        requests[0].response.complete(
            ChatQueryResponse(listOf(queryResult("stale", "stale current")))
        )
        advanceUntilIdle()
        assertEquals(ChatQueryScope.AllSessions, coordinator.state.scope)
        assertEquals(listOf("all"), coordinator.state.results.map { it.id })
    }

    @Test
    fun queryTextChangeInvalidatesAnInflightRequestBeforeNextSubmit() = runTest {
        val response = CompletableDeferred<ChatQueryResponse>()
        val coordinator = ChatReferenceQueryCoordinator(
            sessionId = "session-1",
            queryPort = ChatReferenceQueryPort { _, _, _ ->
                withContext(NonCancellable) { response.await() }
            },
            scope = this
        )
        coordinator.onQueryChange("old")
        coordinator.submit()
        runCurrent()
        val oldToken = coordinator.requestToken

        coordinator.onQueryChange("new")
        assertTrue(coordinator.requestToken > oldToken)
        assertTrue(coordinator.state.results.isEmpty())
        response.complete(ChatQueryResponse(listOf(queryResult("stale", "old"))))
        advanceUntilIdle()

        assertEquals("new", coordinator.state.query)
        assertTrue(coordinator.state.results.isEmpty())
    }

    @Test
    fun delayedResponsePreservesCategorySelectedWhileLoading() = runTest {
        val response = CompletableDeferred<ChatQueryResponse>()
        val coordinator = ChatReferenceQueryCoordinator(
            sessionId = "session-1",
            queryPort = ChatReferenceQueryPort { _, _, _ -> response.await() },
            scope = this
        )
        coordinator.onQueryChange("函数")
        coordinator.submit()
        runCurrent()

        coordinator.updateCategory(ChatQueryCategory.Graph)
        assertEquals(ChatQueryCategory.Graph, coordinator.state.selectedCategory)

        response.complete(ChatQueryResponse(listOf(queryResult("result", "函数结果"))))
        advanceUntilIdle()

        assertEquals(ChatQueryCategory.Graph, coordinator.state.selectedCategory)
        assertEquals(listOf("result"), coordinator.state.results.map { it.id })
        assertTrue(coordinator.state.loadState is ChatQueryLoadState.Ready)
    }

    @Test
    fun productionSendAdapterRetriesSameLogicalRequestAndPreservesDraftOnFailure() = runTest {
        val store = RecordingFix1DraftStore()
        val requests = mutableListOf<ChatRepositorySendRequest>()
        var succeeds = false
        val adapter = ChatRepositorySendAdapter("session-1") { request ->
            requests += request
            succeeds
        }
        val coordinator = ChatSendCoordinator(store, adapter)
        val draft = ChatComposerDraft(clientRequestId = "logical-1", text = "retry me")

        assertEquals(
            ChatSendAttempt.Failed("发送失败，请重试。", retryable = true),
            coordinator.send("session-1", draft)
        )
        assertEquals(draft, store.load("session-1"))

        succeeds = true
        assertEquals(ChatSendAttempt.Sent("logical-1"), coordinator.send("session-1", draft))
        assertEquals(listOf("logical-1", "logical-1"), requests.map { it.messageId })
        assertFalse(requests[0] === requests[1])
    }

    @Test
    fun successfulMixedSendMetadataRestoresRoomReloadOrderWithoutChangingNames() = runTest {
        val draftStore = RecordingFix1DraftStore()
        val orderStore = RecordingAttachmentOrderStore()
        val coordinator = ChatSendCoordinator(
            draftStore = draftStore,
            sendPort = ChatSendPort { ChatSendSubmission.Success("message-1") },
            attachmentOrderStore = orderStore
        )
        val draft = ChatComposerDraft(
            clientRequestId = "message-1",
            text = "mixed",
            attachments = listOf(
                attachment("draft-image", "z-image.png", ChatAttachmentKind.Image),
                attachment("draft-source", "a-source.pdf", ChatAttachmentKind.Source)
            )
        )

        assertEquals(ChatSendAttempt.Sent("message-1"), coordinator.send("session-1", draft))
        assertEquals(
            listOf("attachment-message-1-0", "attachment-message-1-1"),
            orderStore.idsByMessage.getValue("message-1")
        )

        val roomNameSorted = MessageRecord(
            message = Message("message-1", "space", "session-1", MessageRole.User, "mixed", 1L),
            attachments = listOf(
                persistedAttachment("attachment-message-1-1", "a-source.pdf", "source-1", null),
                persistedAttachment("attachment-message-1-0", "z-image.png", null, "image/png")
            ),
            quote = null
        )
        val restored = applyStoredAttachmentOrder(listOf(roomNameSorted), orderStore)
        val ui = ChatUiState.from("session", restored, ChatComposerState(""))

        assertEquals(listOf("z-image.png", "a-source.pdf"), ui.messages.single().attachments.map { it.name })
        assertEquals(listOf("z-image.png", "a-source.pdf"), restored.single().attachments.map { it.name })
    }

    private fun queryResult(id: String, summary: String) = ChatQueryResult(
        id = id,
        category = ChatQueryCategory.Session,
        summary = summary,
        sourceIdentity = "session",
        target = ChatQueryTarget.Message("session-1", id)
    )

    private fun attachment(id: String, name: String, kind: ChatAttachmentKind) = ChatDraftAttachment(
        id = id,
        kind = kind,
        name = name,
        mimeType = if (kind == ChatAttachmentKind.Image) "image/png" else null,
        sourceId = if (kind == ChatAttachmentKind.Source) "source-1" else null,
        uri = if (kind == ChatAttachmentKind.Image) "content://image" else null,
        readiness = ChatAttachmentReadiness.Ready
    )

    private fun persistedAttachment(id: String, name: String, sourceId: String?, mimeType: String?) =
        MessageAttachment(id, "space", "message-1", name, mimeType, "content://$id", sourceId)
}

private data class PendingQuery(
    val query: String,
    val scope: ChatQueryScope,
    val response: CompletableDeferred<ChatQueryResponse> = CompletableDeferred()
)

private class RecordingFix1DraftStore : ChatDraftStore {
    private val values = mutableMapOf<String, ChatComposerDraft>()
    override fun load(sessionId: String): ChatComposerDraft? = values[sessionId]
    override fun save(sessionId: String, draft: ChatComposerDraft) { values[sessionId] = draft }
    override fun clear(sessionId: String) { values.remove(sessionId) }
}

private class RecordingAttachmentOrderStore : ChatAttachmentOrderStore {
    val idsByMessage = mutableMapOf<String, List<String>>()
    override fun load(messageId: String): List<String>? = idsByMessage[messageId]
    override fun save(messageId: String, attachmentIds: List<String>) { idsByMessage[messageId] = attachmentIds }
}
