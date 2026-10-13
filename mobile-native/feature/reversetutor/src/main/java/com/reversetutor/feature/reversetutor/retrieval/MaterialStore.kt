package com.reversetutor.feature.reversetutor.retrieval

import com.reversetutor.feature.reversetutor.core.PyCompat
import kotlin.math.min

/**
 * 学生资料库内存检索（规格源：spike app/tools/retrieval.py 中的 MaterialStore）。
 *
 * 工具=事实层：查询学生录入的教材/题目片段；无命中时返回空结果，
 * 作为「资料库里没有」的事实交由 LLM 判断，不替 LLM 做教学假设。
 */
class MaterialStore {

    data class MaterialDoc(
        val docId: String,
        val title: String,
        val content: String,
        val grade: String = "",
        val tags: List<String> = emptyList(),
        val createdAt: Double = 0.0
    )

    data class SearchHit(
        val docId: String,
        val title: String,
        val grade: String,
        val score: Double,
        val snippet: String,
        val tags: List<String>
    )

    private val docs: MutableMap<String, MaterialDoc> = LinkedHashMap()

    init {
        // 内置默认资料库（与 spike _DEFAULT_DOCS 一致）
        addDoc(
            docId = "doc_quad_01",
            title = "二次函数顶点公式推导（人教版九年级上册）",
            content = """一般式 y = ax² + bx + c (a ≠ 0)。
第一步：提取二次项系数 a，得 y = a[x² + (b/a)x] + c。
第二步：配方，在括号内加上并减去一次项系数一半的平方 (b/2a)²：
y = a[x² + (b/a)x + (b/2a)² - (b/2a)²] + c
  = a[(x + b/2a)² - b²/(4a²)] + c
  = a(x + b/2a)² - b²/(4a) + c
  = a(x + b/2a)² + (4ac - b²)/(4a)。
因此顶点坐标为 (-b/(2a), (4ac - b²)/(4a))，对称轴为直线 x = -b/(2a)。
注意：顶点横坐标符号为负！配方时切记括号内加了常数项后，必须等价减去。""",
            grade = "初三",
            tags = listOf("二次函数", "配方法", "顶点公式", "对称轴")
        )
        addDoc(
            docId = "doc_quad_02",
            title = "配方法解一元二次方程与几何意义",
            content = """配方法的几何直观：
将 x² + bx 视为边长为 x 的大正方形与宽为 b、长为 x 的矩形组合。
把矩形沿中线切为两个宽为 b/2 的长条，分别拼在大正方形的相邻两边。
此时右上角缺少一个边长为 b/2 的小正方形，其面积为 (b/2)²。
补上这个小正方形后，整体拼成一个边长为 (x + b/2) 的完整正方形。
因为面积增加了 (b/2)²，必须减去该面积保持等价：
x² + bx = (x + b/2)² - (b/2)²。这就是配方法的几何来源。""",
            grade = "初三",
            tags = listOf("配方法", "几何直观", "一元二次方程", "拼图类比")
        )
        addDoc(
            docId = "doc_quad_03",
            title = "极值点偏移解题通法（高中选修）",
            content = """对于函数 f(x) 在区间上有两个极值点或零点 x₁ < x₂，若 f(x₁) = f(x₂)，
判断中点 (x₁ + x₂)/2 与极值点 x₀ 的大小关系即为极值点偏移问题。
常用方法：
1. 对称化构造法：构造辅助函数 F(x) = f(x) - f(2x₀ - x) 或 G(x) = f(x₀ + x) - f(x₀ - x)。
2. 对数均值不等式法：利用 √(x₁x₂) < (x₁ - x₂)/(ln x₁ - ln x₂) < (x₁ + x₂)/2。
3. 比值换元法：令 t = x₂/x₁ > 1，将双变量问题转化为单变量函数。
典型例题：f(x) = x e^(-x)，证明 x₁ + x₂ > 2。""",
            grade = "高二",
            tags = listOf("极值点偏移", "导数", "对称化构造", "对数均值不等式")
        )
        addDoc(
            docId = "doc_quad_04",
            title = "初中常见数学易错点汇总",
            content = """易错点 1：二次函数顶点横坐标写成 +b/(2a)，漏掉负号。
易错点 2：配方时直接写成 (x + b/2)²，忘记减去常数项 (b/2)²，导致式子不恒等。
易错点 3：遇到二次项系数 a ≠ 1 时，直接对一次项系数取半，忘记先提取 a。
易错点 4：二次函数中忽略 a ≠ 0 的条件，当 a = 0 时退化为一次函数。
易错点 5：判别式 Δ = b² - 4ac 计算时，若 c 为负数常把 -4ac 的符号弄错。""",
            grade = "初三",
            tags = listOf("易错点", "符号错误", "等价变形", "二次函数")
        )
    }

