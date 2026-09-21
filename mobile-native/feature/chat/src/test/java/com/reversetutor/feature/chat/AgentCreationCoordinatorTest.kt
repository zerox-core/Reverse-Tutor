package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCreationCoordinatorTest {

    private class ScriptedGateway(
        private val turnResults: List<AgentCreationTurnResult>,
        private val analysisProvider: (String) -> AgentCreationDocAnalysis = { cannedAnalysis() }
    ) : AgentCreationGateway {
        var converseCalls = 0
        var analyzeCalls = 0
        var failConverse = false

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?
        ): AgentCreationTurnResult {
            converseCalls++
            if (failConverse) throw IllegalStateException("gateway down")
            return turnResults[converseCalls.coerceAtMost(turnResults.size) - 1]
        }

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis {
            analyzeCalls++
            return analysisProvider(fileName)
        }

        companion object {
            fun cannedAnalysis(): AgentCreationDocAnalysis = AgentCreationDocAnalysis(
                materialTitle = "物理讲义",
                materialType = "教材",
                outline = listOf("浮力", "压强"),
                knowledgePoints = listOf("浮力", "液体压强"),
                suggestedPath = listOf("浮力 → 压强 → 综合"),
                summary = "覆盖初二物理力学两章。"
            )
        }
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    @Test
    fun startPostsOpeningMessageAndEntersConversing() {
        val coordinator = AgentCreationCoordinator(ScriptedGateway(emptyList()), clock())

        coordinator.start()
        coordinator.start()

        assertEquals(AgentCreationPhase.Conversing, coordinator.state.phase)
        val feed = coordinator.state.feed
        assertEquals(1, feed.size)
        val opening = feed.first() as AgentCreationFeedEntry.Assistant
        assertTrue(opening.text.contains("想学什么"))
    }

    @Test
    fun sendUserTextPatchesDraftAndAppendsBubbles() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 30,
                    assistantNote = "草案先起个头。",
                    followUpQuestion = "这次的目标是什么？",
                    draft = AgentCreationDraftPatch(title = "浮力·讲学练会话", learnerRole = "初二学生")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想把初中物理浮力讲明白")

        val state = coordinator.state
        assertFalse(state.busy)
        assertEquals(30, state.rawUnderstanding)
        assertEquals("浮力·讲学练会话", state.draft.title)
        assertEquals("初二学生", state.draft.learnerRole)
        // 开场 + user + note + followUp + draft 卡
        assertEquals(5, state.feed.size)
        assertTrue((state.feed[2] as AgentCreationFeedEntry.Assistant).text.contains("草案先起个头"))
        assertEquals(1, gateway.converseCalls)
    }

    @Test
    fun displayedUnderstandingCappedWhileRequiredFieldsMissing() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 85,
                    followUpQuestion = "再聊聊？",
                    draft = AgentCreationDraftPatch(title = "只有标题")
                ),
                AgentCreationTurnResult(
                    understanding = 85,
                    followUpQuestion = "齐了吗？",
                    draft = AgentCreationDraftPatch(learnerRole = "初二学生")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("第一句")
        assertEquals(85, coordinator.state.rawUnderstanding)
        assertEquals(60, coordinator.state.displayedUnderstanding)
        assertFalse(coordinator.state.understandingHigh)
        assertFalse(coordinator.state.canCreate)

        coordinator.sendUserText("第二句")
        assertEquals(85, coordinator.state.displayedUnderstanding)
        assertTrue(coordinator.state.understandingHigh)
        assertTrue(coordinator.state.canCreate)
    }

    @Test
    fun secondTurnPatchKeepsUnmentionedFields() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 30,
                    followUpQuestion = "目标？",
                    draft = AgentCreationDraftPatch(title = "旧标题", learnerRole = "初二学生")
                ),
                AgentCreationTurnResult(
                    understanding = 60,
                    followUpQuestion = "基础？",
                    draft = AgentCreationDraftPatch(goal = "期末冲刺")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("第一句")
        coordinator.sendUserText("第二句")

        val config = coordinator.configuration()
        assertEquals("旧标题", config.title)
        assertEquals("初二学生", config.learnerRole)
        assertEquals("期末冲刺", config.goal)
        assertEquals(60, coordinator.state.rawUnderstanding)
    }

    @Test
    fun requestDocumentFlagSurfacesOnlyWhenNoAnalysisYet() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 65,
                    followUpQuestion = "有教材吗？",
                    requestDocument = true,
                    draft = AgentCreationDraftPatch(title = "t", learnerRole = "r")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我是初二学生")

        assertTrue(coordinator.state.requestDocumentActive)
    }

    @Test
    fun attachDocumentAnalyzesThenFeedsConclusionBack() = runBlocking {
        val gateway = ScriptedGateway(emptyList())
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.attachDocument("physics.pdf", "1.2 MB")

        val state = coordinator.state
        assertEquals(AgentCreationPhase.Conversing, state.phase)
        assertEquals(1, gateway.analyzeCalls)
        assertNotNull(state.docAnalysis)
        val card = state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>().single()
        assertEquals(AgentCreationFeedEntry.FileCard.FileStatus.Analyzed, card.status)
        val last = state.feed.last() as AgentCreationFeedEntry.Assistant
        assertTrue(last.text.contains("物理讲义"))
        assertFalse(state.requestDocumentActive)
    }

    @Test
    fun attachDocumentFailureMarksCardFailedAndKeepsConversation() = runBlocking {
        val gateway = ScriptedGateway(
            emptyList(),
            analysisProvider = { throw IllegalStateException("parse failed") }
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.attachDocument("bad.pdf", "9 KB")

        val state = coordinator.state
        assertEquals(AgentCreationPhase.Conversing, state.phase)
        assertNull(state.docAnalysis)
        val card = state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>().single()
        assertEquals(AgentCreationFeedEntry.FileCard.FileStatus.Failed, card.status)
        val last = state.feed.last() as AgentCreationFeedEntry.Assistant
        assertTrue(last.text.contains("没读出来"))
    }

    @Test
    fun retryDocumentAnalysisRestoresCard() = runBlocking {
        var broken = true
        val gateway = ScriptedGateway(
            emptyList(),
            analysisProvider = { if (broken) throw IllegalStateException("boom") else ScriptedGateway.cannedAnalysis() }
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()
        coordinator.attachDocument("a.pdf", "1 KB")
        val cardId = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>().single().id

        broken = false
        coordinator.retryDocumentAnalysis(cardId)

        val card = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.FileCard>().single()
        assertEquals(AgentCreationFeedEntry.FileCard.FileStatus.Analyzed, card.status)
        assertNotNull(coordinator.state.docAnalysis)
        assertEquals(2, gateway.analyzeCalls)
    }

    @Test
    fun generationFailureRetriesOnceThenDegrades() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(AgentCreationTurnResult(understanding = 10, followUpQuestion = "占位"))
        )
        gateway.failConverse = true
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("随便聊聊")

        val state = coordinator.state
        assertFalse(state.busy)
        assertEquals(AgentCreationPhase.Conversing, state.phase)
        assertNotNull(state.generationError)
        assertEquals(2, gateway.converseCalls)
        val last = state.feed.last() as AgentCreationFeedEntry.Assistant
        assertTrue(last.text.contains("再说一遍"))
    }

    @Test
    fun emptyOrBusyInputIsIgnored() = runBlocking {
        val gateway = ScriptedGateway(emptyList())
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("   ")

        assertEquals(1, coordinator.state.feed.size)
        assertEquals(0, gateway.converseCalls)
    }
}
