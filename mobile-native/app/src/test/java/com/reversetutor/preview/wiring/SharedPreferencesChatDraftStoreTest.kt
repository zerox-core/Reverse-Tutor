package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.ChatAttachmentKind
import com.reversetutor.feature.chat.ChatAttachmentReadiness
import com.reversetutor.feature.chat.ChatComposerDraft
import com.reversetutor.feature.chat.ChatDraftAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesChatDraftStoreTest {
    @Test
    fun adapterRestoresProcessDraftPerSessionAndClearsOnlyRequestedSession() {
        val preferences = MemoryChatDraftPreferences()
        val firstProcess = SharedPreferencesChatDraftStore(preferences)
        val draft = ChatComposerDraft(
            clientRequestId = "logical-1",
            text = "unfinished",
            attachments = listOf(
                ChatDraftAttachment(
                    id = "source-1",
                    kind = ChatAttachmentKind.Source,
                    name = "notes.pdf",
                    sourceId = "source-1",
                    readiness = ChatAttachmentReadiness.Ready
                )
            )
        )
        firstProcess.save("session-a", draft)
        firstProcess.save("session-b", draft.copy(text = "other"))

        val recreatedProcess = SharedPreferencesChatDraftStore(preferences)
        assertEquals(draft, recreatedProcess.load("session-a"))
        assertEquals("other", recreatedProcess.load("session-b")?.text)

        recreatedProcess.clear("session-a")
        assertNull(recreatedProcess.load("session-a"))
        assertEquals("other", recreatedProcess.load("session-b")?.text)
    }

    @Test
    fun recreatedStoreMakesInterruptedPreparationRetryable() {
        val preferences = MemoryChatDraftPreferences()
        SharedPreferencesChatDraftStore(preferences).save(
            "session-a",
            ChatComposerDraft(
                text = "preserved",
                attachments = listOf(
                    ChatDraftAttachment(
                        id = "image-1",
                        kind = ChatAttachmentKind.Image,
                        name = "one.png",
                        uri = "content://one",
                        readiness = ChatAttachmentReadiness.Preparing
                    )
                )
            )
        )

        val restored = SharedPreferencesChatDraftStore(preferences).load("session-a")!!

        assertEquals("preserved", restored.text)
        val readiness = restored.attachments.single().readiness
        org.junit.Assert.assertTrue(readiness is ChatAttachmentReadiness.Failed)
        org.junit.Assert.assertTrue((readiness as ChatAttachmentReadiness.Failed).retryable)
    }
}

private class MemoryChatDraftPreferences : ChatDraftPreferences {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
