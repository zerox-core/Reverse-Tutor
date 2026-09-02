package com.reversetutor.core.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTableRepositoryTest {

    @Test
    fun tableAndRowsAreSessionBoundAndCallIdempotent() = kotlinx.coroutines.runBlocking {
        val repository = SessionTableRepository(InMemorySessionTableStore())
        val first = repository.createIfAbsent(
            "table-call-1", "session-1", "document-1", "错题表",
            listOf(SessionTableColumn("题目"), SessionTableColumn("状态")), 100L
        )
        val second = repository.createIfAbsent(
            "table-call-1", "session-1", "document-1", "重复表",
            listOf(SessionTableColumn("x")), 101L
        )

        assertTrue(first.applied)
        assertFalse(second.applied)
        assertEquals(first.table!!.id, second.table!!.id)
        assertTrue(repository.upsertRowIfAbsent("row-call-1", "session-1", first.table!!.id, "q1", listOf("函数", "待复习"), 102L))
        assertFalse(repository.upsertRowIfAbsent("row-call-1", "session-1", first.table!!.id, "q1", listOf("重复", "重复"), 103L))
        assertFalse(repository.upsertRowIfAbsent("row-call-2", "session-2", first.table!!.id, "q2", listOf("越权", "拒绝"), 104L))
        assertEquals(1, repository.read("session-1", first.table!!.id)!!.rows.size)
        assertNull(repository.read("session-2", first.table!!.id))
    }
}
