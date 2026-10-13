package com.reversetutor.feature.reversetutor.knowledge

/**
 * 确定性工具逻辑的学科知识库（规格源：spike app/tools/knowledge.py）。
 *
 * 核心设计原则：数学正确性对齐设计文档，针对二次函数配方法与顶点公式等概念
 * 提供确定性事实与素材模板。
 */
object TopicKnowledgeBase {

    data class ConceptKnowledge(
        val terms: List<String> = emptyList(),
        val procedureTerms: List<String> = emptyList(),
        val keyFact: String = "",
        val blindSpotLabels: List<String> = emptyList()
    )

    data class ProbingQuestionSet(
        val direct: String = "",
        val guided: String = "",
        val socratic: String = "",
        val generic: String = ""
    )

    data class LadderStep(
        val stepId: Int,
        val description: String,
        val activities: List<String>,
        val estimatedTime: Int,
        val successCriteria: String
    )

    data class AnalogyEntry(
        val analogy: String,
        val mapping: Map<String, Any>,
        val limitations: List<String>,
        val extensionQuestions: List<String>
    )

    data class CounterexampleEntry(
        val match: List<String>,
        val counterexample: String,
        val correctVersion: String,
        val followUpHint: String
    )

    data class MisconceptionEntry(
        val type: String,
        val patterns: List<String>,
        val rootCause: String,
        val manifestations: List<String>,
        val intervention: String,
        val prerequisiteGaps: List<String>,
        val severity: String
    )

    val QUADRATIC_CONCEPTS: Map<String, ConceptKnowledge> = mapOf(
        "配方" to ConceptKnowledge(
            terms = listOf("配方", "配方法", "凑", "完全平方"),
            procedureTerms = listOf("加", "补", "减去", "除以", "平方"),
            keyFact = "x² + bx = (x + b/2)² - (b/2)²",
            blindSpotLabels = listOf("补常数项与等价变形", "为什么要减去 (b/2)²")
        ),
        "完全平方式" to ConceptKnowledge(
            terms = listOf("完全平方", "展开", "(x+", "平方项"),
            procedureTerms = listOf("展开", "合并", "中间项", "系数"),
            keyFact = "(x + a)² = x² + 2ax + a²，中间项系数 2a 决定了要补多少",
            blindSpotLabels = listOf("展开与因式分解方向", "中间项系数来源")
        ),
        "顶点" to ConceptKnowledge(
            terms = listOf("顶点", "-b/2a", "最高点", "最低点", "h", "k"),
            procedureTerms = listOf("代入", "求出", "写出"),
            keyFact = "y = a(x - h)² + k 的顶点是 (h, k)；顶点横坐标 x = -b/(2a)",
            blindSpotLabels = listOf("顶点横纵坐标的来源", "符号规则")
        ),
        "对称轴" to ConceptKnowledge(
            terms = listOf("对称轴", "x =", "直线"),
            procedureTerms = listOf("求出", "写出"),
            keyFact = "对称轴是直线 x = -b/(2a)，顶点在对称轴上",
            blindSpotLabels = listOf("对称轴与顶点的关系")
        ),
        "判别式" to ConceptKnowledge(
            terms = listOf("判别式", "Δ", "delta", "根", "交点"),
            procedureTerms = listOf("计算", "判断", "大于", "等于", "小于"),
            keyFact = "Δ = b² - 4ac：Δ>0 两个交点，Δ=0 一个切点，Δ<0 无交点",
            blindSpotLabels = listOf("判别式与图像交点的对应", "Δ=0 的几何意义")
        ),
        "开口方向" to ConceptKnowledge(
            terms = listOf("开口", "a >", "a <", "向上", "向下"),
            procedureTerms = listOf("判断", "看"),
            keyFact = "a > 0 开口向上（有最低点），a < 0 开口向下（有最高点）；二次函数要求 a ≠ 0",
            blindSpotLabels = listOf("系数符号与开口方向", "a=0 时退化为一次函数")
        )
    )

