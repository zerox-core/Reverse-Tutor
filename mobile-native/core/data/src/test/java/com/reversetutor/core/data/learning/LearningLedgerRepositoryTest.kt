package com.reversetutor.core.data.learning

import com.reversetutor.core.data.local.dao.LearningLedgerDao
import com.reversetutor.core.data.local.entity.LearningFactReceiptEntity
import com.reversetutor.core.data.local.entity.ScopeSignalEntity
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningLedgerRepositoryTest {

    private fun fact(knowledgePoint: String) = LearningFactReceipt(
        knowledgePoint = knowledgePoint,
        evidenceType = "explanation",
        result = "passed",
        confidence = 0.8f,
        sourceWindowId = "w1",
        sourceTurnId = "t1",
        occurredAtEpochMillis = 10L
    )

    private fun signal(category: String, count: Int) =
        ScopeSignal(category = category, count = count, sourceTurnId = "t1", observedAtEpochMillis = 10L)

    @Test
    fun spaceIdIsolation() = runBlocking {
        val dao = FakeLearningLedgerDao()
        val repo = LearningLedgerRepository(dao, defaultSpaceId = "space-a")
        repo.appendLearningFact(fact("kp-1"), spaceId = "space-a")
        repo.appendLearningFact(fact("kp-2"), spaceId = "space-b")

        assertEquals(1, repo.listLearningFacts("space-a").size)
        assertEquals(1, repo.listLearningFacts("space-b").size)
    }

    @Test
    fun appendOnlyLearningFactReceipt() = runBlocking {
        val dao = FakeLearningLedgerDao()
        val repo = LearningLedgerRepository(dao, defaultSpaceId = "space-a")
        val first = repo.appendLearningFact(fact("kp-1"))
        val second = repo.appendLearningFact(fact("kp-2"))

        assertEquals(first, repo.listLearningFacts().first())
        assertEquals(2, dao.receipts.size)
    }

    @Test
    fun taskWindowCanEmitLearningFact() = runBlocking {
        val dao = FakeLearningLedgerDao()
        val repo = LearningLedgerRepository(dao, defaultSpaceId = "space-a")
        val taskRoot = WindowRef("w1", "w1", null, WindowKind.TASK_ROOT)
        repo.appendLearningFact(fact("kp-1").let { it.copy(sourceWindowId = taskRoot.id) })

        assertEquals(1, repo.listLearningFacts().size)
    }

    @Test
    fun companionMemoryExcludedFromLedger() {
        val fields = LearningFactReceiptEntity.ALLOWED_PERSISTED_FIELDS
        assertFalse(fields.any { it.contains("message", ignoreCase = true) || it.contains("text", ignoreCase = true) })
        assertFalse(fields.any { it.contains("transcript", ignoreCase = true) || it.contains("provider", ignoreCase = true) })
        assertFalse(fields.any { it.contains("url", ignoreCase = true) || it.contains("authorization", ignoreCase = true) || it.contains("secret", ignoreCase = true) || it.contains("partition", ignoreCase = true) })
        assertTrue(fields.contains("knowledgePoint"))
        assertTrue(fields.contains("sourceTurnId"))
    }

    @Test
    fun scopeSignalIsMinimalAndGuardGated() = runBlocking {
        val fields = ScopeSignalEntity.ALLOWED_PERSISTED_FIELDS
        assertFalse(fields.any { it.contains("message", ignoreCase = true) || it.contains("text", ignoreCase = true) || it.contains("transcript", ignoreCase = true) })
        assertTrue(fields.contains("category"))
        assertTrue(fields.contains("count"))
        assertTrue(fields.contains("sourceTurnId"))
        assertTrue(fields.contains("occurredAtEpochMillis"))

        val dao = FakeLearningLedgerDao()
        val repo = LearningLedgerRepository(dao, defaultSpaceId = "space-a")
        val learningRoot = WindowRef("w1", "w1", null, WindowKind.LEARNING_ROOT)
        val envelope = LearningIntentEnvelope("w1", "math", "high_school", "goal", "examples")

        val recorded = repo.appendScopeSignal(signal("unrelated", 1), learningRoot, envelope)
        assertTrue(recorded)
        assertEquals(1, dao.signals.size)

        val companionRoot = WindowRef("cr", "cr", null, WindowKind.COMPANION_ROOT)
        val notRecorded = repo.appendScopeSignal(signal("unrelated", 1), companionRoot, envelope)
        assertFalse(notRecorded)
        assertEquals(1, dao.signals.size)
    }

    private class FakeLearningLedgerDao : LearningLedgerDao {
        val receipts = mutableListOf<LearningFactReceiptEntity>()
        val signals = mutableListOf<ScopeSignalEntity>()

        override suspend fun insertReceipt(receipt: LearningFactReceiptEntity): Long {
            receipts.add(receipt)
            return 1L
        }

        override suspend fun listFactsBySpace(spaceId: String): List<LearningFactReceiptEntity> =
            receipts.filter { it.spaceId == spaceId }

        override suspend fun listFactsByWindow(windowId: String): List<LearningFactReceiptEntity> =
            receipts.filter { it.sourceWindowId == windowId }

        override suspend fun insertScopeSignal(signal: ScopeSignalEntity): Long {
            signals.add(signal)
            return 1L
        }

        override suspend fun listScopeSignals(windowId: String): List<ScopeSignalEntity> =
            signals.filter { it.windowId == windowId }

        override suspend fun listScopeSignalsBySpace(spaceId: String): List<ScopeSignalEntity> =
            signals.filter { it.spaceId == spaceId }
    }
}
