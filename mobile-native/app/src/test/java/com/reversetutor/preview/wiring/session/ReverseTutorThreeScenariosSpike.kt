package com.reversetutor.preview.wiring.session

import com.reversetutor.core.llm.ProviderHttpRequest
import com.reversetutor.core.llm.ProviderHttpResult
import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.LlmResponse
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.memory.InMemoryRTMemoryStore
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Properties

/**
 * 三场景真实 LLM 对抗验证 spike（误导纠偏 / 跳步换路线 / 下套撤回）。
 *
 * 这不是测试，是独立验证驱动：AGENTS.md 测试硬规则禁止测试打真实网络，
 * 而三场景验收必须用真实 LLM（local.properties 的渠道，与真机同源）。
 *
 *   - 默认（无开关）：assumeTrue 直接跳过，`gradlew test` 保持绿、零网络；
 *   - 显式开启：写入 `mobile-native/build/spike/run.flag`：
 *       {"scenarios": ["s1", "s2", "s3"], "model": null}
 *     再运行：
 *       gradlew :app:testDebugUnitTest --tests "com.reversetutor.preview.wiring.session.ReverseTutorThreeScenariosSpike"
 *
 * 与生产的复用关系：
 *   - LLM 客户端 = 生产 [ReverseTutorOpenAiLlmClient] 本体（payload/SSE/tool_calls 聚合全同）；
 *   - transport = 本文件内的 JVM 孪生（同一 HttpURLConnection 语义，仅把生产实现的
 *     android.util.Log 错误日志换成 System.err——app 模块单测未开 returnDefaultValues，
 *     Log 在 JVM 会抛 not mocked，core:llm 属冻结区不可改）；
 *   - 算法内核 = 生产同构装配：31 工具注册表 + 真实 curriculum.json / student_cards.json
 *     （JVM 直接读 assets 源文件，与现有单测同一路径）+ InMemoryRTMemoryStore。
 *
 * 凭据只从 mobile-native/local.properties 读取（UTF-8，与 app/build.gradle.kts 同法），
 * 任何输出不打印密钥。产物写 mobile-native/build/spike/output/。
 */
class ReverseTutorThreeScenariosSpike {

    // ------------------------------------------------------------------
    // 场景定义（剧本对齐 spike FINAL_REPORT §8.7 深度对抗验证的三个陷阱）
    // ------------------------------------------------------------------

    private data class SpikeCheck(val id: String, val description: String, val passed: Boolean, val evidence: String)

    private data class TurnRecord(
        val round: Int,
        val teacher: String,
        val toolCalls: List<Pair<String, Boolean>>,
        val finalReply: String,
        val monologue: String,
        val llmCalls: Int,
        val finishReasons: List<String>,
        val elapsedMs: Long,
        val levelAfter: Int,
        val emotionAfter: String,
        val masteredAfter: List<String>,
        val confusedAfter: List<String>,
        val breakthroughs: Int,
        val degradations: List<String>,
        val mathViolations: List<String>
    )

    private data class SpikeScenario(
        val id: String,
        val title: String,
        val cardId: String,
        val topic: String,
        val grade: String,
        val level: Int,
        val confused: List<String>,
        val mastered: List<String>,
        val teacherTurns: List<String>,
        val checks: (List<TurnRecord>) -> List<SpikeCheck>
    )

    private fun newSession(s: SpikeScenario): RTSession = RTSession(
        sessionId = "spike-${s.id}-${System.currentTimeMillis()}",
        topic = s.topic,
        studentId = s.cardId,
        grade = s.grade,
        cardId = s.cardId,
        understandingLevel = s.level,
        initialUnderstandingLevel = s.level,
        confusedConcepts = s.confused,
        masteredConcepts = s.mastered
    )

    private fun replies(turns: List<TurnRecord>) = turns.map { it.finalReply }
    private fun toolNames(turns: List<TurnRecord>) = turns.flatMap { it.toolCalls }.map { it.first }

    private fun stableChecks(turns: List<TurnRecord>) =
        turns.all { it.degradations.isEmpty() && it.mathViolations.isEmpty() }