    val PROBING_QUESTIONS: Map<String, ProbingQuestionSet> = mapOf(
        "补常数项与等价变形" to ProbingQuestionSet(
            direct = "老师，x² + 6x 补上多少才能配成完全平方式？补完之后式子还和原来相等吗？",
            guided = "老师，我们先看 x² + 6x，它比 (x + 3)² 少了什么？那要怎么补才不改变式子的值？",
            socratic = "老师，如果我把 x² + 6x 直接写成 (x + 3)²，取 x = 1 时两边还相等吗？这说明什么？",
            generic = "老师，配方时补上的常数项应该怎么算？补完为什么还要减回去？"
        ),
        "补常数项与符号规则" to ProbingQuestionSet(
            direct = "老师，配方后得到的 (x + 3)² - 9 里，顶点坐标到底怎么读出来？",
            guided = "老师，对照 y = a(x - h)² + k，(x + 3)² - 9 里的 h 和 k 分别是多少？",
            socratic = "老师，(x + 3)² 可以看成 (x - (-3))² 吗？那顶点横坐标是 3 还是 -3？",
            generic = "老师，怎么从配好方的式子读出顶点坐标？"
        ),
        "顶点横纵坐标的来源" to ProbingQuestionSet(
            direct = "老师，为什么顶点横坐标是 -b/(2a) 而不是 +b/(2a)？",
            guided = "老师，从 y = a(x - h)² + k 看，什么时候 y 取到最值？那一刻 x 是多少？",
            socratic = "老师，如果 a(x - h)² + k 的最值出现在 x = h，那一般式里这个 h 等于多少？",
            generic = "老师，顶点坐标公式是怎么推出来的？"
        ),
        "判别式与图像交点的对应" to ProbingQuestionSet(
            direct = "老师，Δ = b² - 4ac 为什么能决定图像和 x 轴有几个交点？",
            guided = "老师，联立 y = ax² + bx + c 和 y = 0 会得到什么？它的解和交点有什么关系？",
            socratic = "老师，如果二次方程没有实数根，图像会和 x 轴相交吗？为什么？",
            generic = "老师，判别式和抛物线与 x 轴的交点个数有什么关系？"
        ),
        "符号规则" to ProbingQuestionSet(
            direct = "老师，x² - 4x 配方时括号里应该填什么？常数项补多少、减多少？",
            guided = "老师，x² - 4x 比 (x - 2)² 少了什么？怎么补齐？",
            socratic = "老师，如果 b 是负数，x² + bx = (x + b/2)² - (b/2)² 还成立吗？",
            generic = "老师，配方遇到负系数时符号要怎么处理？"
        ),
        "_default" to ProbingQuestionSet(
            direct = "老师，你能再具体讲讲这一步是怎么得到的吗？",
            guided = "老师，我们先从最简单的例子看，然后你能推到一般情况吗？",
            socratic = "老师，如果换个条件，这个结论还成立吗？",
            generic = "老师，这一步能再展开讲讲吗？"
        )
    )

    val TEACHING_LADDER_QUADRATIC: List<LadderStep> = listOf(
        LadderStep(
            stepId = 1,
            description = "回顾完全平方式的展开",
            activities = listOf("展开 (x + 3)²，观察中间项系数", "对 (x + a)² 重复一次找规律"),
            estimatedTime = 3,
            successCriteria = "能独立展开任意 (x + a)² 并指出中间项系数为 2a"
        ),
        LadderStep(
            stepId = 2,
            description = "理解配方法的补项与等价变形",
            activities = listOf("把 x² + 6x 配方：补 (b/2)² 再减去，得 (x + 3)² - 9", "用 x = 1 数值验证两边相等"),
            estimatedTime = 5,
            successCriteria = "知道要补 (b/2)² 且明白为什么还要减回去"
        ),
        LadderStep(
            stepId = 3,
            description = "从配方式读出顶点",
            activities = listOf("对照 y = a(x - h)² + k，读出 h 和 k", "验证顶点在对称轴 x = -b/(2a) 上"),
            estimatedTime = 4,
            successCriteria = "能从配好的式子直接读出顶点坐标"
        ),
        LadderStep(
            stepId = 4,
            description = "推广到一般式并处理符号",
            activities = listOf("对一般 ax² + bx + c 完成配方", "专门讨论 b < 0 时的符号"),
            estimatedTime = 5,
            successCriteria = "能推出顶点横坐标 x = -b/(2a) 并解释负号来源"
        )
    )

