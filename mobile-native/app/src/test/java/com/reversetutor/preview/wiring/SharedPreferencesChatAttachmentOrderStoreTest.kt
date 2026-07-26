package com.reversetutor.preview.wiring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesChatAttachmentOrderStoreTest {
    @Test
    fun recreatedStorePreservesOrderedIdsAndEmptySaveRemovesMetadata() {
        val preferences = MemoryChatAttachmentOrderPreferences()
        SharedPreferencesChatAttachmentOrderStore(preferences).save(
            "message-1",
            listOf("attachment-message-1-1", "attachment-message-1-0")
        )

        val recreated = SharedPreferencesChatAttachmentOrderStore(preferences)
        assertEquals(
            listOf("attachment-message-1-1", "attachment-message-1-0"),
            recreated.load("message-1")
        )

        recreated.save("message-1", emptyList())
        assertNull(SharedPreferencesChatAttachmentOrderStore(preferences).load("message-1"))
    }
}

private class MemoryChatAttachmentOrderPreferences : ChatAttachmentOrderPreferences {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
