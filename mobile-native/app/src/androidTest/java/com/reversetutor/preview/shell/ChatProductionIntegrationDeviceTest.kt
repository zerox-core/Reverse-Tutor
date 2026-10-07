package com.reversetutor.preview.shell

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.graph.GraphNodeInput
import com.reversetutor.core.data.memory.AnchorInput
import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.feature.chat.ChatQueryCategory
import com.reversetutor.feature.chat.ChatQueryScope
import com.reversetutor.feature.chat.ChatAttachmentKind
import com.reversetutor.feature.chat.ChatAttachmentReadiness
import com.reversetutor.feature.chat.ChatComposerDraft
import com.reversetutor.feature.chat.ChatDraftAttachment
import com.reversetutor.feature.chat.ChatRoute
import com.reversetutor.preview.wiring.SharedPreferencesChatAttachmentOrderStore
import com.reversetutor.preview.wiring.SharedPreferencesChatDraftStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatProductionIntegrationDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sessions = DataModule.sessionRepository(context)
    private val messages = DataModule.messageRepository(context)

    @Before
    fun reset() = runBlocking {
        clearFeatureStores()
        DataModule.localDataWipeRepository(context).wipeLocalData(1L)
    }

    @After
    fun cleanUp() = runBlocking {
        clearFeatureStores()
        DataModule.localDataWipeRepository(context).wipeLocalData(2L)
    }

    @Test
    fun repositoryQueryPortReadsCurrentAndAllMessageGraphAndSourceRecords() = runBlocking {
        createSession("session-a", "A")
        createSession("session-b", "B")
        messages.sendUserMessage("session-a", "函数 current", 10L, "message-a")
        messages.sendUserMessage("session-b", "函数 all", 11L, "message-b")
        DataModule.sourceRepository(context).importSource(
            SourceImportInput(
                requestId = 1L,
                fileName = "函数.pdf",
                mimeType = "text/markdown",
                text = "函数 source",
                sourceId = "source-a"
            ),
            nowEpochMillis = 12L
        )
        DataModule.memoryRepository(context).createAnchor(
            AnchorInput(
                title = "函数 evidence",
                body = "graph evidence",
                sourceMessageId = "message-a",
                sourceId = "source-a"
            ),
            nowEpochMillis = 13L,
            anchorId = "anchor-query"
        )
        DataModule.graphRepository(context).saveNode(
            GraphNodeInput(
                id = "node-a",
                label = "函数 graph",
                kind = GraphNodeKind.Concept,
                sourceMemoryId = "memory-anchor-query"
            ),
            nowEpochMillis = 14L
        )
        val port = RepositoryChatReferenceQueryPort(
            sessionRepository = sessions,
            messageRepository = messages,
            sourceRepository = DataModule.sourceRepository(context),
            graphRepository = DataModule.graphRepository(context),
            sourceIdsForSession = { id -> if (id == "session-a") setOf("source-a") else emptySet() }
        )

        val current = port.query("函数", ChatQueryScope.CurrentSession, "session-a")
        assertEquals(
            listOf(ChatQueryCategory.Session, ChatQueryCategory.Graph, ChatQueryCategory.Source),
            current.results.map { it.category }
        )

        val all = port.query("函数", ChatQueryScope.AllSessions, "session-a")
        assertEquals(4, all.results.size)
        assertEquals(setOf("session-a", "session-b"), all.results.mapNotNull { it.target.sessionId }.toSet())
    }

    @Test
    fun productionChatRouteSendsThroughRepositoryAdapterAndClearsSharedPreferencesDraft() {
        runBlocking { createSession("session-route", "Route") }
        val draftStore = SharedPreferencesChatDraftStore(context)
        val orderStore = SharedPreferencesChatAttachmentOrderStore(context)
        compose.setContent {
            MaterialTheme {
                ChatRoute(
                    messageRepository = messages,
                    sessionId = "session-route",
                    sessionTitle = "Route",
                    draftStore = draftStore,
                    attachmentOrderStore = orderStore
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("production send")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(5_000L) {
            runBlocking { messages.listMessages("session-route").any { it.text == "production send" } }
        }

        assertEquals(1, runBlocking { messages.listMessages("session-route").count { it.text == "production send" } })
        assertNull(SharedPreferencesChatDraftStore(context).load("session-route"))
    }

    @Test
    fun actualSharedPreferencesRestoreNormalizesInterruptedPreparation() {
        SharedPreferencesChatDraftStore(context).save(
            "session-restart",
            ChatComposerDraft(
                text = "preserved",
                attachments = listOf(
                    ChatDraftAttachment(
                        id = "image-restart",
                        kind = ChatAttachmentKind.Image,
                        name = "restart.png",
                        uri = "content://restart",
                        readiness = ChatAttachmentReadiness.Preparing
                    )
                )
            )
        )

        val restored = SharedPreferencesChatDraftStore(context).load("session-restart")!!
        assertEquals("preserved", restored.text)
        assertEquals(true, (restored.attachments.single().readiness as ChatAttachmentReadiness.Failed).retryable)
    }

    @Test
    fun visibleRetryUsesProductionSendPathAndRetainsSameFailedDraft() {
        runBlocking { createSession("session-retry", "Retry") }
        val draftStore = SharedPreferencesChatDraftStore(context)
        val failedDraft = ChatComposerDraft(
            clientRequestId = "retry-request",
            attachments = listOf(
                ChatDraftAttachment(
                    id = "invalid-ready",
                    kind = ChatAttachmentKind.Source,
                    name = " ",
                    readiness = ChatAttachmentReadiness.Ready
                )
            )
        )
        draftStore.save("session-retry", failedDraft)
        compose.setContent {
            MaterialTheme {
                ChatRoute(
                    messageRepository = messages,
                    sessionId = "session-retry",
                    sessionTitle = "Retry",
                    draftStore = draftStore,
                    attachmentOrderStore = SharedPreferencesChatAttachmentOrderStore(context)
                )
            }
        }

        compose.onNodeWithContentDescription("发送").performClick()
        compose.onNodeWithText("发送失败，请重试。").assertExists()
        assertEquals("retry-request", SharedPreferencesChatDraftStore(context).load("session-retry")?.clientRequestId)

        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("发送失败，请重试。").assertExists()
        assertEquals("retry-request", SharedPreferencesChatDraftStore(context).load("session-retry")?.clientRequestId)
        assertEquals(0, runBlocking { messages.listMessages("session-retry").size })
    }

    private suspend fun createSession(id: String, title: String) {
        sessions.createSession(
            input = SessionCreationInput(title, "Learner", "Goal", "Profile"),
            nowEpochMillis = 5L,
            sessionId = id
        )
    }

    private fun clearFeatureStores() {
        listOf(
            "reverse-tutor-chat-drafts",
            "reverse-tutor-chat-attachment-order"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
