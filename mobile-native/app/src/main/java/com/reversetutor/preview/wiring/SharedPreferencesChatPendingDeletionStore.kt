package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.ChatPendingDeletionStore
import com.reversetutor.feature.chat.PendingChatMessageDeletion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class SharedPreferencesChatPendingDeletionStore(
    private val preferences: ChatPendingDeletionPreferences
) : ChatPendingDeletionStore {
    constructor(context: Context) : this(
        AndroidChatPendingDeletionPreferences(
            context.getSharedPreferences("reverse-tutor-chat-pending-deletions", Context.MODE_PRIVATE)
        )
    )

    override fun load(sessionId: String): PendingChatMessageDeletion? =
        preferences.get(key(sessionId))?.let(::decodePendingDeletion)

    override fun loadAll(): List<PendingChatMessageDeletion> = preferences.entries()
        .filterKeys { it.startsWith("pending:") }
        .values
        .mapNotNull(::decodePendingDeletion)
        .sortedBy { it.requestedAtEpochMillis }

    override fun save(pending: PendingChatMessageDeletion) {
        preferences.put(key(pending.sessionId), encodePendingDeletion(pending))
    }

    override fun clear(sessionId: String) {
        preferences.remove(key(sessionId))
    }

    private fun key(sessionId: String): String = "pending:${sessionId.trim()}"
}

interface ChatPendingDeletionPreferences {
    fun get(key: String): String?
    fun entries(): Map<String, String>
    fun put(key: String, value: String)
    fun remove(key: String)
}

private class AndroidChatPendingDeletionPreferences(
    private val preferences: SharedPreferences
) : ChatPendingDeletionPreferences {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun entries(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

private fun encodePendingDeletion(pending: PendingChatMessageDeletion): String {
    val bytes = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeUTF(pending.sessionId)
            data.writeUTF(pending.messageId)
            data.writeLong(pending.requestedAtEpochMillis)
            data.writeLong(pending.expiresAtEpochMillis)
        }
    }.toByteArray()
    val digits = "0123456789abcdef"
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(digits[value ushr 4])
            append(digits[value and 0x0f])
        }
    }
}

private fun decodePendingDeletion(encoded: String): PendingChatMessageDeletion? = runCatching {
    require(encoded.length % 2 == 0)
    val bytes = ByteArray(encoded.length / 2) { index ->
        val high = encoded[index * 2].digitToInt(16)
        val low = encoded[index * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
    DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        PendingChatMessageDeletion(
            sessionId = data.readUTF(),
            messageId = data.readUTF(),
            requestedAtEpochMillis = data.readLong(),
            expiresAtEpochMillis = data.readLong()
        )
    }
}.getOrNull()
