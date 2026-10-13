package com.reversetutor.feature.reversetutor.curriculum

import com.reversetutor.feature.reversetutor.session.KgNode
import com.reversetutor.feature.reversetutor.session.RTSession
import org.json.JSONObject

/**
 * 课标知识边界目录（规格源：spike app/tools/curriculum.py）。
 *
 * 数据来自 assets/curriculum.json（由 spike CURRICULUM 导出，内容与代码分离）：
 * { "高中数学": { "grade_order": [...], "concepts": [ {...} ] } }
 *
 * 判定是确定性的：工具=事实层，把「该概念学生应该处在什么状态」作为事实
 * 交给 LLM 决策层，不替 LLM 决定怎么教。
 */
class CurriculumCatalog(private val curriculumJson: String) {

    data class ConceptEntry(
        val id: String,
        val grade: String,
        val track: String,
        val difficulty: Int,
        val prerequisites: List<String>,
        val standardDefinition: String,
        val keyFacts: List<String>
    )

    private data class SubjectData(
        val gradeOrder: List<String>,
        val concepts: List<ConceptEntry>
    )

    private val subjects: MutableMap<String, SubjectData> = LinkedHashMap()

    /** 与 spike 一致的别名表（概念口语名 → 课标 id）。 */
    private val aliases: Map<String, String> = mapOf(
        "顶点公式" to "配方法与顶点公式",
        "配方" to "配方法与顶点公式",
        "导数" to "导数的概念与几何意义",
        "求导" to "导数的运算",
        "单调性" to "函数的单调性与最值",
        "零点" to "函数零点与方程的根",
        "圆锥曲线" to "圆锥曲线的标准方程与性质",
        "数列" to "数列与等差等比数列"
    )

