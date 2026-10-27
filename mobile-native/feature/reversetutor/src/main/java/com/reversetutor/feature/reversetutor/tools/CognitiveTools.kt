package com.reversetutor.feature.reversetutor.tools

import org.json.JSONArray
import org.json.JSONObject

/**
 * Type 1 · 确定性能力工具（认知骨架的可执行部分）。
 *
 * ## 与 [CoreTools] 的分工
 *
 * [CoreTools] 的 31 个工具是「学科知识层」工具（课标、资料检索、角色台词、诊断评分的
 * 词表匹配）。本文件是「推理形式层」工具——它不关心是数学还是英语，只检查
 * **讲解的形式结构是否成立**。
 *
 * ## 为什么只有 3 个
 *
 * 2026-10-27 对照评估：新提出的 40 个认知算子里，有 6 个与 [CoreTools] 现有工具
 * 完全同构（如 `search_course_material` == `search_materials`），已合并去重、不新建；
 * 其余 30+ 个本质是「有强制结构的推理模板」，属于 Type 2（见
 * `prompt/CognitiveScaffolds.kt`），硬写成规则算法只会比模型自己判断更差。
 *
 * 只有这 3 个同时满足：结果不依赖语言理解、可写单测断言、换模型调用结果不变。
 */
object CognitiveTools {

    // ------------------------------------------------------------------
    // 词表（全部为形式标记，不含任何学科内容）
    // ------------------------------------------------------------------

    /**
     * 「跳过论证」的常见标记词。
     *
     * 这些词本身不是错误——它们在**结论已经建立之后**使用是恰当的。它们可疑，
     * 只因为说话人在**结论尚未建立时**用它们替代了论证过程。
     */
    private val WARRANT_SKIP_MARKERS = listOf(
        "显然", "显而易见", "同理", "同理可证", "同理可得", "易证", "易得",
        "容易看出", "容易验证", "不难发现", "不难看出", "直接可得", "直接代入",
        "顾名思义", "众所周知", "无需证明", "不证自明", "由此可知", "综上所述",
        "照此类推", "依次类推"
    )

    /**
     * 「确实做了论证」的连接词。在跳过标记的同一句或紧邻前一句里出现，
     * 说明论证其实展开了，标记只是行文习惯。
     */
    private val JUSTIFICATION_CONNECTIVES = listOf(
        "因为", "由于", "所以", "因此", "于是", "从而", "由此得到", "推出",
        "代入", "展开", "化简", "整理得", "根据", "按照", "由定义", "依定义",
        "只需说明", "可验证", "计算得", "两边", "移项", "合并"
    )