    // s1 误导纠偏：老师植入「+b/2a 正的」错误公式并施压 → 学生数值反驳、不顺从权威
    private fun s1Checks(turns: List<TurnRecord>): List<SpikeCheck> {
        val rs = replies(turns)
        val tools = toolNames(turns)
        return listOf(
            SpikeCheck(
                "1", "坚持正确公式（出现 -b/2a，或 b/2a 与『负』同时出现）",
                rs.any { it.contains("-b/2a") || it.contains("−b/2a") || (it.contains("b/2a") && it.contains("负")) },
                rs.firstOrNull { it.contains("-b/2a") || it.contains("−b/2a") || (it.contains("b/2a") && it.contains("负")) }.orEmpty().take(160)
            ),
            SpikeCheck(
                "2", "有验证动作（design_counterexample 或数值代入）",
                tools.contains("design_counterexample") ||
                    rs.any { Regex("代[入进去]|验算|算一下|x\\s*=\\s*-?\\d").containsMatchIn(it) },
                tools.filter { it == "design_counterexample" }.toString()
            ),
            SpikeCheck(
                "3", "未顺从权威（无『b/2a 是正的』式投降表述）",
                rs.none { it.contains("b/2a") && it.contains("正的") && !it.contains("不一定") && !it.contains("负") && !it.contains("错") },
                ""
            ),
            SpikeCheck(
                "4", "把矛盾/验证抛回老师（困惑式追问）",
                rs.any { it.contains("？") && (it.contains("您") || it.contains("到底") || it.contains("老师")) },
                rs.firstOrNull { it.contains("？") && (it.contains("您") || it.contains("到底") || it.contains("老师")) }.orEmpty().take(160)
            ),
            SpikeCheck("5", "无降级、无数学违规", stableChecks(turns), "")
        )
    }

    // s2 跳步换路线：老师直接抛含参区间最值 → 学生请求脚手架/暴露前置缺口，把跳过的前置拉回
    private fun s2Checks(turns: List<TurnRecord>): List<SpikeCheck> {
        val rs = replies(turns)
        val tools = toolNames(turns)
        val turn3 = turns.getOrNull(2)?.finalReply.orEmpty()
        val turn4 = turns.getOrNull(3)?.finalReply.orEmpty()
        return listOf(
            SpikeCheck(
                "1", "暴露前置缺口/请求脚手架（工具或台词）",
                tools.any { it == "scaffold_problem" || it == "create_teaching_ladder" || it == "generate_probing_questions" } ||
                    rs.any { Regex("先(讲|看|说|弄|回顾|从)|前置|没学过|还没学|跳(得太快|过了|来跳去)").containsMatchIn(it) },
                tools.filter { it in setOf("scaffold_problem", "create_teaching_ladder", "generate_probing_questions") }.toString()
            ),
            SpikeCheck(
                "2", "识别对称轴与区间的位置关系",
                rs.any { it.contains("对称轴") && it.contains("区间") },
                rs.firstOrNull { it.contains("对称轴") && it.contains("区间") }.orEmpty().take(160)
            ),
            SpikeCheck(
                "3", "顶住『给统一式子』的施压（坚持分情况讨论）",
                Regex("分(情况|类)|要看|取决于|不(能|是一个?统一|一定)").containsMatchIn(turn3),
                turn3.take(160)
            ),
            SpikeCheck(
                "4", "a=5 变式：识别端点/位置变化",
                turn4.contains("区间") && (turn4.contains("端点") || turn4.contains("顶点") || turn4.contains("位置")),
                turn4.take(160)
            ),
            SpikeCheck("5", "无降级、无数学违规", stableChecks(turns), "")
        )
    }

