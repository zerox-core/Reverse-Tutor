package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.memory.InMemoryRTMemoryStore
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchException
import com.reversetutor.feature.reversetutor.retrieval.RTWebSearchProvider
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.SessionStateSink
import com.reversetutor.feature.reversetutor.tools.ToolContext
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 3 首批四工具契约测试（对拍规格源：spike
 * `tests/test_tools_contract.py` + `tests/test_retrieval.py::TestWebSearchTool`）。
 *
 * 覆盖：注册总数 31、各分类计数、四工具最小参数成功、
 * web_search 三态（未配置/provider 成功/provider 失败降级）、
 * 教学策略落库与排序、跨会话历史检索、进度报告回落。
 */
class Stage3ToolsContractTest {

    private fun ctx(
        store: InMemoryRTMemoryStore = InMemoryRTMemoryStore(),
        provider: RTWebSearchProvider? = null,
        session: RTSession = RTSession(sessionId = "sess-test", topic = "二次函数顶点公式")
    ): Pair<ToolContext, InMemoryRTMemoryStore> {
        val extra = mutableMapOf<String, Any>(
            CoreTools.KEY_SESSION_STATE_SINK to SessionStateSink(),
            CoreTools.KEY_MEMORY_STORE to store
        )
        provider?.let { extra[CoreTools.KEY_WEB_SEARCH_PROVIDER] = it }
        return ToolContext(session = session, extra = extra) to store
    }

    private fun registry(): ToolRegistry = ToolRegistry().also { CoreTools.registerAll(it) }

    // ---------------------------------------------------------------- 注册表

    @Test
    fun `注册表为 31 个工具`() {
        assertEquals(31, registry().count())
    }

    @Test
    fun `分类计数与 spike 一致`() {
        // 对齐 spike tests/test_tools_contract.py::EXPECTED_CATEGORIES
        val expected = mapOf(
            "角色扮演" to 6, "认知诊断" to 4, "教学策略" to 5,
            "决策路由" to 4, "记忆管理" to 5, "评估反馈" to 4, "事实检索" to 3
        )
        val actual = registry().allTools().groupBy { it.category }.mapValues { it.value.size }
        assertEquals(expected, actual)
    }

    // ---------------------------------------------------------------- 最小参数

    @Test
    fun `四工具最小参数均成功`() {
        val (context, _) = ctx()
        val reg = registry()
        val minArgs = mapOf(
            "web_search" to JSONObject().put("query", "极值点偏移 典型例题"),
            "save_teaching_pattern" to JSONObject()
                .put("strategy", "几何类比").put("effectiveness", 0.8).put("context", "学生卡在符号"),
            "retrieve_historical_context" to JSONObject().put("query", "配方法 卡住"),
            "generate_progress_report" to JSONObject().put("student_id", "anonymous")
        )
        for ((name, args) in minArgs) {
            val (result, trace) = reg.execute(name, args, context)
            assertTrue("$name 最小参数应成功: ${result.error}", result.success)
            assertEquals(name, result.metadata.toolName)
            assertTrue(trace.isNotEmpty())
            assertTrue(trace.last().success)
        }
    }

    @Test
    fun `缺必填参数应校验失败`() {
        val (context, _) = ctx()
        val reg = registry()
        for ((name, args) in listOf(
            "web_search" to JSONObject(),
            "save_teaching_pattern" to JSONObject().put("strategy", "x"),
            "retrieve_historical_context" to JSONObject()
        )) {
            val (result, _) = reg.execute(name, args, context)
            assertFalse("$name 缺参数应失败", result.success)
            assertTrue(result.error!!.isNotEmpty())
        }
    }

    // ---------------------------------------------------------------- web_search

    @Test
    fun `web_search 未配置 provider 返回 available=false 的事实`() {
        val (context, _) = ctx(provider = null)
        val (result, trace) = registry().execute("web_search", JSONObject().put("query", "q"), context)
        assertTrue("未配置是事实不是失败", result.success)
        assertTrue(trace.first().success)
        val data = result.data as JSONObject
        assertFalse(data.getBoolean("available"))
        assertEquals(0, data.getJSONArray("results").length())
        assertTrue(data.getString("note").contains("未配置"))
    }

