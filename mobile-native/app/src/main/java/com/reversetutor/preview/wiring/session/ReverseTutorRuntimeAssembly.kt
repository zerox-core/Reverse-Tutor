package com.reversetutor.preview.wiring.session

import android.content.Context
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.GenerationPartialStore
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.llm.ProviderHttpTransport
import com.reversetutor.core.llm.UrlConnectionProviderHttpTransport
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.loop.LlmMessage
import com.reversetutor.feature.reversetutor.loop.LlmResponse
import com.reversetutor.feature.reversetutor.loop.LlmUsage
import com.reversetutor.feature.reversetutor.loop.ToolCallRequest
import com.reversetutor.feature.reversetutor.loop.ToolLoop
import com.reversetutor.feature.reversetutor.loop.ToolLoopLlmClient
import com.reversetutor.feature.reversetutor.memory.InMemoryRTMemoryStore
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.scenario.ScenarioPackage
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_REVERSE_TUTOR_MODEL = "deepseek-chat"

/**
 * 反转家教算法运行时装配（装配层 R-RT-2）。
 *
 * 职责：
 * 1. 从 assets 加载课标目录与学生角色卡（内容与代码分离，与 spike 一致）；
 * 2. 构建 31 工具注册表 + ToolLoop 决策引擎（单例，进程内共享 RTSession 状态）；
 * 3. 构建 [ReverseTutorTurnPreparationCoordinator]，桥接真实仓库层：
 *    入队/认领/完成/失败走 BackgroundJobDao，消息走 MessageRepository；
 *    LLM 客户端由调用方按运行模式注入（Fake 脚本化 / Production OpenAI tool-calling）。
 *
 * 冻结区约束：只调用 core/data 公开 API（DAO/仓库），不修改任何冻结文件。
 */
object ReverseTutorRuntimeAssembly {

    private const val ASSET_CURRICULUM = "curriculum.json"
    private const val ASSET_STUDENT_CARDS = "student_cards.json"

    /** 场景包资产目录（由 `py -m scripts.build_scenario_packages` 生成）。 */
    private const val ASSET_SCENARIOS_DIR = "scenarios"

    fun buildCoordinator(
        context: Context,
        conversationRunRepository: ConversationRunRepositoryImpl,
        messageRepository: MessageRepository,
        backgroundGenerationRepository: com.reversetutor.core.data.background.BackgroundGenerationRepository,
        llmClientProvider: suspend () -> ToolLoopLlmClient,
        conversationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ): ReverseTutorTurnPreparationCoordinator {
        val appContext = context.applicationContext

        // ---- 算法内核（单例状态：RTSession / 知识图谱 / 学生卡） ----
        val catalog = CurriculumCatalog(readAsset(appContext, ASSET_CURRICULUM))
        StudentCards.setDefault(StudentCards(readAsset(appContext, ASSET_STUDENT_CARDS)))
        val registry = ToolRegistry()
        CoreTools.registerAll(registry)
        // 场景素材注入（2026-10-14：算法为基底、场景为配置数据的第一步）：
        // 此前 addDoc 无生产调用点，活动包的 sources 永不进资料库。
        val materialStore = MaterialStore()
        val scenarioDocs = installScenarioPackages(appContext, materialStore)
        // 长期记忆（阶段 3 首批，2026-10-14）：save_teaching_pattern /
        // retrieve_historical_context / generate_progress_report 需要跨会话状态。
        // 此处用进程内实现（Room 属冻结区，待用户批准后再替换实现）。
        val memoryStore = InMemoryRTMemoryStore()
        val toolLoop = ToolLoop(
            registry = registry,
            catalog = catalog,
            store = materialStore,
            memoryStore = memoryStore
        )

        // ---- 仓库桥接 ----
        val database = DataModule.database(appContext)
        val backgroundJobDao = database.backgroundJobDao()
        val secretStore = DataModule.secretStore(appContext)

        return ReverseTutorTurnPreparationCoordinator(
            isSessionDeleted = { sessionId ->
                conversationRunRepository.isSessionDeleted(sessionId)
            },
            enqueueJob = { input, now ->
                backgroundGenerationRepository.enqueueGenerationJob(input, now)
            },
            claimJob = { id, now ->
                // 原子认领：Queued → Running，让外部 Worker 收到 AlreadyRunning 静默退出
                backgroundJobDao.claimQueued(
                    id = id,
                    queuedStatus = "Queued",
                    runningStatus = "Running",
                    startedAtEpochMillis = now
                )
            },
            updateJobCompleted = { id, now ->
                backgroundJobDao.getById(id)?.let { entity ->
                    backgroundJobDao.upsert(
                        entity.copy(
                            status = "Completed",
                            completedAtEpochMillis = now,
                            errorMessage = null
                        )
                    )
                }
            },
            updateJobFailed = { id, now, error ->
                backgroundJobDao.getById(id)?.let { entity ->
                    backgroundJobDao.upsert(
                        entity.copy(
                            status = "Failed",
                            completedAtEpochMillis = now,
                            errorMessage = error.take(500)
                        )
                    )
                }
            },
            saveAssistantMessage = { spaceId, sessionId, text, monologue, now ->
                val messageId = "message-${java.util.UUID.randomUUID()}"
                messageRepository.saveMessage(
                    Message(
                        id = messageId,
                        spaceId = spaceId.ifBlank { "default" },
                        sessionId = sessionId,
                        role = MessageRole.Assistant,
                        text = text,
                        createdAtEpochMillis = now,
                        monologue = monologue
                    )
                )
                messageId
            },
            loadMessages = { sessionId ->
                messageRepository.listMessages(sessionId).map { message ->
                    ConversationMessageContract(
                        messageId = message.id,
                        role = when (message.role) {
                            MessageRole.Assistant -> "assistant"
                            else -> "user"
                        },
                        text = message.text,
                        timestampEpochMillis = message.createdAtEpochMillis
                    )
                }
            },
            partialStore = GenerationPartialStore(),
            toolLoop = toolLoop,
            llmClientProvider = llmClientProvider,
            coroutineScope = conversationScope
        )
    }

