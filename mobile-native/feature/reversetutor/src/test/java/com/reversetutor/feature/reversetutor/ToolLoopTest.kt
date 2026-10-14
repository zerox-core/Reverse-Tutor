package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.LlmResponse
import com.reversetutor.feature.reversetutor.loop.MockToolLoopLlmClient
import com.reversetutor.feature.reversetutor.loop.ToolCallRequest
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.memory.InMemoryRTMemoryStore
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.SessionStateSink
import com.reversetutor.feature.reversetutor.tools.ToolContext
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class ToolLoopTest {

    private lateinit var registry: ToolRegistry
    private lateinit var catalog: CurriculumCatalog
    private lateinit var store: MaterialStore
    private lateinit var toolLoop: ToolLoop

    @Before
    fun setUp() {
        registry = ToolRegistry()
        CoreTools.registerAll(registry)
        val userDir = System.getProperty("user.dir")
        val assets = "$userDir/src/main/assets"
        catalog = CurriculumCatalog(File("$assets/curriculum.json").readText())
        store = MaterialStore()
        toolLoop = ToolLoop(
            registry = registry,
            catalog = catalog,
            store = store,
            maxStepsPerTurn = 12,
            recentRounds = 3
        )
    }

    // ------------------------------------------------------------------
    // 1. repairToolArguments 测试
    // ------------------------------------------------------------------

    @Test
    fun repairToolArgumentsBareString() {
        val raw = """{"expected_outcomes": 教师给出解释}"""
        val repaired = ToolLoop.repairToolArguments(raw)
        assertEquals("教师给出解释", repaired.optString("expected_outcomes"))
    }

    @Test
    fun repairToolArgumentsMixedValues() {
        val raw = """{"reason": 符号写反了, "level": 2, "ok": true, "extra": null}"""
        val repaired = ToolLoop.repairToolArguments(raw)
        assertEquals("符号写反了", repaired.optString("reason"))
        assertEquals(2, repaired.optInt("level"))
        assertTrue(repaired.optBoolean("ok"))
        assertTrue(repaired.isNull("extra"))
    }

    @Test
    fun repairToolArgumentsValidJsonUntouched() {
        val raw = """{"concept": "二次函数", "difficulty": 3}"""
        val parsed = ToolLoop.repairToolArguments(raw)
        assertEquals("二次函数", parsed.optString("concept"))
        assertEquals(3, parsed.optInt("difficulty"))
    }

    @Test
    fun repairToolArgumentsEmptyAndInvalid() {
        val empty = ToolLoop.repairToolArguments("")
        assertEquals(0, empty.length())

        val unrepairable = ToolLoop.repairToolArguments("not a json at all {{{{")
        assertTrue(unrepairable.has("_raw_arguments"))
    }

    // ------------------------------------------------------------------
    // 2. historyForLlm 上下文压缩测试
    // ------------------------------------------------------------------

    @Test
    fun historyForLlmCompressesOldRounds() {
        val session = RTSession(sessionId = "comp-1", currentRound = 5)
        val messages = listOf(
            // Round 1 (旧轮次：current - round = 4 >= recent 3)
            LlmMessage(role = "user", content = "老师第一问", round = 1),
            LlmMessage(
                role = "assistant",
                toolCalls = listOf(ToolCallRequest("c1", "search_materials", "{}")),
                round = 1
            ),
            LlmMessage(role = "tool", toolCallId = "c1", name = "search_materials", content = "{}", round = 1),
            LlmMessage(role = "assistant", content = "学生第一答", round = 1),

            // Round 4 (最近轮次：current - round = 1 < recent 3)
            LlmMessage(role = "user", content = "老师第四问", round = 4),
            LlmMessage(
                role = "assistant",
                toolCalls = listOf(ToolCallRequest("c4", "check_understanding", "{}")),
                round = 4
            ),
            LlmMessage(role = "tool", toolCallId = "c4", name = "check_understanding", content = "{}", round = 4),
            LlmMessage(role = "assistant", content = "学生第四答", round = 4)
        )

        val llmHistory = toolLoop.historyForLlm(session, messages)

        // 第一条恒为 system 提示词
        assertEquals("system", llmHistory.first().role)

        // 验证 Round 1 的 tool 和带 tool_calls 的 assistant 被丢弃
        val r1Tools = llmHistory.filter { it.role == "tool" && it.toolCallId == "c1" }
        assertTrue("旧轮次的 tool 消息应被压缩丢弃", r1Tools.isEmpty())
        val r1AssistantShells = llmHistory.filter { it.role == "assistant" && !it.toolCalls.isNullOrEmpty() && it.toolCalls?.first()?.callId == "c1" }
        assertTrue("旧轮次的 tool_calls assistant 骨架应被压缩丢弃", r1AssistantShells.isEmpty())

        // 验证 Round 1 的 user 与纯 assistant 依然保留
        assertTrue(llmHistory.any { it.role == "user" && it.content == "老师第一问" })
        assertTrue(llmHistory.any { it.role == "assistant" && it.content == "学生第一答" })

        // 验证 Round 4 的全量消息均完整保留（未压缩）
        assertTrue(llmHistory.any { it.role == "tool" && it.toolCallId == "c4" })
        assertTrue(llmHistory.any { it.role == "assistant" && !it.toolCalls.isNullOrEmpty() && it.toolCalls?.first()?.callId == "c4" })
        assertTrue(llmHistory.any { it.role == "assistant" && it.content == "学生第四答" })
    }

    // ------------------------------------------------------------------
    // 3. runTurn 自主工具循环端到端测试（Mock LLM）
    // ------------------------------------------------------------------

    @Test
    fun runTurnAutonomousDecisionLoop() = runBlocking {
        val initialSession = RTSession(
            sessionId = "turn-test",
            topic = "二次函数顶点公式",
            understandingLevel = 1,
            currentRound = 0
        )

        val client = MockToolLoopLlmClient()
        val result = toolLoop.runTurn(
            session = initialSession,
            historyMessages = emptyList(),
            userText = "配方法就是把 x²+6x 凑成 (x+3)²，因为多了 9 所以减回去，得到顶点",
            isOpening = false,
            llmClient = client
        )

        val report = result.report
        val updatedSession = result.updatedSession
        val updatedMessages = result.updatedMessages

        // 验证轮次递增
        assertEquals(1, report.round)
        assertEquals(1, updatedSession.currentRound)

        // 验证 Mock 客户端驱动的工具调用执行
        assertEquals(3, report.toolCallCount)
        assertEquals(3, report.llmCalls.size)
        assertTrue(report.toolCalls.any { it.tool == "check_understanding" })
        assertTrue(report.toolCalls.any { it.tool == "speak_as_confused_student" })
        assertTrue(report.toolCalls.any { it.tool == "challenge_teacher_explanation" })

        // 验证最终回复产生
        assertNotNull(report.finalReply)
        assertTrue(report.finalReply!!.contains("老师，我好像有点明白了"))

        // 验证消息轨迹包含完整链路 (user -> assistant tc -> tool -> assistant tc -> tool -> assistant final)
        val roles = updatedMessages.map { it.role }
        assertEquals(listOf("user", "assistant", "tool", "tool", "assistant", "tool", "assistant"), roles)

        // 验证状态自适应更新
        assertTrue("多轮工具执行后应记录 toolTrace", updatedSession.toolTrace.isNotEmpty())
    }

    /**
     * 阶段 3 快照接线（2026-10-14）：注入 memoryStore 后每轮写会话快照，
     * `generate_progress_report` 才能跨会话聚合（对齐 spike session_service
     * 每轮 `save_snapshot` 的行为）。
     */
    @Test
    fun runTurnWritesSessionSnapshotForProgressReport() = runBlocking {
        val memoryStore = InMemoryRTMemoryStore()
        val loop = ToolLoop(
            registry = registry,
            catalog = catalog,
            store = store,
            memoryStore = memoryStore
        )
        val session = RTSession(
            sessionId = "snap-test",
            topic = "二次函数顶点公式",
            studentId = "stu-snap",
            understandingLevel = 2,
            initialUnderstandingLevel = 1
        )
        loop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "配方法就是把 x²+6x 凑成 (x+3)²，因为多了 9 所以减回去，得到顶点",
            isOpening = false,
            llmClient = MockToolLoopLlmClient()
        )

        val snapshots = memoryStore.sessionsForReport("stu-snap", null)
        assertEquals("每轮应写 1 条会话快照", 1, snapshots.size)
        assertEquals("snap-test", snapshots[0].getString("session_id"))

        // 报告应能读到该快照，而不是回落当前会话
        val context = ToolContext(
            session = session,
            extra = mapOf(
                CoreTools.KEY_MEMORY_STORE to memoryStore,
                CoreTools.KEY_SESSION_STATE_SINK to SessionStateSink()
            )
        )
        val (result, _) = registry.execute(
            "generate_progress_report",
            JSONObject().put("student_id", "stu-snap"),
            context
        )
        assertTrue(result.success)
        val summary = (result.data as JSONObject).getString("summary")
        assertTrue("报告应聚合快照而非回落：$summary", summary.contains("共 1 次会话"))
    }

    /**
     * 跨会话聚合的判别测试：回落路径最多只能报 1 次会话，
     * 因此「共 2 次会话」只可能来自快照聚合。
     */
    @Test
    fun progressReportAggregatesMultipleSessionSnapshots() = runBlocking {
        val memoryStore = InMemoryRTMemoryStore()
        val loop = ToolLoop(
            registry = registry,
            catalog = catalog,
            store = store,
            memoryStore = memoryStore
        )
        val userText = "配方法就是把 x²+6x 凑成 (x+3)²，因为多了 9 所以减回去，得到顶点"
        for (sid in listOf("snap-a", "snap-b")) {
            loop.runTurn(
                session = RTSession(
                    sessionId = sid, topic = "二次函数顶点公式", studentId = "stu-multi",
                    understandingLevel = 1, initialUnderstandingLevel = 1
                ),
                historyMessages = emptyList(),
                userText = userText,
                isOpening = false,
                llmClient = MockToolLoopLlmClient()
            )
        }
        assertEquals(2, memoryStore.sessionsForReport("stu-multi", null).size)

        val (result, _) = registry.execute(
            "generate_progress_report",
            JSONObject().put("student_id", "stu-multi"),
            ToolContext(
                session = RTSession(sessionId = "snap-b", studentId = "stu-multi"),
                extra = mapOf(
                    CoreTools.KEY_MEMORY_STORE to memoryStore,
                    CoreTools.KEY_SESSION_STATE_SINK to SessionStateSink()
                )
            )
        )
        val data = result.data as JSONObject
        val summary = data.getString("summary")
        assertTrue("两条快照应聚合为 2 次会话，实际：$summary", summary.contains("共 2 次会话"))
        assertEquals(2, data.getJSONArray("charts").getJSONObject(0)
            .getJSONObject("data").getJSONArray("values").length())
    }

    /** 未注入 memoryStore 时不得写快照，也不得抛异常（向后兼容）。 */
    @Test
    fun runTurnWithoutMemoryStoreDoesNotFail() = runBlocking {
        val session = RTSession(sessionId = "no-store", topic = "二次函数顶点公式")
        val result = toolLoop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "配方法就是把 x²+6x 凑成 (x+3)²，因为多了 9 所以减回去，得到顶点",
            isOpening = false,
            llmClient = MockToolLoopLlmClient()
        )
        assertNotNull(result.report.finalReply)
        assertEquals(1, result.updatedSession.currentRound)
    }

    // ------------------------------------------------------------------
    // 4. 守卫与滑坡拦截测试
    // ------------------------------------------------------------------
    @Test
    fun runTurnMathGuardViolationCaptured() = runBlocking {
        val session = RTSession(sessionId = "math-guard-test")
        val client = MockToolLoopLlmClient()
        // 模拟 LLM 直接输出带有数学错误的内容
        client.queueResponse(
            LlmResponse(
                content = "我知道了，顶点横坐标是 +b/2a！",
                model = "test-model"
            )
        )

        val result = toolLoop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "讲讲顶点坐标",
            llmClient = client
        )

        assertTrue("MathGuard 应检测到 +b/2a 违规", result.report.mathViolations.isNotEmpty())
    }

    @Test
    fun runTurnIdentitySlipCaptured() = runBlocking {
        val session = RTSession(sessionId = "identity-test")
        val client = MockToolLoopLlmClient()
        // 模拟 LLM 误用教师口吻
        client.queueResponse(
            LlmResponse(
                content = "我来教你，二次函数配方法应该这样做。",
                model = "test-model"
            )
        )

        val result = toolLoop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "教教我",
            llmClient = client
        )

        assertTrue(result.report.degradations.any { it.type == "identity_slip" })
    }

    // ------------------------------------------------------------------
    // 5. 步数上限与异常故障降级测试
    // ------------------------------------------------------------------

    @Test
    fun runTurnStepLimitFallback() = runBlocking {
        val session = RTSession(sessionId = "step-limit-test")
        val client = MockToolLoopLlmClient()
        // 持续返回工具调用，耗尽 12 步上限
        for (i in 0 until 15) {
            client.queueResponse(
                LlmResponse(
                    toolCalls = listOf(
                        ToolCallRequest("c$i", "search_materials", """{"query": "配方"}""")
                    ),
                    model = "test-model"
                )
            )
        }

        val result = toolLoop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "一直查资料",
            llmClient = client
        )

        assertEquals("超出步数限制应触发兜底学生台词", "老师，我有点跟不上，能再说一遍吗？", result.report.finalReply)
        assertEquals(12, result.report.llmCalls.size)
    }

    @Test
    fun runTurnLlmFailureDegradation() = runBlocking {
        val session = RTSession(sessionId = "fail-test", currentRound = 1)
        val throwingClient = object : com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient {
            override suspend fun chat(
                messages: List<LlmMessage>,
                tools: List<JSONObject>,
                model: String?
            ): LlmResponse {
                throw IOException("Simulated network drop 502 Bad Gateway")
            }
        }

        val result = toolLoop.runTurn(
            session = session,
            historyMessages = emptyList(),
            userText = "网络中断时发送",
            llmClient = throwingClient
        )

        assertTrue(result.report.errors.isNotEmpty())
        assertEquals("多轮中网络故障应触发温柔学生卡顿降级", "老师……我这边好像有点卡，等一下再问我一遍好不好？", result.report.finalReply)
        assertTrue(result.report.degradations.any { it.type == "llm_failure" })
    }

    // ------------------------------------------------------------------
    // 7. 智能插队（2026-10-11 拍板）：连发新消息注入进行中的回合
    // ------------------------------------------------------------------

    /** 记录每轮收到的上下文，按脚本顺序返回响应。 */
    private class RecordingLlmClient(
        private val responses: List<LlmResponse>
    ) : ToolLoopLlmClient {
        val received = mutableListOf<List<LlmMessage>>()
        private var index = 0

        override suspend fun chat(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?
        ): LlmResponse {
            received.add(messages)
            val response = responses[index.coerceAtMost(responses.size - 1)]
            index++
            return response
        }
    }

    @Test
    fun runTurnInjectsLateUserTextAndDefersCompletion() = runBlocking {
        val client = RecordingLlmClient(
            listOf(
                LlmResponse(content = "第一版过渡回答", model = "mock"),
                LlmResponse(content = "带新内容的最终回答", model = "mock")
            )
        )
        // 调用序：第1轮开头(空) → 终答分支(返回哈哈哈) → 第2轮开头(空) → 终答分支(空)收束
        var providerCalls = 0
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "inject-test", topic = "代数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "3x+1=2x+5 怎么解",
            llmClient = client,
            pendingUserTexts = {
                providerCalls++
                if (providerCalls == 2) listOf("哈哈哈") else emptyList()
            }
        )

        // 终答被推迟到第 2 轮，注入的新消息出现在第 2 轮上下文里
        assertEquals(2, client.received.size)
        val round2 = client.received[1]
        assertTrue(
            "第一版过渡回答应保留为 assistant 上下文",
            round2.any { it.role == "assistant" && it.content == "第一版过渡回答" }
        )
        assertTrue(
            "插队的新消息应以 user 身份注入",
            round2.any { it.role == "user" && it.content == "哈哈哈" }
        )
        assertEquals("带新内容的最终回答", result.report.finalReply)
    }

    @Test
    fun runTurnWithoutPendingTextsCompletesNormally() = runBlocking {
        val client = RecordingLlmClient(
            listOf(LlmResponse(content = "一次到位的回答", model = "mock"))
        )
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "no-inject", topic = "代数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "3x+1=2x+5 怎么解",
            llmClient = client,
            pendingUserTexts = { emptyList() }
        )

        assertEquals(1, client.received.size)
        assertEquals("一次到位的回答", result.report.finalReply)
    }

    @Test
    fun runTurnExtractsThinkingEnvelope() = runBlocking {
        val rawLlmOutput = "<thinking>\n我卡在配方法常数项上了\n</thinking>\n老师，为什么这里是加 9？"
        val client = RecordingLlmClient(
            listOf(LlmResponse(content = rawLlmOutput, model = "mock"))
        )
        val emittedEvents = mutableListOf<JSONObject>()
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "envelope-test", topic = "二次函数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "两边加上 9",
            llmClient = client,
            onEvent = { emittedEvents.add(it) }
        )

        // 验证独白提取与正文剥离
        assertEquals("我卡在配方法常数项上了", result.report.finalMonologue)
        assertEquals("老师，为什么这里是加 9？", result.report.finalReply)
        assertEquals("老师，为什么这里是加 9？", result.updatedMessages.last().content)

        // 验证通过事件通知流式消费端
        val roundEvent = emittedEvents.firstOrNull { it.optString("type") == "llm_round" }
        assertNotNull(roundEvent)
        assertEquals("我卡在配方法常数项上了", roundEvent!!.optString("content"))
    }

    @Test
    fun runTurnMonologueIsolatedFromMathGuardAndIdentityCheck() = runBlocking {
        // SPEC §10 decision 9: 独白不进红线检查；即使独白出现违规内容，仅正文受检
        val rawLlmOutput = "<thinking>\n我想到了顶点横坐标是 +b/2a，但我不能说我来教你\n</thinking>\n老师，顶点坐标怎么推导呀？"
        val client = RecordingLlmClient(
            listOf(LlmResponse(content = rawLlmOutput, model = "mock"))
        )
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "isolate-test", topic = "二次函数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "推导一下顶点",
            llmClient = client
        )

        assertTrue(result.report.finalMonologue?.contains("+b/2a") == true)
        assertEquals("老师，顶点坐标怎么推导呀？", result.report.finalReply)
        assertTrue("独白中的 +b/2a 不应被 MathGuard 拦截到正文违规中", result.report.mathViolations.isEmpty())
        assertTrue("独白中的教师口吻不应触发 identity_slip 降级", result.report.degradations.none { it.type == "identity_slip" })
    }

    @Test
    fun runTurnPrefersChineseEnvelopeOverEnglishReasoningContent() {
        runBlocking {
            // 2026-10-12 真机反馈「思考链全是英文」：推理模型原生 reasoning_content 是
            // 英文流时，必须让中文 <thinking> 信封胜出，而不是英文推理。
            val client = RecordingLlmClient(
                listOf(
                    LlmResponse(
                        content = "<thinking>\n我卡在常数项为什么变成 9 上了\n</thinking>\n老师，为什么两边都要加 9 呀？",
                        rawMessage = JSONObject().put("reasoning_content", "Let me analyze the completing-the-square step carefully."),
                        model = "mock"
                    )
                )
            )
            val result = toolLoop.runTurn(
                session = RTSession(sessionId = "cjk-priority-test", topic = "二次函数", currentRound = 0),
                historyMessages = emptyList(),
                userText = "配方法这一步",
                llmClient = client
            )

            assertEquals("我卡在常数项为什么变成 9 上了", result.report.finalMonologue)
            assertEquals("老师，为什么两边都要加 9 呀？", result.report.finalReply)
            assertFalse("英文 reasoning_content 不应漏进独白", result.report.finalMonologue?.contains("Let me") == true)
        }
    }

    @Test
    fun runTurnSkipsEnglishOnlyReasoningContent() {
        runBlocking {
            // 无信封且 reasoning_content 全英文：独白应为空（宁可不展示，也不上屏英文推理），
            // 中文正文原样保留。
            val client = RecordingLlmClient(
                listOf(
                    LlmResponse(
                        content = "老师，顶点公式是不是 x = -b/(2a) 呀？",
                        rawMessage = JSONObject().put("reasoning_content", "The student should recall the vertex formula."),
                        model = "mock"
                    )
                )
            )
            val result = toolLoop.runTurn(
                session = RTSession(sessionId = "cjk-guard-test", topic = "二次函数", currentRound = 0),
                historyMessages = emptyList(),
                userText = "顶点公式是什么",
                llmClient = client
            )

            assertEquals(null, result.report.finalMonologue)
            assertEquals("老师，顶点公式是不是 x = -b/(2a) 呀？", result.report.finalReply)
        }
    }

    @Test
    fun isMostlyChineseRatioGate() {
        assertTrue(ToolLoop.isMostlyChinese("我在思考配方法"))
        assertTrue(ToolLoop.isMostlyChinese("mixed english 加中文，比例够就行"))
        assertFalse(ToolLoop.isMostlyChinese("pure English reasoning"))
        // 真机 2026-10-13 翻车样本：英文思考流嵌一句中文（实测占比 2.9% / 6.6%），必须拦下
        assertFalse(ToolLoop.isMostlyChinese("Let me think about whether to use a tool. I could log a breakthrough. 那是不是所有题都能这么搬? That's a good student question."))
        // 中文为主夹英文术语（DeepSeek 式中英夹杂）应放行
        assertTrue(ToolLoop.isMostlyChinese("这个 reasoning 通道的 token 数不对，得检查一下"))
        assertFalse(ToolLoop.isMostlyChinese(""))
        assertFalse(ToolLoop.isMostlyChinese("123456 !!! ???"))
    }

    @Test
    fun runTurnSkipsEnglishThinkingEnvelope() {
        runBlocking {
            // 真机 2026-10-13 复刻：模型不守提示词语言，信封本身是大段英文（夹一句中文问句）。
            // 比例闸必须拦下信封，也拦下纯英文 reasoning_content，独白宁缺毋滥，正文完好。
            val client = RecordingLlmClient(
                listOf(
                    LlmResponse(
                        content = "<thinking>feel a mix of pride and curiosity. Let me log a breakthrough. 那是不是所有题都能这么搬? That's a good probing question.</thinking>\n老师，那是不是所有题都能这么搬呀？",
                        rawMessage = JSONObject().put("reasoning_content", "The student has mastered basic solving. Let me log this mastery."),
                        model = "mock"
                    )
                )
            )
            val emittedEvents = mutableListOf<JSONObject>()
            val result = toolLoop.runTurn(
                session = RTSession(sessionId = "envelope-lang-guard", topic = "一元一次方程", currentRound = 0),
                historyMessages = emptyList(),
                userText = "对的啊",
                llmClient = client,
                onEvent = { emittedEvents.add(it) }
            )

            assertNull("英文信封不应漏进独白", result.report.finalMonologue)
            assertEquals("老师，那是不是所有题都能这么搬呀？", result.report.finalReply)
            assertTrue(
                "不应有任何 llm_round 独白事件",
                emittedEvents.none { it.optString("type") == "llm_round" && it.optString("content").isNotBlank() }
            )
        }
    }

    @Test
    fun runTurnIntermediateRoundSkipsEnglishContentFallback() {
        runBlocking {
            // 中间轮（带工具调用）无信封、reasoning 全英文：退回过渡语前必须过比例闸，
            // 英文过渡语不能作为内心话上屏（2026-10-13 真机第 2/3 轮翻车场景）。
            val client = RecordingLlmClient(
                listOf(
                    LlmResponse(
                        content = "I want to keep this light. Let me call the tool now.",
                        toolCalls = listOf(ToolCallRequest("c-lang", "search_materials", "{}")),
                        rawMessage = JSONObject().put("reasoning_content", "Just call the tool to search materials."),
                        model = "mock"
                    ),
                    LlmResponse(content = "<thinking>老师讲了材料我就懂了。</thinking>\n老师，看完材料我明白多啦！", model = "mock")
                )
            )
            val emittedEvents = mutableListOf<JSONObject>()
            val result = toolLoop.runTurn(
                session = RTSession(sessionId = "intermediate-lang-guard", topic = "代数", currentRound = 0),
                historyMessages = emptyList(),
                userText = "老师讲讲这个",
                llmClient = client,
                onEvent = { emittedEvents.add(it) }
            )

            assertEquals("老师，看完材料我明白多啦！", result.report.finalReply)
            val monologueEvents = emittedEvents.filter { it.optString("type") == "llm_round" }
            assertTrue(
                "英文中间轮内容不应上屏（第2轮中文独白正常放行）",
                monologueEvents.none { it.optString("content").contains("I want to keep this light") }
            )
            assertTrue(
                "终答轮中文信封应作为独白透出",
                monologueEvents.any { it.optString("content").contains("老师讲了材料我就懂了") }
            )
            assertEquals("中文信封应写入 finalMonologue", "老师讲了材料我就懂了。", result.report.finalMonologue)
        }
    }

    @Test
    fun runTurnStreamsThinkingDeltasWithoutLeakingReplyBody() = runBlocking {
        // 真流式（2026-10-13 用户拍板）：chatStreaming 的原始增量按到达顺序转成
        // llm_thinking 事件；content 增量经 MonologueStreamSplitter 差分后只透出
        // 信封内独白，正文增量（终答气泡内容）绝不上思考抽屉。
        val client = StreamingLlmClient(
            deltas = listOf(
                "先想想" to "",
                "" to "<thinking>",
                "" to "我卡在常数项上了",
                "" to "</thinking>\n",
                "" to "老师，为什么这里是加 9？"
            ),
            response = LlmResponse(
                content = "<thinking>我卡在常数项上了</thinking>\n老师，为什么这里是加 9？",
                rawMessage = JSONObject().put("reasoning_content", "先想想"),
                model = "mock"
            )
        )
        val emittedEvents = mutableListOf<JSONObject>()
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "stream-test", topic = "二次函数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "两边加上 9",
            llmClient = client,
            onEvent = { emittedEvents.add(it) }
        )

        assertEquals("老师，为什么这里是加 9？", result.report.finalReply)
        assertEquals("我卡在常数项上了", result.report.finalMonologue)

        val thinkingEvents = emittedEvents.filter { it.optString("type") == "llm_thinking" }
        val reasoningDeltas = thinkingEvents.map { it.optString("reasoning") }.filter { it.isNotEmpty() }
        val contentDeltas = thinkingEvents.map { it.optString("content") }.filter { it.isNotEmpty() }

        assertEquals("reasoning 原始增量按序透传", listOf("先想想"), reasoningDeltas)
        assertEquals("content 只透出信封内独白增量", listOf("我卡在常数项上了"), contentDeltas)
        assertTrue(
            "终答正文不得漏进 llm_thinking",
            thinkingEvents.none { it.optString("content").contains("老师，为什么") }
        )
        // 定稿轮 llm_round 照旧（流式只增不改既有事件协议）
        val roundEvent = emittedEvents.firstOrNull { it.optString("type") == "llm_round" }
        assertNotNull(roundEvent)
        assertEquals("我卡在常数项上了", roundEvent!!.optString("content"))
    }

    @Test
    fun runTurnStreamsIntermediateRoundReasoningBeforeToolCall() = runBlocking {
        // 中间轮（带工具调用）思考流也应实时透出，不等轮末 llm_round。
        val client = StreamingLlmClient(
            deltas = listOf(
                "老师讲了材料我才好接话" to "",
                "" to "<thinking>我看完材料就明白了</thinking>"
            ),
            response = LlmResponse(
                content = "<thinking>我看完材料就明白了</thinking>",
                toolCalls = listOf(ToolCallRequest("c-stream", "search_materials", "{}")),
                rawMessage = JSONObject().put("reasoning_content", "老师讲了材料我才好接话"),
                model = "mock"
            )
        )
        val followUp = RecordingLlmClient(
            listOf(LlmResponse(content = "老师，看完材料我明白多啦！", model = "mock"))
        )
        val emittedEvents = mutableListOf<JSONObject>()
        var call = 0
        val result = toolLoop.runTurn(
            session = RTSession(sessionId = "stream-mid-test", topic = "代数", currentRound = 0),
            historyMessages = emptyList(),
            userText = "老师讲讲这个",
            llmClient = object : ToolLoopLlmClient {
                override suspend fun chat(
                    messages: List<LlmMessage>,
                    tools: List<JSONObject>,
                    model: String?
                ): LlmResponse {
                    call++
                    return if (call == 1) {
                        client.chat(messages, tools, model)
                    } else {
                        followUp.chat(messages, tools, model)
                    }
                }

                override suspend fun chatStreaming(
                    messages: List<LlmMessage>,
                    tools: List<JSONObject>,
                    model: String?,
                    onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit
                ): LlmResponse {
                    call++
                    return if (call == 1) {
                        client.chatStreaming(messages, tools, model, onThinkingDelta)
                    } else {
                        followUp.chatStreaming(messages, tools, model, onThinkingDelta)
                    }
                }
            },
            onEvent = { emittedEvents.add(it) }
        )

        assertEquals("老师，看完材料我明白多啦！", result.report.finalReply)
        val thinkingEvents = emittedEvents.filter { it.optString("type") == "llm_thinking" }
        assertTrue(
            "中间轮 reasoning 增量应先于工具调用透出",
            thinkingEvents.any { it.optString("reasoning") == "老师讲了材料我才好接话" }
        )
        assertTrue(
            "中间轮信封独白增量应透出",
            thinkingEvents.any { it.optString("content") == "我看完材料就明白了" }
        )
    }

    /** 真流式测试客户端：按脚本回放原始增量后返回定稿响应。 */
    private class StreamingLlmClient(
        private val deltas: List<Pair<String, String>>,
        private val response: LlmResponse
    ) : ToolLoopLlmClient {

        override suspend fun chat(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?
        ): LlmResponse = response

        override suspend fun chatStreaming(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?,
            onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit
        ): LlmResponse {
            deltas.forEach { (reasoningDelta, contentDelta) ->
                onThinkingDelta(reasoningDelta, contentDelta)
            }
            return response
        }
    }
}
