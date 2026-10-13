package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.GenerationState
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.TurnMonologuePort
import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.LlmResponse
import com.reversetutor.feature.reversetutor.loop.ToolCallRequest
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 反转家教端到端冒烟（Fake 模式全链路，无网络）：
 *
 *   FormalLearningPresets 预设卡 → RoutingTurnPreparationPort 路由判定
 *   → ReverseTutorTurnPreparationCoordinator（原子认领）
 *   → ToolLoop 真实 31 工具注册表 + 真实工具执行（diagnose_learning_barrier）
 *   → TurnMonologuePort LOADING 实时读路径 → READY 契约投影 + 消息持久化
 *
 * LLM 用脚本化客户端（两轮：工具调用 → 最终台词），其余桥接均为内存实现。
 */
class ReverseTutorEndToEndSmokeTest {

    private val savedMessages = mutableListOf<Triple<String, String, String?>>() // sessionId, text, monologue
    private val completedJobIds = mutableListOf<String>()
    private val defaultPortCalls = mutableListOf<String>()
    private val partialStore = com.reversetutor.core.data.background.GenerationPartialStore()

    private lateinit var coordinator: ReverseTutorTurnPreparationCoordinator
    private lateinit var routingPort: RoutingTurnPreparationPort
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private class ScriptedLlmClient(
        private val script: MutableList<LlmResponse>
    ) : ToolLoopLlmClient {
        override suspend fun chat(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?
        ): LlmResponse {
            check(script.isNotEmpty()) { "LLM 脚本耗尽：收到第 ${script.size + 1} 轮请求" }
            return script.removeAt(0)
        }
    }