    /**
     * Production 模式的 LLM 客户端工厂：每回合重读激活 profile，
     * 与 ChatGenerationRepository.activeLegacyProfile 同口径。
     */
    fun buildProductionLlmClient(context: Context): suspend () -> ToolLoopLlmClient {
        val appContext = context.applicationContext
        val transport: ProviderHttpTransport = UrlConnectionProviderHttpTransport()
        val llmProfileRepository = DataModule.llmProfileRepository(appContext)
        val secretStore = DataModule.secretStore(appContext)
        return {
            val profile = llmProfileRepository.listProfiles().firstOrNull { it.enabled }
            ReverseTutorOpenAiLlmClient(
                transport = transport,
                profileProvider = { profile },
                apiKeyResolver = { secretRef -> secretRef?.let { secretStore.get(it) } }
            )
        }
    }

    /** Fake 模式的 LLM 客户端工厂：本地脚本化学生台词，不发网络请求。 */
    fun buildFakeLlmClient(): suspend () -> ToolLoopLlmClient = {
        ReverseTutorFakeLlmClient()
    }

    private fun readAsset(context: Context, name: String): String {
        return context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * 把随包发布的场景素材注入资料库，返回注入条数。
     *
     * 现状（最小切片）：装配期装入全部打包场景——会话尚未携带 scenarioId，
     * 按会话按场景隔离随「会话 ↔ 场景绑定」一起做。素材关键词互不重叠，
     * 跨场景检索不会互相干扰。
     *
     * 目录缺失或单个包损坏都不影响启动（算法内核不因场景数据失败而不可用）。
     */
    private fun installScenarioPackages(context: Context, store: MaterialStore): Int {
        val names = runCatching {
            context.assets.list(ASSET_SCENARIOS_DIR)?.filter { it.endsWith(".json") }.orEmpty()
        }.getOrElse { return 0 }

        var installed = 0
        for (name in names.sorted()) {
            val parsed = runCatching {
                ScenarioPackage.parse(readAsset(context, "$ASSET_SCENARIOS_DIR/$name"))
            }.getOrNull() ?: continue
            installed += parsed.installInto(store)
        }
        return installed
    }
}

/**
 * OpenAI tool-calling 桥接客户端（规格源：spike app/llm/provider.py OpenAiProvider.chat）。
 *
 * - 请求：{model, messages, tools:[{type:function, function:t}]}，temperature 固定 0.7；
 * - 响应：choices[0].message 的 content / tool_calls / finish_reason + usage；
 * - 每回合构造一次（llmClientProvider），激活 profile 每回合重读，支持热切换模型。
 * - 2026-10-13 真流式：默认走 SSE（stream=true），思考增量实时回调；流式失败
 *   自动降级回一次性请求，行为与旧版一致。
 */
class ReverseTutorOpenAiLlmClient(
    private val transport: ProviderHttpTransport,
    private val profileProvider: () -> LlmProfile?,
    private val apiKeyResolver: suspend (String?) -> String?,
    private val fallbackModel: String = DEFAULT_REVERSE_TUTOR_MODEL,
    private val temperature: Double = 0.7,
    private val timeoutMillis: Int = 120_000
) : ToolLoopLlmClient {

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?
    ): LlmResponse {
        val (_, baseUrl, apiKey, effectiveModel) = resolveEndpoint(model)
        val payload = buildPayload(messages, tools, effectiveModel, streaming = false)

        val startedAt = System.currentTimeMillis()
        val result = transport.execute(
            com.reversetutor.core.llm.ProviderHttpRequest(
                url = "$baseUrl/chat/completions",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $apiKey"
                ),
                jsonBody = payload.toString(),
                timeoutMillis = timeoutMillis,
                streaming = false
            )
        )
        val elapsed = System.currentTimeMillis() - startedAt

