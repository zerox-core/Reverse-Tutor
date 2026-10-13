package com.reversetutor.feature.reversetutor.tools

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 工具统一执行元数据（对齐 spike v1.1 契约）。
 */
data class ToolMetadata(
    val toolName: String,
    val executionTimeMs: Long,
    val version: String = "1.0",
    val attempt: Int = 1
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("tool_name", toolName)
        put("execution_time_ms", executionTimeMs)
        put("version", version)
        put("attempt", attempt)
    }
}

/**
 * 工具统一返回值规范（对齐 spike v1.1 ToolResult）。
 *
 * {
 *   "success": true/false,
 *   "data": {...} | null,
 *   "error": null | "...",
 *   "metadata": { "tool_name", "execution_time_ms", "version", "attempt" }
 * }
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: ToolMetadata
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("success", success)
        if (data != null) {
            put("data", data)
        } else {
            put("data", JSONObject.NULL)
        }
        if (error != null) {
            put("error", error)
        } else {
            put("error", JSONObject.NULL)
        }
        put("metadata", metadata.toJsonObject())
    }

    fun toLlmPayload(): String = toJsonObject().toString()
}

/**
 * 工具调用追踪记录（对齐 spike trace entry）。
 */
data class ToolTraceRecord(
    val tool: String,
    val category: String,
    val input: JSONObject,
    val output: ToolResult,
    val success: Boolean,
    val error: String?,
    val durationMs: Long,
    val attempt: Int,
    var round: Int = 0,
    var callId: String = "",
    var seq: Int = 0,
    var timestamp: String = ""
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("tool", tool)
        put("category", category)
        put("input", input)
        put("output", output.toJsonObject())
        put("success", success)
        if (error != null) put("error", error) else put("error", JSONObject.NULL)
        put("duration_ms", durationMs)
        put("attempt", attempt)
        put("round", round)
        put("call_id", callId)
        put("seq", seq)
        put("timestamp", timestamp)
    }
}

/**
 * 工具执行上下文（传递会话事实、课标知识、资料库等）。
 */
data class ToolContext(
    val session: RTSession,
    val curriculumCatalog: CurriculumCatalog? = null,
    val materialStore: MaterialStore? = null,
    val failInjection: Map<String, String?> = emptyMap(),
    val extra: Map<String, Any> = emptyMap()
)

class ToolExecutionException(message: String, val code: String = "tool_error") : Exception(message)

/**
 * 注册工具抽象定义。
 */
