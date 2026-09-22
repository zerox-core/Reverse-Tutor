package com.reversetutor.core.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-006: the assembler carries the window-memory injection block through
 * the same degradation contract as the early-history digest - absent port
 * means blank, failing port means blank plus a warning, never a crash.
 */
class WindowMemoryContextPortAssemblerTest {

    private val emptyMessagePort = object : MessageContextPort {
        override suspend fun listRecentMessages(
            spaceId: String,
            sessionId: String,
            limit: Int,
        ): List<ContextMessage> = emptyList()
    }
    private val emptyMemoryPort = object : MemoryContextPort {
        override suspend fun listMemoryReferences(
            spaceId: String,
            sessionId: String,
            limit: Int,
        ): List<MemoryReferenceContract> = emptyList()
    }
    private val emptyErrorPort = object : ErrorContextPort {
        override suspend fun listHistoricalErrors(
            spaceId: String,
            sessionId: String,
            limit: Int,
        ): List<ErrorReferenceContract> = emptyList()
    }
    private val emptyGraphPort = object : GraphContextPort {
        override suspend fun listPrerequisiteGaps(
            spaceId: String,
            sessionId: String,
            limit: Int,
        ): List<String> = emptyList()

        override suspend fun listPendingReviewPoints(
            spaceId: String,
            sessionId: String,
            limit: Int,
        ): List<String> = emptyList()
    }
    private val emptySourcePort = object : SourceContextPort {
        override suspend fun listSourceEvidence(
            spaceId: String,
            sessionId: String,
            limit: Int,
            queryText: String,
        ): List<SourceReferenceContract> = emptyList()
    }

    private fun assembler(windowMemoryPort: WindowMemoryContextPort?) = ConversationContextAssembler(
        messagePort = emptyMessagePort,
        memoryPort = emptyMemoryPort,
        errorPort = emptyErrorPort,
        graphPort = emptyGraphPort,
        sourcePort = emptySourcePort,
        windowMemoryPort = windowMemoryPort,
    )

    @Test
    fun absentPortLeavesDigestBlankWithoutWarning() = runBlocking {
        val contract = assembler(null).assemble("space", "ses", "")

        assertEquals("", contract.windowMemoryDigest)
        assertTrue(contract.warnings.isEmpty())
    }

    @Test
    fun portValueFlowsIntoContract() = runBlocking {
        val port = WindowMemoryContextPort { _, _, _ -> "【窗口记忆】\n- 学习目标：v" }

        val contract = assembler(port).assemble("space", "ses", "目标")

        assertEquals("【窗口记忆】\n- 学习目标：v", contract.windowMemoryDigest)
        assertTrue(contract.warnings.isEmpty())
    }

    @Test
    fun failingPortDegradesToBlankWithWarning() = runBlocking {
        val port = WindowMemoryContextPort { _, _, _ -> throw IllegalStateException("db down") }

        val contract = assembler(port).assemble("space", "ses", "")

        assertEquals("", contract.windowMemoryDigest)
        assertTrue(contract.warnings.any { it.source == "window_memory" })
    }
}