        val body = when (result) {
            is com.reversetutor.core.llm.ProviderHttpResult.Response -> result
            else -> throw IllegalStateException("LLM 请求失败（网络层）：$result")
        }
        if (body.statusCode !in 200..299) {
            throw IllegalStateException("LLM HTTP ${body.statusCode}: ${body.body.take(300)}")
        }

        val data = JSONObject(body.body)
        val choice = data.optJSONArray("choices")?.optJSONObject(0)
            ?: throw IllegalStateException("LLM 响应缺少 choices: ${body.body.take(300)}")
        val message = choice.optJSONObject("message") ?: JSONObject()
        val content = message.optString("content", "").takeIf { it.isNotBlank() }
        val toolCalls = mutableListOf<ToolCallRequest>()
        message.optJSONArray("tool_calls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val function = call.optJSONObject("function") ?: continue
                toolCalls.add(
                    ToolCallRequest(
                        callId = call.optString("id").ifBlank { "call_$i" },
                        name = function.optString("name"),
                        arguments = function.optString("arguments").ifBlank { "{}" }
                    )
                )
            }
        }

        val usageJson = data.optJSONObject("usage")
        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = choice.optString("finish_reason", "stop"),
            model = data.optString("model", effectiveModel),
            elapsedMs = elapsed,
            usage = usageJson?.let {
                LlmUsage(
                    promptTokens = it.optInt("prompt_tokens", 0),
                    completionTokens = it.optInt("completion_tokens", 0),
                    totalTokens = it.optInt("total_tokens", 0)
                )
            },
            rawMessage = message
        )
    }

    /**
     * 真流式实现：SSE 逐行回调，delta.reasoning_content 与 delta.content 增量
     * 双通道实时回调（思考抽屉逐字显示），tool_calls 分片聚合，finish_reason
     * 与 usage 取尾块。任何流式层失败回退到一次性 chat，保证回合不中断。
     */
    override suspend fun chatStreaming(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?,
        onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit
    ): LlmResponse {
        return try {
            chatStreamingInternal(messages, tools, model, onThinkingDelta)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // 流式通道失败（不支持 stream 的渠道/网络中断/解析异常）→ 降级一次性
            chat(messages, tools, model)
        }
    }

    private suspend fun chatStreamingInternal(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?,
        onThinkingDelta: (reasoningDelta: String, contentDelta: String) -> Unit
    ): LlmResponse {
        val (_, baseUrl, apiKey, effectiveModel) = resolveEndpoint(model)
        val payload = buildPayload(messages, tools, effectiveModel, streaming = true)

        val startedAt = System.currentTimeMillis()
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        // tool_calls 分片聚合：delta 只带 {index, id?, function:{name?, arguments 片段}}
        val toolCallAcc = sortedMapOf<Int, ToolCallAccumulator>()
        var finishReason = "stop"
        var reportedModel = effectiveModel
        var usageJson: JSONObject? = null

        val result = transport.executeStreaming(
            com.reversetutor.core.llm.ProviderHttpRequest(
                url = "$baseUrl/chat/completions",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $apiKey",
                    // SSE 增量关键头：identity 防 gzip 缓冲整段流
                    "Accept" to "text/event-stream",
                    "Accept-Encoding" to "identity"
                ),
                jsonBody = payload.toString(),
                timeoutMillis = timeoutMillis,
                streaming = true
            ),
            onLine = { line ->
                val data = parseSseData(line) ?: return@executeStreaming
                val choice = data.optJSONArray("choices")?.optJSONObject(0)
                val delta = choice?.optJSONObject("delta")
                if (delta != null) {
                    val reasoningDelta = delta.optString("reasoning_content", "")
                    if (reasoningDelta.isNotEmpty()) {
                        reasoningBuilder.append(reasoningDelta)
                    }
                    val contentDelta = delta.optString("content", "")
                    if (contentDelta.isNotEmpty()) {
                        contentBuilder.append(contentDelta)
                    }
                    if (reasoningDelta.isNotEmpty() || contentDelta.isNotEmpty()) {
                        onThinkingDelta(reasoningDelta, contentDelta)
                    }
                    // tool_calls 分片：按 index 聚合 id/name/arguments
                    delta.optJSONArray("tool_calls")?.let { calls ->
                        for (i in 0 until calls.length()) {
                            val fragment = calls.optJSONObject(i) ?: continue
                            val index = fragment.optInt("index", i)
                            val acc = toolCallAcc.getOrPut(index) { ToolCallAccumulator() }
                            fragment.optString("id", "").takeIf { it.isNotEmpty() }?.let { acc.id = it }
                            fragment.optJSONObject("function")?.let { fn ->
                                fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { acc.name = it }
                                acc.arguments.append(fn.optString("arguments", ""))
                            }
                        }
                    }
                }
                choice?.optString("finish_reason", "")?.takeIf { it.isNotEmpty() }?.let {
                    finishReason = it
                }
                data.optJSONObject("usage")?.let { usageJson = it }
                data.optString("model", "").takeIf { it.isNotEmpty() }?.let { reportedModel = it }
            },
            // 必须走三参重载：接口的两参默认实现退化为一次性 execute（不会路由
            // 到三参），UrlConnection 传输层也只实现三参版本。回合不需要中途
            // 切断，abort 恒 false。
            shouldAbort = { false }
        )

        val elapsed = System.currentTimeMillis() - startedAt
        when (result) {
            is com.reversetutor.core.llm.ProviderHttpResult.Response -> {
                if (result.statusCode !in 200..299) {
                    throw IllegalStateException("LLM HTTP ${result.statusCode}: ${result.body.take(300)}")
                }
            }
            else -> throw IllegalStateException("LLM 请求失败（网络层）：$result")
        }

        val fullContent = contentBuilder.toString().takeIf { it.isNotBlank() }
        val rawMessage = JSONObject().apply {
            if (!reasoningBuilder.isEmpty()) put("reasoning_content", reasoningBuilder.toString())
            if (!contentBuilder.isEmpty()) put("content", contentBuilder.toString())
        }
        return LlmResponse(
            content = fullContent,
            toolCalls = toolCallAcc.values.mapNotNull { it.toToolCall() },
            finishReason = finishReason,
            model = reportedModel,
            elapsedMs = elapsed,
            usage = usageJson?.let {
                LlmUsage(
                    promptTokens = it.optInt("prompt_tokens", 0),
                    completionTokens = it.optInt("completion_tokens", 0),
                    totalTokens = it.optInt("total_tokens", 0)
                )
            },
            rawMessage = rawMessage
        )
    }

    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun toToolCall(): ToolCallRequest? =
            name.takeIf { it.isNotBlank() }?.let {
                ToolCallRequest(
                    callId = id.ifBlank { "call_${it.hashCode()}" },
                    name = it,
                    arguments = arguments.toString().ifBlank { "{}" }
                )
            }
    }

    /** SSE data 行解析：非 data: 行/哨兵 [DONE]/坏 JSON 返回 null。 */
    private fun parseSseData(line: String): JSONObject? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return try {
            JSONObject(payload)
        } catch (_: Exception) {
            null
        }
    }

        private suspend fun resolveEndpoint(model: String?): EndpointConfig {
        val profile = profileProvider()
        val baseUrl = profile?.baseUrl?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("反转家教未配置 LLM 渠道，请先在设置中启用模型")
        val apiKey = apiKeyResolver(profile.secretRef)
            ?: throw IllegalStateException("LLM 密钥不可用（secretRef=${profile.secretRef ?: "null"}）")
        val effectiveModel = model?.takeIf { it.isNotBlank() }
            ?: profile.model.takeIf { it.isNotBlank() }
            ?: fallbackModel
        return EndpointConfig(profile, baseUrl, apiKey, effectiveModel)
    }

    private data class EndpointConfig(
        val profile: LlmProfile,
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    private fun buildPayload(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String,
        streaming: Boolean
    ): JSONObject = JSONObject().apply {
        put("model", model)
        put("temperature", temperature)
        put("messages", encodeMessages(messages))
        if (tools.isNotEmpty()) {
            put(
                "tools",
                JSONArray().apply {
                    tools.forEach { tool ->
                        put(JSONObject().put("type", "function").put("function", tool))
                    }
                }
            )
        }
        if (streaming) {
            put("stream", true)
            // R94/R95 同款：开思考流（不认识该字段的供应商会忽略），
            // usage 走尾块（include_usage）
            put("enable_thinking", true)
            put("stream_options", JSONObject().put("include_usage", true))
        }
    }

    /** LlmMessage → OpenAI messages 数组（assistant.tool_calls / tool 结果回合）。 */
    private fun encodeMessages(messages: List<LlmMessage>): JSONArray {
        val array = JSONArray()
        messages.forEach { message ->
            val entry = JSONObject()
            when {
                message.role == "tool" -> {
                    entry.put("role", "tool")
                    entry.put("tool_call_id", message.toolCallId ?: "")
                    entry.put("content", message.content ?: "")
                }
                message.role == "assistant" && !message.toolCalls.isNullOrEmpty() -> {
                    entry.put("role", "assistant")
                    entry.put("content", message.content ?: JSONObject.NULL)
                    val calls = JSONArray()
                    val assistantToolCalls = message.toolCalls.orEmpty()
                    assistantToolCalls.forEach { call ->
                        calls.put(
                            JSONObject()
                                .put("id", call.callId)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", call.name)
                                        .put("arguments", call.arguments)
                                )
                        )
                    }
                    entry.put("tool_calls", calls)
                }
                else -> {
                    entry.put("role", message.role)
                    entry.put("content", message.content ?: "")
                }
            }
            array.put(entry)
        }
        return array
    }
}