    @Test
    fun `web_search provider 成功返回 title snippet url`() {
        val fake = object : RTWebSearchProvider {
            override val providerName = "cmd"
            override fun search(query: String, maxResults: Int): List<JSONObject> =
                listOf(
                    JSONObject()
                        .put("title", "T").put("snippet", "S").put("url", "U")
                )
        }
        val (context, _) = ctx(provider = fake)
        val (result, _) = registry().execute("web_search", JSONObject().put("query", "hello"), context)
        assertTrue(result.success)
        val data = result.data as JSONObject
        assertTrue(data.getBoolean("available"))
        assertEquals("cmd", data.getString("provider"))
        val first = data.getJSONArray("results").getJSONObject(0)
        assertEquals("T", first.getString("title"))
        assertEquals("S", first.getString("snippet"))
        assertEquals("U", first.getString("url"))
    }

    @Test
    fun `web_search provider 失败结构化降级并重试一次`() {
        val boom = object : RTWebSearchProvider {
            override val providerName = "cmd"
            override fun search(query: String, maxResults: Int): List<JSONObject> =
                throw RTWebSearchException("no net")
        }
        val (context, _) = ctx(provider = boom)
        val (result, trace) = registry().execute("web_search", JSONObject().put("query", "q"), context)
        assertFalse("失败须结构化降级回给 LLM", result.success)
        assertEquals("重试 1 次 → 2 条 trace（对齐 spike test_failure_degrades）", 2, trace.size)
    }

    @Test
    fun `web_search max_results 默认 3 并截断`() {
        val fake = object : RTWebSearchProvider {
            override val providerName = "cmd"
            override fun search(query: String, maxResults: Int): List<JSONObject> =
                (1..8).map { i -> JSONObject().put("title", "t$i").put("snippet", "").put("url", "") }
        }
        val (context, _) = ctx(provider = fake)
        val (result, _) = registry().execute("web_search", JSONObject().put("query", "q"), context)
        assertEquals(3, (result.data as JSONObject).getJSONArray("results").length())
    }

    // ---------------------------------------------------------------- 教学策略

    @Test
    fun `save_teaching_pattern 落库并返回复用条件`() {
        val (context, store) = ctx()
        val (result, _) = registry().execute(
            "save_teaching_pattern",
            JSONObject().put("strategy", "几何类比").put("effectiveness", 0.8)
                .put("context", "学生卡在符号"),
            context
        )
        assertTrue(result.success)
        val data = result.data as JSONObject
        assertTrue(data.getString("pattern_id").startsWith("pat_"))
        assertEquals(0.8, data.getDouble("success_rate"), 1e-9)
        val reuse = data.getJSONObject("reuse_conditions")
        assertEquals("学生卡在符号", reuse.getString("when"))
        assertEquals("几何类比", reuse.getString("strategy"))
        assertEquals(1, store.topTeachingPatterns(5).size)
    }

    @Test
    fun `save_teaching_pattern context 缺省时回落默认场景`() {
        val (context, _) = ctx()
        val (result, _) = registry().execute(
            "save_teaching_pattern",
            JSONObject().put("strategy", "s").put("effectiveness", 0.5),
            context
        )
        assertEquals(
            "类似的知识点卡壳场景",
            (result.data as JSONObject).getJSONObject("reuse_conditions").getString("when")
        )
    }

    @Test
    fun `save_teaching_pattern effectiveness 越界被 clamp`() {
        val (context, store) = ctx()
        val (result, _) = registry().execute(
            "save_teaching_pattern",
            JSONObject().put("strategy", "s").put("effectiveness", 1.7),
            context
        )
        assertTrue(result.success)
        assertEquals(1.0, (result.data as JSONObject).getDouble("success_rate"), 1e-9)
        assertEquals(1.0, store.topTeachingPatterns(1)[0].getDouble("effectiveness"), 1e-9)
    }

    @Test
    fun `topTeachingPatterns 按有效性降序`() {
        val store = InMemoryRTMemoryStore()
        store.addTeachingPattern("低", 0.2, "")
        store.addTeachingPattern("高", 0.9, "")
        store.addTeachingPattern("中", 0.5, "")
        assertEquals(listOf("高", "中", "低"), store.topTeachingPatterns(3).map { it.getString("strategy") })
    }

