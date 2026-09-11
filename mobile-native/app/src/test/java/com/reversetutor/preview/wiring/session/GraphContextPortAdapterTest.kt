package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.learning.LearningLedgerRepository
import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.dao.LearningLedgerDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.LearningFactReceiptEntity
import com.reversetutor.core.data.local.entity.ScopeSignalEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.domain.LearningFactReceipt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V1-016 due-review reminder wiring: [GraphContextPortAdapter.listPendingReviewPoints]
 * must surface the mastery ladder's due knowledge points (old-parity
 * `list_due_reviews`) ahead of graph NeedsReview labels, deduplicated and
 * bounded, with an injected clock for determinism.
 */
class GraphContextPortAdapterTest {

    private val dayMillis = 86_400_000L

    @Test
    fun dueKnowledgePointsSurfaceFirstAndMergeWithGraphReviewLabels() = runBlocking {
        val ledger = LearningLedgerRepository(FakeLearningLedgerDao(), defaultSpaceId = "space-1")
        val t0 = 1_000L
        ledger.appendLearningFact(fact("因式分解", occurredAtEpochMillis = t0), spaceId = "space-1")
        ledger.appendLearningFact(fact("函数单调性", occurredAtEpochMillis = t0), spaceId = "space-1")
        val adapter = GraphContextPortAdapter(
            graphRepository = graphRepository(graphReviewLabels = listOf("因式分解", "图形待复习点")),
            learningLedgerRepository = ledger,
            // Both ladder next reviews land at t0 + 1 day, so both are due.
            nowEpochMillis = { t0 + 2 * dayMillis }
        )

        val points = adapter.listPendingReviewPoints("space-1", "session-1", limit = 5)

        // Ledger due points first (earliest-due, then knowledge-point order),
        // then graph labels, deduplicated against the due points.
        assertEquals(listOf("函数单调性", "因式分解", "图形待复习点"), points)
        assertEquals(
            listOf("函数单调性"),
            adapter.listPendingReviewPoints("space-1", "session-1", limit = 1)
        )
    }

    @Test
    fun notYetDueKnowledgePointsAreExcluded() = runBlocking {
        val ledger = LearningLedgerRepository(FakeLearningLedgerDao(), defaultSpaceId = "space-1")
        val t0 = 1_000L
        ledger.appendLearningFact(fact("勾股定理", occurredAtEpochMillis = t0), spaceId = "space-1")
        val adapter = GraphContextPortAdapter(
            graphRepository = graphRepository(graphReviewLabels = emptyList()),
            learningLedgerRepository = ledger,
            // Next review is t0 + 1 day; at t0 + half a day nothing is due.
            nowEpochMillis = { t0 + dayMillis / 2 }
        )

        assertEquals(
            emptyList<String>(),
            adapter.listPendingReviewPoints("space-1", "session-1", limit = 5)
        )
    }

    @Test
    fun nullLedgerKeepsGraphOnlyBehaviour() = runBlocking {
        val adapter = GraphContextPortAdapter(
            graphRepository = graphRepository(graphReviewLabels = listOf("图形待复习点"))
        )

        assertEquals(
            listOf("图形待复习点"),
            adapter.listPendingReviewPoints("space-1", "session-1", limit = 5)
        )
    }

    @Test
    fun otherSpacesFactsDoNotLeakIntoDueSelection() = runBlocking {
        val ledger = LearningLedgerRepository(FakeLearningLedgerDao(), defaultSpaceId = "space-1")
        val t0 = 1_000L
        ledger.appendLearningFact(fact("跨空间知识点", occurredAtEpochMillis = t0), spaceId = "space-other")
        val adapter = GraphContextPortAdapter(
            graphRepository = graphRepository(graphReviewLabels = emptyList()),
            learningLedgerRepository = ledger,
            nowEpochMillis = { t0 + 2 * dayMillis }
        )

        assertEquals(
            emptyList<String>(),
            adapter.listPendingReviewPoints("space-1", "session-1", limit = 5)
        )
    }

    private fun fact(knowledgePoint: String, occurredAtEpochMillis: Long) = LearningFactReceipt(
        knowledgePoint = knowledgePoint,
        evidenceType = "explanation",
        result = "passed",
        confidence = 0.8f,
        sourceWindowId = "w1",
        sourceTurnId = "turn-$knowledgePoint-$occurredAtEpochMillis",
        occurredAtEpochMillis = occurredAtEpochMillis
    )

    private fun graphRepository(graphReviewLabels: List<String>): GraphRepository {
        val graphDao = FakeGraphDao(
            sessionNodes = graphReviewLabels.mapIndexed { index, label ->
                GraphNodeEntity(
                    id = "node-$index",
                    spaceId = "space-1",
                    label = label,
                    kind = "Other",
                    createdAtEpochMillis = 1L,
                    status = "NeedsReview"
                )
            }
        )
        val sessionDao = FakeSessionDao(
            sessions = mapOf(
                "session-1" to SessionEntity(
                    id = "session-1",
                    spaceId = "space-1",
                    title = "会话",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L
                )
            )
        )
        return GraphRepository(graphDao, sessionDao)
    }

    private class FakeGraphDao(private val sessionNodes: List<GraphNodeEntity>) : GraphDao {
        override suspend fun insertNode(node: GraphNodeEntity) = Unit
        override suspend fun insertEdge(edge: GraphEdgeEntity) = Unit
        override suspend fun listNodesBySpace(spaceId: String): List<GraphNodeEntity> = emptyList()
        override suspend fun listNodesBySession(sessionId: String): List<GraphNodeEntity> = sessionNodes
        override suspend fun listEdgesBySpace(spaceId: String): List<GraphEdgeEntity> = emptyList()
    }

    private class FakeSessionDao(private val sessions: Map<String, SessionEntity>) : SessionDao {
        override suspend fun upsert(session: SessionEntity) = Unit
        override suspend fun getById(id: String): SessionEntity? = sessions[id]
        override suspend fun listBySpace(spaceId: String): List<SessionEntity> = sessions.values.filter { it.spaceId == spaceId }
        override suspend fun rename(id: String, title: String, updatedAtEpochMillis: Long): Int = 0
        override suspend fun setPinned(id: String, pinned: Boolean, updatedAtEpochMillis: Long): Int = 0
        override suspend fun archive(id: String, updatedAtEpochMillis: Long): Int = 0
        override suspend fun updateSessionModelBinding(sessionId: String, modelBindingId: String): Int = 0
        override suspend fun updateSessionSettingsModelBinding(sessionId: String, modelBindingId: String): Int = 0
    }

    private class FakeLearningLedgerDao : LearningLedgerDao {
        val receipts = mutableListOf<LearningFactReceiptEntity>()
        override suspend fun insertReceipt(receipt: LearningFactReceiptEntity): Long {
            receipts.add(receipt)
            return 1L
        }
        override suspend fun listFactsBySpace(spaceId: String): List<LearningFactReceiptEntity> =
            receipts.filter { it.spaceId == spaceId }
        override suspend fun listFactsByWindow(windowId: String): List<LearningFactReceiptEntity> =
            receipts.filter { it.sourceWindowId == windowId }
        override suspend fun hasFactForTurn(windowId: String, turnId: String): Boolean =
            receipts.any { it.sourceWindowId == windowId && it.sourceTurnId == turnId }
        override suspend fun insertScopeSignal(signal: ScopeSignalEntity): Long = 1L
        override suspend fun listScopeSignals(windowId: String): List<ScopeSignalEntity> = emptyList()
        override suspend fun listScopeSignalsBySpace(spaceId: String): List<ScopeSignalEntity> = emptyList()
    }
}