    init {
        val root = JSONObject(curriculumJson)
        for (subject in root.keys()) {
            val data = root.getJSONObject(subject)
            val concepts = mutableListOf<ConceptEntry>()
            val arr = data.getJSONArray("concepts")
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                concepts.add(
                    ConceptEntry(
                        id = e.getString("id"),
                        grade = e.optString("grade", ""),
                        track = e.optString("track", ""),
                        difficulty = e.optInt("difficulty", 3),
                        prerequisites = stringList(e, "prerequisites"),
                        standardDefinition = e.optString("standard_definition", ""),
                        keyFacts = stringList(e, "key_facts")
                    )
                )
            }
            subjects[subject] = SubjectData(
                gradeOrder = stringList(data, "grade_order"),
                concepts = concepts
            )
        }
    }

    fun conceptCount(): Int = subjects.values.sumOf { it.concepts.size }

    fun subjectCount(): Int = subjects.size

    /** 遍历全部 (subject, entry)，供图谱先修边生成。 */
    fun allConcepts(): List<Pair<String, ConceptEntry>> =
        subjects.flatMap { (subj, data) -> data.concepts.map { subj to it } }

    /** 概念查找结果：(subject, entry, conceptId)；未命中 entry 为 null。 */
    data class FindResult(
        val subject: String?,
        val entry: ConceptEntry?,
        val conceptId: String
    )

    /**
     * 在课标库中模糊查找概念。
     * 匹配策略：别名表 → 概念 id 与查询串互相包含（取 id 最短者，越短越具体）。
     */
    fun findInCurriculum(concept: String, subject: String? = null): FindResult {
        val c = concept.trim()
        if (c.isEmpty()) return FindResult(null, null, "")
        // 1. 别名直查
        val aliasId = aliases[c]
        if (aliasId != null) {
            for ((subj, data) in subjects) {
                for (e in data.concepts) {
                    if (e.id == aliasId) return FindResult(subj, e, e.id)
                }
            }
        }
        // 2. 互相包含匹配（查询串包含概念 id，或概念 id 包含查询串）
        var best: FindResult? = null
        for ((subj, data) in subjects) {
            if (subject != null && subject != subj && subj.contains(subject).not()) continue
            for (e in data.concepts) {
                if (c.contains(e.id) || e.id.contains(c)) {
                    if (best == null || e.id.length < best.conceptId.length) {
                        best = FindResult(subj, e, e.id)
                    }
                }
            }
        }
        return best ?: FindResult(null, null, "")
    }

    /**
     * 确定性判定：学生（session 人设）对某概念应处的状态。
     *
     * 判定优先级（前者覆盖后者）：
     *   1. 对话事实：knowledge_graph 中已有该节点 → 以其 status 为准
     *   2. 学段未到：概念学段在学生会话学段之后 → not_learned
     *   3. 盲区/困惑命中：target_blind_spot 或 confused_concepts 匹配 → confused
     *   4. 难度阈值：difficulty > level+1 → not_learned；== level+1 → learning；<= level → mastered
     *   5. 课标未命中 → 按总体水平回退抽象判定（level>=4 → mastered，否则 not_learned）
     */
    fun conceptStatus(concept: String, session: RTSession): ConceptStatus {
        val level = session.understandingLevel
        val blind = session.targetBlindSpot
        val confusedList = session.confusedConcepts
        val masteredList = session.masteredConcepts
        val grade = session.grade

        val found = findInCurriculum(concept, session.topic)
        val entry = found.entry
        val cid = found.conceptId

        fun hit(text: String): Boolean =
            text.isNotEmpty() && cid.isNotEmpty() && (cid.contains(text) || text.contains(cid))

        fun hitText(text: String, target: String): Boolean =
            text.isNotEmpty() && target.isNotEmpty() &&
                (target.contains(text) || text.contains(target))

        // 1. 对话事实优先：图谱里已有节点 → 以对话中学到的状态为准
        for (n in session.knowledgeGraphNodes) {
            if (n.id.isNotEmpty() && (concept.contains(n.id) || n.id.contains(concept))) {
                val st = n.status
                val mapped = if (st == "mastered") "mastered"
                else if (st == "learning") "learning" else "not_learned"
                return ConceptStatus(
                    conceptId = cid, found = entry != null,
                    grade = entry?.grade ?: "", track = entry?.track ?: "",
                    difficulty = entry?.difficulty,
                    prerequisites = entry?.prerequisites ?: emptyList(),
                    standardDefinition = entry?.standardDefinition ?: "",
                    keyFacts = entry?.keyFacts ?: emptyList(),
                    expectedStatus = mapped,
                    reason = "对话中学过：知识图谱记录该概念状态为 $st（掌握度 ${n.level}）",
                    source = "knowledge_graph"
                )
            }
        }

        if (entry == null) {
            // 5. 回退抽象判定（文本直接比对，不依赖课标 cid）
            return when {
                masteredList.any { hitText(it, concept) } &&
                    confusedList.none { hitText(it, concept) } ->
                    ConceptStatus(
                        conceptId = "", found = false, grade = "", track = "",
                        difficulty = null, prerequisites = emptyList(),
                        standardDefinition = "", keyFacts = emptyList(),
                        expectedStatus = "mastered",
                        reason = "课标未收录，但已掌握清单命中",
                        source = "fallback_persona"
                    )
                confusedList.any { hitText(it, concept) } || hitText(blind, concept) ->
                    ConceptStatus(
                        conceptId = "", found = false, grade = "", track = "",
                        difficulty = null, prerequisites = emptyList(),
                        standardDefinition = "", keyFacts = emptyList(),
                        expectedStatus = "confused",
                        reason = "课标未收录，但处于困惑/盲区清单",
                        source = "fallback_persona"
                    )
                else ->
                    ConceptStatus(
                        conceptId = "", found = false, grade = "", track = "",
                        difficulty = null, prerequisites = emptyList(),
                        standardDefinition = "", keyFacts = emptyList(),
                        expectedStatus = if (level >= 4) "mastered" else "not_learned",
                        reason = "课标未收录，按总体水平 $level/5 抽象判定",
                        source = "fallback_level"
                    )
            }
        }

        // 2. 学段未到
        val subjectData = subjects[found.subject]
        val gi = gradeIndex(grade, subjectData?.gradeOrder ?: emptyList())
        val ci = gradeIndex(entry.grade, subjectData?.gradeOrder ?: emptyList())
        if (gi >= 0 && ci >= 0 && ci > gi) {
            return ConceptStatus(
                conceptId = cid, found = true, grade = entry.grade, track = entry.track,
                difficulty = entry.difficulty, prerequisites = entry.prerequisites,
                standardDefinition = entry.standardDefinition, keyFacts = entry.keyFacts,
                expectedStatus = "not_learned",
                reason = "课标：${entry.grade}${entry.track}、难度${entry.difficulty}；" +
                    "学生学段 $grade 尚未学到 → 未学过",
                source = "curriculum"
            )
        }

        // 3. 盲区/困惑命中
        if (hit(blind) || confusedList.any { hit(it) }) {
            return ConceptStatus(
                conceptId = cid, found = true, grade = entry.grade, track = entry.track,
                difficulty = entry.difficulty, prerequisites = entry.prerequisites,
                standardDefinition = entry.standardDefinition, keyFacts = entry.keyFacts,
                expectedStatus = "confused",
                reason = "课标：${entry.grade}${entry.track}、难度${entry.difficulty}；" +
                    "但概念在会话盲区/困惑清单中 → 困惑中",
                source = "curriculum"
            )
        }

        // 4. 难度阈值：水平 N ≈ 稳定掌握难度 ≤ N，N+1 踮脚可学，再高未掌握
        val d = entry.difficulty
        return when {
            d <= level -> ConceptStatus(
                conceptId = cid, found = true, grade = entry.grade, track = entry.track,
                difficulty = d, prerequisites = entry.prerequisites,
                standardDefinition = entry.standardDefinition, keyFacts = entry.keyFacts,
                expectedStatus = "mastered",
                reason = "课标：${entry.grade}${entry.track}、难度$d；" +
                    "学生水平 $level/5 可稳定掌握 → 已掌握",
                source = "curriculum"
            )
            d == level + 1 -> ConceptStatus(
                conceptId = cid, found = true, grade = entry.grade, track = entry.track,
                difficulty = d, prerequisites = entry.prerequisites,
                standardDefinition = entry.standardDefinition, keyFacts = entry.keyFacts,
                expectedStatus = "learning",
                reason = "课标：${entry.grade}${entry.track}、难度$d；" +
                    "学生水平 $level/5 踮脚可学 → 学习中（可能半懂）",
                source = "curriculum"
            )
            else -> ConceptStatus(
                conceptId = cid, found = true, grade = entry.grade, track = entry.track,
                difficulty = d, prerequisites = entry.prerequisites,
                standardDefinition = entry.standardDefinition, keyFacts = entry.keyFacts,
                expectedStatus = "not_learned",
                reason = "课标：${entry.grade}${entry.track}、难度$d；" +
                    "超出学生水平 $level/5 的可及范围 → 未掌握",
                source = "curriculum"
            )
        }
    }

    private fun gradeIndex(grade: String, gradeOrder: List<String>): Int {
        for ((i, g) in gradeOrder.withIndex()) {
            if (g.isNotEmpty() && grade.contains(g)) return i
        }
        return -1 // 未知学段（含空串）——不做学段判断
    }

    companion object {
        private fun stringList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
        }
    }
}

/** concept_status 返回值（对应 spike 的 out dict）。 */
data class ConceptStatus(
    val conceptId: String,
    val found: Boolean,
    val grade: String,
    val track: String,
    val difficulty: Int?,
    val prerequisites: List<String>,
    val standardDefinition: String,
    val keyFacts: List<String>,
    val expectedStatus: String, // mastered | learning | confused | not_learned
    val reason: String,
    val source: String
)

/** KgNode 便捷构造，供图谱模块复用（避免循环依赖）。 */
fun kgNodeOf(id: String, status: String, level: Double = 0.0) = KgNode(id, status, level)
fun kgNodeOf(id: String, status: String, level: Int) = KgNode(id, status, level.toDouble())
