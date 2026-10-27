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
import com.reversetutor.feature.reversetutor.prompt.CognitiveScaffolds
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CognitiveTools
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
 * 解析几何试验田 · V1 认知骨架观察器。
 *
 * **这不是测试，是观察工具。** AGENTS.md 禁止测试打真实网络，所以默认
 * `assumeTrue` 跳过；显式开启后才会真正调用 `local.properties` 里的模型。
 *
 * 与生产装配的差异（仅 2 处，均为观察所需）：
 * 1. 工具注册表 = CoreTools(31) + CognitiveTools(3)，生产装配同此；
 * 2. 会 dump 每轮完整 `<thinking>` 原文，用于检查模型选了哪几条骨架、槽位填得实不实。
 *
 * 开启方式：
 * ```
 * mobile-native/build/spike/run.flag 写 {"scenarios":["geom"],"model":null}
 * gradlew :app:testDebugUnitTest --tests "*GeometryScaffoldProbe"
 * ```
 * 产物：`mobile-native/build/spike/output/geom.transcript.txt`
 */
class GeometryScaffoldProbe {

    // ------------------------------------------------------------------
    // 场景：来自 docs/cognitive-harness/sample-parsing-geometry-round1.md
    // ------------------------------------------------------------------

    /**
     * 学生开场白里故意埋 3 个可攻击点：
     * 1. 「所以我设 y=k(x-4)」        → 垂线情形完全没提（F1.degeneracy）
     * 2. 「用韦达定理拿到 x1+x2」    → 适用条件 Δ>0 未验证（F2.felicity / F2.warrant）
     * 3. 「代进去就是答案」          → 从 k 的范围到 x0 的范围跳步（F2.warrant + F5.step）
     */
    private val studentOpening = """
        老师，这道椭圆题我思路有了，但不确定对不对，你帮我看看。

        我是这么想的：P 在 x 轴上，不在椭圆上，所以我设直线 y = k(x-4)，跟椭圆联立，
        得到关于 x 的二次方程。然后用韦达定理拿到 x1+x2，就能算中点坐标了。
        中点有了、斜率也有了，垂直平分线就写得出来，让它跟 y=0 联立，就能解出 x0。

        最后 x0 是关于 k 的式子，k 的范围我用 Δ > 0 解出来，代进去就是答案。

        这样想对吗？
    """.trimIndent()

    /**
     * 老师第一句。刻意**只敲打一个点**（垂线），看模型会不会自己扩充到另外两个点。
     * 如果它只回答垂线而不提 Δ 与跳步，说明它把「选骨架」当成了「回答被问到的那一条」，
     * 而非「独立审阅整段讲解」——这正是 V1 要测出的失败模式。
     */
    private val teacherLine = "你说设 y = k(x-4)。那如果这条线是竖着立在那儿的呢？"

    private fun newSession(): RTSession = RTSession(
        sessionId = "geom-probe-${System.currentTimeMillis()}",
        topic = "解析几何：椭圆与直线交点问题",
        grade = "高中",
        understandingLevel = 3,
        initialUnderstandingLevel = 3,
        confusedConcepts = listOf("判别式与存在性", "中垂线求法"),
        masteredConcepts = listOf("椭圆标准方程", "韦达定理")
    )

    // ------------------------------------------------------------------
    // 装配（与生产同构）
    // ------------------------------------------------------------------

    private fun buildKernel(root: File): ToolLoop {
        val assets = File(root, "feature/reversetutor/src/main/assets")
        StudentCards.setDefault(StudentCards(File(assets, "student_cards.json").readText(Charsets.UTF_8)))
        val registry = ToolRegistry()
        CoreTools.registerAll(registry)
        CognitiveTools.registerAll(registry)
        return ToolLoop(
            registry = registry,
            catalog = CurriculumCatalog(File(assets, "curriculum.json").readText(Charsets.UTF_8)),
            store = MaterialStore(),
            memoryStore = InMemoryRTMemoryStore()
        )
    }

    private fun buildClient(creds: Credentials, model: String): RecordingClient =
        RecordingClient(
            ReverseTutorOpenAiLlmClient(
                transport = ProbeJvmHttpTransport(),
                profileProvider = {
                    LlmProfile(
                        id = "geom-probe",
                        spaceId = "spike",
                        name = "geom-probe",
                        provider = LlmProviderKind.OpenAiCompatible,
                        model = model,
                        secretRef = "spike-local",
                        createdAtEpochMillis = 0L,
                        updatedAtEpochMillis = 0L,
                        baseUrl = creds.baseUrl
                    )
                },
                apiKeyResolver = { creds.apiKey }
            )
        )

    // ------------------------------------------------------------------
    // 驱动
    // ------------------------------------------------------------------

    private data class TurnObservation(
        val label: String,
        val userText: String,
        val toolCalls: List<String>,
        val monologue: String,
        val finalReply: String,
        val selectedCount: Int,
        val charsPerScaffold: Double,
        val severity: String,
        val looksShallow: Boolean,
        val severityDegenerate: Boolean,
        val llmCalls: Int,
        val elapsedMs: Long
    )