    /** 纯应答词（去掉标点后整句仅由它们组成，即视为「没有自主产出」）。 */
    private val ACKNOWLEDGMENT_WORDS = listOf(
        "嗯", "嗯嗯", "对", "对的", "是", "是的", "好", "好的", "行", "可以",
        "明白", "明白了", "懂了", "理解", "理解了", "没错", "有道理", "确实",
        "知道了", "收到", "ok", "okay", "收到明白", "对就是这样", "就是这样",
        "继续", "然后呢", "接着", "说下去"
    )

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    fun registerAll(registry: ToolRegistry) {
        registry.register(
            RegisteredTool(
                "locate_missing_warrant", "论证检验",
                "定位讲解中被「显然/同理/易证/直接可得」等标记掩盖掉的那一步论证：" +
                    "返回命中的标记、它所在句子、被跳过的中间命题，以及该标记是否属于行文习惯。" +
                    "纯形式检测，不判断学科内容对错——是否真的成立由你结合上下文决定。",
                schema(
                    listOf("explanation"),
                    "explanation" to sProp("老师的讲解原文（可多段）"),
                    "markers" to arrProp("自定义跳过标记词（可选；缺省用内置词表）")
                )
            ) { args, _ -> locateMissingWarrant(args) }
        )
        registry.register(
            RegisteredTool(
                "detect_fluency_illusion", "论证检验",
                "检测「听懂了但没学会」的流畅错觉：统计老师最近若干轮里自己产出的推理量、" +
                    "纯应答轮数、以及讲解长度趋势，给出 0~1 的风险值与触发信号。" +
                    "纯计数统计，不含任何语义判断。",
                schema(
                    listOf("recent_turns"),
                    "recent_turns" to arrProp("近期对话，每条含 role（teacher/student）与 text", "object"),
                    "window" to iProp("只看最近几轮老师发言", 1, 20)
                )
            ) { args, _ -> detectFluencyIllusion(args) }
        )
        registry.register(
            RegisteredTool(
                "build_argument_graph", "论证检验",
                "把一段论证建成有向图并做确定性结构分析：查出无出处支撑的悬空断言、" +
                    "循环论证、以及论证深度分布。断言与其前提由你从讲解中抽取后传入，" +
                    "图算法部分（可达性/环检测/深度）由本工具确定计算。",
                schema(
                    listOf("claims"),
                    "claims" to arrProp(
                        "断言节点数组，每项 { id: 唯一标识, text: 断言原文, " +
                            "premises: [它直接依赖的其它 id], is_evidence: 是否属于外部事实/已知条件 }",
                        "object"
                    )
                )
            ) { args, _ -> buildArgumentGraph(args) }
        )
    }

    // ==================================================================
    // 1. locate_missing_warrant
    // ==================================================================

    private fun locateMissingWarrant(args: JSONObject): JSONObject {
        val explanation = str(args, "explanation")
        require(explanation.isNotBlank()) { "explanation 不能为空" }
        val markers = strList(args, "markers").ifEmpty { WARRANT_SKIP_MARKERS }

        val sentences = splitSentences(explanation)
        val hits = JSONArray()
        val gaps = JSONArray()
        var suspicious = 0

        sentences.forEachIndexed { index, sentence ->
            val matched = markers.filter { it in sentence }
            if (matched.isEmpty()) return@forEachIndexed

            // 本句或紧邻前一句里是否真的展开了论证
            val ownJustified = JUSTIFICATION_CONNECTIVES.any { it in sentence }
            val prevJustified = index > 0 &&
                JUSTIFICATION_CONNECTIVES.any { it in sentences[index - 1] }
            val hasIntervening = ownJustified || prevJustified

            hits.put(
                JSONObject().apply {
                    put("marker", matched.first())
                    put("all_markers", JSONArray(matched))
                    put("sentence_index", index)
                    put("sentence", sentence)
                    put("has_intervening_justification", hasIntervening)
                }
            )

            if (!hasIntervening) {
                suspicious++
                gaps.put(
                    JSONObject().apply {
                        put("marker", matched.first())
                        put("asserted_sentence", sentence)
                        put("preceding_sentence", if (index > 0) sentences[index - 1] else "")
                        put("has_intervening_justification", false)
                        put(
                            "skipped_step",
                            "「${matched.first()}」跳过了从上一句到这一句之间的推导。" +
                                "需要补出的是：这个结论凭什么从上一步成立。"
                        )
                        put("severity", if (matched.first() in STRONG_SKIP_MARKERS) "high" else "medium")
                    }
                )
            }
        }

        return out(
            "marker_total" to hits.length(),
            "suspicious_total" to suspicious,
            "markers_found" to hits,
            "warrant_gaps" to gaps,
            "verdict" to when {
                hits.length() == 0 -> "未发现跳过论证的标记词——但这不等于论证完整，只表示没用到这类词。"
                suspicious == 0 -> "命中了标记词，但每处都能在同句或紧邻前句找到论证连接词，属行文习惯。"
                else -> "共 ${suspicious} 处标记词未伴随任何论证展开，这些位置是最可能被跳过的环节。"
            },
            "note" to "以上为形式层面的检测结果；被跳过的这一步是否真的成立，需要你结合学科内容自行判断。"
        )
    }

