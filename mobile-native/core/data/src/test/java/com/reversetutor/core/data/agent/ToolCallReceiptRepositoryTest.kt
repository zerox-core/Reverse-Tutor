package com.reversetutor.core.data.agent

import com.reversetutor.core.domain.ToolSafeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallReceiptRepositoryTest {

    @Test
    fun sameCallIdReturnsTheOriginalSafeReceipt() = kotlinx.coroutines.runBlocking {
        val repository = ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
        val first = repository.recordIfAbsent(ToolCallReceipt("call-1", "session-1", "reference.open", "completed", ToolSafeResult.ReferenceOpenTarget("source-1"), 100L))
        val retry = repository.recordIfAbsent(ToolCallReceipt("call-1", "session-1", "reference.open", "completed", ToolSafeResult.ReferenceOpenTarget("source-2"), 101L))

        assertEquals(first, retry)
        assertTrue(retry.safeResult is ToolSafeResult.ReferenceOpenTarget)
        assertEquals("source-1", (retry.safeResult as ToolSafeResult.ReferenceOpenTarget).sourceId)
    }
}
