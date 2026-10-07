package com.reversetutor.core.data.background

import java.util.concurrent.ConcurrentHashMap

/** Process-local, bounded preview state. It is never written to Room or logs. */
class GenerationPartialStore {
    private data class Entry(val token: String, val text: String, val monologue: String? = null)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun append(jobId: String, token: String, chunk: String): String? {
        if (jobId.isBlank() || token.isBlank() || chunk.isBlank()) return null
        val key = jobId.trim()
        val current = entries[key]
        if (current != null && current.token != token) return null
        val next = (current?.text.orEmpty() + chunk).take(MaxPreviewChars)
        entries[key] = Entry(token, next, current?.monologue)
        return next
    }

    /**
     * 2026-09-21 思考链流式透出：独白快照走全量覆盖（不是追加），与正文增量
     * 分开存放；正文未开始时条目可能只有独白。
     */
    fun setMonologue(jobId: String, token: String, monologue: String?): String? {
        if (jobId.isBlank() || token.isBlank()) return null
        val key = jobId.trim()
        val current = entries[key]
        if (current != null && current.token != token) return null
        val next = monologue?.take(MaxMonologuePreviewChars)
        entries[key] = Entry(token, current?.text.orEmpty(), next)
        return next
    }

    fun get(jobId: String, token: String): String? = entries[jobId.trim()]
        ?.takeIf { it.token == token }
        ?.text

    fun getMonologue(jobId: String, token: String): String? = entries[jobId.trim()]
        ?.takeIf { it.token == token }
        ?.monologue

    fun clear(jobId: String, token: String? = null) {
        val key = jobId.trim()
        if (token == null || entries[key]?.token == token) entries.remove(key)
    }

    private companion object {
        const val MaxPreviewChars = 1200
        const val MaxMonologuePreviewChars = 600
    }
}