class RegisteredTool(
    val name: String,
    val category: String,
    val description: String,
    val parametersSchema: JSONObject,
    val handler: (args: JSONObject, context: ToolContext) -> JSONObject
) {

    fun toOpenAiSchema(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("parameters", parametersSchema)
    }

    /**
     * 执行工具并包含 1 次失败重试语义与超时守护。
     */
    fun execute(
        rawArgs: JSONObject,
        context: ToolContext,
        maxRetries: Int = 1,
        timeoutSeconds: Long = 5
    ): Pair<ToolResult, List<ToolTraceRecord>> {
        val traceEntries = mutableListOf<ToolTraceRecord>()
        var attempts = 0
        var lastResult: ToolResult? = null

        var currentCtx = context
        while (attempts <= maxRetries) {
            attempts++
            val t0 = System.currentTimeMillis()
            val forcedFail = currentCtx.failInjection[name]

            val result = executeOnce(rawArgs, currentCtx, forcedFail, timeoutSeconds, attempts)
            val dur = System.currentTimeMillis() - t0

            val timedResult = result.copy(
                metadata = result.metadata.copy(executionTimeMs = dur, attempt = attempts)
            )

            traceEntries.add(
                ToolTraceRecord(
                    tool = name,
                    category = category,
                    input = rawArgs,
                    output = timedResult,
                    success = timedResult.success,
                    error = timedResult.error,
                    durationMs = dur,
                    attempt = attempts
                )
            )

            lastResult = timedResult

            if (!timedResult.success && (forcedFail == "once" || forcedFail == "once_timeout")) {
                // once 模式：首轮失败后移除注入，第二轮放行
                val updatedInjection = currentCtx.failInjection.toMutableMap().apply { remove(name) }
                currentCtx = currentCtx.copy(failInjection = updatedInjection)
            }

            if (timedResult.success) {
                break
            }
        }

        return (lastResult ?: createErrorResult("执行未返回结果", attempts, 0)) to traceEntries
    }

    private fun executeOnce(
        rawArgs: JSONObject,
        context: ToolContext,
        forcedFail: String?,
        timeoutSeconds: Long,
        attempt: Int
    ): ToolResult {
        val t0 = System.currentTimeMillis()
        try {
            if (!forcedFail.isNullOrEmpty()) {
                if (forcedFail == "timeout" || forcedFail == "once_timeout") {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(timeoutSeconds + 1).coerceAtMost(6000))
                }
                throw ToolExecutionException("注入的模拟失败: $forcedFail", "injected_failure")
            }

            // 执行参数校验
            validateRequiredParameters(rawArgs)

            // 超时保护
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit(Callable {
                handler(rawArgs, context)
            })

            val data: JSONObject = try {
                future.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                return createErrorResult("工具执行超时（>${timeoutSeconds}s）", attempt, System.currentTimeMillis() - t0)
            } catch (e: Exception) {
                val cause = e.cause ?: e
                if (cause is ToolExecutionException) {
                    return createErrorResult(cause.message ?: "工具异常", attempt, System.currentTimeMillis() - t0)
                }
                return createErrorResult("工具内部异常: ${cause.javaClass.simpleName}: ${cause.message}", attempt, System.currentTimeMillis() - t0)
            } finally {
                executor.shutdownNow()
            }

            // 范围校验
            validateRanges(data, "")

            val dur = System.currentTimeMillis() - t0
            return ToolResult(
                success = true,
                data = data,
                error = null,
                metadata = ToolMetadata(toolName = name, executionTimeMs = dur, attempt = attempt)
            )
        } catch (e: ToolExecutionException) {
            return createErrorResult(e.message ?: "执行失败", attempt, System.currentTimeMillis() - t0)
        } catch (e: IllegalArgumentException) {
            return createErrorResult(e.message ?: "参数校验失败", attempt, System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            return createErrorResult("工具执行框架异常: ${e.javaClass.simpleName}: ${e.message}", attempt, System.currentTimeMillis() - t0)
        }
    }

    private fun validateRequiredParameters(args: JSONObject) {
        val required = parametersSchema.optJSONArray("required") ?: return
        for (i in 0 until required.length()) {
            val key = required.getString(i)
            if (!args.has(key) || args.isNull(key)) {
                throw IllegalArgumentException("参数校验失败: 缺少必填参数 '$key'")
            }
            val v = args.get(key)
            if (v is String && v.trim().isEmpty()) {
                throw IllegalArgumentException("参数校验失败: 参数 '$key' 不能为空字符串")
            }
            if (v is JSONArray && v.length() == 0) {
                throw IllegalArgumentException("参数校验失败: 参数 '$key' 列表不能为空")
            }
        }
    }

    private fun createErrorResult(error: String, attempt: Int, dur: Long): ToolResult =
        ToolResult(
            success = false,
            data = null,
            error = error,
            metadata = ToolMetadata(toolName = name, executionTimeMs = dur, attempt = attempt)
        )

    companion object {
        private val RANGE_RULES: List<Triple<String, Double, Double>> = listOf(
            Triple("overall_level", 1.0, 5.0),
            Triple("level", 1.0, 5.0),
            Triple("urgency_level", 1.0, 5.0),
            Triple("difficulty", 1.0, 5.0),
            Triple("student_level", 1.0, 5.0),
            Triple("real_student_level", 1.0, 5.0),
            Triple("student_capability", 1.0, 5.0),
            Triple("severity_level", 1.0, 5.0),
            Triple("confidence", 0.0, 1.0),
            Triple("effectiveness", 0.0, 1.0),
            Triple("readiness_score", 0.0, 1.0),
            Triple("quality_score", 0.0, 1.0),
            Triple("gain_score", 0.0, 1.0),
            Triple("retention_prediction", 0.0, 1.0),
            Triple("mistake_frequency", 0.0, 1.0),
            Triple("enthusiasm_level", 0.0, 1.0),
            Triple("persistence", 0.0, 1.0),
            Triple("student_fatigue", 0.0, 1.0),
            Triple("learning_progress", 0.0, 1.0),
            Triple("hint_strength", 0.0, 1.0),
            Triple("success_rate", 0.0, 1.0),
            Triple("estimated_difficulty_reduction", 0.0, 1.0)
        )

        fun validateRanges(data: Any?, prefix: String = "") {
            if (data !is JSONObject) return
            for (key in data.keys()) {
                val value = data.get(key)
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (value is JSONObject) {
                    validateRanges(value, path)
                    continue
                }
                if (value is Number) {
                    val dVal = value.toDouble()
                    for ((ruleKey, lo, hi) in RANGE_RULES) {
                        if (key == ruleKey) {
                            if (dVal < lo || dVal > hi) {
                                throw ToolExecutionException(
                                    "返回值越界: $path=$dVal 超出合法范围 [$lo, $hi]",
                                    "range_violation"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