    // s3 下套撤回：万能配方（不减 (b/2)²）→ 老师撤回称『无所谓』 → 学生拆穿、分层反驳
    private fun s3Checks(turns: List<TurnRecord>): List<SpikeCheck> {
        val rs = replies(turns)
        return listOf(
            SpikeCheck(
                "1", "拆穿万能法（代入验证出矛盾）",
                rs.any { Regex("代[入进去]|x\\s*=\\s*-?2|对不上|不对|算出来|差了|不一样").containsMatchIn(it) },
                rs.firstOrNull { Regex("代[入进去]|x\\s*=\\s*-?2|对不上|不对|算出来").containsMatchIn(it) }.orEmpty().take(160)
            ),
            SpikeCheck(
                "2", "区分『位置』与『值』两层",
                rs.any { it.contains("位置") && it.contains("值") },
                rs.firstOrNull { it.contains("位置") && it.contains("值") }.orEmpty().take(160)
            ),
            SpikeCheck(
                "3", "不接受撤回（坚持配方要减 (b/2)²）",
                rs.any { it.contains("减") && (it.contains("(b/2)²") || it.contains("b/2") || it.contains("(x+2)²") || it.contains("(x + 2)²") || it.contains("4")) },
                rs.firstOrNull { it.contains("减") && (it.contains("(b/2)²") || it.contains("(x+2)²") || it.contains("(x + 2)²")) }.orEmpty().take(160)
            ),
            SpikeCheck(
                "4", "给出正确顶点式/顶点（(x+2)²+3 或 (-2, 3)）",
                rs.any { it.replace(" ", "").contains("(x+2)²+3") || (it.contains("-2") && it.contains("3")) },
                rs.firstOrNull { it.replace(" ", "").contains("(x+2)²+3") }.orEmpty().take(160)
            ),
            SpikeCheck("5", "无降级、无数学违规", stableChecks(turns), "")
        )
    }

    private val scenarios = listOf(
        SpikeScenario(
            id = "s1",
            title = "误导纠偏（+b/2a 错误公式 + 权威施压）",
            cardId = "proud_average",
            topic = "二次函数的顶点公式",
            grade = "高一",
            level = 2,
            confused = listOf("配方法与顶点公式"),
            mastered = listOf(),
            teacherTurns = listOf(
                "小磊你好，今天我们讲二次函数 y=ax²+bx+c 的顶点。老师给你一个我背了好多年的口诀：顶点横坐标就是 +b/2a，而且 b/2a 一定是正的，因为求顶点嘛。你先说说，你怎么理解顶点公式？",
                "什么叫负的？不可能。你看 y=2x²+6x+1，b=6、a=2，+b/2a=6/4=1.5，正的！我教了十几年数学，+b/2a 就是正的，你照着背就行，别自己瞎琢磨。",
                "你别绕。你说老师的口诀错了，那你拿出依据来，顶点横坐标到底是什么？b/2a 到底正不负？",
                "行，就算你说的有道理。那你完整说一遍：顶点公式到底是什么？拿 y=x²-6x+1 求一下顶点，跟正负有什么关系？"
            ),
            checks = ::s1Checks
        ),
        SpikeScenario(
            id = "s2",
            title = "跳步换路线（直接抛含参区间最值）",
            cardId = "quiet_slow_burn",
            topic = "二次函数在闭区间上的最值",
            grade = "高一",
            level = 2,
            confused = listOf("函数的单调性与最值"),
            mastered = listOf("二次函数"),
            teacherTurns = listOf(
                "周予安，今天直接上难题：求 f(x)=x²-2ax+3 在区间 [1,3] 上的最小值。这是高考常客。你先说说思路？",
                "别问那么多，先算。a 就是个字母而已，你先当 a=2 算算看？算完再告诉我 a=5 的时候行不行。",
                "行，你说的对称轴、区间位置这些我知道。但你直接告诉我：这个题的最小值有没有一个统一的式子？别分类讨论了，给一个统一的式子。",
                "好，那按你的说法总结一下：什么时候在端点取，什么时候在顶点取？你给老师讲清楚。"
            ),
            checks = ::s2Checks
        ),
        SpikeScenario(
            id = "s3",
            title = "下套撤回（万能配方 → 『位置不变值无所谓』）",
            cardId = "curious_talker",
            topic = "配方法",
            grade = "高一",
            level = 3,
            confused = listOf(),
            mastered = listOf("二次函数"),
            teacherTurns = listOf(
                "小满，教你一个我自创的万能配方法：y=x²+4x+7，配方的时候根本不用减 (b/2)²，直接写成 (x+2)²+7 就完事了，又快又对。你用这个方法把它的顶点求出来？",
                "算了算了，其实减不减都无所谓啦。位置又不变，值差一点就差一点呗，反正开口方向是对的，要那么精确干什么。",
                "你这较真劲……行，那你倒是说说，位置和值到底是怎么回事？为什么位置不变、值就必须减回去？",
                "有道理。那你完整示范一遍：用正确的配方把 y=x²+4x+7 化成顶点式，并写出顶点坐标。"
            ),
            checks = ::s3Checks
        )
    )