    @Before
    fun setUp() {
        val testCurriculum = """
        {
          "math": {
            "grade_order": ["初一", "初二", "初三"],
            "concepts": [
              {
                "id": "一元一次方程",
                "grade": "初一",
                "track": "代数",
                "difficulty": 2,
                "prerequisites": [],
                "standard_definition": "含有一个未知数且未知数的次数为1的整式方程",
                "key_facts": ["移项要变号", "等式两边同乘除"]
              }
            ]
          }
        }
        """.trimIndent()
        val testCards = """
        {
          "card_01_struggling": {
            "card_id": "card_01_struggling",
            "name": "李想",
            "identity_text": "高二学生",
            "personality_text": "基础薄弱但不想承认，容易混淆移项变号规则",
            "speech_style": "口语化，句子短，常带语气词",
            "catchphrases": ["这题我肯定会的"],
            "example_buckets": {
              "confused_low": [{"line": "老师我还是有点搞不明白这里为什么变号"}]
            }
          }
        }
        """.trimIndent()

        StudentCards.setDefault(StudentCards(testCards))
        val registry = ToolRegistry()
        CoreTools.registerAll(registry)
        val toolLoop = ToolLoop(
            registry = registry,
            catalog = CurriculumCatalog(testCurriculum),
            store = MaterialStore(),
            maxStepsPerTurn = 5
        )

        val llmScript = mutableListOf(
            LlmResponse(
                content = null,
                toolCalls = listOf(
                    ToolCallRequest(
                        callId = "call_1",
                        name = "diagnose_learning_barrier",
                        arguments = """{"concept_id": "一元一次方程", "barrier_type": "CONCEPT_CONFUSION"}"""
                    )
                ),
                finishReason = "tool_calls"
            ),
            LlmResponse(
                content = "老师，我好像懂了——移项就是把那一项挪到等号另一边，同时要变号，对吧？",
                toolCalls = emptyList(),
                finishReason = "stop"
            )
        )

        coordinator = ReverseTutorTurnPreparationCoordinator(
            isSessionDeleted = { false },
            enqueueJob = { input, now ->
                BackgroundGenerationJob(
                    id = "job-1",
                    spaceId = input.spaceId,
                    sessionId = input.sessionId,
                    userMessageId = input.userMessageId,
                    userText = input.userText,
                    token = input.token,
                    modelBindingId = null,
                    status = BackgroundJobStatus.Queued,
                    createdAtEpochMillis = now,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    errorMessage = null,
                    capabilities = null,
                    quoteExcerpt = null,
                    imageAttachments = input.imageAttachments,
                    contextEvidence = input.contextEvidence,
                    sessionPolicy = input.sessionPolicy
                )
            },
            claimJob = { _, _ -> 1 },
            updateJobCompleted = { id, _ -> completedJobIds.add(id) },
            updateJobFailed = { _, _, _ -> },
            saveAssistantMessage = { _, sessionId, text, monologue, _ ->
                savedMessages.add(Triple(sessionId, text, monologue))
                "msg-1"
            },
            loadMessages = { _ -> emptyList() },
            partialStore = partialStore,
            toolLoop = toolLoop,
            llmClientProvider = { ScriptedLlmClient(llmScript) },
            coroutineScope = testScope,
            nowEpochMillis = { 1000L }
        )

        val defaultPort = BackgroundTurnPreparationPort { request ->
            defaultPortCalls.add(request.sessionId)
            BackgroundTurnPreparationResult.Unavailable
        }
        routingPort = RoutingTurnPreparationPort(
            reversePort = coordinator,
            defaultPort = defaultPort
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun reverseRequest(sessionId: String, token: String, text: String) =
        BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = sessionId,
            userMessageId = "u-1",
            userText = text,
            token = token,
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = NewSessionConfiguration(
                title = "反转家教课堂",
                learnerDisplayName = "李想",
                builtInPresetId = REVERSE_TUTOR_PRESET_ID
            )
        )

    @Test
    fun presetCatalogContainsReverseTutorCard() {
        val preset = FormalLearningPresets.byId(REVERSE_TUTOR_PRESET_ID)
        assertNotNull("预设库必须包含 reverse-tutor 卡（路由入口）", preset)
        assertEquals("反转家教课堂", preset!!.title)
        assertEquals("李想", preset.learnerName)
    }

    @Test
    fun reverseSessionRoutesToAlgoKernelAndCompletesFullTurn() = runBlocking {
        val result = routingPort.prepareAndEnqueue(
            reverseRequest("session-rt", "tok-rt", "3x - 5 = 10，把 -5 移过去")
        )

        // 1. 路由命中反转内核：Queued + LOADING 契约投影
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        val queued = result as BackgroundTurnPreparationResult.Queued
        assertEquals("job-1", queued.jobId)
        assertEquals(GenerationState.LOADING, queued.contract.generation.state)
        assertTrue("反转会话不应触达默认链路", defaultPortCalls.isEmpty())

        // 2. LOADING 期间思考抽屉实时读路径：真实装配中 ChatScreen 对端口整体
        //    as? TurnMonologuePort——路由端口本身不实现该接口，协调器实现；
        //    下一用例验证协调器桥接，此处仅验证路由/回合主链路。

        // 3. 异步回合完成：READY 投影 + 消息 + monologue 持久化
        assertEquals(listOf("job-1"), completedJobIds)
        val contract = coordinator.getLatestContract("session-rt")
        assertNotNull(contract)
        assertEquals(GenerationState.READY, contract!!.generation.state)
        // 当前语义（与 spike 一致）：topic 取会话标题。改进点：应优先取
        // diagnose 工具诊断出的 concept_id（已记后续任务）。
        assertEquals("反转家教课堂", contract.currentKnowledgePoint)
        assertNotNull(contract.evaluation)
        assertNotNull(contract.action)

        // 4. 消息落库：学生台词 + 纯内心话独白（2026-10-13 剔除统计块后，
        //    脚本无信封/中文思考 → 独白为空而非决策轨迹）
        assertEquals(1, savedMessages.size)
        val (sessionId, text, monologue) = savedMessages.first()
        assertEquals("session-rt", sessionId)
        assertTrue("台词应来自 LLM 最终轮", text.contains("移项"))
        assertFalse("统计块不应再拼接进独白", monologue.orEmpty().contains("反转家教决策轨迹"))
        assertFalse("工具名不应出现在独白", monologue.orEmpty().contains("diagnose_learning_barrier"))
    }

    @Test
    fun coordinatorBridgesTurnMonologuePortForLoadingDrawer() = runBlocking {
        routingPort.prepareAndEnqueue(
            reverseRequest("session-mono", "tok-mono", "一元一次方程怎么解")
        )

        // 协调器实现 TurnMonologuePort：LOADING 抽屉实时读（ChatScreen as? 路径）
        val monologuePort = coordinator as TurnMonologuePort
        assertFalse("统计块不应再出现在独白", monologuePort.getMonologue("job-1", "tok-mono").orEmpty().contains("反转家教决策轨迹"))
        assertFalse(monologuePort.getPreview("job-1", "tok-mono").isNullOrEmpty())
        // 未写过的 job/token 组合返回空（不抛异常）
        assertEquals(null, monologuePort.getMonologue("no-such-job", "tok-x"))
    }

    @Test
    fun normalSessionRoutesToDefaultPort() = runBlocking {
        val result = routingPort.prepareAndEnqueue(
            BackgroundTurnPreparationRequest(
                spaceId = "space-1",
                sessionId = "session-normal",
                userMessageId = "u-2",
                userText = "帮我复习一下上次的错题",
                token = "tok-normal",
                quoteExcerpt = null,
                imageAttachments = emptyList(),
                sessionSnapshot = NewSessionConfiguration(title = "普通会话") // builtInPresetId=null
            )
        )

        assertEquals(BackgroundTurnPreparationResult.Unavailable, result)
        assertEquals(listOf("session-normal"), defaultPortCalls)
        assertTrue("普通会话不应进入反转内核", completedJobIds.isEmpty())
        assertTrue(savedMessages.isEmpty())
    }
}
