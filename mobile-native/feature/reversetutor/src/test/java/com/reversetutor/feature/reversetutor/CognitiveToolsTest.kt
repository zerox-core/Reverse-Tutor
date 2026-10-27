package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.prompt.CognitiveScaffolds
import com.reversetutor.feature.reversetutor.tools.CognitiveTools
import com.reversetutor.feature.reversetutor.tools.ToolContext
import com.reversetutor.feature.reversetutor.tools.ToolRegistry
import com.reversetutor.feature.reversetutor.session.RTSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Type 1 认知能力工具的契约测试。
 *
 * 这三个工具与学科无关：输入是「讲解的形式结构」，输出是确定性判定。
 * 因此测试里刻意混用数学、英语、编程三种题材，验证同一套判定对任何学科都成立。
 */
class CognitiveToolsTest {

    private fun registry(): ToolRegistry = ToolRegistry().also { CognitiveTools.registerAll(it) }

    private fun ctx() = ToolContext(session = RTSession(sessionId = "cognitive-test"))

    private fun run(name: String, args: JSONObject): JSONObject {
        val tool = registry().get(name) ?: error("未注册: $name")
        val (result, _) = tool.execute(args, ctx())
        assertTrue("$name 执行失败: ${result.error}", result.success)
        return result.data as JSONObject
    }

    // ==================================================================
    // locate_missing_warrant
    // ==================================================================

    @Test
    fun warrantDetectorFlagsBareObviouslyWithoutJustification() {
        val data = run(
            "locate_missing_warrant",
            JSONObject().put(
                "explanation",
                "把直线设成 y = kx + m。显然它和椭圆一定有两个交点。" +
                    "所以判别式大于零。"
            )
        )

        assertTrue("应至少命中一处可疑", data.getInt("suspicious_total") >= 1)
        val gaps = data.getJSONArray("warrant_gaps")
        val first = gaps.getJSONObject(0)
        assertEquals("显然", first.getString("marker"))
        assertFalse("该处没有论证展开", first.getBoolean("has_intervening_justification"))
        assertEquals("high", first.getString("severity"))
        assertTrue("应给出被跳过的步骤说明", first.getString("skipped_step").isNotBlank())
    }

    @Test
    fun warrantDetectorAcceptsMarkerFollowedByRealJustification() {
        // 「因为…所以…」展开在同句里，标记词只是行文习惯，不该判为跳步。
        val data = run(
            "locate_missing_warrant",
            JSONObject().put(
                "explanation",
                "显然，因为判别式恒大于零，所以这条直线必然与椭圆交于两点。"
            )
        )

        assertEquals(0, data.getInt("suspicious_total"))
        assertTrue(data.getInt("marker_total") >= 1)
        assertTrue(
            "应说明这是行文习惯而非跳步",
            data.getString("verdict").contains("行文习惯")
        )
    }

    @Test
    fun warrantDetectorIsSubjectAgnostic() {
        // 英语题材 + 编程题材：同一套形式检测必须同样有效。
        val english = run(
            "locate_missing_warrant",
            JSONObject().put(
                "explanation",
                "这个 that 引导的是定语从句。同理，后面那个 which 也是定语从句。"
            )
        )
        val programming = run(
            "locate_missing_warrant",
            JSONObject().put(
                "explanation",
                "这里加锁就安全了。同理可得，所有共享变量都这样处理即可。"
            )
        )

        assertTrue(english.getInt("suspicious_total") >= 1)
        assertTrue(programming.getInt("suspicious_total") >= 1)
    }

    @Test
    fun warrantDetectorReturnsCleanWhenNoMarkerPresent() {
        val data = run(
            "locate_missing_warrant",
            JSONObject().put(
                "explanation",
                "因为直线过定点 P，代入后得到关于 k 的二次方程。" +
                    "由于判别式大于零，k 的取值范围是全体实数。"
            )
        )

        assertEquals(0, data.getInt("marker_total"))
        assertTrue(data.getString("verdict").contains("未发现"))
    }

    // ==================================================================
    // detect_fluency_illusion
    // ==================================================================

