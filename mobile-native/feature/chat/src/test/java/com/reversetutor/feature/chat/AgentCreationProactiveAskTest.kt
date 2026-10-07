package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R87：创建会话主动问——满条件主动要资料（只问一次）、路径排好请用户确认（只确认一次）。 */
class AgentCreationProactiveAskTest {

    private fun readyDraft() = NewSessionConfiguration(
        goal = "学物理",
        learnerRole = "初三学生，基础薄弱"
    )

    // ---- 规划器：主动要资料 ----

    @Test
    fun documentAskFiresWhenGoalRoleReadyAndTwoRounds() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(2) { planner.recordRound(null) }
        val strategy = planner.strategyFor(readyDraft(), detScore = 50, documentAvailable = false)
        assertTrue(strategy.shouldRequestDocument)
        assertNull(strategy.targetFollowUpField)
    }

    @Test
    fun documentAskSuppressedBeforeTwoRounds() {
        val planner = AgentCreationFollowUpPlanner()
        val strategy = planner.strategyFor(readyDraft(), detScore = 50, documentAvailable = false)
        assertFalse(strategy.shouldRequestDocument)
    }

    @Test
    fun documentAskSuppressedWhenDocumentAvailable() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(2) { planner.recordRound(null) }
        val strategy = planner.strategyFor(readyDraft(), detScore = 50, documentAvailable = true)
        assertFalse(strategy.shouldRequestDocument)
    }

    @Test
    fun documentAskedOnlyOnce() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(2) { planner.recordRound(null) }
        planner.markDocumentAsked()
        val strategy = planner.strategyFor(readyDraft(), detScore = 50, documentAvailable = false)
        assertFalse(strategy.shouldRequestDocument)
    }

    // ---- 规划器：路径确认 ----

    @Test
    fun pathConfirmFiresWhenPathPresent() {
        val planner = AgentCreationFollowUpPlanner()
        val draft = readyDraft().copy(learningPath = listOf("密度", "浮力", "压强"))
        val strategy = planner.strategyFor(draft, detScore = 60, documentAvailable = false)
        assertTrue(strategy.mustConfirmPath)
        assertNull(strategy.targetFollowUpField)
    }

    @Test
    fun documentAskTakesPrecedenceOverPathConfirm() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(2) { planner.recordRound(null) }
        val draft = readyDraft().copy(learningPath = listOf("密度", "浮力"))
        val strategy = planner.strategyFor(draft, detScore = 60, documentAvailable = false)
        assertTrue(strategy.shouldRequestDocument)
        assertFalse(strategy.mustConfirmPath)
    }

    @Test
    fun pathConfirmOnlyOnceAndSkippedWhenEmpty() {
        val planner = AgentCreationFollowUpPlanner()
        assertFalse(
            planner.strategyFor(readyDraft(), 60, documentAvailable = false).mustConfirmPath
        )
        planner.markPathConfirmAsked()
        val draft = readyDraft().copy(learningPath = listOf("密度"))
        assertFalse(
            planner.strategyFor(draft, 60, documentAvailable = false).mustConfirmPath
        )
    }

    @Test
    fun convergeSuppressesProactiveAsks() {
        val planner = AgentCreationFollowUpPlanner()
        repeat(2) { planner.recordRound(null) }
        planner.forceConverge()
        val draft = readyDraft().copy(learningPath = listOf("密度"))
        val strategy = planner.strategyFor(draft, 60, documentAvailable = false)
        assertFalse(strategy.shouldRequestDocument)
        assertFalse(strategy.mustConfirmPath)
    }

    @Test
    fun plannerStateRoundTripsProactiveFlags() {
        val planner = AgentCreationFollowUpPlanner()
        planner.markDocumentAsked()
        planner.markPathConfirmAsked()
        val restored = AgentCreationFollowUpPlanner()
        restored.restoreState(planner.exportState())
        repeat(2) { restored.recordRound(null) }
        val draft = readyDraft().copy(learningPath = listOf("密度"))
        val strategy = restored.strategyFor(draft, 60, documentAvailable = false)
        assertFalse(strategy.shouldRequestDocument)
        assertFalse(strategy.mustConfirmPath)
    }

    @Test
    fun pathConfirmQuestionListsNumberedPath() {
        val planner = AgentCreationFollowUpPlanner()
        val question = planner.pathConfirmQuestion(listOf("密度", "浮力"))
        assertTrue(question.contains("1. 密度"))
        assertTrue(question.contains("2. 浮力"))
        assertTrue(question.contains("你看行吗"))
    }

    // ---- 协调器：客户端把关 ----

    private class ScriptedGateway(
        private val result: AgentCreationTurnResult
    ) : AgentCreationGateway {
        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult = result

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
            AgentCreationDocAnalysis(materialTitle = "讲义")
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    @Test
    fun coordinatorForcesDocumentRequestEvenIfLlmSilent() = runBlocking {
        // LLM 只补 goal+role、从不主动 requestDocument：第 3 轮起客户端必须自己放行要资料。
        val gateway = ScriptedGateway(
            AgentCreationTurnResult(
                understanding = 50,
                draft = AgentCreationDraftPatch(goal = "学物理", learnerRole = "初三学生")
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()
        coordinator.sendUserText("我想学物理")
        coordinator.sendUserText("孩子初三，基础弱")
        assertFalse(coordinator.state.requestDocumentActive)
        coordinator.sendUserText("嗯")
        assertTrue(coordinator.state.requestDocumentActive)
    }

    @Test
    fun coordinatorFallsBackToDeterministicPathConfirmQuestion() = runBlocking {
        // LLM 给了路径但从不开口确认：路径进草案的下一轮，客户端必须补确认话术。
        val gateway = ScriptedGateway(
            AgentCreationTurnResult(
                understanding = 30,
                draft = AgentCreationDraftPatch(
                    goal = "学物理",
                    learningPath = listOf("密度", "浮力", "压强")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()
        coordinator.sendUserText("我想学物理")
        coordinator.sendUserText("就按这个目标来")
        val texts = coordinator.state.feed
            .filterIsInstance<AgentCreationFeedEntry.Assistant>()
            .map { it.text }
        assertTrue(texts.any { it.contains("1. 密度") && it.contains("你看行吗") })
    }
}