    val ANALOGIES: Map<String, AnalogyEntry> = mapOf(
        "配方" to AnalogyEntry(
            analogy = "配方就像拼一个缺角的正方形：x² + bx 是一块缺了角的正方形地砖，缺的角是 (b/2)²；我们先补上这个角拼成完整的 (x + b/2)²，但因为补角是借来的，最后还要减掉它，式子才和原来相等。",
            mapping = mapOf(
                "source_domain" to "补全缺角的正方形",
                "target_domain" to "二次三项式配方",
                "correspondences" to listOf(
                    "正方形面积 x² ↔ 二次项 x²",
                    "缺角大小 (b/2)² ↔ 需要补的常数项",
                    "补角再减掉 ↔ 等式保持不变"
                )
            ),
            limitations = listOf("类比只覆盖二次项系数为 1 的情形", "几何拼接不能直接处理 a ≠ 1 的缩放"),
            extensionQuestions = listOf("如果二次项系数是 a 不是 1，拼图还成立吗？要先做什么？")
        ),
        "判别式" to AnalogyEntry(
            analogy = "判别式就像游戏的关卡难度指示器：Δ > 0 有两条通关路径（两个交点），Δ = 0 只有一条最优路径（恰好相切），Δ < 0 无路可走（不相交）。",
            mapping = mapOf(
                "source_domain" to "游戏通关路径",
                "target_domain" to "二次方程的根的个数",
                "correspondences" to listOf(
                    "通关路径 ↔ 方程的实数根",
                    "路径条数 ↔ 交点个数"
                )
            ),
            limitations = listOf("根是数值不是路径", "判别式还有代数上的推导意义"),
            extensionQuestions = listOf("Δ = 0 时为什么两条路径合并成一条？几何上发生了什么？")
        ),
        "顶点" to AnalogyEntry(
            analogy = "抛物线像一只碗（a > 0）或一顶倒扣的碗（a < 0），顶点就是碗底或碗顶那个最稳定的点，整个图形绕着对称轴镜像对称。",
            mapping = mapOf(
                "source_domain" to "碗的最低/最高点",
                "target_domain" to "二次函数的最值点",
                "correspondences" to listOf(
                    "碗底 ↔ 最小值",
                    "碗顶 ↔ 最大值",
                    "碗的对称面 ↔ 对称轴"
                )
            ),
            limitations = listOf("碗的深浅对应 a 的大小，类比不精确", "没有覆盖 a = 0 退化为直线的情形"),
            extensionQuestions = listOf("a 越大碗越深还是越浅？这对顶点位置有影响吗？")
        ),
        "符号规则" to AnalogyEntry(
            analogy = "(x - h)² 里的减号就像『搬家记录』：x - 3 表示向右搬 3 格，顶点在 3；x + 3 等于 x - (-3)，表示向左搬 3 格，顶点在 -3。括号里看着是加，其实是减去一个负数。",
            mapping = mapOf(
                "source_domain" to "搬家方向记录",
                "target_domain" to "函数图像平移与顶点符号",
                "correspondences" to listOf(
                    "向右搬 h 格 ↔ y = (x - h)² 顶点在 x = h",
                    "向左搬 h 格 ↔ y = (x + h)² 顶点在 x = -h"
                )
            ),
            limitations = listOf("类比只针对横向平移", "纵向平移 k 的符号是直观的加减"),
            extensionQuestions = listOf("y = (x + 3)² - 9 的顶点为什么是 (-3, -9)？")
        ),
        "_default" to AnalogyEntry(
            analogy = "可以把这个抽象关系想象成『地图上的路线』：定义是起点，性质是路上的检查站，结论是终点；每一步推导都要有一条能走通的路。",
            mapping = mapOf(
                "source_domain" to "地图路线",
                "target_domain" to "推理链",
                "correspondences" to listOf("路线 ↔ 推理步骤")
            ),
            limitations = listOf("纯结构性类比，细节覆盖有限"),
            extensionQuestions = listOf("你能为这个结论画出自己的『路线图』吗？")
        )
    )