    @Test
    fun fluencyRiskIsHighWhenTeacherOnlyAcknowledges() {
        val turns = JSONArray().apply {
            put(turn("teacher", "嗯"))
            put(turn("student", "那这一步为什么要把 k 替换掉？"))
            put(turn("teacher", "对"))
            put(turn("student", "所以你的意思是先联立再讨论？"))
            put(turn("teacher", "好的"))
        }

        val data = run(
            "detect_fluency_illusion",
            JSONObject().put("recent_turns", turns)
        )

        assertEquals(3, data.getInt("teacher_turn_count"))
        assertEquals(3, data.getInt("acknowledgment_only_turns"))
        assertEquals(0, data.getInt("self_reasoning_turns"))
        assertTrue(
            "纯应答应触发高风险",
            data.getDouble("fluency_illusion_risk") >= 0.65
        )
        assertEquals("high", data.getString("risk_level"))
    }

    @Test
    fun fluencyRiskIsLowWhenTeacherProducesOwnReasoning() {
        val turns = JSONArray().apply {
            put(
                turn(
                    "teacher",
                    "因为直线过点 P，所以我设 y = k(x-4)，代进椭圆后得到关于 x 的二次方程，" +
                        "于是可以用韦达定理求两根之和。"
                )
            )
            put(turn("student", "那斜率不存在呢？"))
            put(
                turn(
                    "teacher",
                    "所以需要单独讨论：当直线垂直于 x 轴时，斜率不存在，此时方程要写成 x = 4，" +
                        "代入后得到两个对称的交点。"
                )
            )
        }

        val data = run(
            "detect_fluency_illusion",
            JSONObject().put("recent_turns", turns)
        )

        assertEquals(0, data.getInt("acknowledgment_only_turns"))
        assertEquals(2, data.getInt("self_reasoning_turns"))
        assertTrue(
            "持续产出推理应判低风险",
            data.getDouble("fluency_illusion_risk") < 0.35
        )
        assertEquals("low", data.getString("risk_level"))
    }

    @Test
    fun fluencyDetectorExplainsItsOwnLimits() {
        // 高风险只意味着「他没说话」，不等于「他没懂」——工具必须自己声明这一点，
        // 否则模型会把它当成理解度判定直接使用。
        val turns = JSONArray().apply { put(turn("teacher", "嗯")) }
        val data = run("detect_fluency_illusion", JSONObject().put("recent_turns", turns))

        assertTrue(data.getString("note").contains("不等于"))
    }

    // ==================================================================
    // build_argument_graph
    // ==================================================================

    @Test
    fun argumentGraphFindsDanglingAssertion() {
        val claims = JSONArray().apply {
            put(claim("c1", "椭圆方程已给定", emptyList(), isEvidence = true))
            put(claim("c2", "设直线为 y = k(x-4)", listOf("c1")))
            put(claim("c3", "所以 x0 的取值范围是全体实数", emptyList()))
        }

        val data = run("build_argument_graph", JSONObject().put("claims", claims))

        val dangling = data.getJSONArray("dangling_claims")
        assertEquals(1, dangling.length())
        assertEquals("c3", dangling.getJSONObject(0).getString("id"))
        assertTrue(data.getString("verdict").contains("悬空断言"))
    }

    @Test
    fun argumentGraphDetectsCircularReasoning() {
        val claims = JSONArray().apply {
            put(claim("a", "因为 B 成立，所以 A 成立", listOf("b")))
            put(claim("b", "因为 A 成立，所以 B 成立", listOf("a")))
        }

        val data = run("build_argument_graph", JSONObject().put("claims", claims))

        val cycles = data.getJSONArray("cycles")
        assertTrue("应检出循环论证", cycles.length() >= 1)
        assertTrue(data.getString("verdict").contains("循环论证"))
    }

    @Test
    fun argumentGraphReportsCompleteChain() {
        val claims = JSONArray().apply {
            put(claim("e1", "已知椭圆方程", emptyList(), isEvidence = true))
            put(claim("d1", "联立得二次方程", listOf("e1")))
            put(claim("d2", "由韦达定理得两根之和", listOf("d1")))
            put(claim("d3", "算出 x0 表达式", listOf("d2")))
        }

        val data = run("build_argument_graph", JSONObject().put("claims", claims))

        assertEquals(0, data.getJSONArray("dangling_claims").length())
        assertEquals(0, data.getJSONArray("cycles").length())
        assertEquals(0, data.getJSONArray("unreached_from_evidence").length())
        assertEquals(3, data.getInt("max_chain_depth"))
        assertTrue(data.getString("verdict").contains("结构上完整"))
    }

