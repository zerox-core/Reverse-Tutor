package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.ChatComposerDraft
import com.reversetutor.feature.chat.ChatDraftCodec
import com.reversetutor.feature.chat.ChatDraftStore

class SharedPreferencesChatDraftStore(
    private val preferences: ChatDraftPreferences
) : ChatDraftStore {
    constructor(context: Context) : this(
        AndroidChatDraftPreferences(
            context.getSharedPreferences("reverse-tutor-chat-drafts", Context.MODE_PRIVATE)
        )
    )

    override fun load(sessionId: String): ChatComposerDraft? =
        preferences.get(key(sessionId))?.let(ChatDraftCodec::decode)

    override fun save(sessionId: String, draft: ChatComposerDraft) {
        preferences.put(key(sessionId), ChatDraftCodec.encode(draft))
    }

    override fun clear(sessionId: String) {
        preferences.remove(key(sessionId))
    }

    private fun key(sessionId: String): String = "draft:${sessionId.trim()}"
}

interface ChatDraftPreferences {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

private class AndroidChatDraftPreferences(
    private val preferences: SharedPreferences
) : ChatDraftPreferences {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
