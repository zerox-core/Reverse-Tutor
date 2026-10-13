package com.reversetutor.feature.reversetutor.tools

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具注册表与调度中心（规格源：spike app/tools/registry.py）。
 *
 * 维护可用工具集、函数调用 schema 导出、失败注入以及执行调度。
 */
class ToolRegistry {

    private val tools: MutableMap<String, RegisteredTool> = ConcurrentHashMap()
    private val failInjection: MutableMap<String, String> = ConcurrentHashMap()
    private val onceConsumed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun register(tool: RegisteredTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): RegisteredTool? = tools[name]

    fun names(): List<String> = tools.keys.sorted()

    fun allTools(): List<RegisteredTool> = tools.keys.sorted().mapNotNull { tools[it] }

    fun schemas(): List<JSONObject> = allTools().map { it.toOpenAiSchema() }

    fun count(): Int = tools.size

    data class SetFailureResult(
        val success: Boolean,
        val tool: String = "",
        val mode: String = "",
        val error: String? = null,
        val targets: List<String> = emptyList()
    )

    fun setFailure(toolName: String, mode: String): SetFailureResult {
        val validModes = setOf("always", "once", "timeout", "once_timeout", "clear")
        if (mode !in validModes) {
            return SetFailureResult(success = false, error = "无效模式: $mode")
        }
        val targets = if (toolName == "*") {
            tools.keys.toList()
        } else if (tools.containsKey(toolName)) {
            listOf(toolName)
        } else {
            return SetFailureResult(success = false, error = "未知工具: $toolName")
        }

        if (mode == "clear") {
            for (t in targets) {
                failInjection.remove(t)
                onceConsumed.remove(t)
            }
            return SetFailureResult(success = true, tool = toolName, mode = "cleared", targets = targets)
        }

        for (t in targets) {
            failInjection[t] = mode
            onceConsumed.remove(t)
        }
        return SetFailureResult(success = true, tool = toolName, mode = mode, targets = targets)
    }

    fun clearFailures(): SetFailureResult {
        failInjection.clear()
        onceConsumed.clear()
        return SetFailureResult(success = true, mode = "cleared")
    }

    fun activeFailures(): Map<String, String> = HashMap(failInjection)

    private fun injectionFor(toolName: String): String? {
        val mode = failInjection[toolName] ?: return null
        if (mode == "once" || mode == "once_timeout") {
            if (onceConsumed.contains(toolName)) {
                return null
            }
            onceConsumed.add(toolName)
            return mode
        }
        return mode
    }

    fun execute(
        name: String,
        rawArgs: JSONObject,
        context: ToolContext
    ): Pair<ToolResult, List<ToolTraceRecord>> {
        val tool = tools[name]
        if (tool == null) {
            val res = ToolResult(
                success = false,
                data = null,
                error = "未知工具: $name",
                metadata = ToolMetadata(toolName = name, executionTimeMs = 0, version = "1.0", attempt = 1)
            )
            val trace = ToolTraceRecord(
                tool = name,
                category = "unknown",
                input = rawArgs,
                output = res,
                success = false,
                error = res.error,
                durationMs = 0,
                attempt = 1
            )
            return res to listOf(trace)
        }

        val mode = injectionFor(name)
        val ctxWithInjection = context.copy(
            failInjection = if (mode != null) mapOf(name to mode) else emptyMap()
        )
        return tool.execute(rawArgs, ctxWithInjection)
    }
}