/** 反转家教会话的创建期配置（路由端口判定输入），供后续 NewSession 预设接入使用。 */
fun reverseTutorSessionConfiguration(title: String): NewSessionConfiguration =
    NewSessionConfiguration(
        title = title,
        builtInPresetId = REVERSE_TUTOR_PRESET_ID
    )

/**
 * Fake 模式反转家教客户端：模拟 LLM 学生回答（脚本化演示），
 * 不发网络请求，保证预览壳在无渠道配置时也能完整走通 ToolLoop。
 */
class ReverseTutorFakeLlmClient : ToolLoopLlmClient {
    private var counter = 0

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<JSONObject>,
        model: String?
    ): LlmResponse {
        counter += 1
        val reply = when (counter) {
            1 -> "<thinking>老师刚才讲得很快，我只记住了公式前半部分，得装作困惑问问关键步骤。</thinking>\n老师，我先说下我的理解：这个概念我大概懂了一半，具体步骤还不太确定，您能再讲一遍关键那步吗？"
            else -> "<thinking>原来这里是两边同时做等价变形，我明白了，再验证一下通用性。</thinking>\n嗯……这样一讲我就清楚多了。那如果换个数字，这个方法还适用吗？"
        }
        return LlmResponse(
            content = reply,
            toolCalls = emptyList(),
            finishReason = "stop",
            model = "fake-reverse-tutor",
            elapsedMs = 10L
        )
    }
}

/** 供装配层在请求到达时做兼容判定（预留：会话快照缺失时按会话仓库补判）。 */
fun BackgroundTurnPreparationRequest.isReverseTutorTurn(): Boolean =
    sessionSnapshot?.builtInPresetId == REVERSE_TUTOR_PRESET_ID