    private suspend fun drive(kernel: ToolLoop, client: RecordingClient): List<TurnObservation> {
        var session = newSession()
        var messages: List<LlmMessage> = emptyList()
        val out = mutableListOf<TurnObservation>()

        val rounds = listOf("学生开场白" to studentOpening, "老师第 1 句" to teacherLine)
        rounds.forEachIndexed { idx, (label, text) ->            val t0 = System.currentTimeMillis()
            // 注意：isOpening=true 会把 userText 换成 PromptAssembler.OPENING_INSTRUCTION，
            // 学生原文根本不会发给模型。本探测要的是「学生带着预设误解开场」，
            // 所以两轮都必须 isOpening=false，学生开场白作为普通用户消息发出。
            val result = kernel.runTurn(
                session = session,
                historyMessages = messages,
                userText = text,
                isOpening = false,
                llmClient = client
            )
            session = result.updatedSession
            messages = result.updatedMessages
            val report = result.report
            val monologue = report.finalMonologue.orEmpty()

            // 度量的是「判定块」，即 thinking 信封内容
            val m = CognitiveScaffolds.measure(monologue.ifBlank { "<thinking>$monologue</thinking>" })
            val severity = buildString {
                if (m.blocking > 0) append("blocking×${m.blocking} ")
                if (m.significant > 0) append("significant×${m.significant} ")
                if (m.minor > 0) append("minor×${m.minor}")
                if (isEmpty()) append("（无严重度标记）")
            }

            out.add(
                TurnObservation(
                    label = label,
                    userText = text,
                    toolCalls = report.toolCalls.map { it.tool + if (it.success) "✓" else "✗" },
                    monologue = monologue,
                    finalReply = report.finalReply.orEmpty(),
                    selectedCount = m.selectedCount,
                    charsPerScaffold = m.charsPerScaffold,
                    severity = severity,
                    looksShallow = m.looksShallow(),
                    severityDegenerate = m.severityIsDegenerate(),
                    llmCalls = report.llmCalls.size,
                    elapsedMs = System.currentTimeMillis() - t0
                )
            )
            println(
                "[geom] ${label}: tools=${report.toolCalls.map { it.tool }} " +
                    "llmCalls=${report.llmCalls.size} 骨架=${m.selectedCount} ${System.currentTimeMillis() - t0}ms"
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // 产物
    // ------------------------------------------------------------------

    private fun render(obs: List<TurnObservation>, model: String, host: String, wallMs: Long): String =
        buildString {
            append("=== 解析几何试验田 · V1 骨架观察 ===\n")
            append("[模型] $model @ $host\n")
            append("[题] 椭圆 x²/4 + y²/2 = 1，P(4,0)，过 P 的直线交椭圆于 A,B，\n")
            append("     AB 中垂线交 x 轴于 M(x0,0)，求 x0 范围。\n")
            append("[埋点] ① 设 y=k(x-4) 没提垂线（F1.degeneracy）\n")
            append("       ② 用韦达定理没验 Δ>0（F2.felicity / F2.warrant）\n")
            append("       ③ 「代进去就是答案」跳步（F2.warrant + F5.step）\n")
            append("[墙钟] ${wallMs}ms\n\n")

            obs.forEach { o ->
                append("══════ ${o.label} ══════\n")
                append("[输入] ${o.userText.replace("\n", "\n       ")}\n\n")
                append("[工具] ${if (o.toolCalls.isEmpty()) "（未调用）" else o.toolCalls.joinToString(", ")}\n")
                append("[LLM] calls=${o.llmCalls} ${o.elapsedMs}ms\n\n")
                append("──── 判定块（模型内部，用户看不到）────\n")
                append(o.monologue.ifBlank { "（空）" }).append("\n\n")
                append("──── 学生台词（用户看到）────\n")
                append(o.finalReply.ifBlank { "（空）" }).append("\n\n")
                append("──── 度量 ────\n")
                append("骨架数=${o.selectedCount}  每骨架字符=${"%.1f".format(o.charsPerScaffold)}  ")
                append("严重度=${o.severity}\n")
                append(
                    "槽位疑似敷衍=${o.looksShallow}  严重度分布退化=${o.severityDegenerate}\n"
                )
                append("判定块是否泄漏进学生台词=${o.finalReply.contains("选:") || o.finalReply.contains("判定:")}\n\n")
            }

            append("\n══════ 人工核对清单 ══════\n")
            obs.forEach { o ->
                append("${o.label}：\n")
                append("  □ 是否抓到垂线情形（K1）\n")
                append("  □ 是否抓到 Δ>0 未验（K3）\n")
                append("  □ 是否抓到 k 范围→x0 范围的跳步（K6）\n")
                append("  □ 学生台词是否真的落在它自己选的骨架上\n")
                append("  □ 判定块是否完全没泄漏进台词\n\n")
            }
        }

    private data class Credentials(val baseUrl: String, val model: String, val apiKey: String)

    private fun findRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val d = dir ?: return null
            if (File(d, "local.properties").isFile && File(d, "settings.gradle.kts").isFile) return d
            if (File(d, "mobile-native/local.properties").isFile) return File(d, "mobile-native")
            dir = d.parentFile
        }
        return null
    }

    private fun loadCreds(root: File): Credentials {
        val props = Properties()
        File(root, "local.properties").reader(Charsets.UTF_8).use { props.load(it) }
        val baseUrl = props.getProperty("reverseTutorDebugLlmBaseUrl").orEmpty().trim().trimEnd('/')
        val model = props.getProperty("reverseTutorDebugLlmDefaultModel").orEmpty().trim()
        val key = props.getProperty("reverseTutorDebugLlmApiKey").orEmpty().trim()
        check(baseUrl.isNotEmpty() && model.isNotEmpty() && key.isNotEmpty()) {
            "local.properties 缺少 reverseTutorDebugLlm* 凭据"
        }
        return Credentials(baseUrl, model, key)
    }

    @Test
    fun probe() {
        val root = findRoot()
        assumeTrue("未找到 mobile-native 根目录", root != null)
        val flag = File(root!!, "build/spike/run.flag")
        assumeTrue("默认离线；写 build/spike/run.flag 才跑真实模型", flag.isFile)
        val scenarios = try {
            JSONObject(flag.readText(Charsets.UTF_8)).optJSONArray("scenarios")
        } catch (_: Exception) {
            null
        }
        assumeTrue(
            "flag.scenarios 需包含 geom",
            scenarios == null || (0 until scenarios.length()).any {
                scenarios.optString(it) == "geom"
            }
        )

        val creds = loadCreds(root)
        val model = creds.model
        val host = runCatching { URL(creds.baseUrl).host }.getOrDefault(creds.baseUrl)
        println("[geom] 模型=$model @ $host key=<len ${creds.apiKey.length}>")

        val kernel = buildKernel(root)
        val client = buildClient(creds, model)
        val t0 = System.currentTimeMillis()
        val obs = runBlocking { drive(kernel, client) }
        val wall = System.currentTimeMillis() - t0

        val outDir = File(root, "build/spike/output").apply { mkdirs() }
        val file = File(outDir, "geom.transcript.txt")
        file.writeText(render(obs, model, host, wall), Charsets.UTF_8)

        println("[geom] 产物=${file.absolutePath}")
        obs.forEach { o ->
            println(
                "[geom] ${o.label}: 骨架=${o.selectedCount} ${o.severity} " +
                    "字数/骨架=${"%.1f".format(o.charsPerScaffold)} 泄漏=${o.finalReply.contains("选:")}"
            )
        }
    }

    // ------------------------------------------------------------------
    // 计数客户端 + JVM transport（与三场景 spike 同法）
    // ------------------------------------------------------------------

    private class RecordingClient(private val inner: ToolLoopLlmClient) : ToolLoopLlmClient {
        var calls = 0
        override suspend fun chat(messages: List<LlmMessage>, tools: List<JSONObject>, model: String?): LlmResponse {
            calls++
            return inner.chat(messages, tools, model)
        }
    }

    private class ProbeJvmHttpTransport : ProviderHttpTransport {
        override suspend fun execute(request: ProviderHttpRequest): ProviderHttpResult =
            doExecute(request, streaming = false, onLine = null)

        override suspend fun executeStreaming(
            request: ProviderHttpRequest,
            onLine: (String) -> Unit
        ): ProviderHttpResult = doExecute(request, streaming = true, onLine = onLine)

        private suspend fun doExecute(
            request: ProviderHttpRequest,
            streaming: Boolean,
            onLine: ((String) -> Unit)?
        ): ProviderHttpResult = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = request.method
                    connectTimeout = request.timeoutMillis
                    readTimeout = request.timeoutMillis
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = request.method.uppercase() != "GET"
                    request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (request.jsonBody.isNotEmpty()) {
                        outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.jsonBody) }
                    }
                }
                try {
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { reader ->
                            if (!streaming || onLine == null) {
                                reader.readText()
                            } else {
                                reader.lineSequence().forEach { line -> onLine(line) }
                                ""
                            }
                        }.orEmpty()
                    if (code !in 200..299) {
                        System.err.println("[geom-transport] http $code url=${request.url}")
                    }
                    ProviderHttpResult.Response(
                        statusCode = code,
                        body = body,
                        headers = conn.headerFields.filterKeys { it != null }
                            .mapValues { (_, values) -> values.joinToString(",") }
                    )
                } finally {
                    conn.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                System.err.println("[geom-transport] timeout url=${request.url}")
                ProviderHttpResult.Timeout
            } catch (_: java.io.IOException) {
                System.err.println("[geom-transport] io failure url=${request.url}")
                ProviderHttpResult.Failure
            }
        }
    }
}
