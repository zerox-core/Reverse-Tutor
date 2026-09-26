package com.reversetutor.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
        var lastStrategy: AgentCreationTurnStrategy? = null

        override suspend fun converse(
            history: List<AgentCreationHistoryTurn>,
            userText: String,
            currentDraft: NewSessionConfiguration,
            docAnalysis: AgentCreationDocAnalysis?,
            strategy: AgentCreationTurnStrategy
        ): AgentCreationTurnResult {
            converseCalls++
            lastStrategy = strategy
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
        // 融合分：0.5×30(u_llm) + 0.5×35(u_det=title15+role20) = 33
        assertEquals(33, state.rawUnderstanding)
        assertEquals("浮力·讲学练会话", state.draft.title)
        assertEquals("初二学生", state.draft.learnerRole)
        // 开场 + user + note + followUp + draft 卡
        assertEquals(5, state.feed.size)
        assertTrue((state.feed[2] as AgentCreationFeedEntry.Assistant).text.contains("草案先起个头"))
        assertEquals(1, gateway.converseCalls)
    }

    @Test
    fun draftCardStaysSingleAndSinksToFeedTail() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 30,
                    followUpQuestion = "目标？",
                    draft = AgentCreationDraftPatch(title = "旧标题", learnerRole = "初二学生")
                ),
                AgentCreationTurnResult(
                    understanding = 50,
                    followUpQuestion = "性格？",
                    draft = AgentCreationDraftPatch(persona = "慢热较真")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("第一句")
        coordinator.sendUserText("第二句")

        // R84：草案卡单卡化——feed 里永远只有一张，且沉在对话流末尾
        val cards = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.DraftCard>()
        assertEquals(1, cards.size)
        assertEquals("慢热较真", cards.single().configuration.persona)
        assertEquals("旧标题", cards.single().configuration.title)
        assertTrue(coordinator.state.feed.last() is AgentCreationFeedEntry.DraftCard)
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
        // 融合分：0.5×85 + 0.5×15(u_det=title15) = 50，必填缺 learnerRole 封顶 60 不生效
        assertEquals(50, coordinator.state.rawUnderstanding)
        assertEquals(50, coordinator.state.displayedUnderstanding)
        assertFalse(coordinator.state.understandingHigh)
        assertFalse(coordinator.state.canCreate)

        coordinator.sendUserText("第二句")
        // 融合分：0.5×85 + 0.5×35(u_det=title15+role20) = 60
        assertEquals(60, coordinator.state.displayedUnderstanding)
        assertFalse(coordinator.state.understandingHigh)
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
        // 融合分：0.5×60 + 0.5×55(u_det=goal20+title15+role20) = 58
        assertEquals(58, coordinator.state.rawUnderstanding)
    }

    @Test
    fun requestDocumentFlagSurfacesOnlyWhenNoAnalysisYet() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 50,
                    followUpQuestion = "基础怎么样？",
                    draft = AgentCreationDraftPatch(goal = "期末冲刺", title = "t", learnerRole = "r")
                ),
                AgentCreationTurnResult(
                    understanding = 70,
                    requestDocument = true
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我要期末冲刺")
        assertFalse(coordinator.state.requestDocumentActive)

        // 第 2 轮：goal+role 就绪、无文档、满 2 轮 → 放行请求资料
        coordinator.sendUserText("我是初二学生")
        assertTrue(coordinator.state.requestDocumentActive)
    }

    @Test
    fun stopAskingForcesConvergenceAndStripsFollowUp() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 50,
                    followUpQuestion = "还聊吗？"
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("别问了，直接生成吧")

        // 收敛：策略快照带 converge，追问被剥离，改发收敛话术
        assertEquals(true, gateway.lastStrategy?.converge)
        val last = coordinator.state.feed.last() as AgentCreationFeedEntry.Assistant
        assertTrue(last.text.contains("不问了"))
        assertFalse(
            coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>()
                .any { it.text.contains("还聊吗") }
        )
    }

    @Test
    fun titleFallbackProposalFillsFromGoal() = runBlocking {
        val gateway = ScriptedGateway(
            listOf(
                AgentCreationTurnResult(
                    understanding = 50,
                    followUpQuestion = "基础怎么样？",
                    draft = AgentCreationDraftPatch(goal = "把浮力讲明白，期末要考")
                )
            )
        )
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("我想把浮力讲明白，期末要考")

        // goal 已填 title 仍空 → 客户端合成提案（首句裁 14 字 + 后缀）
        assertEquals("把浮力讲明白 · 讲学练", coordinator.state.draft.title)
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

    // R91 回归：网关响应被挂起期间，用户气泡必须已经上屏、状态变更已通知，
    // 不能等整轮生成结束才和回复一起出现（2026-09-26 真机反馈）。
    @Test
    fun userBubbleAppearsImmediatelyBeforeGatewayResponds() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val blockingGateway = object : AgentCreationGateway {
            override suspend fun converse(
                history: List<AgentCreationHistoryTurn>,
                userText: String,
                currentDraft: NewSessionConfiguration,
                docAnalysis: AgentCreationDocAnalysis?,
                strategy: AgentCreationTurnStrategy
            ): AgentCreationTurnResult {
                release.await()
                return AgentCreationTurnResult(understanding = 10, followUpQuestion = "占位")
            }

            override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
                ScriptedGateway.cannedAnalysis()
        }
        val coordinator = AgentCreationCoordinator(blockingGateway, clock())
        var notifications = 0
        coordinator.onStateChanged = { notifications++ }
        coordinator.start()
        val baseline = notifications

        val job = async { coordinator.sendUserText("我想学浮力") }
        yield()

        val midState = coordinator.state
        assertTrue(midState.busy)
        val bubbles = midState.feed.filterIsInstance<AgentCreationFeedEntry.User>()
        assertEquals(1, bubbles.size)
        assertEquals("我想学浮力", bubbles.single().text)
        assertTrue(notifications > baseline)

        release.complete(Unit)
        job.await()

        assertFalse(coordinator.state.busy)
        assertTrue(coordinator.state.feed.size >= 3)
    }

    // R93：流式期间占位气泡随口语快照生长，轮次落定后占位撤掉、正式气泡接管；
    // 同时坐实协调器走 converseStreaming（老 converse 被调会直接 error）。
    @Test
    fun streamingPartialsGrowPlaceholderBubbleThenFinalize() = runBlocking {
        val gateway = object : AgentCreationGateway {
            override suspend fun converseStreaming(
                history: List<AgentCreationHistoryTurn>,
                userText: String,
                currentDraft: NewSessionConfiguration,
                docAnalysis: AgentCreationDocAnalysis?,
                strategy: AgentCreationTurnStrategy,
                onPartialSpoken: (String) -> Unit
            ): AgentCreationTurnResult {
                onPartialSpoken("记下")
                onPartialSpoken("记下了。")
                return AgentCreationTurnResult(
                    understanding = 30,
                    assistantNote = "记下了。",
                    followUpQuestion = "目标是什么？"
                )
            }

            override suspend fun converse(
                history: List<AgentCreationHistoryTurn>,
                userText: String,
                currentDraft: NewSessionConfiguration,
                docAnalysis: AgentCreationDocAnalysis?,
                strategy: AgentCreationTurnStrategy
            ): AgentCreationTurnResult = error("R93 起创建链路走 converseStreaming，不应再调 converse")

            override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
                ScriptedGateway.cannedAnalysis()
        }
        val coordinator = AgentCreationCoordinator(gateway, clock())
        val seen = mutableListOf<String>()
        coordinator.onStateChanged = {
            coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>()
                .lastOrNull()?.let { seen += it.text }
        }
        coordinator.start()

        coordinator.sendUserText("我想学浮力")

        // 流式期间两帧快照都被通知上屏过
        assertTrue(seen.contains("记下"))
        assertTrue(seen.contains("记下了。"))
        // 落定：开场 + note + followUp 三条助手气泡，无占位残留、无双份
        val assistants = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>()
        assertEquals(3, assistants.size)
        assertEquals("记下了。", assistants[1].text)
        assertEquals("目标是什么？", assistants[2].text)
    }

    // R93：生成失败时流式占位气泡也必须撤掉，不留半截话（降级话术照常出现、照重重试）。
    @Test
    fun streamingPlaceholderRemovedOnGenerationFailure() = runBlocking {
        var calls = 0
        val gateway = object : AgentCreationGateway {
            override suspend fun converseStreaming(
                history: List<AgentCreationHistoryTurn>,
                userText: String,
                currentDraft: NewSessionConfiguration,
                docAnalysis: AgentCreationDocAnalysis?,
                strategy: AgentCreationTurnStrategy,
                onPartialSpoken: (String) -> Unit
            ): AgentCreationTurnResult {
                calls++
                onPartialSpoken("半截话")
                throw IllegalStateException("stream broken")
            }

            override suspend fun converse(
                history: List<AgentCreationHistoryTurn>,
                userText: String,
                currentDraft: NewSessionConfiguration,
                docAnalysis: AgentCreationDocAnalysis?,
                strategy: AgentCreationTurnStrategy
            ): AgentCreationTurnResult = error("R93 起创建链路走 converseStreaming，不应再调 converse")

            override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis =
                ScriptedGateway.cannedAnalysis()
        }
        val coordinator = AgentCreationCoordinator(gateway, clock())
        coordinator.start()

        coordinator.sendUserText("随便聊聊")

        assertEquals(2, calls)
        assertNotNull(coordinator.state.generationError)
        val assistants = coordinator.state.feed.filterIsInstance<AgentCreationFeedEntry.Assistant>()
        assertFalse(assistants.any { it.text.contains("半截话") })
        assertTrue(assistants.last().text.contains("再说一遍"))
    }
}
