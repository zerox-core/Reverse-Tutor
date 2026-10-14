package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.background.GenerationPartialStore
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.GenerationState
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.loop.LlmCallRecord
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.LlmResponse
import com.reversetutor.feature.reversetutor.loop.ToolCallRequest
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReverseTutorTurnPreparationCoordinatorTest {

    private val queuedJobs = mutableListOf<BackgroundGenerationJob>()
    private val claimedJobIds = mutableListOf<String>()
    private val completedJobIds = mutableListOf<String>()
    private val failedJobs = mutableMapOf<String, String>()
    private val savedMessages = mutableListOf<Triple<String, String, String?>>() // sessionId, text, monologue
    private val partialStore = GenerationPartialStore()

    private lateinit var toolLoop: ToolLoop
    private lateinit var mockLlmClient: TestToolLoopLlmClient
    private lateinit var coordinator: ReverseTutorTurnPreparationCoordinator
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

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
          "proud_average": {
            "card_id": "proud_average",
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
        val catalog = CurriculumCatalog(testCurriculum)
        val registry = ToolRegistry()
        CoreTools.registerAll(registry)
        val store = MaterialStore()

        toolLoop = ToolLoop(
            registry = registry,
            catalog = catalog,
            store = store,
            maxStepsPerTurn = 5
        )

        mockLlmClient = TestToolLoopLlmClient()

        coordinator = ReverseTutorTurnPreparationCoordinator(
            isSessionDeleted = { it == "deleted-session" },
            enqueueJob = { input, now ->
                val job = BackgroundGenerationJob(
                    id = "job-${queuedJobs.size + 1}",
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
                queuedJobs.add(job)
                job
            },
            claimJob = { id, _ ->
                claimedJobIds.add(id)
                1
            },
            updateJobCompleted = { id, _ ->
                completedJobIds.add(id)
            },
            updateJobFailed = { id, _, error ->
                failedJobs[id] = error
            },
            saveAssistantMessage = { _, sessionId, text, monologue, _ ->
                savedMessages.add(Triple(sessionId, text, monologue))
                "msg-${savedMessages.size}"
            },
            loadMessages = { _ -> emptyList() },
            partialStore = partialStore,
            toolLoop = toolLoop,
            llmClientProvider = { mockLlmClient },
            coroutineScope = testScope,
            nowEpochMillis = { 1000L }
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun prepareAndEnqueueBlankInputReturnsBlankInput() = runBlocking {
        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-1",
            userMessageId = "u-1",
            userText = "   ",
            token = "tok-1",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertEquals(BackgroundTurnPreparationResult.BlankInput, result)
        assertTrue(queuedJobs.isEmpty())
    }

    @Test
    fun prepareAndEnqueueDeletedSessionReturnsSessionUnavailable() = runBlocking {
        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "deleted-session",
            userMessageId = "u-1",
            userText = "讲讲方程",
            token = "tok-1",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertEquals(BackgroundTurnPreparationResult.SessionUnavailable, result)
        assertTrue(queuedJobs.isEmpty())
    }

    @Test
    fun prepareAndEnqueueQueuesJobAndClaimsRunning() = runBlocking {
        mockLlmClient.replyText = "老师，移项的时候为什么要变号呢？"

        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-1",
            userMessageId = "u-1",
            userText = "今天我们来学一元一次方程的移项",
            token = "tok-1",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = NewSessionConfiguration(
                title = "一元一次方程",
                learnerDisplayName = "李想"
            )
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        val queued = result as BackgroundTurnPreparationResult.Queued
        assertEquals("job-1", queued.jobId)
        assertEquals(GenerationState.LOADING, queued.contract.generation.state)

        // 验证 Job 被认领为 Running
        assertEquals(listOf("job-1"), claimedJobIds)

        // 验证异步协程执行结果
        assertEquals(listOf("job-1"), completedJobIds)
        assertEquals(1, savedMessages.size)
        val saved = savedMessages.first()
        assertEquals("session-1", saved.first)
        assertEquals("老师，移项的时候为什么要变号呢？", saved.second)
        assertNotNull(saved.third) // monologue 不为空

        // 验证最新契约已投影为 READY
        val latestContract = coordinator.getLatestContract("session-1")
        assertNotNull(latestContract)
        assertEquals(GenerationState.READY, latestContract!!.generation.state)
        assertEquals("一元一次方程", latestContract.currentKnowledgePoint)
        assertNotNull(latestContract.evaluation)
        assertNotNull(latestContract.action)
    }

    @Test
    fun runTurnCapturesToolEventsAndGeneratesMonologue() = runBlocking {
        // 配置 LLM 第一次返回工具调用，第二次返回最终回答
        mockLlmClient.responses = mutableListOf(
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
                content = "老师我明白了一点，等号两边要同时做运算对吧？",
                toolCalls = emptyList(),
                finishReason = "stop"
            )
        )

        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-2",
            userMessageId = "u-2",
            userText = "3x - 5 = 10，把 -5 移过去",
            token = "tok-2",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(listOf("job-1"), completedJobIds)

        val monologue = partialStore.getMonologue("job-1", "tok-2")
        assertNotNull(monologue)
        // 2026-10-13 用户拍板：剔除「反转家教决策轨迹」统计块，抽屉只留纯内心话；
        // 无信封/中文思考的轮次独白为空。
        assertFalse("统计块不应再拼接进独白", monologue!!.contains("反转家教决策轨迹"))
        assertFalse("工具名不应出现在独白", monologue.contains("diagnose_learning_barrier"))

        val latest = coordinator.getLatestContract("session-2")
        assertNotNull(latest)
        assertEquals(GenerationState.READY, latest!!.generation.state)
    }

    @Test
    fun runTurnWithThinkingEnvelopeReplacesPlaceholderAndEnrichesMonologue() = runBlocking {
        mockLlmClient.replyText = "<thinking>卡在配方法的常数项了，想问问为什么是加9</thinking>\n老师，为什么这里两边都要加9呢？"

        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-thinking",
            userMessageId = "u-think",
            userText = "配方法第一步：两边加9",
            token = "tok-think",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(listOf("job-1"), completedJobIds)

        assertEquals(1, savedMessages.size)
        val saved = savedMessages.first()
        assertEquals("session-thinking", saved.first)
        // 验证正文干净，独白标签已被剥离
        assertEquals("老师，为什么这里两边都要加9呢？", saved.second)

        // 验证独白是纯内心话：有第一人称思考、无轮次标注、无统计块，占位符被替换
        assertNotNull(saved.third)
        val monologue = saved.third!!
        assertTrue("独白应包含第一人称思考", monologue.contains("卡在配方法的常数项了，想问问为什么是加9"))
        assertFalse("轮次标注应剔除", monologue.contains("轮内心"))
        assertFalse("统计块应剔除", monologue.contains("反转家教决策轨迹"))
        assertFalse("占位符文本不应残留在终态独白中", monologue.contains("正在分析教师讲解..."))
    }

    @Test
    fun runTurnOnLlmFailureDegradesGracefullyAndStillCompletes() = runBlocking {
        mockLlmClient.shouldThrow = RuntimeException("LLM API 连接超时")

        val request = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-3",
            userMessageId = "u-3",
            userText = "测试失败分支",
            token = "tok-3",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )

        val result = coordinator.prepareAndEnqueue(request)

        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        // ToolLoop 内部消化 LLM 异常并降级 → 走正常完成路径而非失败路径
        assertEquals(listOf("job-1"), completedJobIds)
        assertTrue(failedJobs.isEmpty())

        // 助手消息为第一轮降级台词
        assertEquals(1, savedMessages.size)
        val saved = savedMessages.first()
        assertEquals("session-3", saved.first)
        assertTrue(
            "降级台词应出现: ${saved.second}",
            saved.second.contains("我准备好学习了") && saved.second.contains("卡了一下")
        )

        // 独白只保留纯内心话（2026-10-13 用户拍板剔除「决策轨迹」统计块）：
        // LLM 失败降级轮无内心话 → 独白为空，降级/错误痕迹留在 TurnReport 不上抽屉
        val monologue = partialStore.getMonologue("job-1", "tok-3")
        assertFalse("降级痕迹不应再写入独白", monologue.orEmpty().contains("llm_failure"))
        assertFalse(monologue.orEmpty().contains("LLM API 连接超时"))

        // 最新契约仍为 READY（降级不是错误）
        val latest = coordinator.getLatestContract("session-3")
        assertNotNull(latest)
        assertEquals(GenerationState.READY, latest!!.generation.state)
    }

    @Test
    fun multiTurnPreservesRTSessionAndAdvancesRound() = runBlocking {
        mockLlmClient.replyText = "第一轮回答"
        val request1 = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-round",
            userMessageId = "u-r1",
            userText = "第1轮输入",
            token = "tok-r1",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )
        coordinator.prepareAndEnqueue(request1)

        val sessionAfterRound1 = coordinator.getSession("session-round")
        assertNotNull(sessionAfterRound1)
        assertEquals(1, sessionAfterRound1!!.currentRound)

        mockLlmClient.replyText = "第二轮回答"
        val request2 = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-round",
            userMessageId = "u-r2",
            userText = "第2轮输入",
            token = "tok-r2",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )
        coordinator.prepareAndEnqueue(request2)

        val sessionAfterRound2 = coordinator.getSession("session-round")
        assertNotNull(sessionAfterRound2)
        assertEquals(2, sessionAfterRound2!!.currentRound)
        assertEquals(2, savedMessages.size)
    }

    // ------------------------------------------------------------------
    // 智能插队（2026-10-11 拍板）：活动回合执行中，连发请求注入而非新建 job
    // ------------------------------------------------------------------

    @Test
    fun concurrentRequestJoinsActiveTurnInsteadOfNewJob() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val llmCalls = AtomicInteger(0)
        val gateClient = object : ToolLoopLlmClient {
            override suspend fun chat(
                messages: List<LlmMessage>,
                tools: List<JSONObject>,
                model: String?
            ): LlmResponse {
                val call = llmCalls.incrementAndGet()
                if (call == 1) {
                    gate.await() // 第一轮挂起，模拟生成中
                }
                val sawInjected = messages.any { it.role == "user" && it.content == "逗逗你" }
                return LlmResponse(
                    content = if (sawInjected) "带新内容的合并回复" else "首轮回复",
                    elapsedMs = 5L
                )
            }
        }
        val gateCoordinator = ReverseTutorTurnPreparationCoordinator(
            isSessionDeleted = { false },
            enqueueJob = { input, now ->
                val job = BackgroundGenerationJob(
                    id = "gjob-${queuedJobs.size + 1}",
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
                queuedJobs.add(job)
                job
            },
            claimJob = { id, _ -> claimedJobIds.add(id); 1 },
            updateJobCompleted = { id, _ -> completedJobIds.add(id) },
            updateJobFailed = { id, _, error -> failedJobs[id] = error },
            saveAssistantMessage = { _, sessionId, text, monologue, _ ->
                savedMessages.add(Triple(sessionId, text, monologue))
                "msg-${savedMessages.size}"
            },
            loadMessages = { _ -> emptyList() },
            partialStore = partialStore,
            toolLoop = toolLoop,
            llmClientProvider = { gateClient },
            coroutineScope = testScope,
            nowEpochMillis = { 1000L }
        )

        val req1 = BackgroundTurnPreparationRequest(
            spaceId = "space-1",
            sessionId = "session-inject",
            userMessageId = "u-i1",
            userText = "哈哈哈",
            token = "tok-i1",
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            sessionSnapshot = null
        )
        val result1 = gateCoordinator.prepareAndEnqueue(req1)
        assertTrue(result1 is BackgroundTurnPreparationResult.Queued)
        assertEquals("gjob-1", (result1 as BackgroundTurnPreparationResult.Queued).jobId)

        // Unconfined 下 executeTurn 已跑到 gate.await()，activeJobs 已登记 → 第二请求应插队
        val req2 = req1.copy(
            userMessageId = "u-i2",
            userText = "逗逗你",
            token = "tok-i2"
        )
        val result2 = gateCoordinator.prepareAndEnqueue(req2)

        // 不新建 job，返回活动 job 让 UI 继续观察同一回合
        assertTrue(result2 is BackgroundTurnPreparationResult.Queued)
        assertEquals("gjob-1", (result2 as BackgroundTurnPreparationResult.Queued).jobId)
        assertEquals("插队不应入队新 job", 1, queuedJobs.size)

        // 放行第一轮：终答分支发现 pending「逗逗你」→ 注入后继续 → 第 2 轮收到新内容
        gate.complete(Unit)
        withTimeout(5_000) {
            while (completedJobIds.isEmpty()) delay(10)
        }

        assertEquals("合并为一条回复", 1, savedMessages.size)
        assertEquals("带新内容的合并回复", savedMessages.first().second)
    }

    private class TestToolLoopLlmClient : ToolLoopLlmClient {
        var replyText: String = "好的老师"
        var responses: MutableList<LlmResponse> = mutableListOf()
        var shouldThrow: Throwable? = null

        override suspend fun chat(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?
        ): LlmResponse {
            shouldThrow?.let { throw it }
            if (responses.isNotEmpty()) {
                return responses.removeAt(0)
            }
            return LlmResponse(
                content = replyText,
                toolCalls = emptyList(),
                finishReason = "stop",
                elapsedMs = 50L
            )
        }
    }
}
