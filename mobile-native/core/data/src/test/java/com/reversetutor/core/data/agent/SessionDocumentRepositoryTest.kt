package com.reversetutor.core.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDocumentRepositoryTest {

    @Test
    fun createIfAbsentUsesToolCallIdAsTheOnlyIdempotencyKey() = kotlinx.coroutines.runBlocking {
        val repository = SessionDocumentRepository(InMemorySessionDocumentStore())

        val first = repository.createIfAbsent("call-1", "space-1", "session-1", "学习笔记", 100L)
        val second = repository.createIfAbsent("call-1", "space-1", "session-1", "另一标题", 200L)

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.document!!.id, second.document!!.id)
        assertEquals("学习笔记", repository.read("session-1", first.document!!.id)!!.title)
    }

    @Test
    fun documentsNeverCrossTheSessionBoundary() = kotlinx.coroutines.runBlocking {
        val repository = SessionDocumentRepository(InMemorySessionDocumentStore())
        val created = repository.createIfAbsent("call-1", "space-1", "session-1", "学习笔记", 100L)

        assertEquals(created.document!!.id, repository.read("session-1", created.document!!.id)!!.id)
        assertNull(repository.read("session-2", created.document!!.id))
    }

    @Test
    fun blockReplacementIsSessionBoundAndCallIdempotent() = kotlinx.coroutines.runBlocking {
        val store = InMemorySessionDocumentStore()
        val repository = SessionDocumentRepository(store)
        val document = repository.createIfAbsent("create-1", "space-1", "session-1", "学习笔记", 100L).document!!
        store.seedBlocks(document.id, listOf(RichDocumentBlock.Paragraph("旧内容")))

        assertTrue(repository.replaceBlockIfAbsent("replace-1", "session-1", document.id, "seed-0", "paragraph", "新内容", 102L).applied)
        assertFalse(repository.replaceBlockIfAbsent("replace-1", "session-1", document.id, "seed-0", "paragraph", "重复", 103L).applied)
        assertFalse(repository.replaceBlockIfAbsent("replace-2", "session-2", document.id, "seed-0", "paragraph", "越权", 104L).applied)
        assertEquals("新内容", (repository.read("session-1", document.id)!!.blocks.single() as RichDocumentBlock.Paragraph).text)
    }
}
