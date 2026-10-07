package com.reversetutor.preview.shell

import android.os.Build
import com.reversetutor.feature.chat.ChatClipboardResult
import com.reversetutor.feature.chat.PendingChatMessageDeletion
import com.reversetutor.core.data.graph.GraphSnapshot
import com.reversetutor.core.data.memory.AnchorInput
import com.reversetutor.core.data.memory.MemorySnapshot
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.MemoryItemKind
import com.reversetutor.feature.chat.ChatMemoryCategory
import com.reversetutor.feature.chat.ChatMemoryCategoryCapability
import com.reversetutor.feature.chat.ChatMemoryCommitResult
import com.reversetutor.feature.chat.ChatMemoryDraft
import com.reversetutor.feature.chat.ChatMessageDeleteResult
import com.reversetutor.feature.chat.ChatRememberedMessageMetadata
import com.reversetutor.preview.wiring.ChatRememberedMessagePreferences
import com.reversetutor.preview.wiring.SharedPreferencesChatRememberedMessageStore
import kotlinx.coroutines.runBlocking
import com.reversetutor.preview.wiring.ChatPendingDeletionPreferences
import com.reversetutor.preview.wiring.SharedPreferencesChatPendingDeletionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagePlatformAdaptersTask3BTest {
    @Test
    fun clipboardAdapterCopiesOnlyPlainMessageBody() {
        var copiedLabel: String? = null
        var copiedText: String? = null
        val adapter = AndroidChatClipboardPort { label, text ->
            copiedLabel = label
            copiedText = text
        }

        assertEquals(ChatClipboardResult.Copied, adapter.copyPlainText("正文", "附件名"))
        assertEquals("消息", copiedLabel)
        assertEquals("正文", copiedText)
    }

    @Test
    fun mediaStoreAndShareMappingsUseRealAndroidContracts() {
        val values = buildChatImageMediaStoreSpec(
            displayName = "lesson.png",
            mimeType = "image/png",
            sdkInt = Build.VERSION_CODES.Q
        )
        assertEquals("lesson.png", values.displayName)
        assertEquals("image/png", values.mimeType)
        assertTrue(values.relativePath!!.contains("Reverse Tutor"))

        val share = buildChatImageShareSpec(
            uriText = "content://media/external/images/1",
            mimeType = "image/png"
        )
        assertEquals("android.intent.action.SEND", share.action)
        assertEquals("image/png", share.mimeType)
        assertTrue(share.grantReadPermission)
    }

    @Test
    fun pendingDeletionSharedPreferencesCodecRestoresAndClearsState() {
        val preferences = RecordingPendingPreferences()
        val store = SharedPreferencesChatPendingDeletionStore(preferences)
        val pending = PendingChatMessageDeletion(
            sessionId = "session-1",
            messageId = "message-1",
            requestedAtEpochMillis = 100L,
            expiresAtEpochMillis = 5_100L
        )

        store.save(pending)
        assertEquals(pending, store.load("session-1"))
        assertEquals(listOf(pending), store.loadAll())
        val second = PendingChatMessageDeletion(
            sessionId = "session-2",
            messageId = "message-2",
            requestedAtEpochMillis = 200L,
            expiresAtEpochMillis = 5_200L
        )
        store.save(second)
        assertEquals(listOf(pending, second), store.loadAll())
        store.clear("session-1")
        assertNull(store.load("session-1"))
        assertEquals(listOf(second), store.loadAll())
    }

    @Test
    fun repositoryCapabilityBoundaryNeverClaimsAtomicDerivativeDeletion() {
        val capability = RepositoryChatMessageActionCapabilities.current

        assertTrue(capability.canDeleteSingleMessage)
        assertEquals(
            ChatMemoryCategory.entries.toSet(),
            capability.memoryCategories.keys
        )
        assertEquals(
            ChatMemoryCategoryCapability.Supported,
            capability.memoryCategories[ChatMemoryCategory.Constraint]
        )
        capability.memoryCategories
            .filterKeys { it != ChatMemoryCategory.Constraint }
            .values
            .forEach { categoryCapability ->
                val unavailable = categoryCapability as ChatMemoryCategoryCapability.Unavailable
                assertTrue(unavailable.reason.isNotBlank())
            }
        assertTrue(capability.canCreateSourceAssociatedConstraint)
        assertFalse(capability.canAtomicallyDeleteAndRestoreDerivatives)
    }

    @Test
    fun repositoryAdapterDerivesRealMemoryGraphImpactAndKeepsUnsupportedEditorOpen() = runBlocking {
        val backend = RecordingActionBackend()
        val adapter = RepositoryChatMessageActionPort(backend) { 123L }

        val impact = adapter.loadDeleteImpact("message-1", "space-1")
        assertEquals(listOf("memory-1"), impact.memories.map { it.id })
        assertEquals(listOf("node-1"), impact.graphItems.map { it.id })
        assertFalse(impact.canDeleteDerivatives)
        assertFalse(impact.canDeleteMessageOnly)
        assertEquals(
            "后端未提供派生内容溯源标记，当前不能安全删除这条消息。",
            impact.deletionBoundary
        )

        ChatMemoryCategory.entries.filterNot { it == ChatMemoryCategory.Constraint }.forEach { category ->
            assertTrue(
                adapter.remember(
                    ChatMemoryDraft("message-1", "space-1", "正文", category, "source-1")
                )
                    is ChatMemoryCommitResult.Unavailable
            )
        }
        assertEquals(
            ChatMemoryCommitResult.Saved,
            adapter.remember(
                ChatMemoryDraft(
                    messageId = "message-1",
                    spaceId = "space-1",
                    text = "必须保留出处",
                    category = ChatMemoryCategory.Constraint,
                    sourceId = "source-1"
                )
            )
        )
        assertEquals("space-1", backend.createdConstraintSpaceId)
        assertEquals(123L, backend.createdConstraintAt)
        assertEquals(
            AnchorInput(
                title = "必须保留出处",
                body = "必须保留出处",
                sourceMessageId = "message-1",
                sourceId = "source-1"
            ),
            backend.createdConstraintInput
        )
    }

    @Test
    fun repositoryDeleteAdapterDistinguishesSuccessAlreadyAbsentAndRetryableFailure() = runBlocking {
        val pending = PendingChatMessageDeletion("session-1", "message-1", 0L, 5_000L)
        val backend = RecordingDeleteBackend()
        val adapter = RepositoryChatMessageDeletePort(backend)

        backend.deleteResult = true
        assertEquals(ChatMessageDeleteResult.Success, adapter.delete(pending))

        backend.deleteResult = false
        backend.messageExists = false
        assertEquals(ChatMessageDeleteResult.AlreadyAbsent, adapter.delete(pending))
        assertEquals("session-1" to "message-1", backend.lastExistenceQuery)

        backend.messageExists = true
        assertEquals(ChatMessageDeleteResult.RetryableFailure, adapter.delete(pending))

        backend.queryFailure = true
        assertEquals(ChatMessageDeleteResult.RetryableFailure, adapter.delete(pending))
    }

    @Test
    fun rememberedMetadataPersistsByMessage() {
        val preferences = RecordingRememberedPreferences()
        val store = SharedPreferencesChatRememberedMessageStore(preferences)
        val metadata = ChatRememberedMessageMetadata("message-1", ChatMemoryCategory.Constraint, 55L)

        store.save(metadata)
        assertEquals(metadata, store.load("message-1"))
        assertEquals(setOf("message-1"), store.loadRememberedMessageIds())
    }
}

