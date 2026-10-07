package com.reversetutor.core.data.agent

import com.reversetutor.core.domain.SessionToolCall
import com.reversetutor.core.domain.ToolSafeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionToolExecutionRepositoryTest {

    private fun repository() = SessionToolExecutionRepository(
        documents = SessionDocumentRepository(InMemorySessionDocumentStore()),
        tables = SessionTableRepository(InMemorySessionTableStore()),
        receipts = ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
    )

    @Test
    fun duplicateDocumentCreateReturnsOriginalReceiptWithoutAnotherWrite() = kotlinx.coroutines.runBlocking {
        val repository = repository()
        val first = repository.execute("space-1", "session-1", SessionToolCall("call-1", "session_document.create", "session-1", "{\"title\":\"学习笔记\",\"content\":\"函数的定义域\"}"), emptySet(), 100L)
        val retry = repository.execute("space-1", "session-1", SessionToolCall("call-1", "session_document.create", "session-1", "{\"title\":\"被忽略\"}"), emptySet(), 101L)

        assertTrue(first.safeResult is ToolSafeResult.Document)
        assertEquals(first, retry)
        val documentId = (first.safeResult as ToolSafeResult.Document).documentId
        assertEquals("学习笔记", repository.readDocument("session-1", documentId)!!.title)
        assertEquals("函数的定义域", (repository.readDocument("session-1", documentId)!!.blocks.single() as RichDocumentBlock.Paragraph).text)
    }

    @Test
    fun documentAndTableCallsCannotCrossSessions() = kotlinx.coroutines.runBlocking {
        val repository = repository()
        val created = repository.execute("space-1", "session-1", SessionToolCall("doc-1", "session_document.create", "session-1", "{\"title\":\"学习笔记\"}"), emptySet(), 100L)
        val documentId = (created.safeResult as ToolSafeResult.Document).documentId

        val denied = repository.execute("space-1", "session-2", SessionToolCall("table-1", "session_table.create", "session-2", "{\"documentId\":\"$documentId\",\"title\":\"越权\",\"columns\":[\"题目\"]}"), emptySet(), 101L)
        assertTrue(denied.safeResult is ToolSafeResult.Rejected)
        assertFalse((denied.safeResult as ToolSafeResult.Rejected).code.isBlank())
    }

    @Test
    fun referenceOpenReturnsOnlyAuthorizedOpaqueSourceId() = kotlinx.coroutines.runBlocking {
        val repository = repository()

        val allowed = repository.execute("space-1", "session-1", SessionToolCall("source-1", "reference.open", "session-1", "{\"sourceId\":\"evidence-1\"}"), setOf("evidence-1"), 100L)
        val denied = repository.execute("space-1", "session-1", SessionToolCall("source-2", "reference.open", "session-1", "{\"sourceId\":\"unknown\"}"), setOf("evidence-1"), 101L)

        assertEquals("evidence-1", (allowed.safeResult as ToolSafeResult.ReferenceOpenTarget).sourceId)
        assertTrue(denied.safeResult is ToolSafeResult.Rejected)
        assertFalse(allowed.toString().contains("http"))
    }
}