    @Test
    fun argumentGraphReportsBrokenPremiseReference() {
        val claims = JSONArray().apply {
            put(claim("x", "结论", listOf("不存在的节点")))
        }

        val data = run("build_argument_graph", JSONObject().put("claims", claims))

        val broken = data.getJSONArray("broken_references")
        assertEquals(1, broken.length())
        assertEquals("不存在的节点", broken.getJSONObject(0).getString("missing_premise"))
    }

    // ==================================================================
    // 骨架清单本身
    // ==================================================================

    @Test
    fun scaffoldMenuCoversEveryFamilyAndIsNonEmpty() {
        assertEquals(40, CognitiveScaffolds.count())
        CognitiveScaffolds.Family.entries.forEach { family ->
            assertTrue(
                "${family.id} 家族不应为空",
                CognitiveScaffolds.countByFamily(family) > 0
            )
        }
        val menu = CognitiveScaffolds.renderMenu()
        CognitiveScaffolds.all.forEach { s ->
            assertTrue("菜单应包含 ${s.id}", menu.contains(s.id))
            assertTrue("${s.id} 应有触发条件", s.trigger.isNotBlank())
            assertTrue("${s.id} 应有槽位", s.slots.isNotEmpty())
            assertTrue("${s.id} 应有理论出处", s.source.isNotBlank())
        }
    }

    @Test
    fun scaffoldIdsAreUnique() {
        val ids = CognitiveScaffolds.all.map { it.id }
        assertEquals("骨架 id 不得重复", ids.size, ids.toSet().size)
    }

    @Test
    fun outputContractRequiresStructuredVerdict() {
        val contract = CognitiveScaffolds.OUTPUT_CONTRACT
        assertTrue(contract.contains("<pedagogy_eval>"))
        assertTrue(contract.contains("选:"))
        assertTrue(contract.contains("判定:"))
        assertTrue(contract.contains("动作:"))
        // 允许「本轮无认知阻力」，否则模型会被迫硬凑一条骨架
        assertTrue(contract.contains("无"))
        // 严重度必须有定义，否则「排序」是空话
        assertTrue(contract.contains("blocking"))
        assertTrue(contract.contains("significant"))
        assertTrue(contract.contains("minor"))
        // 不设条数上限：初版的「1~3 条」已删，防止回归
        assertFalse("不得再出现硬条数上限", contract.contains("1~3"))
        assertTrue("必须说明不限条数", contract.contains("所有"))
    }

    // ==================================================================
    // 判定块度量（选几条不再是凭感觉定的，而是可统计的）
    // ==================================================================

    @Test
    fun measureCountsSelectionAndSeverity() {
        val block = """
            <pedagogy_eval>
            选: F1.degeneracy,F2.felicity,F5.step
            判定:
            - [blocking] F1.degeneracy 退化条件：斜率不存在；退化后是否仍成立：不成立，中垂线变成 x 轴本身；是否需要单独讨论：必须。
            - [significant] F2.felicity 声称动作：用韦达定理；恰当性条件：Δ>0 且二次项系数非零；未满足：Δ 从未验证。
            - [minor] F5.step 这一步跨度：从 k 的范围直接到 x0 的范围；是否超出可承载范围：是。
            动作: 只问垂线情形。
            </pedagogy_eval>
        """.trimIndent()

        val m = CognitiveScaffolds.measure(block)
        assertEquals(3, m.selectedCount)
        assertEquals(1, m.blocking)
        assertEquals(1, m.significant)
        assertEquals(1, m.minor)
        assertFalse(m.isEmpty)
        assertFalse(m.isSingleScaffold)
        assertFalse("槽位填得够实", m.looksShallow())
        assertFalse("严重度分布合理", m.severityIsDegenerate())
    }

    @Test
    fun measureFlagsShallowSlotFilling() {
        // 选了 3 条却几乎不填槽位：这是「为了格式硬凑」的典型形态
        val block = """
            <pedagogy_eval>
            选: F1.degeneracy,F2.felicity,F5.step
            判定: [blocking] 有问题 [significant] 也有问题 [minor] 小问题
            动作: 随便问一句。
            </pedagogy_eval>
        """.trimIndent()

        val m = CognitiveScaffolds.measure(block)
        assertEquals(3, m.selectedCount)
        assertTrue(
            "每骨架仅 ${"%.1f".format(m.charsPerScaffold)} 字，应低于阈值 ${CognitiveScaffolds.SHALLOW_THRESHOLD}",
            m.looksShallow()
        )
    }