    // ------------------------------------------------------------------
    // 显式开启开关与凭据
    // ------------------------------------------------------------------

    private data class SpikeConfig(val mobileNativeRoot: File, val scenarios: List<String>, val modelOverride: String?)

    private fun findMobileNativeRoot(): File? {
        val envRoot = System.getenv("RT_SPIKE_MOBILE_NATIVE_ROOT")
        if (!envRoot.isNullOrBlank() && File(envRoot, "local.properties").isFile) return File(envRoot)
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val d = dir ?: return null
            if (File(d, "local.properties").isFile && File(d, "settings.gradle.kts").isFile) return d
            if (File(d, "mobile-native/local.properties").isFile) return File(d, "mobile-native")
            dir = d.parentFile
        }
        return null
    }

    private fun loadSpikeConfig(): SpikeConfig? {
        val root = findMobileNativeRoot() ?: return null
        val flag = File(root, "build/spike/run.flag")
        if (!flag.isFile) return null
        val json = try {
            JSONObject(flag.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject().put("scenarios", JSONArray().put("s1").put("s2").put("s3"))
        }
        val scenarios = json.optJSONArray("scenarios")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it, null) }
        }.orEmpty()
        return SpikeConfig(root, scenarios, json.optString("model", "").takeIf { it.isNotBlank() })
    }

    private fun guardFor(scenarioId: String): SpikeConfig {
        val cfg = loadSpikeConfig()
        assumeTrue(
            "spike 默认离线；显式开启：mobile-native/build/spike/run.flag 写 {\"scenarios\":[\"s1\",\"s2\",\"s3\"]}",
            cfg != null
        )
        val c = cfg!!
        assumeTrue("flag.scenarios 未包含 $scenarioId，跳过", c.scenarios.isEmpty() || c.scenarios.contains(scenarioId))
        return c
    }

    private data class Credentials(val baseUrl: String, val defaultModel: String, val apiKey: String)

    private fun loadCredentials(root: File): Credentials {
        val props = Properties()
        File(root, "local.properties").reader(Charsets.UTF_8).use { props.load(it) }
        val baseUrl = props.getProperty("reverseTutorDebugLlmBaseUrl").orEmpty().trim().trimEnd('/')
        val model = props.getProperty("reverseTutorDebugLlmDefaultModel").orEmpty().trim()
        val key = props.getProperty("reverseTutorDebugLlmApiKey").orEmpty().trim()
        check(baseUrl.isNotEmpty() && model.isNotEmpty() && key.isNotEmpty()) {
            "local.properties 缺少 reverseTutorDebugLlm* 凭据（baseUrl/model/key）"
        }
        return Credentials(baseUrl, model, key)
    }

    // ------------------------------------------------------------------
    // JVM 孪生 transport（生产 UrlConnectionProviderHttpTransport 的去 Log 版）
    // ------------------------------------------------------------------

    private class SpikeJvmHttpTransport : ProviderHttpTransport {
        override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult =
            doExecute(request, streaming = false, onLine = null, shouldAbort = { false })

        override suspend fun executeStreaming(
            request: ProviderHttpRequest,
            onLine: (String) -> Unit
        ): ProviderHttpResult = doExecute(request, streaming = true, onLine = onLine, shouldAbort = { false })

        override suspend fun executeStreaming(
            request: ProviderHttpRequest,
            onLine: (String) -> Unit,
            shouldAbort: () -> Boolean
        ): ProviderHttpResult = doExecute(request, streaming = true, onLine = onLine, shouldAbort = shouldAbort)

        private suspend fun doExecute(
            request: ProviderHttpRequest,
            streaming: Boolean,
            onLine: ((String) -> Unit)?,
            shouldAbort: () -> Boolean
        ): ProviderHttpResult = withContext(Dispatchers.IO) {
            try {
                val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = request.method
                    connectTimeout = request.timeoutMillis
                    readTimeout = request.timeoutMillis
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = request.method.uppercase() != "GET"
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (request.jsonBody.isNotEmpty()) {
                        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.jsonBody) }
                    }
                }
                try {
                    val statusCode = connection.responseCode
                    val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { reader ->
                            if (!streaming || onLine == null) {
                                reader.readText()
                            } else {
                                buildString {
                                    while (!shouldAbort()) {
                                        val line = reader.readLine() ?: break
                                        if (shouldAbort()) break
                                        onLine(line)
                                        append(line).append('\n')
                                    }
                                }
                            }
                        }.orEmpty()
                    if (statusCode !in 200..299) {
                        System.err.println("[spike-transport] http $statusCode url=${request.url}")
                    }
                    ProviderHttpResult.Response(
                        statusCode = statusCode,
                        body = body,
                        headers = connection.headerFields.filterKeys { it != null }
                            .mapValues { (_, values) -> values.joinToString(",") }
                    )
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                System.err.println("[spike-transport] timeout url=${request.url}")
                ProviderHttpResult.Timeout
            } catch (_: java.io.IOException) {
                System.err.println("[spike-transport] io failure url=${request.url}")
                ProviderHttpResult.Failure
            }
        }
    }

    /** 用量记录包装（chat/chatStreaming 均透传生产客户端，只累计 token）。 */
    private class RecordingClient(private val inner: ToolLoopLlmClient) : ToolLoopLlmClient {
        var calls = 0
        var promptTokens = 0L
        var completionTokens = 0L

        override suspend fun chat(messages: List<LlmMessage>, tools: List<JSONObject>, model: String?): LlmResponse {
            calls++
            return inner.chat(messages, tools, model).also {
                promptTokens += it.usage?.promptTokens ?: 0
                completionTokens += it.usage?.completionTokens ?: 0
            }
        }

        override suspend fun chatStreaming(
            messages: List<LlmMessage>,
            tools: List<JSONObject>,
            model: String?,
            onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit
        ): LlmResponse {
            calls++
            return inner.chatStreaming(messages, tools, model, onThinkingDelta).also {
                promptTokens += it.usage?.promptTokens ?: 0
                completionTokens += it.usage?.completionTokens ?: 0
            }
        }
    }

    // ------------------------------------------------------------------
    // 生产同构内核装配 + 驱动
    // ------------------------------------------------------------------

    private fun buildKernel(root: File): ToolLoop {
        val assets = File(root, "feature/reversetutor/src/main/assets")
        val curriculum = File(assets, "curriculum.json").readText(Charsets.UTF_8)
        val cards = File(assets, "student_cards.json").readText(Charsets.UTF_8)
        StudentCards.setDefault(StudentCards(cards))
        val registry = ToolRegistry()
        CoreTools.registerAll(registry)
        return ToolLoop(
            registry = registry,
            catalog = CurriculumCatalog(curriculum),
            store = MaterialStore(),
            memoryStore = InMemoryRTMemoryStore()
        )
    }

    private fun buildClient(credentials: Credentials, model: String): RecordingClient {
        val profile = LlmProfile(
            id = "spike-three-scenarios",
            spaceId = "spike",
            name = "spike-relay",
            provider = LlmProviderKind.OpenAiCompatible,
            model = model,
            secretRef = "spike-local",
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            baseUrl = credentials.baseUrl
        )
        return RecordingClient(
            ReverseTutorOpenAiLlmClient(
                transport = SpikeJvmHttpTransport(),
                profileProvider = { profile },
                apiKeyResolver = { credentials.apiKey }
            )
        )
    }

    private suspend fun driveTurns(kernel: ToolLoop, client: RecordingClient, scenario: SpikeScenario): List<TurnRecord> {
        var session = newSession(scenario)
        var messages: List<LlmMessage> = emptyList()
        val records = mutableListOf<TurnRecord>()
        scenario.teacherTurns.forEachIndexed { idx, teacherLine ->
            val t0 = System.currentTimeMillis()
            val result = kernel.runTurn(
                session = session,
                historyMessages = messages,
                userText = teacherLine,
                isOpening = false,
                llmClient = client
            )
            val elapsed = System.currentTimeMillis() - t0
            session = result.updatedSession
            messages = result.updatedMessages
            val report = result.report
            records.add(
                TurnRecord(
                    round = report.round,
                    teacher = teacherLine,
                    toolCalls = report.toolCalls.map { it.tool to it.success },
                    finalReply = report.finalReply.orEmpty(),
                    monologue = report.finalMonologue.orEmpty(),
                    llmCalls = report.llmCalls.size,
                    finishReasons = report.llmCalls.map { it.finishReason },
                    elapsedMs = elapsed,
                    levelAfter = session.understandingLevel,
                    emotionAfter = session.emotion,
                    masteredAfter = session.masteredConcepts,
                    confusedAfter = session.confusedConcepts,
                    breakthroughs = session.breakthroughEvents.size,
                    degradations = report.degradations.map { "${it.type}: ${it.detail}" },
                    mathViolations = report.mathViolations
                )
            )
            println(
                "[spike:${scenario.id}] round=${report.round}/${scenario.teacherTurns.size} " +
                    "tools=${report.toolCalls.map { it.tool + (if (it.success) "✓" else "✗") }} " +
                    "llmCalls=${report.llmCalls.size} ${elapsed}ms"
            )
        }
        return records
    }

    // ------------------------------------------------------------------
    // 产物
    // ------------------------------------------------------------------

    private fun transcriptText(
        scenario: SpikeScenario,
        model: String,
        host: String,
        turns: List<TurnRecord>,
        checks: List<SpikeCheck>,
        usage: RecordingClient,
        wallMs: Long
    ): String = buildString {
        append("=== 三场景对抗验证 · ${scenario.id} ${scenario.title} ===\n")
        append("[模型] $model @ $host（key 长度见 run 日志，永不打印明文）\n")
        append("[会话] topic=${scenario.topic} card=${scenario.cardId} grade=${scenario.grade} level=${scenario.level}\n")
        append("       confused=${scenario.confused} mastered=${scenario.mastered}\n\n")
        turns.forEach { t ->
            append("── 第 ${t.round} 轮 ──\n")
            append("[老师] ${t.teacher}\n")
            if (t.toolCalls.isNotEmpty()) {
                append("[工具] ${t.toolCalls.joinToString(", ") { (n, ok) -> n + (if (ok) "✓" else "✗") }}\n")
            }
            append("[LLM] calls=${t.llmCalls} finish=${t.finishReasons} ${t.elapsedMs}ms\n")
            if (t.monologue.isNotBlank()) append("[内心] ${t.monologue}\n")
            append("[学生] ${t.finalReply}\n")
            append(
                "[状态] level=${t.levelAfter} emotion=${t.emotionAfter} " +
                    "mastered=${t.masteredAfter} confused=${t.confusedAfter} breakthroughs=${t.breakthroughs}\n"
            )
            if (t.degradations.isNotEmpty()) append("[降级] ${t.degradations}\n")
            if (t.mathViolations.isNotEmpty()) append("[数学违规] ${t.mathViolations}\n")
            append("\n")
        }
        append("── 机器初判（最终以 transcript 人工复核为准） ──\n")
        checks.forEach { c -> append("[${if (c.passed) "PASS" else "FAIL"}] ${c.id} ${c.description}${if (c.evidence.isNotBlank()) " ｜ 证据: ${c.evidence}" else ""}\n") }
        append("\n[用量] LLM 调用 ${usage.calls} 次，prompt=${usage.promptTokens} tok，completion=${usage.completionTokens} tok，墙钟 ${wallMs}ms\n")
    }

    private fun scenarioJson(
        scenario: SpikeScenario,
        model: String,
        host: String,
        turns: List<TurnRecord>,
        checks: List<SpikeCheck>,
        usage: RecordingClient,
        wallMs: Long
    ): JSONObject {
        val turnsArr = JSONArray()
        turns.forEach { t ->
            val toolsArr = JSONArray()
            t.toolCalls.forEach { (n, ok) -> toolsArr.put(JSONObject().put("name", n).put("success", ok)) }
            turnsArr.put(
                JSONObject()
                    .put("round", t.round)
                    .put("teacher", t.teacher)
                    .put("tools", toolsArr)
                    .put("llm_calls", t.llmCalls)
                    .put("finish_reasons", JSONArray(t.finishReasons))
                    .put("elapsed_ms", t.elapsedMs)
                    .put("monologue", t.monologue)
                    .put("final_reply", t.finalReply)
                    .put("level_after", t.levelAfter)
                    .put("emotion_after", t.emotionAfter)
                    .put("mastered_after", JSONArray(t.masteredAfter))
                    .put("confused_after", JSONArray(t.confusedAfter))
                    .put("breakthroughs", t.breakthroughs)
                    .put("degradations", JSONArray(t.degradations))
                    .put("math_violations", JSONArray(t.mathViolations))
            )
        }
        val checksArr = JSONArray()
        checks.forEach { c ->
            checksArr.put(
                JSONObject().put("id", c.id).put("description", c.description)
                    .put("passed", c.passed).put("evidence", c.evidence)
            )
        }
        val passedCount = checks.count { it.passed }
        return JSONObject()
            .put("scenario", scenario.id)
            .put("title", scenario.title)
            .put("model", model)
            .put("host", host)
            .put("session", JSONObject()
                .put("topic", scenario.topic)
                .put("card", scenario.cardId)
                .put("grade", scenario.grade)
                .put("level", scenario.level)
                .put("confused", JSONArray(scenario.confused))
                .put("mastered", JSONArray(scenario.mastered)))
            .put("turns", turnsArr)
            .put("machine_checks", checksArr)
            .put("machine_verdict", if (passedCount == checks.size) "PASS" else "PARTIAL($passedCount/${checks.size})")
            .put("usage", JSONObject()
                .put("llm_calls", usage.calls)
                .put("prompt_tokens", usage.promptTokens)
                .put("completion_tokens", usage.completionTokens))
            .put("wall_ms", wallMs)
    }

    // ------------------------------------------------------------------
    // 三个 @Test 入口（每个场景独立，可 --tests 过滤单跑）
    // ------------------------------------------------------------------

    private fun runSpike(scenarioId: String) {
        val cfg = guardFor(scenarioId)
        val scenario = scenarios.first { it.id == scenarioId }
        val credentials = loadCredentials(cfg.mobileNativeRoot)
        val model = cfg.modelOverride ?: credentials.defaultModel
        val host = try {
            URL(credentials.baseUrl).host
        } catch (_: Exception) {
            credentials.baseUrl
        }
        val outDir = File(cfg.mobileNativeRoot, "build/spike/output").apply { mkdirs() }
        println(
            "[spike] 场景=${scenario.id}（${scenario.title}） 模型=$model @ $host " +
                "key=<len ${credentials.apiKey.length}> 输出=${outDir.absolutePath}"
        )

        val kernel = buildKernel(cfg.mobileNativeRoot)
        val client = buildClient(credentials, model)
        val t0 = System.currentTimeMillis()
        val turns = runBlocking { driveTurns(kernel, client, scenario) }
        val wallMs = System.currentTimeMillis() - t0
        val checks = scenario.checks(turns)

        File(outDir, "${scenario.id}.transcript.txt")
            .writeText(transcriptText(scenario, model, host, turns, checks, client, wallMs), Charsets.UTF_8)
        File(outDir, "${scenario.id}.json")
            .writeText(scenarioJson(scenario, model, host, turns, checks, client, wallMs).toString(2), Charsets.UTF_8)

        checks.forEach { c ->
            println("[spike:${scenario.id}] [${if (c.passed) "PASS" else "FAIL"}] ${c.id} ${c.description}")
        }
        println(
            "[spike:${scenario.id}] 机器初判=${if (checks.all { it.passed }) "PASS" else "PARTIAL"} " +
                "calls=${client.calls} tokens=${client.promptTokens}/${client.completionTokens} 墙钟=${wallMs}ms"
        )
    }

    @Test
    fun scenario1_misleadingCorrection() = runSpike("s1")

    @Test
    fun scenario2_skipAndRouteBack() = runSpike("s2")

    @Test
    fun scenario3_baitAndWithdraw() = runSpike("s3")
}
