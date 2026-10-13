package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.CoreTools
import com.reversetutor.feature.reversetutor.tools.RegisteredTool
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import com.reversetutor.feature.reversetutor.tools.ToolResult
import com.reversetutor.feature.reversetutor.tools.SessionStateSink
import com.reversetutor.feature.reversetutor.tools.ToolTraceRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 工具契约测试（对拍规格源：spike tests/test_tools_contract.py，裁剪到首批 27 工具）。
 *
 * 覆盖：注册数量与分类、schema 完整性、27 工具最小参数执行、统一返回格式
 * （success/data/error/metadata）、参数缺失校验、未知工具、非知识库领域退化。
 *
 * 与 spike 的差异（均为声明过的移植形态）：
 * - 31 → 27：web_search / save_teaching_pattern / retrieve_historical_context /
 *   generate_progress_report 留批 2；
 * - 分类计数相应为 事实检索2/角色扮演6/认知诊断4/教学策略5/决策路由4/记忆管理3/评估反馈3；
 * - _repair_tool_arguments / _context_compression / SSE 流式属 ToolLoop 层，归阶段 6 测试。
 */
class ToolContractTest {

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

    // ------------------------------------------------------------------
    // helper：对齐 spike ADHOC_CTX —— 每个工具用干净会话与独立 sink，避免用例间顺序耦合
    // ------------------------------------------------------------------
    private fun ctx(): com.reversetutor.feature.reversetutor.tools.ToolContext =
        com.reversetutor.feature.reversetutor.tools.ToolContext(
            session = RTSession(sessionId = "adhoc-test"),
            curriculumCatalog = catalog,
            materialStore = store,
            extra = mapOf(CoreTools.KEY_SESSION_STATE_SINK to SessionStateSink())
        )

