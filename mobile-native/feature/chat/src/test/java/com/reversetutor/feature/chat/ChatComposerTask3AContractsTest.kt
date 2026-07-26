package com.reversetutor.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerTask3AContractsTest {
    @Test
    fun draftCodecRestoresTextQuoteMixedAttachmentsAndOrder() {
        val draft = ChatComposerDraft(
            clientRequestId = "request-1",
            text = "line one\nline two",
            quote = ChatQuoteTarget("message-1", "quoted\ntext"),
            attachments = listOf(
                readyAttachment("image", ChatAttachmentKind.Image, uri = "content://image/1"),
                readyAttachment("source", ChatAttachmentKind.Source, sourceId = "source-1"),
                failedAttachment("camera", ChatAttachmentKind.Camera, "camera write failed")
            )
        )

        assertEquals(draft, ChatDraftCodec.decode(ChatDraftCodec.encode(draft)))
    }

    @Test
    fun draftCodecTurnsInterruptedPreparingIntoRecoverableFailure() {
        val draft = ChatComposerDraft(
            text = "keep text",
            attachments = listOf(
                readyAttachment("preparing", ChatAttachmentKind.Image).copy(
                    readiness = ChatAttachmentReadiness.Preparing
                )
            )
        )

        val restored = ChatDraftCodec.decode(ChatDraftCodec.encode(draft))!!

        assertEquals("keep text", restored.text)
        val readiness = restored.attachments.single().readiness
        assertTrue(readiness is ChatAttachmentReadiness.Failed)
        assertTrue((readiness as ChatAttachmentReadiness.Failed).retryable)
        assertFalse(restored.canSend)
    }

    @Test
    fun attachmentPolicyEnforcesNineTwentyMbAndPreservesMixedOrder() {
        var draft = ChatComposerDraft()
        repeat(9) { index ->
            val result = ChatAttachmentPolicy.add(
                draft,
                readyAttachment("item-$index", if (index % 2 == 0) ChatAttachmentKind.Image else ChatAttachmentKind.Source)
            )
            assertTrue(result is ChatAttachmentMutation.Accepted)
            draft = (result as ChatAttachmentMutation.Accepted).draft
        }

        val tenth = ChatAttachmentPolicy.add(draft, readyAttachment("tenth", ChatAttachmentKind.Image))
        assertEquals(ChatAttachmentRejection.TooMany, (tenth as ChatAttachmentMutation.Rejected).reason)
        assertEquals((0..8).map { "item-$it" }, draft.attachments.map { it.id })

        val oversized = ChatAttachmentPolicy.add(
            ChatComposerDraft(attachments = draft.attachments.take(1)),
            readyAttachment("large", ChatAttachmentKind.Image, sizeBytes = 20L * 1024L * 1024L + 1L)
        )
        assertEquals(ChatAttachmentRejection.ImageTooLarge, (oversized as ChatAttachmentMutation.Rejected).reason)
        assertEquals(listOf("item-0"), oversized.draft.attachments.map { it.id })

        val reordered = ChatAttachmentPolicy.move(draft, fromIndex = 8, toIndex = 1)
        assertEquals(listOf("item-0", "item-8", "item-1"), reordered.attachments.take(3).map { it.id })
    }

    @Test
    fun unresolvedAttachmentFailureDisablesSendAndRetryCanRecoverIt() {
        val failed = failedAttachment("failed", ChatAttachmentKind.Image, "cannot read")
        val draft = ChatComposerDraft(text = "ready text", attachments = listOf(failed))

        assertFalse(draft.canSend)
        val preparing = ChatAttachmentPolicy.retry(draft, "failed")
        assertTrue(preparing.attachments.single().readiness is ChatAttachmentReadiness.Preparing)
        val ready = ChatAttachmentPolicy.markReady(preparing, "failed")
        assertTrue(ready.canSend)
    }

    @Test
    fun successfulSendClearsDraftFailurePreservesItAndDuplicateTapIsBlocked() = runTest {
        val store = RecordingDraftStore()
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val port = ChatSendPort { draft ->
            requests += draft.clientRequestId
            release.await()
            ChatSendSubmission.Success("message-1")
        }
        val coordinator = ChatSendCoordinator(store, port)
        val draft = ChatComposerDraft(clientRequestId = "logical-1", text = "send")
        store.save("session-1", draft)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.send("session-1", draft)
        }
        val duplicate = coordinator.send("session-1", draft)
        assertEquals(ChatSendAttempt.DuplicateBlocked, duplicate)
        release.complete(Unit)
        assertEquals(ChatSendAttempt.Sent("message-1"), first.await())
        assertNull(store.load("session-1"))
        assertEquals(listOf("logical-1"), requests)

        val failingStore = RecordingDraftStore()
        val failing = ChatSendCoordinator(failingStore) {
            ChatSendSubmission.Failure("offline", retryable = true)
        }
        val failure = failing.send("session-2", draft)
        assertEquals(ChatSendAttempt.Failed("offline", retryable = true), failure)
        assertEquals(draft, failingStore.load("session-2"))
    }

    @Test
    fun cameraPermissionStateDoesNotLoopAndOffersSettingsWhenPermanent() {
        assertEquals(
            ChatPermissionAction.RequestPermission,
            ChatCameraPermissionMachine.actionFor(ChatPermissionState.Requestable)
        )
        assertEquals(
            ChatPermissionState.PermanentlyDenied,
            ChatCameraPermissionMachine.afterResult(granted = false, mayAskAgain = false)
        )
        assertEquals(
            ChatPermissionAction.OpenSettings,
            ChatCameraPermissionMachine.actionFor(ChatPermissionState.PermanentlyDenied)
        )
        assertEquals(
            ChatPermissionAction.LaunchCamera,
            ChatCameraPermissionMachine.actionFor(ChatPermissionState.Granted)
        )
    }

    @Test
    fun queryScopePreservesTextAndMultipleResultsRequireCategorizedSelection() {
        val results = listOf(
            ChatQueryResult("message-1", ChatQueryCategory.Session, "函数", "当前会话", ChatQueryTarget.Message("session-1", "message-1")),
            ChatQueryResult("node-1", ChatQueryCategory.Graph, "函数节点", "会话图谱", ChatQueryTarget.GraphNode("session-1", "node-1")),
            ChatQueryResult("source-1", ChatQueryCategory.Source, "函数资料", "source.pdf", ChatQueryTarget.Source("session-1", "source-1"))
        )
        val current = ChatReferenceQueryState(query = "函数").withScope(ChatQueryScope.AllSessions)
        val ready = current.withResults(results, offline = true)

        assertEquals("函数", ready.query)
        assertEquals(ChatQueryScope.AllSessions, ready.scope)
        assertEquals(listOf("会话", "图谱", "资料"), ChatQueryCategory.entries.map { it.label })
        assertTrue(ready.selectionRequired)
        assertTrue(ready.loadState is ChatQueryLoadState.Offline)
        assertNull(ready.automaticNavigation)

        val selected = ready.select("node-1")
        assertNotNull(selected)
        assertEquals(ChatQueryHighlight.GraphNode("session-1", "node-1"), selected!!.highlight)
    }

    private fun readyAttachment(
        id: String,
        kind: ChatAttachmentKind,
        uri: String? = null,
        sourceId: String? = null,
        sizeBytes: Long? = 1L
    ) = ChatDraftAttachment(
        id = id,
        kind = kind,
        name = "$id.bin",
        mimeType = if (kind == ChatAttachmentKind.Source) null else "image/png",
        uri = uri,
        sourceId = sourceId,
        sizeBytes = sizeBytes,
        readiness = ChatAttachmentReadiness.Ready
    )

    private fun failedAttachment(id: String, kind: ChatAttachmentKind, reason: String) =
        readyAttachment(id, kind).copy(readiness = ChatAttachmentReadiness.Failed(reason, retryable = true))
}

private class RecordingDraftStore : ChatDraftStore {
    private val values = mutableMapOf<String, ChatComposerDraft>()

    override fun load(sessionId: String): ChatComposerDraft? = values[sessionId]

    override fun save(sessionId: String, draft: ChatComposerDraft) {
        values[sessionId] = draft
    }

    override fun clear(sessionId: String) {
        values.remove(sessionId)
    }
}
