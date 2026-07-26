package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.ChatAttachmentOrderStore

class SharedPreferencesChatAttachmentOrderStore(
    private val preferences: ChatAttachmentOrderPreferences
) : ChatAttachmentOrderStore {
    constructor(context: Context) : this(
        AndroidChatAttachmentOrderPreferences(
            context.getSharedPreferences("reverse-tutor-chat-attachment-order", Context.MODE_PRIVATE)
        )
    )

    override fun load(messageId: String): List<String>? =
        preferences.get(key(messageId))
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.toList()

    override fun save(messageId: String, attachmentIds: List<String>) {
        val normalized = attachmentIds.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalized.isEmpty()) {
            preferences.remove(key(messageId))
        } else {
            preferences.put(key(messageId), normalized.joinToString("\n"))
        }
    }

    private fun key(messageId: String): String = "attachment-order:${messageId.trim()}"
}

interface ChatAttachmentOrderPreferences {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

private class AndroidChatAttachmentOrderPreferences(
    private val preferences: SharedPreferences
) : ChatAttachmentOrderPreferences {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
