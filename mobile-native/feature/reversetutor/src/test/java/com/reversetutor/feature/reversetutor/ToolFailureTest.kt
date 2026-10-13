package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.SessionStateSink
import com.reversetutor.feature.reversetutor.tools.ToolContext
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 工具失败注入与重试语义测试（对拍规格源：spike tests/test_tool_failure.py）。
 *
 * 覆盖：
 * 1. always 注入：重试 1 次仍失败，共 2 次 attempt，返回包含“注入”的错误
 * 2. once 注入：首轮失败，第二轮重试自动放行并成功
 * 3. timeout 注入：模拟超时休眠后失败，发生重试，耗时 > 1 秒，错误包含 timeout
 * 4. clear 注入：清除后立即恢复正常执行
 * 5. 未知工具注入保护：对不存在的工具设注入返回失败
 * 6. 状态防污染：失败工具调用 data 必须为 null
 */
class ToolFailureTest {

    private lateinit var registry: ToolRegistry
    private lateinit var catalog: CurriculumCatalog
    private lateinit var store: MaterialStore

    @Before
    fun setUp() {
        registry = ToolRegistry()
        CoreTools.registerAll(registry)
        val userDir = System.getProperty("user.dir")
        val assets = "$userDir/src/main/assets"
        catalog = CurriculumCatalog(File("$assets/curriculum.json").readText())
        store = MaterialStore()
    }

    private fun ctx(): ToolContext = ToolContext(
        session = RTSession(sessionId = "fail-test"),
        curriculumCatalog = catalog,
        materialStore = store,
        extra = mapOf(CoreTools.KEY_SESSION_STATE_SINK to SessionStateSink())
    )

    private val checkUnderstandingArgs: JSONObject
        get() = JSONObject().apply {
            put("concept", "配方法")
            put("student_explanation", "x²+6x 凑 (x+3)²，因为多加了 9 所以要减回去")
        }

    private val suggestAnalogyArgs: JSONObject
        get() = JSONObject().apply {
            put("abstract_concept", "配方法")
            put("student_background", "初中")
        }

    @Test
    fun alwaysFailWithOneRetry() {
        registry.setFailure("check_understanding", "always")
        try {
            val (result, trace) = registry.execute("check_understanding", checkUnderstandingArgs, ctx())
            assertFalse("always 模式下最终必须返回失败", result.success)
            assertEquals("失败后最多重试 1 次，共 2 次 trace", 2, trace.size)
            assertEquals("trace attempt 应为 [1, 2]", listOf(1, 2), trace.map { it.attempt })
            assertTrue("错误信息应包含注入标识", result.error?.contains("注入") == true)
        } finally {
            registry.clearFailures()
        }
    }

    @Test
    fun onceFailThenRetrySucceeds() {
        registry.setFailure("suggest_analogy", "once")
        try {
            val (result, trace) = registry.execute("suggest_analogy", suggestAnalogyArgs, ctx())
            assertEquals("once 模式下首轮失败次轮成功，共 2 次 trace", 2, trace.size)
            assertFalse("首轮 attempt 应失败", trace[0].success)
            assertTrue("第二轮 attempt 应成功", trace[1].success)
            assertTrue("最终返回结果应成功", result.success)
        } finally {
            registry.clearFailures()
        }
    }

    @Test
    fun timeoutInjection() {
        registry.setFailure("check_understanding", "timeout")
        try {
            val t0 = System.currentTimeMillis()
            val (result, trace) = registry.execute("check_understanding", checkUnderstandingArgs, ctx())
            val elapsed = (System.currentTimeMillis() - t0) / 1000.0

            assertFalse("timeout 注入最终应返回失败", result.success)
            val err = result.error?.lowercase() ?: ""
            assertTrue("错误信息应包含 timeout 或 超时", "timeout" in err || "超时" in err)
            assertEquals("超时后也应重试 1 次，共 2 次 trace", 2, trace.size)
            assertTrue("模拟超时耗时应大于 1 秒，实际 $elapsed 秒", elapsed > 1.0)
        } finally {
            registry.clearFailures()
        }
    }

    @Test
    fun clearFailures() {
        registry.setFailure("suggest_analogy", "always")
        assertEquals("always", registry.activeFailures()["suggest_analogy"])
        registry.clearFailures()
        assertTrue("clear 后活动注入应为空", registry.activeFailures().isEmpty())

        val (result, _) = registry.execute("suggest_analogy", suggestAnalogyArgs, ctx())
        assertTrue("清除注入后工具应正常执行成功", result.success)
    }

    @Test
    fun setFailureUnknownTool() {
        val r = registry.setFailure("no_such_tool", "always")
        assertFalse("对未知工具设置失败注入应返回失败", r.success)
    }

    @Test
    fun failureDoesNotPolluteState() {
        registry.setFailure("check_understanding", "always")
        try {
            val (result, _) = registry.execute("check_understanding", checkUnderstandingArgs, ctx())
            assertFalse(result.success)
            assertNull("失败时 data 必须为 null，不得返回污染的部分数据", result.data)
        } finally {
            registry.clearFailures()
        }
    }
}