    // ---------------------------------------------------------------- 历史上下文

    @Test
    fun `无历史时 retrieve_historical_context 返回占位洞察`() {
        val (context, _) = ctx()
        val (result, _) = registry().execute(
            "retrieve_historical_context", JSONObject().put("query", "配方法 卡住"), context
        )
        assertTrue(result.success)
        val data = result.data as JSONObject
        assertEquals(0, data.getJSONArray("relevant_sessions").length())
        assertEquals("暂无相关历史记录", data.getJSONArray("key_insights").getString(0))
        assertEquals("这是较早的探索，按新会话处理", data.getString("continuity_suggestions"))
    }

    @Test
    fun `log_breakthrough 与 log_struggle_point 写入长期记忆`() {
        val (context, store) = ctx()
        val reg = registry()
        reg.execute(
            "log_breakthrough",
            JSONObject().put("concept", "配方法").put("before_state", "L2")
                .put("after_state", "L4").put("trigger", "拼正方形类比"),
            context
        )
        reg.execute(
            "log_struggle_point",
            JSONObject().put("topic", "为什么要减去 (b/2)²").put("failed_attempts", 2),
            context
        )
        val all = store.queryEvents(query = "", eventType = null, timeRange = null, limit = 10)
        assertEquals("两条事件均应落长期记忆", 2, all.size)
        assertTrue(all.any { it.optString("event_type") == "breakthrough" })
        assertTrue(all.any { it.optString("event_type") == "struggle" })
    }

    @Test
    fun `跨会话检索历史卡壳并排除本会话`() {
        val store = InMemoryRTMemoryStore()
        val reg = registry()

        // 历史会话：另一次卡在同一主题
        val (past, _) = ctx(
            store = store,
            session = RTSession(sessionId = "sess-past", topic = "配方法")
        )
        reg.execute(
            "log_struggle_point",
            JSONObject().put("topic", "符号规则").put("failed_attempts", 2)
                .put("attempted_strategies", JSONArray().put("背诵")),
            past
        )

        // 当前会话：同一主题再卡一次
        val (now, _) = ctx(store = store, session = RTSession(sessionId = "sess-now", topic = "配方法"))
        val (result, _) = reg.execute(
            "log_struggle_point",
            JSONObject().put("topic", "符号规则").put("failed_attempts", 3),
            now
        )
        assertTrue(result.success)
        val cases = (result.data as JSONObject).getJSONArray("similar_historical_cases")
        assertEquals("应命中历史会话 1 条且排除本会话", 1, cases.length())
        val c = cases.getJSONObject(0)
        assertEquals("sess-past", c.getString("session_id"))
        assertEquals("符号规则", c.getString("concept"))
        assertEquals("背诵", c.getJSONArray("resolved_with").getString(0))
    }

    @Test
    fun `retrieve_historical_context 汇总顿悟洞察与有效策略`() {
        val store = InMemoryRTMemoryStore()
        val (past, _) = ctx(store = store, session = RTSession(sessionId = "sess-past"))
        val reg = registry()
        reg.execute(
            "log_breakthrough",
            JSONObject().put("concept", "配方法").put("before_state", "L2")
                .put("after_state", "L4").put("trigger", "拼正方形类比"),
            past
        )
        store.addTeachingPattern("几何类比", 0.82, "学生卡在符号")

        val (now, _) = ctx(store = store, session = RTSession(sessionId = "sess-now"))
        val (result, _) = reg.execute(
            "retrieve_historical_context", JSONObject().put("query", "配方法"), now
        )
        val data = result.data as JSONObject
        assertEquals(1, data.getJSONArray("relevant_sessions").length())
        val sessionRow = data.getJSONArray("relevant_sessions").getJSONObject(0)
        assertEquals("sess-past", sessionRow.getString("session_id"))
        assertEquals("配方法", sessionRow.getString("summary"))
        assertEquals("拼正方形类比", sessionRow.getJSONArray("key_moments").getString(0))

        val insights = (0 until data.getJSONArray("key_insights").length())
            .map { data.getJSONArray("key_insights").getString(it) }
        assertTrue(insights.any { it.contains("成功突破") })
        assertTrue(insights.any { it.contains("几何类比") && it.contains("0.82") })
        assertEquals("可以延续之前的类比或脚手架方式", data.getString("continuity_suggestions"))
    }

