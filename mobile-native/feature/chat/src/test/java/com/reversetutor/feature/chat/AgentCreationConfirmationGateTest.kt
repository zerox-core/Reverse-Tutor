package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R102 确认闸门端到端（全假模型零额度）：模型 draft 回包只能进 Proposed，
 * 代码层确认信号才升 Confirmed；流程推进（出孵化草案）只读 Confirmed 槽位。
 * 场景①即「魔方事故」复现：模型再也不能替用户锁定目标。
 */
class AgentCreationConfirmationGateTest {

    private class ScriptedGateway(
        private val turnResults: List<AgentCreationTurnResult>
    ) : AgentCreationGateway {
        var converseCalls = 0
        var incubationCalls = 0

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
            return super.proposeIncubation(history, currentDraft, docAnalysis)
        }
    }

    private fun clock(): () -> Long {
        var tick = 0L
        return { tick++ }
    }

    private fun lastAssistant(coordinator: AgentCreationCoordinator): AgentCreationFeedEntry.Assistant =
        coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>().last()

    /** 场景①：魔方事故复现——模型回包「锁定目标」只进 Proposed；用户反问被拦截，选项问题原样保留。 */
    @Test
    fun cubeAccidentCannotRecurModelCannotLockGoal() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                // 事故原貌：用户只说「我想学魔方」，模型直接回包「三阶基础还原」+ 派生标题 + 锁定话术。
                AgentCreationTurnResult(
                    understanding = 80,
                    assistantNote = "那我们锁定三阶魔方基础还原吧！",
                    followUpQuestion = "你更喜欢从三阶入门，还是从更简单的异形入门？",
                    draft = AgentCreationDraftPatch(
                        goal = "学会三阶魔方基础还原",
                        title = "三阶魔方基础还原 · 讲学练"
                    )
                ),
                AgentCreationTurnResult(
                    understanding = 85,
                    followUpQuestion = "零基础的话推荐先学三阶——它是所有魔方的根基，你觉得呢？"
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()

        coordinator.sendUserText("我想学魔方")
        // 提案只进 Proposed：goal 不落草案、不出孵化卡（title 是展示字段，上卡不代表目标定稿）。
        assertTrue(coordinator.state.draft.goal.isBlank())
        assertEquals(CreationState.Interview, coordinator.creationGraph.state)
        assertEquals(0, gateway.incubationCalls)

        // 用户反问「我该学什么呀？」——非正面回答拦截：悬置提案不得确认，流程原地不动。
        coordinator.sendUserText("我该学什么呀？")
        assertTrue(coordinator.state.draft.goal.isBlank())
        assertEquals(CreationState.Interview, coordinator.creationGraph.state)
        assertEquals(0, gateway.incubationCalls)
        assertTrue(
            coordinator.state.feed
                .filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>().isEmpty()
        )
        // LLM 给的建议问题原样保留（含「三阶」推荐），不被兜底话术覆盖。
        assertTrue(lastAssistant(coordinator).text.contains("三阶"))
    }

    /** 场景②：正常推进——亲口给值直接 Confirmed 落草案；没表态的提案不落；止损出孵化草案。 */
    @Test
    fun confirmedSlotsDriveProposal() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 40,
                    followUpQuestion = "你现在的基础怎么样？",
                    draft = AgentCreationDraftPatch(goal = "学会三阶魔方基础还原")
                ),
                AgentCreationTurnResult(
                    understanding = 55,
                    followUpQuestion = "这个 AI 学生什么性格？",
                    draft = AgentCreationDraftPatch(learnerRole = "零基础，完全没玩过魔方")
                ),
                AgentCreationTurnResult(
                    understanding = 70,
                    followUpQuestion = "「慢热但较真」这个性格提案你看行吗？",
                    draft = AgentCreationDraftPatch(persona = "慢热但较真")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()

        // 轮1：用户亲口说的目标与提案重合 → userSourced 直接 Confirmed 落草案。
        coordinator.sendUserText("我想学会三阶魔方基础还原")
        assertEquals("学会三阶魔方基础还原", coordinator.state.draft.goal)

        // 轮2：亲口给基础 → Confirmed 落草案。
        coordinator.sendUserText("我零基础，完全没玩过魔方")
        assertEquals("零基础，完全没玩过魔方", coordinator.state.draft.learnerRole)

        // 轮3：人设只是模型提案、用户没表态（「你看着办」不是确认）→ 不落草案、不出卡。
        coordinator.sendUserText("你看着办")
        assertTrue(coordinator.state.draft.persona.isBlank())
        assertEquals(CreationState.Interview, coordinator.creationGraph.state)
        assertEquals(0, gateway.incubationCalls)

        // 轮4 用户止损：「别问了，直接生成」→ 收敛兜底把桌上提案升 Confirmed → 出孵化草案。
        coordinator.sendUserText("别问了，直接生成吧")
        assertEquals(CreationState.ConfirmDraft, coordinator.creationGraph.state)
        assertEquals(1, gateway.incubationCalls)
        assertEquals(
            1,
            coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.IncubationDraftCard>().size
        )
    }

    /** 场景③：收敛兜底 Goal 例外——目标永不默认，学习者角色兜底 Confirmed，强制追问目标且不说「不问了」。 */
    @Test
    fun convergenceWithoutGoalKeepsAskingGoal() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 90,
                    followUpQuestion = "还聊吗？",
                    draft = AgentCreationDraftPatch(learnerRole = "初二学生")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()

        coordinator.sendUserText("别问了，直接生成吧")

        // 学习者角色按收敛兜底落定，目标仍空白、不出草案卡。
        assertTrue(coordinator.state.draft.goal.isBlank())
        assertEquals("初二学生", coordinator.state.draft.learnerRole)
        assertEquals(CreationState.Interview, coordinator.creationGraph.state)
        assertEquals(0, gateway.incubationCalls)
        // 改发强制目标追问；收敛话术「不问了」在目标缺失时不许说。
        val last = lastAssistant(coordinator)
        assertTrue(last.text.contains("你想达到什么目标"))
        assertFalse(last.text.contains("不问了"))
    }

    /** 场景④：整句肯定采纳悬置提案——goal 落草案并按 R102 规则派生标题。 */
    @Test
    fun wholeSentenceConfirmAdoptsProposal() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 40,
                    followUpQuestion = "学会三阶魔方基础还原，这个目标对吗？",
                    draft = AgentCreationDraftPatch(goal = "学会三阶魔方基础还原")
                ),
                AgentCreationTurnResult(understanding = 55, followUpQuestion = "基础怎么样？")
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想学魔方")
        assertTrue(coordinator.state.draft.goal.isBlank())

        // 整句肯定 → 采纳悬置提案：goal 落草案 + title 兜底（Goal 已确认才允许派生）。
        coordinator.sendUserText("对")
        assertEquals("学会三阶魔方基础还原", coordinator.state.draft.goal)
        assertEquals("学会三阶魔方基础还原 · 讲学练", coordinator.state.draft.title)
    }

    /** 场景⑤：了解度 95 + 全字段提案 + 「锁定」话术——分数对状态转移零投票权，全部只进 Proposed。 */
    @Test
    fun highUnderstandingScoreHasZeroVote() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 95,
                    assistantNote = "那我们就锁定速拧方向吧！",
                    followUpQuestion = "你看这个方向行吗？",
                    draft = AgentCreationDraftPatch(
                        goal = "三阶速拧进 30 秒",
                        learnerRole = "有基础",
                        persona = "好胜",
                        title = "三阶速拧 · 讲学练"
                    )
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock(), graphEnabled = true)
        coordinator.start()

        coordinator.sendUserText("随便学学")

        // 全部只进 Proposed：goal/role/persona 一律不落草案，不出孵化卡。
        assertTrue(coordinator.state.draft.goal.isBlank())
        assertTrue(coordinator.state.draft.learnerRole.isBlank())
        assertTrue(coordinator.state.draft.persona.isBlank())
        assertEquals(CreationState.Interview, coordinator.creationGraph.state)
        assertEquals(0, gateway.incubationCalls)
        // 分数只是展示值：0.5×95 + 0.5×15(u_det=title15) = 55，高分既不被封顶也推不动流程。
        assertEquals(55, coordinator.state.displayedUnderstanding)
        assertEquals(coordinator.state.rawUnderstanding, coordinator.state.displayedUnderstanding)
    }
}
