package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.ChatMemoryCategory
import com.reversetutor.feature.chat.ChatRememberedMessageMetadata
import com.reversetutor.feature.chat.ChatRememberedMessageStore

class SharedPreferencesChatRememberedMessageStore(
    private val preferences: ChatRememberedMessagePreferences
) : ChatRememberedMessageStore {
    constructor(context: Context) : this(
        AndroidChatRememberedMessagePreferences(
            context.getSharedPreferences("reverse-tutor-chat-remembered", Context.MODE_PRIVATE)
        )
    )

    override fun load(messageId: String): ChatRememberedMessageMetadata? =
        preferences.get(key(messageId))?.let { decodeRemembered(messageId, it) }

    override fun loadRememberedMessageIds(): Set<String> = preferences.entries()
        .filterKeys { it.startsWith("remembered:") }
        .mapNotNullTo(linkedSetOf()) { (key, value) ->
            val messageId = key.removePrefix("remembered:")
            decodeRemembered(messageId, value)?.messageId
        }

    override fun save(metadata: ChatRememberedMessageMetadata) {
        preferences.put(key(metadata.messageId), "${metadata.category.name}:${metadata.rememberedAtEpochMillis}")
    }

    private fun key(messageId: String): String = "remembered:${messageId.trim()}"
}

interface ChatRememberedMessagePreferences {
    fun get(key: String): String?
    fun entries(): Map<String, String>
    fun put(key: String, value: String)
}

private class AndroidChatRememberedMessagePreferences(
    private val preferences: SharedPreferences
) : ChatRememberedMessagePreferences {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun entries(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

private fun decodeRemembered(messageId: String, encoded: String): ChatRememberedMessageMetadata? = runCatching {
    val separator = encoded.lastIndexOf(':')
    require(separator > 0)
    ChatRememberedMessageMetadata(
        messageId = messageId,
        category = ChatMemoryCategory.valueOf(encoded.substring(0, separator)),
        rememberedAtEpochMillis = encoded.substring(separator + 1).toLong()
    )
}.getOrNull()
