package com.reversetutor.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionToolPolicyTest {
    private val sessionId = "session-1"

    @Test
    fun permitsOnlyCurrentSessionDocumentAndTableTools() {
        assertTrue(SessionToolPolicy.authorize(sessionId, call("session_document.create")).allowed)
        assertTrue(SessionToolPolicy.authorize(sessionId, call("session_table.upsert_row")).allowed)
    }

    @Test
    fun rejectsShellAndCrossSessionCalls() {
        assertFalse(SessionToolPolicy.authorize(sessionId, call("shell.execute")).allowed)
        assertFalse(SessionToolPolicy.authorize(sessionId, call("session_document.create", sessionId = "session-2")).allowed)
    }

    @Test
    fun rejectsOversizedAndSensitiveArguments() {
        assertFalse(SessionToolPolicy.authorize(sessionId, call("reference.open", arguments = "{" + "x".repeat(2_001) + "}")).allowed)
        assertFalse(SessionToolPolicy.authorize(sessionId, call("reference.open", arguments = "{\"url\":\"https://example.test\"}")).allowed)
    }

    private fun call(name: String, sessionId: String = this.sessionId, arguments: String = "{}") =
        SessionToolCall("call-1", name, sessionId, arguments)
}