    /** 语气最强、最常掩盖真实漏洞的几个标记，命中即判 high。 */
    private val STRONG_SKIP_MARKERS = setOf(
        "显然", "显而易见", "同理", "同理可证", "易证", "不证自明", "众所周知", "无需证明"
    )

    /**
     * 中英文混排的句子切分。
     *
     * 只保留非空句；保留标点以便回读上下文（不去标点，因为标记词本身可能紧邻标点）。
     */
    private fun splitSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in text) {
            buf.append(ch)
            if (ch in SENTENCE_ENDINGS) {
                val s = buf.toString().trim()
                if (s.isNotEmpty()) out.add(s)
                buf.setLength(0)
            }
        }
        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private const val SENTENCE_ENDINGS = "。！？；!?;\n"

    // ==================================================================
    // 2. detect_fluency_illusion
    // ==================================================================

    private fun detectFluencyIllusion(args: JSONObject): JSONObject {
        val turns = objList(args, "recent_turns")
        require(turns.isNotEmpty()) { "recent_turns 不能为空" }
        val window = intArg(args, "window", 6).coerceIn(1, 20)

        val teacherTurns = turns
            .filter { str(it, "role").lowercase().let { r -> r == "teacher" || r == "user" } }
            .takeLast(window)

        if (teacherTurns.isEmpty()) {
            return out(
                "teacher_turn_count" to 0,
                "fluency_illusion_risk" to 0.0,
                "signals" to jarr(listOf("窗口内没有老师发言，无法评估")),
                "note" to "无可评估样本。"
            )
        }

        val stats = teacherTurns.map { t ->
            val text = str(t, "text")
            val normalized = text.replace(Regex("[\\s，。！？、；：,.!?;:~～…]"), "")
            val isAckOnly = normalized.isNotEmpty() && (
                normalized in ACKNOWLEDGMENT_WORDS ||
                    (normalized.length <= 6 && ACKNOWLEDGMENT_WORDS.any { normalized.contains(it) })
                )
            val hasReasoning = JUSTIFICATION_CONNECTIVES.any { it in text }
            TurnStat(
                length = normalized.length,
                isAckOnly = isAckOnly,
                hasReasoning = hasReasoning
            )
        }

        val ackOnly = stats.count { it.isAckOnly }
        val reasoning = stats.count { it.hasReasoning }
        val totalChars = stats.sumOf { it.length }
        val meanLength = totalChars.toDouble() / stats.size

        // 确定性打分：应答占比为主，长度趋势与推理缺失为辅。
        val ackRatio = ackOnly.toDouble() / stats.size
        val reasoningRatio = reasoning.toDouble() / stats.size
        val shortPenalty = when {
            meanLength < 8 -> 0.30
            meanLength < 20 -> 0.15
            else -> 0.0
        }
        val risk = ((ackRatio * 0.55) + ((1.0 - reasoningRatio) * 0.30) + shortPenalty)
            .coerceIn(0.0, 1.0)

        val signals = mutableListOf<String>()
        if (ackOnly > 0) {
            signals.add("窗口内 $ackOnly/${stats.size} 轮老师只说应答词，未产出任何推理内容")
        }
        if (reasoning == 0) {
            signals.add("窗口内老师没有使用过任何因果/推导连接词——全程在接收，没有在讲解")
        } else if (reasoningRatio < 0.4) {
            signals.add("只有 $reasoning/${stats.size} 轮包含因果/推导连接词，推进主要靠学生单方面输出")
        }
        if (meanLength < 8) {
            signals.add("平均每轮仅 ${"%.1f".format(meanLength)} 字，信息量不足以判断是否真的理解")
        }
        if (signals.isEmpty()) {
            signals.add("窗口内老师有持续的自有推理产出，暂未观察到流畅错觉信号")
        }

        return out(
            "teacher_turn_count" to stats.size,
            "acknowledgment_only_turns" to ackOnly,
            "self_reasoning_turns" to reasoning,
            "mean_production_chars" to round2(meanLength),
            "fluency_illusion_risk" to round2(risk),
            "risk_level" to when {
                risk >= 0.65 -> "high"
                risk >= 0.35 -> "medium"
                else -> "low"
            },
            "signals" to jarr(signals),
            "note" to "风险值只反映「话轮产出结构」，不反映理解质量本身；" +
                "高风险意味着「他只是没说话」，不等于「他没懂」，需要你自己追问确认。"
        )
    }

    private data class TurnStat(val length: Int, val isAckOnly: Boolean, val hasReasoning: Boolean)

    // ==================================================================
    // 3. build_argument_graph
    // ==================================================================

    private fun buildArgumentGraph(args: JSONObject): JSONObject {
        val raw = objList(args, "claims")
        require(raw.isNotEmpty()) { "claims 不能为空" }

        val nodes = raw.mapIndexedNotNull { index, o ->
            val id = str(o, "id").ifBlank { "c$index" }
            val text = str(o, "text")
            if (text.isBlank()) null
            else ClaimNode(
                id = id,
                text = text,
                premises = strList(o, "premises"),
                isEvidence = bool(o, "is_evidence", false)
            )
        }
        require(nodes.isNotEmpty()) { "claims 中没有有效的断言（text 全为空）" }

        val ids = nodes.map { it.id }.toSet()
        val byId = nodes.associateBy { it.id }

        // 悬空：既不是外部事实，也没有任何前提支撑
        val dangling = nodes.filter { !it.isEvidence && it.premises.isEmpty() }
        // 指向不存在节点的前提（抽取错误或遗漏）
        val brokenRefs = nodes.flatMap { n ->
            n.premises.filterNot { it in ids }.map { n.id to it }
        }
        // 环检测（循环论证）
        val cycles = findCycles(nodes, ids)
        // 深度：从外部事实出发的可达层数
        val depth = computeDepth(nodes, byId, ids)

        val maxDepth = depth.values.maxOrNull() ?: 0
        val unreached = nodes.filter { it.id !in depth }.map { it.id }

        return out(
            "node_count" to nodes.size,
            "edge_count" to nodes.sumOf { n -> n.premises.count { it in ids } },
            "dangling_claims" to jarr(
                dangling.map {
                    JSONObject().apply {
                        put("id", it.id)
                        put("text", it.text)
                        put("why", "既未标注为外部事实，也没有任何前提支撑——它只是被断言了")
                    }
                }
            ),
            "broken_references" to jarr(
                brokenRefs.map {
                    JSONObject().apply {
                        put("from", it.first)
                        put("missing_premise", it.second)
                    }
                }
            ),
            "cycles" to jarr(cycles),
            "unreached_from_evidence" to jarr(unreached),
            "max_chain_depth" to maxDepth,
            "verdict" to when {
                cycles.isNotEmpty() -> "存在循环论证：${cycles.size} 个环，论证无法靠自身建立。"
                dangling.isNotEmpty() -> "存在 ${dangling.size} 个悬空断言：它们没有出处，是整个论证最薄弱处。"
                unreached.isNotEmpty() -> "有 ${unreached.size} 个结论从外部事实出发不可达，推导链断了。"
                else -> "结构上完整：所有断言都可从外部事实逐层推出。"
            },
            "note" to "本工具只做结构分析，不判断断言内容是否正确；悬空断言是否成立需你自行核对。"
        )
    }

    private data class ClaimNode(
        val id: String,
        val text: String,
        val premises: List<String>,
        val isEvidence: Boolean
    )

    /** DFS 找环，返回每个环的 id 序列（截断以防爆炸）。 */
    private fun findCycles(nodes: List<ClaimNode>, ids: Set<String>): List<List<String>> {
        val byId = nodes.associateBy { it.id }
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()

        fun dfs(current: String, path: MutableList<String>, inPath: MutableSet<String>) {
            if (cycles.size >= 20) return
            if (current in inPath) {
                val start = path.indexOf(current)
                if (start >= 0) cycles.add(path.subList(start, path.size).toList())
                return
            }
            if (current in visited) return
            inPath.add(current)
            path.add(current)
            byId[current]?.premises?.filter { it in ids }?.forEach { next ->
                dfs(next, path, inPath)
            }
            path.removeAt(path.size - 1)
            inPath.remove(current)
            visited.add(current)
        }

        nodes.filter { !it.isEvidence }.forEach { dfs(it.id, mutableListOf(), mutableSetOf()) }
        return cycles
    }

    /** 从外部事实出发的 BFS 层数；不可达的不出现在 map 里。 */
    private fun computeDepth(
        nodes: List<ClaimNode>,
        byId: Map<String, ClaimNode>,
        ids: Set<String>
    ): Map<String, Int> {
        // 反图：premise -> 依赖它的节点
        val dependents = mutableMapOf<String, MutableList<String>>()
        nodes.forEach { n ->
            n.premises.filter { it in ids }.forEach { p ->
                dependents.getOrPut(p) { mutableListOf() }.add(n.id)
            }
        }
        val depth = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        nodes.filter { it.isEvidence }.forEach { d ->
            depth[d.id] = 0
            queue.add(d.id)
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = depth[cur] ?: 0
            dependents[cur]?.forEach { next ->
                val candidate = d + 1
                if (depth[next] == null || candidate < depth[next]!!) {
                    depth[next] = candidate
                    queue.add(next)
                }
            }
        }
        // 完全无前提链的节点（悬空）视为深度 0 起点，避免误报不可达
        nodes.filter { !it.isEvidence && it.premises.isEmpty() }.forEach {
            if (it.id !in depth) depth[it.id] = 0
        }
        return depth
    }

    // ------------------------------------------------------------------
    // 局部 helper（与 CoreTools 的同名私有实现保持独立，避免交叉耦合）
    // ------------------------------------------------------------------

    private fun schema(required: List<String> = emptyList(), vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        for ((k, v) in props) properties.put(k, v)
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
    }

    private fun sProp(desc: String): JSONObject =
        JSONObject().put("type", "string").put("description", desc)

    private fun iProp(desc: String, min: Int? = null, max: Int? = null): JSONObject {
        val o = JSONObject().put("type", "integer").put("description", desc)
        if (min != null) o.put("minimum", min)
        if (max != null) o.put("maximum", max)
        return o
    }

    private fun arrProp(desc: String, itemType: String = "string"): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", JSONObject().put("type", itemType))
            .put("description", desc)

    private fun out(vararg fields: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in fields) o.put(k, wrap(v))
        return o
    }

    private fun wrap(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is JSONObject -> v
        is JSONArray -> v
        is List<*> -> jarr(v)
        else -> v
    }

    private fun jarr(items: List<*>): JSONArray {
        val a = JSONArray()
        for (it in items) a.put(wrap(it))
        return a
    }

    private fun str(args: JSONObject, key: String, default: String = ""): String {
        val v = args.opt(key) ?: return default
        if (v === JSONObject.NULL) return default
        return v.toString()
    }

    private fun intArg(args: JSONObject, key: String, default: Int): Int {
        val v = args.opt(key) ?: return default
        if (v === JSONObject.NULL) return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun bool(args: JSONObject, key: String, default: Boolean): Boolean {
        val v = args.opt(key) ?: return default
        if (v === JSONObject.NULL) return default
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> default
        }
    }

    private fun strList(args: JSONObject, key: String): List<String> {
        val arr = args.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.opt(i)
            if (v == null || v === JSONObject.NULL) null else v.toString()
        }
    }

    private fun objList(args: JSONObject, key: String): List<JSONObject> {
        val arr = args.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
