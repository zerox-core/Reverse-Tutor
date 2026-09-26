package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R85：创建会话跨页面 / 跨进程持久化——恢复、续聊、落盘、清盘。 */
class AgentCreationStateStoreTest {

    private class InMemoryStore(
        var snapshot: AgentCreationSnapshot? = null
    ) : AgentCreationStateStore {
        var saveCount = 0
        var clearCount = 0

        override fun load(): AgentCreationSnapshot? = snapshot

        override fun save(snapshot: AgentCreationSnapshot) {
            saveCount++
            this.snapshot = snapshot
        }

        override fun clear() {
            clearCount++
            snapshot = null
        }
    }

    private class StaticGateway(
        private val result: AgentCreationTurnResult = AgentCreationTurnResult(
            understanding = 60,
            draft = AgentCreationDraftPatch(goal = "把浮力讲明白", learnerRole = "初二学生")
        )
    ) : AgentCreationGateway {
        var lastStrategy: AgentCreationTurnStrategy? = null

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult {
            lastStrategy = strategy
            return result
        }

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
            AgentCreationDocAnalysis(materialTitle = "讲义")
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    private fun restoredStore(): InMemoryStore {
        val config = NewSessionConfiguration(
            title = "浮力讲学练",
            learnerRole = "初二学生",
            goal = "把浮力讲明白",
            persona = "慢热但较真"
        )
        return InMemoryStore(
            AgentCreationSnapshot(
                draft = config,
                feed = listOf(
                    AgentCreationFeedEntry.Assistant(id = "e-1", text = "想学什么？"),
                    AgentCreationFeedEntry.User(id = "e-2", text = "我想学物理"),
                    AgentCreationFeedEntry.DraftCard(id = "e-3", configuration = config)
                ),
                rawUnderstanding = 58,
                requestDocumentActive = true,
                history = listOf(AgentCreationHistoryTurn(isUser = true, text = "我想学物理")),
                planner = AgentCreationPlannerState(
                    rounds = 3,
                    askedCounts = mapOf("学习目标" to 1),
                    converged = true
                ),
                docAnalysis = AgentCreationDocAnalysis(materialTitle = "物理讲义"),
                entrySequence = 7
            )
        )
    }

    @Test
    fun restoresFeedDraftUnderstandingAndDocAnalysis() {
        val store = restoredStore()
        val coordinator = AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)

        // R92：快照先挂起等用户确认，不再静默恢复；start() 也不发开场白。
        assertTrue(coordinator.state.resumeAvailable)
        assertEquals(AgentCreationPhase.Idle, coordinator.state.phase)
        coordinator.start()
        assertTrue(coordinator.state.feed.isEmpty())

        assertTrue(coordinator.resumePending())
        assertFalse(coordinator.state.resumeAvailable)
        assertEquals(AgentCreationPhase.Conversing, coordinator.state.phase)
        assertEquals(3, coordinator.state.feed.size)
        assertEquals("慢热但较真", coordinator.state.draft.persona)
        assertEquals(58, coordinator.state.rawUnderstanding)
        assertTrue(coordinator.state.requestDocumentActive)
        assertEquals("物理讲义", coordinator.state.docAnalysis?.materialTitle)
        assertFalse(coordinator.state.busy)
        // 已恢复 Conversing：start() 不得重复发开场白。
        coordinator.start()
        assertEquals(3, coordinator.state.feed.size)
    }

    @Test
    fun restoredConversationKeepsPlannerProgress() = runBlocking {
        val store = restoredStore()
        val gateway = StaticGateway()
        val coordinator = AgentCreationCoordinator(gateway, clock(), stateStore = store)
        coordinator.resumePending()

        coordinator.sendUserText("那就按这个来")

        val strategy = gateway.lastStrategy
        assertNotNull(strategy)
        assertEquals(3, strategy!!.rounds)
        assertTrue(strategy.converge)
        assertTrue(
            coordinator.state.feed.any { it is AgentCreationFeedEntry.User && it.text == "那就按这个来" }
        )
    }

    @Test
    fun mutationsPersistSnapshot() = runBlocking {
        val store = InMemoryStore()
        val coordinator = AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)

        assertEquals(0, store.saveCount)
        coordinator.start()
        assertEquals(1, store.saveCount)

        coordinator.sendUserText("我想把浮力讲明白")
        val snapshot = store.snapshot
        assertNotNull(snapshot)
        assertTrue(
            snapshot!!.feed.any { it is AgentCreationFeedEntry.User && it.text == "我想把浮力讲明白" }
        )
        assertEquals("把浮力讲明白", snapshot.draft.goal)
        assertTrue(snapshot.history.isNotEmpty())
    }

    @Test
    fun markCreatedClearsStoredSnapshot() {
        val store = InMemoryStore()
        val coordinator = AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)
        coordinator.start()
        assertNotNull(store.snapshot)

        coordinator.markCreated()

        assertEquals(1, store.clearCount)
        assertNull(store.snapshot)
    }

    @Test
    fun idleCoordinatorDoesNotPersist() {
        val store = InMemoryStore()
        AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)
        assertEquals(0, store.saveCount)
    }

    // R92：入口询问「创建新会话」= 清掉旧快照与追问进度，从零开始。
    @Test
    fun startFreshDiscardsSnapshotAndBeginsNewConversation() {
        val store = restoredStore()
        val coordinator = AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)
        assertTrue(coordinator.state.resumeAvailable)

        coordinator.startFresh()

        assertEquals(1, store.clearCount)
        assertFalse(coordinator.state.resumeAvailable)
        assertEquals(AgentCreationPhase.Conversing, coordinator.state.phase)
        assertEquals(NewSessionConfiguration(), coordinator.state.draft)
        // 只有一条新开场白，旧对话流不复现。
        assertEquals(1, coordinator.state.feed.size)
        assertTrue(coordinator.state.feed.first() is AgentCreationFeedEntry.Assistant)
        assertNull(store.snapshot?.feed?.firstOrNull { it.id == "e-1" })
    }

    // R92：空快照（只有开场都没有的残留）不触发入口询问，行为同无快照。
    @Test
    fun emptySnapshotDoesNotPromptResume() {
        val store = InMemoryStore(AgentCreationSnapshot())
        val coordinator = AgentCreationCoordinator(StaticGateway(), clock(), stateStore = store)

        assertFalse(coordinator.state.resumeAvailable)
        assertEquals(AgentCreationPhase.Idle, coordinator.state.phase)
        assertFalse(coordinator.resumePending())
    }
}