    // ---------------------------------------------------------------- 进度报告

    @Test
    fun `无快照时 generate_progress_report 回落当前会话`() {
        val session = RTSession(
            sessionId = "sess-1",
            studentId = "anonymous",
            topic = "二次函数顶点公式",
            understandingLevel = 3,
            initialUnderstandingLevel = 2,
            masteredConcepts = listOf("配方法")
        )
        val (context, _) = ctx(session = session)
        val (result, _) = registry().execute(
            "generate_progress_report", JSONObject().put("student_id", "anonymous"), context
        )
        assertTrue(result.success)
        val data = result.data as JSONObject
        val report = data.getString("markdown_report")
        assertTrue(report.contains("# 学习报告"))
        assertTrue(report.contains("- 学生：anonymous"))
        assertTrue(report.contains("- 会话数：1"))
        assertTrue(report.contains("- 配方法"))
        val summary = data.getString("summary")
        assertTrue(summary.contains("共 1 次会话"))
        assertTrue(summary.contains("掌握 1 个概念：配方法"))
        assertTrue(summary.contains("理解水平 3 → 3"))
        assertEquals(1, data.getJSONArray("charts").length())
        val chart = data.getJSONArray("charts").getJSONObject(0)
        assertEquals("line", chart.getString("type"))
        assertEquals("理解水平变化", chart.getString("title"))
        assertEquals(3, chart.getJSONObject("data").getJSONArray("values").getInt(0))
    }

    @Test
    fun `include_charts=false 时无图表数据`() {
        val (context, _) = ctx()
        val (result, _) = registry().execute(
            "generate_progress_report",
            JSONObject().put("student_id", "anonymous").put("include_charts", false),
            context
        )
        assertEquals(0, (result.data as JSONObject).getJSONArray("charts").length())
    }

    @Test
    fun `有历史快照时按学生过滤并聚合`() {
        val store = InMemoryRTMemoryStore()
        val reg = registry()
        val snap = JSONObject()
            .put("session_id", "sess-old")
            .put("student_id", "stu-1")
            .put("topic", "配方法")
            .put("status", "closed")
            .put("understanding_level", 4)
            .put("initial_understanding_level", 1)
            .put("mastered_concepts", JSONArray().put("配方法").put("顶点公式"))
            .put("struggle_events", JSONArray().put(JSONObject().put("concept", "符号规则")))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "user"))
                    .put(JSONObject().put("role", "assistant"))
                    .put(JSONObject().put("role", "user"))
            )
            .put("created_at", "2026-10-10T00:00:00+00:00")
        store.saveSnapshot(snap)

        val (context, _) = ctx(store = store)
        val (result, _) = reg.execute(
            "generate_progress_report", JSONObject().put("student_id", "stu-1"), context
        )
        val data = result.data as JSONObject
        val summary = data.getString("summary")
        assertTrue("应聚合 1 次快照", summary.contains("共 1 次会话"))
        assertTrue("2 条 user 消息 → 4 分钟", summary.contains("累计约 4 分钟"))
        assertTrue(summary.contains("掌握 2 个概念"))
        val report = data.getString("markdown_report")
        assertTrue(report.contains("- 符号规则"))

        // 其他学生查不到 → 回落当前会话
        val (other, _) = ctx(store = store)
        val (otherResult, _) = reg.execute(
            "generate_progress_report", JSONObject().put("student_id", "stu-2"), other
        )
        val otherReport = (otherResult.data as JSONObject).getString("markdown_report")
        assertTrue("stu-2 应回落当前会话而非命中 stu-1 快照", otherReport.contains("- 会话数：1"))
        assertTrue(otherReport.contains("- 学生：stu-2"))
        assertFalse("不应带出他人掌握的概念", otherReport.contains("- 顶点公式"))
    }
}