    private fun runTool(name: String, args: JSONObject): Pair<ToolResult, List<ToolTraceRecord>> =
        registry.execute(name, args, ctx())

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        for ((k, v) in pairs) put(k, v ?: JSONObject.NULL)
    }

    private fun arr(vararg items: Any?): JSONArray = JSONArray().apply {
        for (i in items) put(i)
    }

    /** spike MIN_ARGS 逐字对拍（去掉 4 个留批工具；其余键值不变）。 */
    private val minArgs: Map<String, JSONObject> = mapOf(
        "generate_student_persona" to obj(
            "topic" to "二次函数顶点公式", "real_student_level" to 2,
            "target_confusion" to "配方法"
        ),
        "lookup_curriculum" to obj("concept" to "极值点偏移"),
        "search_materials" to obj("query" to "对称化构造"),
        "speak_as_confused_student" to obj(
            "confusion_point" to "为什么是 -b/2a", "intensity" to "moderate"
        ),
        "make_deliberate_mistake" to obj(
            "correct_concept" to "顶点公式", "mistake_type" to "sign_error",
            "hint_strength" to 0.5
        ),
        "express_partial_understanding" to obj(
            "understood_parts" to arr("凑平方"), "confused_parts" to arr("为什么减")
        ),
        "simulate_breakthrough" to obj(
            "concept" to "配方法", "trigger" to "拼正方形类比"
        ),
        "challenge_teacher_explanation" to obj(
            "teacher_statement" to "顶点横坐标是 -b/(2a)",
            "challenge_angle" to "boundary_case"
        ),
        "check_understanding" to obj(
            "concept" to "配方法",
            "student_explanation" to "x²+6x 凑 (x+3)²，因为多加了 9 所以要减回去"
        ),
        "detect_misconception" to obj(
            "dialogue_history" to arr("顶点横坐标是 +b/2a"),
            "topic" to "二次函数顶点公式", "error_pattern" to "符号写反"
        ),
        "assess_readiness" to obj(
            "current_mastery" to obj("level" to 2, "mastered" to arr("完全平方式")),
            "next_topic" to "顶点公式"
        ),
        "identify_learning_style" to obj(
            "interaction_log" to arr("我想看图像", "像拼图游戏")
        ),
        "generate_probing_questions" to obj(
            "blind_spots" to arr("为什么减去 (b/2)²"), "difficulty" to 2,
            "max_questions" to 3
        ),
        "create_teaching_ladder" to obj(
            "start_point" to "完全平方式", "end_goal" to "顶点公式", "student_level" to 2
        ),
        "design_counterexample" to obj(
            "student_claim" to "顶点横坐标是 +b/2a", "misconception_type" to "sign_error"
        ),
        "suggest_analogy" to obj(
            "abstract_concept" to "配方法", "student_background" to "初中"
        ),
        "scaffold_problem" to obj(
            "target_problem" to "求 y=x²+6x+1 的顶点", "student_capability" to 2
        ),
        "decide_next_action" to obj(
            "current_progress" to obj("level" to 2, "stuck_rounds" to 2),
            "student_signals" to arr("回答犹豫")
        ),
        "select_teaching_route" to obj(
            "available_routes" to arr("直接讲解", "类比", "图像"),
            "student_state" to obj("level" to 2)
        ),
        "determine_intervention_urgency" to obj(
            "student_emotion" to "frustrated", "error_severity" to 3,
            "time_wasted" to 10, "self_correction_attempts" to 0
        ),
        "plan_session_closure" to obj(
            "session_duration" to 15, "goals_achieved" to arr("配方法"),
            "goals_pending" to arr("顶点"), "student_fatigue" to 0.3
        ),
        "log_breakthrough" to obj(
            "concept" to "配方法", "before_state" to "L2", "after_state" to "L4",
            "trigger" to "类比"
        ),
        "log_struggle_point" to obj(
            "topic" to "为什么要减去 (b/2)²", "failed_attempts" to 2,
            "attempted_strategies" to arr("背诵")
        ),
        "update_knowledge_graph" to obj(
            "mastered_concepts" to arr("配方法"), "pending_concepts" to arr("顶点")
        ),
        "evaluate_session_quality" to obj(
            "initial_goals" to arr("配方法"), "tools_used" to arr("check_understanding")
        ),
        "measure_learning_gain" to obj(
            "pre_assessment" to obj("level" to 2), "post_assessment" to obj("level" to 4),
            "time_spent" to 12.0
        ),
        "detect_teaching_failure" to obj(
            "interaction_log" to arr(
                obj("round" to 1, "level" to 1),
                obj("round" to 2, "level" to 1),
                obj("round" to 3, "level" to 1)
            )
        )
    )

    // ------------------------------------------------------------------
    // 注册数量与分类（对拍 test_registry_counts，31→27、记忆/评估各 -1）
    // ------------------------------------------------------------------
    @Test
    fun registryCounts() {
        assertEquals(27, registry.count())
        val expected = mapOf(
            "事实检索" to 2, "角色扮演" to 6, "认知诊断" to 4,
            "教学策略" to 5, "决策路由" to 4, "记忆管理" to 3, "评估反馈" to 3
        )
        val byCat = HashMap<String, Int>()
        for (t in registry.allTools()) byCat.merge(t.category, 1, Int::plus)
        for ((cat, n) in expected) {
            assertEquals("$cat 应有 $n 个工具", n, byCat[cat] ?: 0)
        }
    }

    @Test
    fun allToolsHaveSchemaAndDescription() {
        for (t in registry.allTools()) {
            val schema: JSONObject = t.toOpenAiSchema()
            assertTrue("${t.name} 缺 name", schema.getString("name").isNotEmpty())
            assertTrue("${t.name} 缺 description", schema.getString("description").isNotEmpty())
            assertTrue("${t.name} 缺 parameters", schema.has("parameters"))
            assertEquals("${t.name} parameters.type 应为 object", "object", schema.getJSONObject("parameters").optString("type"))
        }
    }

    /** 对拍 test_tool_contract：27 工具 × 最小参数 → 统一字段 + trace + success。 */
    @Test
    fun toolContractWithMinArgs() {
        for ((name, args) in minArgs) {
            val (result, trace) = runTool(name, args)
            // 统一返回格式：ToolResult 恒含 success/data/error/metadata 四字段
            assertNotNull(result.metadata)
            assertEquals("metadata.tool_name", name, result.metadata.toolName)
            assertTrue("metadata.execution_time_ms 应 >= 0", result.metadata.executionTimeMs >= 0)
            assertTrue("metadata.version 应存在", result.metadata.version.isNotEmpty())
            assertTrue("trace 应非空", trace.isNotEmpty())
            val entry = trace.last()
            assertEquals("trace 末条工具名", name, entry.tool)
            assertTrue("trace.duration_ms 应 >= 0", entry.durationMs >= 0)
            assertTrue("trace.attempt 应 >= 1", entry.attempt >= 1)
            assertTrue("$name 最小参数应成功（error=${result.error}）", result.success)
            assertNotNull("$name 成功时 data 不应为 null", result.data)
        }
    }

    /** 对拍 test_param_validation_rejects_missing：缺必填参数 → success=false 且 error 非空。 */
    @Test
    fun paramValidationRejectsMissing() {
        val cases = listOf(
            "check_understanding" to "concept",
            "make_deliberate_mistake" to "correct_concept",
            "log_breakthrough" to "concept",
            "measure_learning_gain" to "pre_assessment",
            "detect_misconception" to "topic",
            "create_teaching_ladder" to "end_goal"
        )
        for ((name, missing) in cases) {
            val args = JSONObject(minArgs.getValue(name).toString())
            args.remove(missing)
            val (result, _) = runTool(name, args)
            assertTrue("$name 缺参数 $missing 应校验失败", !result.success)
            assertTrue("$name 缺参数 $missing 应有 error", !result.error.isNullOrBlank())
        }
    }

    /** 对拍 test_unknown_tool。 */
    @Test
    fun unknownTool() {
        val (result, _) = runTool("no_such_tool", obj())
        assertTrue(!result.success)
        assertTrue("未知工具" in (result.error ?: ""))
    }

    /** 对拍 test_check_understanding_generic_domain：魔方域不再恒 L1。 */
    @Test
    fun checkUnderstandingGenericDomain() {
        // 实质性解释：概念复述 + 定义标记 + 因果 + 通用步骤 → L3+
        val (r1, _) = runTool(
            "check_understanding",
            obj(
                "concept" to "白色十字的朝向与位置规则",
                "student_explanation" to
                    ("白色十字就是先把白色棱块找出来，然后把棱块转到黄色中心的对面，" +
                        "再对齐侧面中心颜色，因为要同色，所以最后一步才拧到底层")
            )
        )
        assertTrue("实质性解释应成功：${r1.error}", r1.success)
        val d1 = r1.data as JSONObject
        assertTrue("实质性解释应得 L3+，实际 overall_level=${d1.optInt("overall_level")}", d1.optInt("overall_level") >= 3)
        assertEquals("白色十字的朝向与位置规则", d1.optString("concept"))

        // 无实质内容 + hedge → 仍应低分（hedge 不加分不扣分）
        val (r2, _) = runTool(
            "check_understanding",
            obj("concept" to "魔方复原", "student_explanation" to "好像就是转来转去吧")
        )
        assertTrue(r2.success)
        assertTrue(
            "空泛 hedge 解释应 ≤2，实际 ${((r2.data as JSONObject).optInt("overall_level"))}",
            (r2.data as JSONObject).optInt("overall_level") <= 2
        )

        // 数学已知概念路径不受影响（回归）
        val (r3, _) = runTool(
            "check_understanding",
            obj(
                "concept" to "二次函数顶点公式",
                "student_explanation" to "顶点横坐标是 -b/2a，因为 x=-b/2a 时代入得到最值"
            )
        )
        assertTrue(r3.success)
        assertTrue(((r3.data as JSONObject).optInt("overall_level")) >= 1)
    }

    /** schema 引用一致性：minArgs 必须恰好覆盖全部 27 个注册工具（防止漏测）。 */
    @Test
    fun minArgsCoverAllRegisteredTools() {
        val registered: List<RegisteredTool> = registry.allTools()
        assertEquals(
            "MIN_ARGS 应与注册表一一对应",
            registered.map { it.name }.sorted(),
            minArgs.keys.sorted()
        )
    }
}