    fun addDoc(
        docId: String,
        title: String,
        content: String,
        grade: String = "",
        tags: List<String> = emptyList()
    ): MaterialDoc {
        val doc = MaterialDoc(
            docId = docId,
            title = title,
            content = content,
            grade = grade,
            tags = tags,
            createdAt = System.currentTimeMillis() / 1000.0
        )
        docs[docId] = doc
        return doc
    }

    fun removeDoc(docId: String): Boolean = docs.remove(docId) != null

    fun getDoc(docId: String): MaterialDoc? = docs[docId]

    fun count(): Int = docs.size

    /**
     * 文本模糊检索。
     * 分词打分规则：
     * - query 中的词项在 title、tags、content 中出现
     * - title 权重 3.0，tags 权重 2.0，content 权重 1.0
     * - gradeFilter 不为空时只保留匹配学段的文档
     */
    fun search(
        query: String,
        topK: Int = 3,
        gradeFilter: String? = null
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // 简易中英文词项提取（字词与英文连续块）
        val tokens = q.split(Regex("[\\s,，、；;]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val results = mutableListOf<SearchHit>()
        for (doc in docs.values) {
            if (!gradeFilter.isNullOrBlank() && doc.grade != gradeFilter) continue

            var score = 0.0
            val contentLower = doc.content.lowercase()
            val titleLower = doc.title.lowercase()
            val tagsJoined = doc.tags.joinToString(" ").lowercase()

            for (token in tokens) {
                val t = token.lowercase()
                if (titleLower.contains(t)) score += 3.0
                if (tagsJoined.contains(t)) score += 2.0
                if (contentLower.contains(t)) {
                    // 出现次数加权（上限 5 次）
                    val count = min(5, contentLower.split(t).size - 1)
                    score += 1.0 * count
                }
            }

            // 整个 query 整体命中加成
            if (contentLower.contains(q.lowercase())) score += 5.0
            if (titleLower.contains(q.lowercase())) score += 8.0

            if (score > 0.0) {
                // 截取最佳摘要片段（优先截取首个命中词项周围 150 字）
                val firstToken = tokens.firstOrNull { contentLower.contains(it.lowercase()) }
                val snippet = if (firstToken != null) {
                    val idx = contentLower.indexOf(firstToken.lowercase())
                    val start = maxOf(0, idx - 40)
                    val end = min(doc.content.length, idx + 120)
                    val prefix = if (start > 0) "..." else ""
                    val suffix = if (end < doc.content.length) "..." else ""
                    prefix + doc.content.substring(start, end) + suffix
                } else {
                    doc.content.take(150) + if (doc.content.length > 150) "..." else ""
                }

                results.add(
                    SearchHit(
                        docId = doc.docId,
                        title = doc.title,
                        grade = doc.grade,
                        score = PyCompat.round(score, 2),
                        snippet = snippet.replace("\n", " "),
                        tags = doc.tags
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }.take(topK)
    }
}