    @Test
    fun shallowThresholdIsCalibratedForChinese() {
        // 阈值必须能区分「中文实填」与「敷衍」。用真实长度的实填样本反推，
        // 防止有人照英文习惯把它调回 40 导致中文实填被误判。
        val honest = """
            <pedagogy_eval>
            选: F1.degeneracy,F2.felicity,F5.step
            判定:
            - [blocking] F1.degeneracy 退化条件：斜率不存在；退化后是否仍成立：不成立，中垂线变成 x 轴本身；是否需要单独讨论：必须。
            - [significant] F2.felicity 声称动作：用韦达定理；恰当性条件：Δ>0 且二次项系数非零；未满足：Δ 从未验证。
            - [minor] F5.step 这一步跨度：从 k 的范围直接到 x0 的范围；是否超出可承载范围：是。
            动作: 只问垂线情形。
            </pedagogy_eval>
        """.trimIndent()
        val lazyOne = """
            <pedagogy_eval>
            选: F1.degeneracy,F2.felicity,F5.step
            判定: [blocking] 有问题 [significant] 也有问题 [minor] 小问题
            动作: 随便问一句。
            </pedagogy_eval>
        """.trimIndent()

        val honestPerScaffold = CognitiveScaffolds.measure(honest).charsPerScaffold
        val lazyPerScaffold = CognitiveScaffolds.measure(lazyOne).charsPerScaffold

        assertTrue(
            "实填(${"%.1f".format(honestPerScaffold)}) 必须高于阈值",
            honestPerScaffold > CognitiveScaffolds.SHALLOW_THRESHOLD
        )
        assertTrue(
            "敷衍(${"%.1f".format(lazyPerScaffold)}) 必须低于阈值",
            lazyPerScaffold < CognitiveScaffolds.SHALLOW_THRESHOLD
        )
    }

    @Test
    fun measureFlagsDegenerateSeverityDistribution() {
        // 选了很多条却全标 minor：说明它不敢判定谁更严重，排序失效
        val block = """
            <pedagogy_eval>
            选: F1.degeneracy,F2.felicity,F5.step
            判定:
            - [minor] F1.degeneracy 斜率不存在这件事好像有点小问题，也许是表达不清导致的吧
            - [minor] F2.felicity 韦达定理那里可能大概是小瑕疵，不影响大局
            - [minor] F5.step 这一步跨度也许稍微大了一点点，问题不大
            动作: 继续追问。
            </pedagogy_eval>
        """.trimIndent()

        val m = CognitiveScaffolds.measure(block)
        assertTrue("全 minor 应被标记为退化分布", m.severityIsDegenerate())
    }

    @Test
    fun measureHandlesEmptySelection() {
        val block = """
            <pedagogy_eval>
            选: 无
            判定: 老师这一轮的说法已经完整，找不到可攻击的形式缺陷。
            动作: 表示听懂了，请他继续下一步。
            </pedagogy_eval>
        """.trimIndent()

        val m = CognitiveScaffolds.measure(block)
        assertTrue(m.isEmpty)
        assertEquals(0, m.selectedCount)
        // 「无」不该被当成敷衍
        assertFalse(m.looksShallow())
        assertFalse(m.severityIsDegenerate())
    }

    @Test
    fun measureDetectsSingleScaffoldHabit() {
        val block = """
            <pedagogy_eval>
            选: F2.warrant
            判定: - [blocking] F2.warrant 被跳过的中间命题：凭什么 Δ 大于零；该命题是否成立：未验证。
            动作: 问他要条件。
            </pedagogy_eval>
        """.trimIndent()

        val m = CognitiveScaffolds.measure(block)
        assertTrue(m.isSingleScaffold)
        assertEquals(1, m.selectedCount)
    }

    // ---- helper ----

    private fun turn(role: String, text: String): JSONObject = JSONObject().apply {
        put("role", role)
        put("text", text)
    }

    private fun claim(
        id: String,
        text: String,
        premises: List<String>,
        isEvidence: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("premises", JSONArray(premises))
        put("is_evidence", isEvidence)
    }
}