    val COUNTEREXAMPLES: List<CounterexampleEntry> = listOf(
        CounterexampleEntry(
            match = listOf("所有质数", "质数都是奇"),
            counterexample = "2 是质数，但 2 是偶数不是奇数",
            correctVersion = "除 2 以外的所有质数都是奇数",
            followUpHint = "可以问：那 2 算质数吗？"
        ),
        CounterexampleEntry(
            match = listOf("x² + bx", "(x + b/2)²", "配方只要", "补上半"),
            counterexample = "取 x = 1、b = 6：左边 x² + 6x = 7，而 (x + 3)² = 16，两边不相等",
            correctVersion = "x² + 6x = (x + 3)² - 9，必须减去 (b/2)² 才是等价变形",
            followUpHint = "可以让学生代入 x = 1 数值验证两边是否相等"
        ),
        CounterexampleEntry(
            match = listOf("顶点是 (b/2a", "顶点横坐标是 b/", "顶点在 +b/2a"),
            counterexample = "对 y = x² + 6x，图像在 y 轴右侧、对称轴在 x = -3，顶点横坐标是 -3 不是 +3",
            correctVersion = "顶点横坐标 x = -b/(2a)，符号要取负",
            followUpHint = "让学生代入 x = -3 检查是否取到最小值"
        ),
        CounterexampleEntry(
            match = listOf("所有偶数", "偶数都是"),
            counterexample = "2 是偶数但它是质数不是合数",
            correctVersion = "除 2 以外的偶数都是合数",
            followUpHint = "可以问：2 的因数有哪些？"
        ),
        CounterexampleEntry(
            match = listOf("二次函数", "a = 0", "a=0"),
            counterexample = "y = 0·x² + 2x + 1 里 x² 项消失，图像是一条直线不是抛物线",
            correctVersion = "二次函数要求二次项系数 a ≠ 0",
            followUpHint = "让学生思考 a = 0 时式子还剩什么"
        )
    )

