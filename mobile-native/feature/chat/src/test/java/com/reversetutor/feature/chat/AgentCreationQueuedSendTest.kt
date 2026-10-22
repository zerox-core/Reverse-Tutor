package com.reversetutor.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 修复 #1（2026-10-15 用户反馈「生成中发消息被静默丢弃，不支持排队或者插队发送」）：
 * 生成中 send → 入队并立刻以「排队中」可见气泡上屏（不再丢弃）；当前生成结束后按 FIFO
 * 自动作为下一轮发送；排队消息可单条取消；排队发送失败把文本回填输入框（文本不丢）。
 */
class AgentCreationQueuedSendTest {

    /**
     * 记录每次收到的用户文本（验证 FIFO 顺序）；第一轮可被闸门挂起模拟「生成中」，
     * failAfterFirstTurn = true 时第 2 轮起一律抛错（模拟排队消息发送失败）。
     */
    private class QueueGateway(
        private val firstTurnGate: CompletableDeferred<Unit>? = null,
        private val failAfterFirstTurn: Boolean = false
    ) : AgentCreationGateway {
        val userTexts = mutableListOf<String>()
        var calls = 0

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult {
            calls++
            userTexts += userText
            if (calls == 1) firstTurnGate?.await()
            if (failAfterFirstTurn && calls > 1) throw IllegalStateException("gateway down")
            return AgentCreationTurnResult(understanding = 20, assistantNote = "收到：$userText")
        }

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
            AgentCreationDocAnalysis(materialTitle = fileName)
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    // (a) 生成中 send → 进入排队而非被丢弃，且立刻以「排队中」气泡可见。
    @Test
    fun sendWhileBusyEnqueuesVisibleQueuedMessageInsteadOfDroppingIt() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gateway = QueueGateway(firstTurnGate = gate)
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        val first = async { coordinator.sendUserText("第一句") }
        yield()
        assertTrue(coordinator.state.busy)
        assertEquals(listOf("第一句"), gateway.userTexts)

        assertTrue(coordinator.sendUserText("生成中排队的这句话"))

        val queued = coordinator.state.queuedMessages
        assertEquals(1, queued.size)
        assertEquals("生成中排队的这句话", queued.single().text)
        assertTrue(queued.single().pending)
        // 排队期间不开新轮：网关仍只收到第一句
        assertEquals(1, gateway.calls)

        gate.complete(Unit)
        first.await()

        // 当前生成结束 → 排队消息自动作为下一轮发出
        assertEquals(listOf("第一句", "生成中排队的这句话"), gateway.userTexts)
        assertTrue(coordinator.state.queuedMessages.isEmpty())
        assertFalse(
            coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.User>().any { it.pending }
        )
    }

    // 2026-10-17 用户拍板：连续发送消息合并回复。当前生成结束后，排队消息合并为单次调用发给模型。
    @Test
    fun queuedMessagesAreMergedIntoSingleGenerationAfterCurrentTurnEnds() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gateway = QueueGateway(firstTurnGate = gate)
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        val first = async { coordinator.sendUserText("命令一") }
        yield()
        coordinator.sendUserText("命令二")
        coordinator.sendUserText("命令三")
        assertEquals(listOf("命令二", "命令三"), coordinator.state.queuedMessages.map { it.text })

        gate.complete(Unit)
        first.await()

        assertEquals(listOf("命令一", "命令二\n命令三"), gateway.userTexts)
        assertTrue(coordinator.state.queuedMessages.isEmpty())
        assertFalse(coordinator.state.busy)
        // 用户气泡依然按单条各自独立落定，保持清晰阅读，后台合并生成
        val users = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.User>()
        assertEquals(listOf("命令一", "命令二", "命令三"), users.map { it.text })
        assertFalse(users.any { it.pending })
    }

    // (c) 排队消息可取消：取消后不再发送，气泡从流里撤掉。
    @Test
    fun queuedMessageCanBeCancelledBeforeItIsSent() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gateway = QueueGateway(firstTurnGate = gate)
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        val first = async { coordinator.sendUserText("第一句") }
        yield()
        coordinator.sendUserText("要撤掉的这句")
        coordinator.sendUserText("保留的这句")
        val cancelledId = coordinator.state.queuedMessages.first().id

        assertTrue(coordinator.cancelQueuedMessage(cancelledId))
        assertEquals(listOf("保留的这句"), coordinator.state.queuedMessages.map { it.text })
        assertFalse(coordinator.state.feed.any { it.id == cancelledId })
        // 已开始发送 / 不存在的 id 不可取消
        assertFalse(coordinator.cancelQueuedMessage(cancelledId))
        assertFalse(coordinator.cancelQueuedMessage("no-such-entry"))

        gate.complete(Unit)
        first.await()

        assertEquals(listOf("第一句", "保留的这句"), gateway.userTexts)
        assertTrue(coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.User>().all { !it.pending })
    }

    // 修复 #1 附加要求：排队发送失败不能丢文本——回填输入框，UI 消费一次即清。
    @Test
    fun failedQueuedSendRestoresItsTextToInputInsteadOfLosingIt() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gateway = QueueGateway(firstTurnGate = gate, failAfterFirstTurn = true)
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        val first = async { coordinator.sendUserText("第一句") }
        yield()
        coordinator.sendUserText("排队但会失败的一句")

        gate.complete(Unit)
        first.await()

        assertEquals("排队但会失败的一句", coordinator.state.restoreInput)
        // 失败也不静默：这一轮照常给出降级话术
        assertTrue(coordinator.state.generationError != null)

        coordinator.consumeRestoreInput()
        assertNull(coordinator.state.restoreInput)
    }

    // 排队气泡不进快照（还没发出去的消息不该在恢复后冒充已发送）。
    @Test
    fun queuedMessagesAreNotPersistedIntoSessionsSnapshot() = runBlocking {
        val store = object : AgentCreationStateStore {
            var saved: AgentCreationSnapshot? = null
            override fun load(): AgentCreationSnapshot? = null
            override fun save(snapshot: AgentCreationSnapshot) {
                saved = snapshot
            }

            override fun clear() {
                saved = null
            }
        }
        val gate = CompletableDeferred<Unit>()
        val gateway = QueueGateway(firstTurnGate = gate)
        val coordinator = AgentCreationCoordinator(gateway, clock(), stateStore = store)
        coordinator.start()

        val first = async { coordinator.sendUserText("第一句") }
        yield()
        coordinator.sendUserText("排队中的这句")
        coordinator.cancelQueuedMessage(coordinator.state.queuedMessages.single().id)
        coordinator.sendUserText("又一条排队的")

        val savedFeed = store.saved?.feed.orEmpty()
        assertFalse(savedFeed.any { it is AgentCreationFeedEntry.User && it.text == "又一条排队的" })

        gate.complete(Unit)
        first.await()
        Unit
    }
}
