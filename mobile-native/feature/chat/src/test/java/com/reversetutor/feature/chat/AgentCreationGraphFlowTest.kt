package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R100 方案B 端到端（全假模型零额度）：收敛出孵化草案卡 → 确认 / 批注 → 学习流程图。
 * graphEnabled=false 时不得出现任何方案B 卡片（R99 契约路径零影响）。
 */
class AgentCreationGraphFlowTest {

    private class ScriptedGateway(
        private val turnResults: List<AgentCreationTurnResult>,
        private val incubation: AgentCreationIncubation = cannedIncubation(),
        private val flow: AgentCreationLearningFlow = cannedFlow()
    ) : AgentCreationGateway {
        var converseCalls = 0
        var incubationCalls = 0
        var flowCalls = 0

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult {
            converseCalls++
            return turnResults[(converseCalls - 1).coerceAtMost(turnResults.size - 1)]
        }

        override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
            AgentCreationDocAnalysis(materialTitle = fileName)

        override suspend fun proposeIncubation(
            history: List<AgentCreationHistoryTurn>,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?
        ): AgentCreationIncubation {
            incubationCalls++
            return incubation
        }

        override suspend fun generateLearningFlow(
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?
        ): AgentCreationLearningFlow {
            flowCalls++
            return flow
        }

        companion object {
            fun cannedIncubation(): AgentCreationIncubation = AgentCreationIncubation(
                personaHypothesis = "慢热但较真的初二学生，卡壳时会先复述问题",
                teachingStyle = "先听你讲，卡壳处追问，讲完带你复盘",
                stageGoals = listOf("理解浮力概念", "会做浮力计算"),
                milestones = listOf("能讲清浮沉条件", "例题全对")
            )

            fun cannedFlow(): AgentCreationLearningFlow = AgentCreationLearningFlow(
                topics = listOf(
                    AgentCreationFlowTopic("浮力概念", listOf("浮沉条件", "阿基米德原理")),
                    AgentCreationFlowTopic("浮力计算", listOf("公式变形", "综合题"))
                ),
                edges = listOf(AgentCreationFlowEdge("浮力概念", "浮力计算"))
            )
        }
    }

    /** 收敛轮：「别问了」命中 isStopAsking → forceConverge，草案字段齐。 */
    private fun convergingTurn(): AgentCreationTurnResult = AgentCreationTurnResult(
        understanding = 90,
        assistantNote = "好的，不问了。",
        draft = AgentCreationDraftPatch(
            title = "浮力讲学练",
            learnerRole = "初二学生",
            goal = "把浮力讲明白",
            learningPath = listOf("浮力概念", "浮力计算")
        )
    )

    /** R102 目标确认轮：用户亲口说「我想把浮力讲明白」，与提案重合 → userSourced 直接 Confirmed。
     *  收敛前必须先确认 Goal——否则 Goal 例外规则让收敛轮出不了孵化草案（这正是新语义）。 */
    private fun goalTurn(): AgentCreationTurnResult = AgentCreationTurnResult(
        understanding = 40,
        followUpQuestion = "基础怎么样？",
        draft = AgentCreationDraftPatch(goal = "把浮力讲明白")
    )

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    @Test
    fun convergenceProducesIncubationCard() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()

        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")

        val cards = coordinator.state.feed
            .filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>()
        assertEquals(1, cards.size)
        assertEquals(
            AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm,
            cards.single().status
        )
        assertEquals(CreationState.ConfirmDraft, coordinator.creationGraph.state)
        assertEquals(1, gateway.incubationCalls)
    }

    @Test
    fun annotationSupersedesCardAndReproposes() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()
        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")
        assertEquals(CreationState.ConfirmDraft, coordinator.creationGraph.state)

        coordinator.sendUserText("人物性格再活泼一点")

        assertEquals(CreationState.ConfirmDraft, coordinator.creationGraph.state)
        assertEquals(3, gateway.converseCalls)
        assertEquals(2, gateway.incubationCalls)
        val cards = coordinator.state.feed
            .filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>()
        assertEquals(2, cards.size)
        assertEquals(
            1,
            cards.count { it.status == AgentCreationFeedEntry.IncubationDraftCard.Status.Superseded }
        )
        assertEquals(
            1,
            cards.count { it.status == AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm }
        )
    }

    @Test
    fun confirmTextGeneratesLearningFlow() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()
        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")

        coordinator.sendUserText("可以")

        assertEquals(CreationState.Done, coordinator.creationGraph.state)
        assertEquals(1, gateway.flowCalls)
        val incubationCards = coordinator.state.feed
            .filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>()
        assertEquals(
            AgentCreationFeedEntry.IncubationDraftCard.Status.Confirmed,
            incubationCards.single().status
        )
        val flowCards = coordinator.state.feed
            .filterIsInstance<AgentCreationFeedEntry.LearningFlowCard>()
        assertEquals(1, flowCards.size)
        assertEquals(2, flowCards.single().flow.topics.size)
        assertEquals("depends_on", flowCards.single().flow.edges.single().relation)
        // 草案叠上人物性格与教学方式
        assertEquals("慢热但较真的初二学生，卡壳时会先复述问题", coordinator.state.draft.persona)
        assertEquals("先听你讲，卡壳处追问，讲完带你复盘", coordinator.state.draft.dialogueStrategy)
    }

    @Test
    fun confirmButtonPathMatchesTextPath() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()
        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")

        coordinator.confirmIncubation()

        assertEquals(CreationState.Done, coordinator.creationGraph.state)
        assertEquals(1, gateway.flowCalls)
        assertTrue(
            coordinator.state.feed
                .filterIsInstance<AgentCreationFeedEntry.LearningFlowCard>()
                .isNotEmpty()
        )
    }

    @Test
    fun messageAfterDoneReopensInterview() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()
        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")
        coordinator.sendUserText("可以")
        assertEquals(CreationState.Done, coordinator.creationGraph.state)

        coordinator.sendUserText("再加一个综合复习阶段")

        // 重开访谈；planner 已收敛 → 本轮重新提案，回到待确认。
        assertEquals(CreationState.ConfirmDraft, coordinator.creationGraph.state)
        assertEquals(3, gateway.converseCalls)
    }

    @Test
    fun graphDisabledProducesNoNewCards() = runBlocking {
        val gateway = ScriptedGateway(listOf(goalTurn(), convergingTurn()))
        val coordinator = AgentCreationCoordinator(gateway, clock()) // graphEnabled 默认 false
        coordinator.start()

        coordinator.sendUserText("我想把浮力讲明白")
        coordinator.sendUserText("别问了，直接生成吧")

        assertTrue(
            coordinator.state.feed
                .filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>().isEmpty()
        )
        assertTrue(
            coordinator.state.feed
                .filterIsInstance<AgentCreationFeedEntry.LearningFlowCard>().isEmpty()
        )
        assertEquals(0, gateway.incubationCalls)
        assertEquals(0, gateway.flowCalls)
        assertEquals(CreationState.CollectGoal, coordinator.creationGraph.state)
    }
}
