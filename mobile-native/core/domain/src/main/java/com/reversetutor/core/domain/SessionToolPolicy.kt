package com.reversetutor.core.domain

/** Pure fail-closed authorization for model-proposed session tools. */
object SessionToolPolicy {
    val allowedNames = setOf(
        "session_document.create",
        "session_document.read",
        "session_document.replace_block",
        "session_table.create",
        "session_table.upsert_row",
        "reference.open"
    )

    private val sensitive = Regex("(?i)sk-[a-z0-9_-]+|authorization\\s*[:=]|bearer\\s+[a-z0-9._-]+|https?://|file://")

    fun authorize(currentSessionId: String, call: SessionToolCall): SessionToolAuthorization {
        val sessionId = currentSessionId.trim()
        if (sessionId.isEmpty() || call.sessionId.trim() != sessionId) return SessionToolAuthorization(false, "tool_scope_denied")
        if (call.name.trim() !in allowedNames) return SessionToolAuthorization(false, "tool_not_allowed")
        if (call.callId.trim().isEmpty()) return SessionToolAuthorization(false, "tool_invalid_arguments")
        val args = call.argumentsJson.trim()
        if (args.length > 2_000) return SessionToolAuthorization(false, "tool_limit_exceeded")
        if (args.length < 2 || !args.startsWith('{') || !args.endsWith('}') || sensitive.containsMatchIn(args)) {
            return SessionToolAuthorization(false, "tool_invalid_arguments")
        }
        return SessionToolAuthorization(true, "allowed")
    }
}