private class RecordingPendingPreferences : ChatPendingDeletionPreferences {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun entries(): Map<String, String> = values.toMap()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private class RecordingActionBackend : ChatMessageActionRepositoryBackend {
    var createdConstraintSpaceId: String? = null
    var createdConstraintInput: AnchorInput? = null
    var createdConstraintAt: Long? = null

    override suspend fun memorySnapshot(spaceId: String) = MemorySnapshot(
        anchors = emptyList(),
        notes = emptyList(),
        errors = emptyList(),
        items = listOf(
            MemoryItem("memory-1", spaceId, MemoryItemKind.Note, "函数", "正文", 1L, "message-1")
        )
    )

    override suspend fun graphSnapshot(spaceId: String) = GraphSnapshot(
        nodes = listOf(
            GraphNode("node-1", spaceId, "函数", GraphNodeKind.Concept, 2L, sourceMemoryId = "memory-1")
        ),
        edges = emptyList()
    )

    override suspend fun createConstraint(
        spaceId: String,
        input: AnchorInput,
        nowEpochMillis: Long
    ): Boolean {
        createdConstraintSpaceId = spaceId
        createdConstraintInput = input
        createdConstraintAt = nowEpochMillis
        return true
    }
}

private class RecordingDeleteBackend : ChatMessageDeleteRepositoryBackend {
    var deleteResult = false
    var messageExists = false
    var queryFailure = false
    var lastExistenceQuery: Pair<String, String>? = null

    override suspend fun deleteMessage(messageId: String): Boolean = deleteResult

    override suspend fun messageExists(sessionId: String, messageId: String): Boolean {
        lastExistenceQuery = sessionId to messageId
        if (queryFailure) error("query failed")
        return messageExists
    }
}

private class RecordingRememberedPreferences : ChatRememberedMessagePreferences {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun entries(): Map<String, String> = values.toMap()
    override fun put(key: String, value: String) { values[key] = value }
}