    val MISCONCEPTIONS_QUADRATIC: List<MisconceptionEntry> = listOf(
        MisconceptionEntry(
            type = "符号混淆",
            patterns = listOf("(x + b/2)² 直接当顶点", "+b/2a", "顶点在 +", "加号就是向右", "x + 3 顶点是 3"),
            rootCause = "未理解配方法中 (x - h)² 的符号规则与平移方向的关系",
            manifestations = listOf("把 (x + b/2)² 直接当作顶点横坐标 +b/2", "认为括号里是加号顶点就在正方向"),
            intervention = "用 y = a(x - h)² + k 的标准形式和图像平移的几何意义解释符号",
            prerequisiteGaps = listOf("完全平方式的展开", "函数图像平移规则"),
            severity = "medium"
        ),
        MisconceptionEntry(
            type = "等价变形缺失",
            patterns = listOf("配成 (x + b/2)² 就行", "不用减", "直接等于", "配方后就是 (x +"),
            rootCause = "把配方当成改写而不是等价变形，遗漏减去 (b/2)² 的步骤",
            manifestations = listOf("x² + 6x 直接写成 (x + 3)²", "不检查两边数值是否相等"),
            intervention = "让学生用具体数值（如 x = 1）验证两边是否相等，自己发现差了一个常数",
            prerequisiteGaps = listOf("等式性质", "完全平方式的展开"),
            severity = "high"
        ),
        MisconceptionEntry(
            type = "公式死记",
            patterns = listOf("背下来", "公式就是", "记的是 -b/2a", "忘了为什么"),
            rootCause = "跳过了配方推导过程，只记忆结论",
            manifestations = listOf("能背顶点公式但说不出 -b/(2a) 的来历", "换形式（缺常数项）就不会用"),
            intervention = "从头用配方法推导一遍顶点公式，把公式还原成过程",
            prerequisiteGaps = listOf("配方法", "完全平方式"),
            severity = "medium"
        ),
        MisconceptionEntry(
            type = "系数遗漏",
            patterns = listOf("顶点是 b/2a", "顶点在 b/2a", "横坐标 b/2a", "横坐标是 b/2a", "b/2 就是顶点", "除以 2a 就行", "不用除以 a"),
            rootCause = "混淆 b/2（配方用）与 -b/(2a)（顶点公式用）",
            manifestations = listOf("配方补项时直接用 b 而不是 b/2", "顶点公式里忘掉分母的 a"),
            intervention = "对比两个场景：配方的补项是 (b/2)²，顶点公式是 -b/(2a)，分步列出来",
            prerequisiteGaps = listOf("配方步骤", "分式运算"),
            severity = "medium"
        )
    )

    val BREAKTHROUGH_TESTS: Map<String, String> = mapOf(
        "配方" to "那我试试：x² + 8x + 1 配方的话，应该补 (8/2)² = 16 再减 16，变成 (x + 4)² - 15，对吗？",
        "顶点" to "那我试试：y = (x - 3)² + 2 的顶点是 (3, 2)，对吗？",
        "符号规则" to "那我试试：x² - 4x 配方应该是 (x - 2)² - 4，对吗？",
        "判别式" to "那我试试：x² + 2x + 5 的判别式 Δ = 4 - 20 = -16 < 0，所以图像和 x 轴没有交点，对吗？",
        "_default" to "那我试试换个数字检验一下这个规律，可以吗？"
    )

    val STUDENT_SENTENCE_TEMPLATES: Map<String, List<String>> = mapOf(
        "mild" to listOf(
            "老师，{confusion_point}这里我有一点点没跟上……{word}是不是还有一步我没看懂？",
            "老师，其他都还好，就是{confusion_point}这里{word}有点含糊，能再说一遍吗？"
        ),
        "moderate" to listOf(
            "老师，{confusion_point}我卡住了：{word}为什么是这样？我按自己的理解推了一遍，结果对不上。",
            "老师，{confusion_point}这里我想不太通——{word}是我的理解哪里出了问题吗？"
        ),
        "severe" to listOf(
            "老师，{confusion_point}我完全懵了，{word}完全不知道从哪里下手，能从头讲讲吗？",
            "老师，{confusion_point}这部分我彻底跟丢了……{word}是不是前面哪一步我就理解错了？"
        )
    )

    val UNCERTAINTY_WORDS: List<String> = listOf("我觉得", "好像", "是不是", "应该是吧", "可能", "我不太确定")

    /** 匹配自由文本到最佳知识库条目。 */
    fun findConcept(concept: String?): Pair<String, ConceptKnowledge> {
        val c = (concept ?: "").lowercase()
        var best = "_default"
        var bestScore = 0
        for ((name, entry) in QUADRATIC_CONCEPTS) {
            val score = entry.terms.count { t -> c.contains(t.lowercase()) }
            if (score > bestScore) {
                best = name
                bestScore = score
            }
        }
        return if (best != "_default") {
            best to (QUADRATIC_CONCEPTS[best] ?: ConceptKnowledge())
        } else {
            "_default" to ConceptKnowledge()
        }
    }
}
