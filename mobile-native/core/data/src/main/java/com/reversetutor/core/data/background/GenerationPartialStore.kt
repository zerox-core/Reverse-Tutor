package com.reversetutor.core.data.background

import java.util.concurrent.ConcurrentHashMap

/** Process-local, bounded preview state. It is never written to Room or logs. */
class GenerationPartialStore {
    private data class Entry(val token: String, val text: String)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun append(jobId: String, token: String, chunk: String): String? {
        if (jobId.isBlank() || token.isBlank() || chunk.isBlank()) return null
        val key = jobId.trim()
        val current = entries[key]
        if (current != null && current.token != token) return null
        val next = (current?.text.orEmpty() + chunk).take(MaxPreviewChars)
        entries[key] = Entry(token, next)
        return next
    }

    fun get(jobId: String, token: String): String? = entries[jobId.trim()]
        ?.takeIf { it.token == token }
        ?.text

    fun clear(jobId: String, token: String? = null) {
        val key = jobId.trim()
        if (token == null || entries[key]?.token == token) entries.remove(key)
    }

    private companion object {
        const val MaxPreviewChars = 1200
    }
}
