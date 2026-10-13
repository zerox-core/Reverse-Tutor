package com.reversetutor.feature.reversetutor.tools

import com.reversetutor.feature.reversetutor.core.PyCompat
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import com.reversetutor.feature.reversetutor.graph.KnowledgeGraphBuilder
import com.reversetutor.feature.reversetutor.guard.MathGuard
import com.reversetutor.feature.reversetutor.knowledge.TopicKnowledgeBase
import com.reversetutor.feature.reversetutor.retrieval.MaterialStore
import com.reversetutor.feature.reversetutor.score.ScoreEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 阶段 2：27 个确定性工具的实现与注册（首批核心；web_search / save_teaching_pattern /
 * retrieve_historical_context / generate_progress_report 留待后续批次）。
 *
 * 移植自 spike：
 * - retrieval.py   → lookup_curriculum / search_materials（事实检索 2）
 * - persona.py     → generate_student_persona / speak_as_confused_student / make_deliberate_mistake /
 *                    express_partial_understanding / simulate_breakthrough / challenge_teacher_explanation（角色扮演 6）
 * - cognitive.py   → check_understanding / detect_misconception / assess_readiness / identify_learning_style（认知诊断 4）
 * - strategy.py    → generate_probing_questions / create_teaching_ladder / design_counterexample /
 *                    suggest_analogy / scaffold_problem（教学策略 5）
 * - routing.py     → decide_next_action / select_teaching_route / determine_intervention_urgency /
 *                    plan_session_closure（决策路由 4）
 * - memory.py      → log_breakthrough / log_struggle_point / update_knowledge_graph（记忆管理 3）
 * - evaluation.py  → evaluate_session_quality / measure_learning_gain / detect_teaching_failure（评估反馈 3）
 *
 * 原则：工具 = 事实层。所有工具只返回「事实/素材」（含中文 note 声明决策权在 LLM），
 * 教学决策完全由 LLM 自主 function calling 做出。
 *
 * 已声明的微小偏差（均有注释）：
 * 1. persona/speak 的 RNG：Python 用 hash 随机化（跨进程本就不稳定），Kotlin 用稳定种子；
 *    persona 种子里的 real_student_level 用 clamp 前的原始值字符串。
 * 2. 记忆工具经 [SessionStateSink] 落内存，阶段 3 迁 Room（events/struggle 相似案例检索届时补齐）。
 * 3. search_materials 的 results 条目在 Python 契约键（id/title/kind/subject/snippet/score）之上
 *    附加 grade/tags（MaterialStore 现有字段；阶段 3 Room 后补 kind/subject 真值）。
 */
object CoreTools {

    /** ToolContext.extra 中共享 [SessionStateSink] 的键；缺省时各记忆工具退化为一次性 sink。 */
    const val KEY_SESSION_STATE_SINK = "sessionStateSink"

    // ------------------------------------------------------------------
    // 词表（cognitive.py 逐字）
    // ------------------------------------------------------------------
    private val HEDGES = listOf(
        "应该", "好像", "可能", "大概", "不太确定", "不确定", "也许", "吧",
        "记不清", "猜", "或许是", "未必"
    )
    private val ASSERTIVES = listOf(
        "一定", "肯定", "因为", "所以", "必然", "显然", "推导", "验证", "可以证明"
    )
    private val CAUSAL = listOf(
        "因为", "所以", "因此", "由于", "从而", "于是", "如果", "那么",
        "必须", "否则", "对照", "相当于", "也就是说"
    )
    private val PROCEDURE = listOf(
        "展开", "补", "减去", "减回", "除以", "代入", "代回", "配方", "合并",
        "移项", "平方", "求出", "验证", "检验", "提出", "化简", "凑", "加上",
        "写成", "变成", "对照", "整理", "拿出", "多出", "多了一个"
    )

    /** 非知识库领域（如魔方/地理/编程）退化用：通用动作与步骤词。 */
    private val GENERIC_PROCEDURE = listOf(
        "先", "然后", "接着", "再", "最后", "步骤", "第一步", "第二步",
        "第三步", "方法", "做法", "操作", "对齐", "调整", "摆放", "移动",
        "翻转", "转动", "拧", "拼", "搭", "摆", "排列", "重复", "练习",
        "检查", "试试"
    )

    /** 通用定义标记（非知识库领域 concept 覆盖的替代信号）。 */
    private val DEFINITION_MARKERS = listOf(
        "就是", "指的是", "意思是", "所谓", "也就是说", "叫做", "称为",
        "的含义", "的意思", "本质是"
    )

    /** 迁移/泛化信号（设计 v1.1 维度 4）。 */
    private val TRANSFER = listOf(
        "一般情况", "一般地", "任意", "无论", "都成立", "推广", "换个", "换一组",
        "换一个", "同样适用", "通式", "任何", "举一反三", "所有情况", "别的例子",
        "其他例子", "其它例子", "万能", "如果换成", "对所有的"
    )

    /** 顶点的 -b/2a 数值代换形如 -6/2。 */
    private val VERTEX_RATIO = Regex("-\\s*[a-z0-9]{1,3}\\s*/\\s*2")

    // ------------------------------------------------------------------
    // 其余常量表（逐字）
    // ------------------------------------------------------------------
    private val PREREQ_MAP: Map<String, List<String>> = linkedMapOf(
        "判别式" to listOf("配方法", "完全平方式", "方程求解"),
        "顶点公式" to listOf("配方法", "完全平方式", "对称轴"),
        "韦达定理" to listOf("方程求解", "判别式"),
        "函数平移" to listOf("顶点", "对称轴")
    )
    private val PREREQ_DEFAULT = listOf("基本概念")

    private val ROUTE_COST: Map<String, Int> = linkedMapOf(
        "quick" to 8, "formula" to 10, "deep" to 20, "visual" to 12, "analogy" to 10, "ladder" to 18
    )

    private val STYLE_SIGNALS: Map<String, List<String>> = linkedMapOf(
        "visual" to listOf("图", "画", "图像", "坐标系", "草图", "看图"),
        "logical" to listOf("因为", "所以", "推导", "证明", "一般", "公式"),
        "analogical" to listOf("类比", "比喻", "像", "比如", "游戏", "拼图", "碗"),
        "hands_on" to listOf("试试", "练习", "算一下", "动手", "代入", "验算")
    )

    private val STYLE_APPROACH: Map<String, String> = linkedMapOf(
        "visual" to "优先用图像/草图解释",
        "logical" to "优先用代数推导和一般规律",
        "analogical" to "优先使用类比和实例",
        "hands_on" to "优先让学生动手代入、验算"
    )

    private val URGENCY_ACTIONS: Map<Int, String> = linkedMapOf(
        1 to "保持观察，不打断",
        2 to "稍等，给老师留出自我修正空间",
        3 to "准备一个提示性追问",
        4 to "提供脚手架，拆解问题",
        5 to "立即提供脚手架/类比，避免挫败升级"
    )

    private val EMOTION_BASE: Map<String, Double> = linkedMapOf(
        "neutral" to 1.5, "engaged" to 1.0, "confused" to 2.5,
        "frustrated" to 3.5, "discouraged" to 3.0
    )

    // persona.py _make_deliberate_mistake 的错误库（逐字）
    private val MISTAKE_LIBRARY: Map<String, List<String>> = linkedMapOf(
        // [wrong, correct, correction, uncorrected]
        "sign_error" to listOf(
            "老师，我把 x² + bx 配方成 (x + b/2)² 就完成了，后面不用再减什么吧？",
            "x² + bx = (x + b/2)² - (b/2)²；例如 x² + 6x = (x + 3)² - 9",
            "学生应指出补了 (b/2)² 之后必须减回去才是等价变形",
            "继续引导：“那我们取 x = 1、b = 6，两边数值还相等吗？”"
        ),
        "boundary_case" to listOf(
            "老师，任何函数 y = ax² + bx + c 的图像都是抛物线，a 取 0 也没关系对吧？",
            "a = 0 时二次项消失，y = bx + c 是一条直线，不是抛物线；二次函数要求 a ≠ 0",
            "学生应指出 a = 0 是边界反例",
            "继续引导：“那 y = 0·x² + 2x + 1 的图像长什么样？”"
        ),
        "over_generalization" to listOf(
            "老师，既然 x² + 6x = (x + 3)² - 9，那所有二次式配方都是补 (b/2)² 再减掉，a ≠ 1 也一样，对吧？",
            "a ≠ 1 时要先提出 a：ax² + bx + c = a(x² + (b/a)x) + c，补项是 (b/(2a))²，且整体要乘 a",
            "学生应发现 a ≠ 1 时补项规则要先提取二次项系数",
            "继续引导：“那 y = 2x² + 4x 补多少？直接补 4 够吗？”"
        ),
        "conceptual_confusion" to listOf(
            "老师，判别式 Δ = b² - 4ac < 0 的时候，抛物线和 x 轴也有两个交点，只是复数交点，对吧？",
            "实数坐标平面上 Δ < 0 时与 x 轴没有交点；复数根在图像上看不到",
            "学生应区分实数交点与复数根",
            "继续引导：“交点的 x 坐标是方程的什么？没有实数根意味着什么？”"
        )
    )

    // persona.py _challenge_teacher_explanation 的挑战库（逐字）
    private data class ChallengeEntry(
        val challenge: String,
        val target: String,
        val acceptable: List<String>,
        val hint: String
    )

    private val CHALLENGE_LIBRARY: Map<String, ChallengeEntry> = linkedMapOf(
        "boundary_case" to ChallengeEntry(
            challenge = "老师，那如果 b 是负数呢？x² - 4x 配方出来的符号还和你说的规律一致吗？",
            target = "是否理解补项与符号规则在 b < 0 时依然成立",
            acceptable = listOf(
                "成立，(x - 2)² - 4，补 (b/2)² 的规则不变",
                "指出 b 取负时中间项符号随之改变但公式形式不变"
            ),
            hint = "提示老师：把 b = -4 代入试试"
        ),
        "counter_example" to ChallengeEntry(
            challenge = "老师，可我找到一个数好像不符合你说的：a = 0 的时候，y = 0·x² + bx + c 还算二次函数吗？",
            target = "是否理解二次函数的定义要求 a ≠ 0",
            acceptable = listOf("a = 0 时不是二次函数", "二次函数要求 a ≠ 0"),
            hint = "提示老师：回顾二次函数的定义"
        ),
        "extension" to ChallengeEntry(
            challenge = "老师，你这个规律能推到一般情况吗？比如对称轴和顶点横坐标是什么关系，能不能统一成一个公式？",
            target = "是否能从特例推广到一般（x = -b/(2a)）",
            acceptable = listOf("对称轴 x = -b/(2a)", "顶点横坐标与对称轴相同"),
            hint = "提示老师：把几个例子的对称轴并列比较"
        ),
        "different_scenario" to ChallengeEntry(
            challenge = "老师，如果题目不给一般式、只给顶点和另一个点，还能反过来求解析式吗？你说的方法还适用吗？",
            target = "是否能正反双向使用顶点式",
            acceptable = listOf("可以，用顶点式 y = a(x - h)² + k 代入另一点求 a"),
            hint = "提示老师：想想顶点式里未知数是哪几个"
        )
    )

    // persona.py _express_partial_understanding 的断点→行动映射（逐字）
    private val GAP_ACTION_MAP: Map<String, String> = linkedMapOf(
        "符号" to "引导学生展开完全平方式并对照 (x - h)² 的标准形式",
        "公式" to "请老师从配方法一步步推导顶点公式",
        "图像" to "请老师画出草图，标注顶点与对称轴",
        "判别式" to "请老师联立方程，把判别式和交点个数连起来"
    )
    private const val GAP_ACTION_DEFAULT = "请老师把断开的两步用具体例子连起来"

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------
    fun registerAll(registry: ToolRegistry) {
        // —— 事实检索（2）——
        registry.register(
            RegisteredTool(
                "lookup_curriculum", "事实检索",
                "查询某概念的课程标准事实：学段归属、难度、先修概念、标准定义，以及按当前学生会话推断的学生应处状态（已掌握/学习中/困惑/未学）",
                schema(
                    listOf("concept"),
                    "concept" to sProp("概念名，如“判别式”或“顶点公式”")
                )
            ) { args, ctx -> lookupCurriculum(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "search_materials", "事实检索",
                "检索用户录入的资料库（教材/笔记/错题本），返回命中片段与来源；无命中也是事实（资料里没有该内容）",
                schema(
                    listOf("query"),
                    "query" to sProp("检索词，如“对称化构造 例题”"),
                    "scope" to sProp("all/textbook/note/mistake（教材/笔记/错题本）"),
                    "top_k" to iProp("返回条数", 1, 10)
                )
            ) { args, ctx -> searchMaterials(args, ctx) }
        )

        // —— 角色扮演（6）——
        registry.register(
            RegisteredTool(
                "generate_student_persona", "角色扮演",
                "根据教学主题和真实学生水平，生成 AI 扮演的学生人设（含说话模式与情绪特质）",
                schema(
                    listOf("topic", "real_student_level"),
                    "topic" to sProp("教学主题，如“二次函数顶点公式”"),
                    "real_student_level" to iProp("真实学生水平 1-5", 1, 5),
                    "target_confusion" to sProp("本次要暴露的盲区"),
                    "grade" to sProp("学生学段，如“高一/高二/高三”；空=不限定"),
                    "session_history" to oProp("历史人设（可选）")
                )
            ) { args, ctx -> generateStudentPersona(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "speak_as_confused_student", "角色扮演",
                "生成“表达困惑”的学生台词素材（含嵌入误解与预期引导）；适用于困惑中/没学过的内容，已掌握的内容不应表达困惑。最终回复由 LLM 生成",
                schema(
                    listOf("confusion_point"),
                    "persona_id" to sProp("当前人设 ID（auto=使用会话当前人设）"),
                    "confusion_point" to sProp("要困惑的知识点"),
                    "intensity" to sProp("mild/moderate/severe"),
                    "context" to sProp("当前对话上下文"),
                    "embed_misconception" to bProp("是否嵌入一个典型错误理解")
                )
            ) { args, ctx -> speakAsConfusedStudent(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "make_deliberate_mistake", "角色扮演",
                "生成一个可控的教学错误（含错误陈述、正确答案、预期纠正方向、未纠正时的引导）",
                schema(
                    listOf("correct_concept"),
                    "correct_concept" to sProp("正确的概念"),
                    "mistake_type" to sProp("boundary_case/over_generalization/sign_error/conceptual_confusion"),
                    "hint_strength" to nProp("0=很难看出，1=明显错", 0.0, 1.0),
                    "concept_detail" to sProp("概念的具体内容（可选，用于生成更贴合的错误）")
                )
            ) { args, ctx -> makeDeliberateMistake(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "express_partial_understanding", "角色扮演",
                "生成“懂了一半”的表达素材：已理解部分、困惑部分与连接断点",
                schema(
                    listOf("understood_parts", "confused_parts"),
                    "understood_parts" to arrProp("已理解的部分"),
                    "confused_parts" to arrProp("仍困惑的部分"),
                    "connection_gap" to sProp("连接断点")
                )
            ) { args, _ -> expressPartialUnderstanding(args) }
        )
        registry.register(
            RegisteredTool(
                "simulate_breakthrough", "角色扮演",
                "生成“顿悟”素材：aha 时刻、新理解、感谢语与一个检验性追问",
                schema(
                    listOf("concept", "trigger"),
                    "concept" to sProp("顿悟的概念"),
                    "trigger" to sProp("触发顿悟的关键信息"),
                    "previous_confusion" to sProp("之前的困惑点")
                )
            ) { args, _ -> simulateBreakthrough(args) }
        )
        registry.register(
            RegisteredTool(
                "challenge_teacher_explanation", "角色扮演",
                "生成对老师解释的挑战提问（边界/反例/推广/变式），用于测试理解深度",
                schema(
                    listOf("teacher_statement"),
                    "teacher_statement" to sProp("老师的解释"),
                    "challenge_angle" to sProp("boundary_case/counter_example/extension/different_scenario"),
                    "difficulty" to iProp("质疑难度 1-5", 1, 5)
                )
            ) { args, _ -> challengeTeacherExplanation(args) }
        )

        // —— 认知诊断（4）——
        registry.register(
            RegisteredTool(
                "check_understanding", "认知诊断",
                "评估老师解释的理解深度：维度评分、盲区、优势、语气信心（返回事实，不做决策）",
                schema(
                    listOf("concept", "student_explanation"),
                    "concept" to sProp("概念名称"),
                    "student_explanation" to sProp("老师的解释文本"),
                    "evaluation_criteria" to arrProp("评估维度（可选）")
                )
            ) { args, _ -> checkUnderstanding(args) }
        )
        registry.register(
            RegisteredTool(
                "detect_misconception", "认知诊断",
                "从近期对话中识别典型误解类型、根因与干预建议",
                schema(
                    listOf("dialogue_history", "topic"),
                    "dialogue_history" to arrProp("近期对话（字符串数组）"),
                    "topic" to sProp("当前主题"),
                    "error_pattern" to sProp("观察到的错误描述（可选）")
                )
            ) { args, _ -> detectMisconception(args) }
        )
        registry.register(
            RegisteredTool(
                "assess_readiness", "认知诊断",
                "评估是否具备进入下一阶段的前置条件",
                schema(
                    listOf("current_mastery", "next_topic"),
                    "current_mastery" to oProp("如 {\"level\": 3} 或 {\"mastered\": [...]}"),
                    "next_topic" to sProp("下一阶段主题"),
                    "time_constraint" to nProp("剩余时间（分钟）", 0.0, null)
                )
            ) { args, _ -> assessReadiness(args) }
        )
        registry.register(
            RegisteredTool(
                "identify_learning_style", "认知诊断",
                "从交互记录识别 dominant 学习风格与各方式有效性",
                schema(
                    listOf("interaction_log"),
                    "interaction_log" to arrProp("历史交互记录"),
                    "response_patterns" to oProp("学生对不同方式的反应（可选）")
                )
            ) { args, _ -> identifyLearningStyle(args) }
        )

        // —— 教学策略（5）——
        registry.register(
            RegisteredTool(
                "generate_probing_questions", "教学策略",
                "针对盲区生成追问问题（direct/guided/socratic 风格）",
                schema(
                    listOf("blind_spots"),
                    "blind_spots" to arrProp("要探查的盲区"),
                    "difficulty" to iProp("问题难度 1-5", 1, 5),
                    "max_questions" to iProp("最多几个问题 1-5", 1, 5),
                    "questioning_style" to sProp("direct/guided/socratic")
                )
            ) { args, _ -> generateProbingQuestions(args) }
        )
        registry.register(
            RegisteredTool(
                "create_teaching_ladder", "教学策略",
                "设计从当前状态到目标状态的分步教学路径（含检查点与应急方案）",
                schema(
                    listOf("start_point", "end_goal"),
                    "start_point" to sProp("当前状态"),
                    "end_goal" to sProp("目标状态"),
                    "student_level" to iProp("学生水平 1-5", 1, 5),
                    "time_budget" to nProp("时间限制（分钟）", 0.0, null)
                )
            ) { args, _ -> createTeachingLadder(args) }
        )
        registry.register(
            RegisteredTool(
                "design_counterexample", "教学策略",
                "针对老师的错误陈述设计反例与正确版本",
                schema(
                    listOf("student_claim"),
                    "student_claim" to sProp("老师的错误陈述"),
                    "misconception_type" to sProp("错误类型（可选）")
                )
            ) { args, _ -> designCounterexample(args) }
        )
        registry.register(
            RegisteredTool(
                "suggest_analogy", "教学策略",
                "为抽象概念选择合适类比（含映射与局限性）",
                schema(
                    listOf("abstract_concept"),
                    "abstract_concept" to sProp("抽象概念"),
                    "student_background" to sProp("学生背景（年级/兴趣）"),
                    "previous_analogies" to arrProp("已用过的类比")
                )
            ) { args, _ -> suggestAnalogy(args) }
        )
        registry.register(
            RegisteredTool(
                "scaffold_problem", "教学策略",
                "把复杂问题拆解为脚手架子问题与分步提示",
                schema(
                    listOf("target_problem"),
                    "target_problem" to sProp("原问题"),
                    "student_capability" to iProp("学生能力 1-5", 1, 5),
                    "scaffolding_style" to sProp("minimal/moderate/heavy")
                )
            ) { args, _ -> scaffoldProblem(args) }
        )

        // —— 决策路由（4）——
        registry.register(
            RegisteredTool(
                "decide_next_action", "决策路由",
                "基于进度与行为信号返回下一步行动建议（continue/switch/wrap_up），最终决策在 LLM",
                schema(
                    listOf("current_progress"),
                    "current_progress" to oProp("当前进度，如 {\"understanding_level\": 3}"),
                    "student_signals" to arrProp("行为信号，如 confident/improving/stuck/tired"),
                    "session_time_elapsed" to nProp("已耗时（分钟）", 0.0, null)
                )
            ) { args, _ -> decideNextAction(args) }
        )
        registry.register(
            RegisteredTool(
                "select_teaching_route", "决策路由",
                "在多条教学路径中按学生状态与约束评分选路（建议性质）",
                schema(
                    listOf("available_routes", "student_state"),
                    "available_routes" to arrProp("可选路径"),
                    "student_state" to oProp("如 {\"level\": 3, \"style\": \"analogical\", \"fatigue\": 0.2}"),
                    "constraints" to oProp("约束，如 {\"time_budget\": 15}")
                )
            ) { args, _ -> selectTeachingRoute(args) }
        )
        registry.register(
            RegisteredTool(
                "determine_intervention_urgency", "决策路由",
                "根据情绪/错误严重度/停留时间计算干预紧急度（1-5）与建议动作",
                schema(
                    emptyList(),
                    "student_emotion" to sProp("neutral/frustrated/confused/discouraged/engaged"),
                    "error_severity" to iProp("错误严重程度 1-5", 1, 5),
                    "time_wasted" to nProp("在错误路径上的时间（分钟）", 0.0, null),
                    "self_correction_attempts" to iProp("自我纠正次数", 0, null)
                )
            ) { args, _ -> determineInterventionUrgency(args) }
        )
        registry.register(
            RegisteredTool(
                "plan_session_closure", "决策路由",
                "返回是否应收尾的建议、总结要点、作业与下次预告",
                schema(
                    emptyList(),
                    "session_duration" to nProp("已对话时长（分钟）", 0.0, null),
                    "goals_achieved" to arrProp("已完成目标"),
                    "goals_pending" to arrProp("未完成目标"),
                    "student_fatigue" to nProp("疲劳度 0-1", 0.0, 1.0)
                )
            ) { args, _ -> planSessionClosure(args) }
        )

        // —— 记忆管理（3）——
        registry.register(
            RegisteredTool(
                "log_breakthrough", "记忆管理",
                "把学生从困惑到理解的转折点写入事件存储",
                schema(
                    listOf("concept", "before_state", "after_state", "trigger"),
                    "concept" to sProp("顿悟的概念"),
                    "before_state" to sProp("顿悟前的理解描述"),
                    "after_state" to sProp("顿悟后的理解描述"),
                    "trigger" to sProp("触发顿悟的关键信息"),
                    "timestamp" to sProp("时间戳（可选，默认现在）")
                )
            ) { args, ctx -> logBreakthrough(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "log_struggle_point", "记忆管理",
                "记录反复卡住的点、严重度与历史相似案例",
                schema(
                    listOf("topic", "failed_attempts"),
                    "topic" to sProp("困难主题"),
                    "failed_attempts" to iProp("失败次数", 0, 100),
                    "attempted_strategies" to arrProp("已尝试策略"),
                    "error_pattern" to sProp("错误模式（可选）")
                )
            ) { args, ctx -> logStrugglePoint(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "update_knowledge_graph", "记忆管理",
                "更新知识图谱节点状态与学习进度",
                schema(
                    emptyList(),
                    "mastered_concepts" to arrProp("新掌握概念"),
                    "pending_concepts" to arrProp("待掌握概念"),
                    "relationships" to arrProp("概念关系边", "object")
                )
            ) { args, ctx -> updateKnowledgeGraph(args, ctx) }
        )

        // —— 评估反馈（3）——
        registry.register(
            RegisteredTool(
                "evaluate_session_quality", "评估反馈",
                "评估会话质量：目标达成/参与度/效率/概念深度与改进建议",
                schema(
                    emptyList(),
                    "session_transcript" to arrProp("完整对话记录（可选，缺省取当前会话）", "object"),
                    "initial_goals" to arrProp("初始目标"),
                    "tools_used" to arrProp("使用的工具列表")
                )
            ) { args, ctx -> evaluateSessionQuality(args, ctx) }
        )
        registry.register(
            RegisteredTool(
                "measure_learning_gain", "评估反馈",
                "计算学习前后水平增益、效率与记忆保持预测",
                schema(
                    listOf("pre_assessment", "post_assessment"),
                    "pre_assessment" to oProp("学习前水平，如 {\"level\": 2}"),
                    "post_assessment" to oProp("学习后水平"),
                    "time_spent" to nProp("耗时（分钟）", 0.0, null)
                )
            ) { args, _ -> measureLearningGain(args) }
        )
        registry.register(
            RegisteredTool(
                "detect_teaching_failure", "评估反馈",
                "检测连续无进展的教学失败信号并给出纠正动作",
                schema(
                    listOf("interaction_log"),
                    "interaction_log" to arrProp("近期交互（含轮次与水平）", "object"),
                    "expected_outcomes" to arrProp("预期目标"),
                    "current_strategy" to sProp("当前策略")
                )
            ) { args, _ -> detectTeachingFailure(args) }
        )
    }

    // ==================================================================
    // 1. 事实检索（retrieval.py）
    // ==================================================================

    private fun lookupCurriculum(args: JSONObject, context: ToolContext): JSONObject {
        val catalog = catalogOf(context)
        val concept = str(args, "concept")
        val found = catalog.findInCurriculum(concept, context.session.topic)
        val status = catalog.conceptStatus(concept, context.session)
        val entry = found.entry
            ?: return out(
                "found" to false,
                "concept" to concept,
                "inferred_status" to status.expectedStatus,
                "status_reason" to status.reason,
                "note" to "课标未收录该概念：无学段归属与标准定义可查；状态为按会话事实/水平的推断。"
            )
        return out(
            "found" to true,
            "concept_id" to found.conceptId,
            "subject" to found.subject,
            "grade" to entry.grade,
            "track" to entry.track,
            "difficulty" to entry.difficulty,
            "prerequisites" to jarr(entry.prerequisites),
            "standard_definition" to entry.standardDefinition,
            "key_facts" to jarr(entry.keyFacts),
            "inferred_status" to status.expectedStatus,
            "status_reason" to status.reason,
            "note" to "以上为课标事实与按学生会话推断的知识状态；如何据此回应由你决定。"
        )
    }

    private fun searchMaterials(args: JSONObject, context: ToolContext): JSONObject {
        val store = storeOf(context)
        val query = str(args, "query")
        val scopeParam = str(args, "scope", "all")
        val topK = intArg(args, "top_k", 3).coerceIn(1, 10)
        // 阶段 3 Room 后按 kind 过滤；MaterialStore.search 的 gradeFilter 暂传 null
        val hits: List<MaterialStore.SearchHit> = store.search(query, topK, null)
        val results = JSONArray()
        for (h in hits) {
            results.put(
                out(
                    "id" to h.docId,
                    "title" to h.title,
                    "kind" to "",
                    "subject" to "",
                    "grade" to h.grade,
                    "tags" to jarr(h.tags),
                    "snippet" to h.snippet,
                    "score" to h.score
                )
            )
        }
        return out(
            "query" to query,
            "scope" to scopeParam,
            "total_in_library" to store.count(),
            "results" to results,
            "note" to (if (hits.isNotEmpty()) "命中片段供参考（title/score 供排序理解）；如何使用由你决定"
            else "资料库无相关命中——这是事实：学生的录入资料里没有这个内容；如何据此回应由你决定")
        )
    }

    // ==================================================================
    // 2. 角色扮演（persona.py）
    // ==================================================================

    private fun generateStudentPersona(args: JSONObject, @Suppress("UNUSED_PARAMETER") context: ToolContext): JSONObject {
        val topic = str(args, "topic")
        val rawLevel = num(args, "real_student_level", 1.0)
        val level = clampInt(rawLevel, 1, 5)
        val targetGiven = args.has("target_confusion") && !args.isNull("target_confusion")
        val target = str(args, "target_confusion")
        val grade = str(args, "grade")
        val history = args.optJSONObject("session_history")
        // Python: random.Random(f"{topic}|{real_student_level}|{target_confusion}")
        // real_student_level 经 pydantic 校验为 int → 种子用整数形态字符串；
        // target_confusion 为 None 时 f-string 呈 "None"
        val rng = rngOf(
            topic,
            PyCompat.round(rawLevel).toInt().toString(),
            if (targetGiven) target else "None"
        )
        val (matched, entry) = TopicKnowledgeBase.findConcept(topic)
        val concepts: List<String> =
            if (matched != "_default" && entry.terms.isNotEmpty()) entry.terms
            else listOf("基础概念与定义", "标准方法与公式", "典型例题与套路", "易错点与变式")

        // 掌握状态按 real_student_level 确定性分配；level=5 完全掌握 →
        // confused/unknown 强制为空（与提示词中的水平事实保持一致）
        val mastered = mutableListOf<String>()
        val confused = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        when {
            level >= 5 -> {
                mastered += concepts
            }
            level == 4 -> {
                mastered += concepts
                if (target.isNotEmpty()) confused.add(target)
            }
            level == 3 -> {
                mastered += concepts.take(1)
                if (target.isNotEmpty()) confused.add(target)
                confused += concepts.drop(1).take(1)
                unknown += concepts.drop(2)
            }
            level == 2 -> {
                if (target.isNotEmpty()) confused.add(target)
                confused += concepts.take(1)
                unknown += concepts.drop(1)
            }
            else -> { // level 1
                if (target.isNotEmpty()) confused.add(target)
                unknown += concepts
            }
        }

        val mistakeFrequency = ScoreEngine.clamp(0.5 - level * 0.08, 0.1, 0.5)
        val speakingPatterns: JSONObject = if (level >= 4) {
            // 熟练/掌握型：自信话术，与高水平事实一致
            out(
                "uncertainty_words" to jarr(listOf("我印象中", "我记得", "按理说")),
                "help_seeking_style" to "老师，我想检验一下自己的理解对不对",
                "breakthrough_style" to "哦！原来关键在这里！",
                "mistake_frequency" to mistakeFrequency
            )
        } else {
            // 学习型：困惑话术
            out(
                "uncertainty_words" to jarr(TopicKnowledgeBase.UNCERTAINTY_WORDS.shuffled(rng).take(3)),
                "help_seeking_style" to "老师，能再讲讲吗？",
                "breakthrough_style" to "哦！原来是这样！我懂了！",
                "mistake_frequency" to mistakeFrequency
            )
        }
        val emotionalTraits = out(
            "frustration_threshold" to clampInt((7 - level).toDouble(), 2, 6),
            "enthusiasm_level" to ScoreEngine.clamp(0.5 + level * 0.08, 0.5, 0.9),
            "persistence" to ScoreEngine.clamp(0.6 + level * 0.06, 0.6, 0.9)
        )

        val hasHistory = history != null && history.length() > 0
        var masteredOut: List<String> = mastered.toList()
        if (hasHistory) {
            val prev = strListFrom(history!!, "mastered")
            masteredOut = (prev.toSet() + masteredOut.toSet()).sorted()
        }

        return out(
            "persona_id" to ("persona_" + UUID.randomUUID().toString().replace("-", "").take(10)),
            "topic" to topic,
            "grade" to grade,
            "mastered" to jarr(masteredOut),
            "confused" to jarr(confused),
            "completely_unknown" to jarr(unknown),
            "speaking_patterns" to speakingPatterns,
            "emotional_traits" to emotionalTraits,
            "continuity_note" to (if (hasHistory) "延续上次人设并体现进步" else "全新人设")
        )
    }

    private fun speakAsConfusedStudent(args: JSONObject, context: ToolContext): JSONObject {
        var intensity = str(args, "intensity", "moderate")
        if (intensity !in listOf("mild", "moderate", "severe")) intensity = "moderate"
        val confusionPoint = str(args, "confusion_point")
        val ctxText = str(args, "context")
        val embedMisconception = bool(args, "embed_misconception", false)
        val rng = rngOf(confusionPoint, intensity, ctxText.take(32))

        // 事实判定：按课标 + 对话事实推断该概念应处状态；
        // 工具素材 = 状态的函数（判定优先级：图谱事实 > 学段 > 盲区 > 难度阈值 > 回退）
        val status = catalogOf(context).conceptStatus(confusionPoint, context.session)
        if (status.expectedStatus == "mastered") {
            val head =
                if (ctxText.isNotEmpty()) "结合刚才的内容，「$confusionPoint」我记得挺清楚的："
                else "老师，「$confusionPoint」这部分我印象中是掌握的："
            return out(
                "student_utterance_draft" to (head + "我来说说我的理解，你听听对不对。"),
                "embedded_misconception" to null,
                "expected_teacher_response" to "确认或纠正学生对「$confusionPoint」的理解",
                "follow_up_hints" to jarr(listOf("如果老师讲得和我的理解不同，追问差异在哪里")),
                "boundary_check" to out(
                    "expected_status" to "mastered",
                    "reason" to status.reason,
                    "source" to status.source
                ),
                "note" to "事实：按课标/人设/对话记录该内容已掌握。素材为自信检验型（复述理解请老师确认），非困惑型。"
            )
        }

        // RTSession 无 persona 字段（阶段 3 接人设存储）→ uncertainty_words 用全表、mf=0.3
        val words = TopicKnowledgeBase.UNCERTAINTY_WORDS
        val word = words[rng.nextInt(words.size)]
        val templates = TopicKnowledgeBase.STUDENT_SENTENCE_TEMPLATES.getValue(intensity)
        val template = templates[rng.nextInt(templates.size)]
        var utterance = template
            .replace("{confusion_point}", confusionPoint)
            .replace("{word}", word)

        var embedded: JSONObject? = null
        val mf = 0.3
        // 短路语义对齐 Python：embed_misconception 为 true 时不消耗 rng.random()
        if (embedMisconception || rng.nextDouble() < mf) {
            val (matchedName, _) = TopicKnowledgeBase.findConcept(confusionPoint)
            // Python 运算符优先级：(matched != "_default" and "符号" in matched) or matched == "顶点"
            if (matchedName != "_default" && matchedName.contains("符号") || matchedName == "顶点") {
                embedded = out(
                    "wrong_belief" to "括号里是 (x + b/2) 的时候，顶点横坐标就是 +b/2",
                    "correct_belief" to "顶点横坐标是 -b/(2a)；(x + b/2)² 相当于 (x - (-b/2))²"
                )
                utterance += " 我猜这里顶点应该是 +b/2 吧……"
            }
        }
        return out(
            "student_utterance_draft" to utterance,
            "embedded_misconception" to embedded,
            "expected_teacher_response" to "解释“$confusionPoint”的推导和符号规则",
            "follow_up_hints" to jarr(
                listOf(
                    "如果老师讲不清，可以请老师用一个具体数字的例子演示",
                    "如果老师答对，可以追问边界：如果 b 是负数还成立吗？"
                )
            ),
            "boundary_check" to out(
                "expected_status" to status.expectedStatus,
                "reason" to status.reason,
                "source" to status.source
            ),
            "note" to "此为台词素材，最终学生回复由 LLM 结合人设与上下文生成"
        )
    }

    private fun makeDeliberateMistake(args: JSONObject, context: ToolContext): JSONObject {
        val correctConcept = str(args, "correct_concept")
        val mistakeType = str(args, "mistake_type", "sign_error")
        val conceptDetail = str(args, "concept_detail")
        if (mistakeType !in listOf("boundary_case", "over_generalization", "sign_error", "conceptual_confusion")) {
            throw ToolExecutionException(
                "mistake_type 必须是 boundary_case/over_generalization/sign_error/conceptual_confusion，收到 '$mistakeType'"
            )
        }
        val (name, _) = TopicKnowledgeBase.findConcept(correctConcept + " " + conceptDetail)
        // 边界检查：学生对该概念应处的状态；未学过/困惑中时“故意犯错考老师”不合人设
        // （不会的人无法设可控陷阱），工具仅报告事实供 LLM 自行决定，不拦调用
        val bStatus = catalogOf(context).conceptStatus(correctConcept, context.session)
        val lib = MISTAKE_LIBRARY.getValue(mistakeType)
        return out(
            "wrong_statement" to lib[0],
            "correct_answer" to lib[1],
            "expected_correction" to lib[2],
            "if_uncorrected_action" to lib[3],
            "hint_strength" to ScoreEngine.clamp(num(args, "hint_strength", 0.5), 0.0, 1.0),
            "mistake_type" to mistakeType,
            "concept" to name,
            "boundary_check" to out(
                "expected_status" to bStatus.expectedStatus,
                "reason" to bStatus.reason,
                "note" to ("学生对 correct_concept 的推断状态；若为 not_learned/confused，" +
                    "故意犯错考老师不合人设（不会的人无法设陷阱），" +
                    "可改用困惑表达或换已掌握的概念。是否采纳由你决定。")
            ),
            "teaching_intent" to "测试老师是否真正理解，错误可控且附带正确答案与引导方向"
        )
    }

    private fun expressPartialUnderstanding(args: JSONObject): JSONObject {
        val understoodParts = strList(args, "understood_parts")
        val confusedParts = strList(args, "confused_parts")
        val connectionGap = str(args, "connection_gap")
        val gap = connectionGap.ifEmpty { confusedParts.firstOrNull() ?: "" }
        val action = GAP_ACTION_MAP.entries.firstOrNull { gap.contains(it.key) }?.value ?: GAP_ACTION_DEFAULT
        val parts = understoodParts.take(3).joinToString("、")
        val confused = confusedParts.take(3).joinToString("、")
        return out(
            "student_response_draft" to "老师，${parts}这部分我大概懂了，但${confused}还是对不上——${gap}。能帮我搭个桥吗？",
            "implicit_gaps" to jarr(confusedParts),
            "recommended_teaching_action" to action,
            "note" to "此为台词素材，最终学生回复由 LLM 生成"
        )
    }

    private fun simulateBreakthrough(args: JSONObject): JSONObject {
        val concept = str(args, "concept")
        val previousConfusion = str(args, "previous_confusion")
        val trigger = str(args, "trigger")
        val (name, entry) = TopicKnowledgeBase.findConcept(concept)
        val keyFact = entry.keyFact.ifEmpty { "${concept}的关键逻辑" }
        val testQ = TopicKnowledgeBase.BREAKTHROUGH_TESTS[name]
            ?: TopicKnowledgeBase.BREAKTHROUGH_TESTS.getValue("_default")
        val prev = previousConfusion.ifEmpty { "之前的困惑" }
        // 配方相关顿悟附数值回声校验
        val check = if (name.contains("配方")) MathGuard.verifyCompletingSquare(6.0) else null
        val extra = check?.let { "（例：${it.leftExpression} = ${it.rightExpression}）" } ?: ""
        return out(
            "aha_moment" to "哦！我明白了！${keyFact}！所以${prev}这个问题就通了$extra",
            "new_understanding" to keyFact,
            "gratitude_expression" to "老师你解释得太清楚了！",
            "test_understanding" to testQ,
            "trigger" to trigger,
            "note" to "此为台词素材，最终学生回复由 LLM 生成"
        )
    }

    private fun challengeTeacherExplanation(args: JSONObject): JSONObject {
        val teacherStatement = str(args, "teacher_statement")
        val angle = str(args, "challenge_angle", "boundary_case")
        if (angle !in listOf("boundary_case", "counter_example", "extension", "different_scenario")) {
            throw ToolExecutionException("challenge_angle 非法: '$angle'")
        }
        val (name, _) = TopicKnowledgeBase.findConcept(teacherStatement)
        val lib = CHALLENGE_LIBRARY.getValue(angle)
        return out(
            "student_challenge" to lib.challenge,
            "depth_test_target" to lib.target,
            "acceptable_answers" to jarr(lib.acceptable),
            "if_cant_answer" to lib.hint,
            "difficulty" to clampInt(num(args, "difficulty", 3.0), 1, 5),
            "matched_concept" to name,
            "note" to "此为台词素材，最终学生回复由 LLM 生成"
        )
    }

    // ==================================================================
    // 3. 认知诊断（cognitive.py）
    // ==================================================================

    private fun checkUnderstanding(args: JSONObject): JSONObject {
        val text = str(args, "student_explanation")
        val concept = str(args, "concept")
        val (name, entry) = TopicKnowledgeBase.findConcept(concept)
        val low = normalizeText(text).lowercase()
        val display = if (name == "_default") concept else name

        // 1. conceptual: term coverage（含书写变体，统一在这里算一次）。
        //    非知识库领域（terms 为空）：用「概念复述 + 定义标记」估计，不再恒 0。
        val terms = entry.terms
        val extraTerms = when (name) {
            "顶点" -> listOf("-b/", "b/2", "最高", "最低", "(x-")
            "完全平方式" -> listOf("(x+", "(x-", "²")
            else -> emptyList()
        }
        var hit = terms.count { normalizeText(it).lowercase() in low }
        val conceptual: Double = if (terms.isNotEmpty()) {
            // 书写变体作为额外命中（不扩大分母，避免稀释）
            val matchedTerms = terms.filter { normalizeText(it).lowercase() in low }.toSet()
            hit += extraTerms.count { it in low }
            if (name == "顶点" && VERTEX_RATIO.containsMatchIn(low) && "-b/2a" !in matchedTerms) {
                hit += 1
            }
            ScoreEngine.clamp(hit / max(terms.size, 1) * 1.5, 0.0, 1.0)
        } else {
            val conceptKey = normalizeText(concept).lowercase()
            // 长/复合概念名很难被整短语复述：整短语命中记满；任意 4 字片段命中记部分
            val echo: Double = if (conceptKey.length < 4) {
                if (conceptKey.isNotEmpty() && conceptKey in low) 1.0 else 0.0
            } else {
                val grams = (0 until conceptKey.length - 3).map { conceptKey.substring(it, it + 4) }.toSet()
                val gramHits = grams.count { it in low }
                if (conceptKey in low) 1.0 else (if (gramHits > 0) 0.6 else 0.0)
            }
            val defs = DEFINITION_MARKERS.count { it in text }
            ScoreEngine.clamp(0.15 + 0.45 * echo + 0.2 * min(defs, 2), 0.0, 1.0)
        }

        // 2. procedural: procedure verbs (3+ distinct verbs = fluent)。
        //    未知领域退化为通用动作/步骤词；已知领域仍用数学动词表。
        val procPool = if (terms.isNotEmpty()) PROCEDURE else GENERIC_PROCEDURE
        val procHits = procPool.filter { it in text || it in low }
        val procedural = ScoreEngine.clamp(procHits.size / 3.0, 0.0, 1.0)

        // 3. reasoning: causal connectives (2+ = reasoned)
        val causalHits = CAUSAL.filter { it in text }
        val reasoning = ScoreEngine.clamp(causalHits.size / 2.0, 0.0, 1.0)

        // 4. transfer（设计 v1.1 维度 4）：默认 0.5；解释中出现泛化/推广信号则上调。
        //    启发式不下调（没有可靠的负向信号）。
        val transferHits = TRANSFER.filter { it in text || it in low }
        val transfer = ScoreEngine.clamp(0.5 + 0.2 * min(transferHits.size, 2), 0.0, 1.0)

        // overall_level = min(5, max(1, ceil(mean(dims)*5)))（-1e-9 抵消浮点误差）
        val avg = (conceptual + procedural + reasoning + transfer) / 4.0
        val overallLevel = clampInt(ceil(avg * 5 - 1e-9), 1, 5)

        // tone/confidence from hedging language — 场景 2 的关键信号
        val hedgeHits = HEDGES.filter { it in text }
        val assertiveHits = ASSERTIVES.filter { it in text }
        val confidence = ScoreEngine.clamp(0.5 + 0.12 * assertiveHits.size - 0.15 * hedgeHits.size, 0.05, 0.98)

        val blindSpots = mutableListOf<String>()
        if (conceptual < 0.6) {
            blindSpots += entry.blindSpotLabels.ifEmpty { listOf("${display} 的核心要点未讲清") }
        }
        if (procedural < 0.5) blindSpots.add("操作步骤描述不完整")
        if (reasoning < 0.4) blindSpots.add("缺少因果解释（为什么这么做）")

        val strengths = mutableListOf<String>()
        if (conceptual >= 0.5) strengths.add("覆盖了“$display”的关键要点")
        if (procedural >= 0.5) strengths.add("描述了具体操作步骤")
        if (reasoning >= 0.5) strengths.add("给出了因果推理")

        var toneNote = ""
        if (hedgeHits.isNotEmpty()) {
            toneNote = "检测到不确定语气词 ${pyList(hedgeHits.take(3))}，confidence 降至 " +
                String.format(Locale.ROOT, "%.2f", confidence)
        }
        val suggested = when {
            overallLevel >= 3 && confidence < 0.5 -> "先质疑边界/变式再决定顿悟"
            overallLevel >= 3 && transferHits.isEmpty() -> "先用变式/迁移问题测试，再决定推进"
            blindSpots.isNotEmpty() -> "针对盲区追问"
            else -> "可以推进到顿悟/变式"
        }
        val result = out(
            "concept" to display,
            "overall_level" to overallLevel,
            "dimension_scores" to out(
                "conceptual_understanding" to rd2(conceptual),
                "procedural_fluency" to rd2(procedural),
                "reasoning_ability" to rd2(reasoning),
                "transfer_ability" to rd2(transfer)
            ),
            "blind_spots" to jarr(blindSpots),
            "strengths" to jarr(strengths),
            "confidence" to rd2(confidence),
            "tone_signals" to out(
                "hedges" to jarr(hedgeHits),
                "assertives" to jarr(assertiveHits),
                "note" to toneNote
            ),
            "transfer_signals" to jarr(transferHits),
            "suggested_next_action" to suggested
        )
        val criteria = strList(args, "evaluation_criteria")
        if (criteria.isNotEmpty()) {
            result.put("evaluation_criteria_applied", jarr(criteria))
        }
        return result
    }

    private fun detectMisconception(args: JSONObject): JSONObject {
        val history = strList(args, "dialogue_history")
        val errorPattern = str(args, "error_pattern")
        val joined = (history.joinToString(" ") + " " + errorPattern).lowercase()
        var best: TopicKnowledgeBase.MisconceptionEntry? = null
        var bestHits = 0
        for (mc in TopicKnowledgeBase.MISCONCEPTIONS_QUADRATIC) {
            val hits = mc.patterns.count { it.lowercase() in joined }
            if (hits > bestHits) {
                best = mc
                bestHits = hits
            }
        }
        val b = best ?: return out(
            "misconception_type" to "general_confusion",
            "root_cause" to "未匹配到已知错误模式；解释仍不完整",
            "manifestations" to jarr(listOf("解释中缺少关键步骤或因果连接")),
            "severity" to "low",
            "suggested_intervention" to "继续追问细节，或换一个更简单的例子",
            "prerequisite_gaps" to jarr(emptyList<String>())
        )
        return out(
            "misconception_type" to b.type,
            "root_cause" to b.rootCause,
            "manifestations" to jarr(b.manifestations),
            "severity" to b.severity,
            "suggested_intervention" to b.intervention,
            "prerequisite_gaps" to jarr(b.prerequisiteGaps),
            "matched_patterns" to jarr(b.patterns.filter { it.lowercase() in joined })
        )
    }

    private fun assessReadiness(args: JSONObject): JSONObject {
        val mastery = args.optJSONObject("current_mastery") ?: JSONObject()
        val nextTopic = str(args, "next_topic")
        val levelRaw = mastery.opt("level")
        val level = if (levelRaw != null && levelRaw !== JSONObject.NULL) {
            // Python float(level)：非数字字符串抛 ValueError → 工具错误
            val d = when (levelRaw) {
                is Number -> levelRaw.toDouble()
                is String -> levelRaw.toDoubleOrNull()
                    ?: throw ToolExecutionException("工具内部异常: ValueError: could not convert string to float: '$levelRaw'")
                else -> throw ToolExecutionException("工具内部异常: TypeError: float() argument must be a string or a number")
            }
            clampInt(d, 1, 5)
        } else {
            val mastered = strListFrom(mastery, "mastered")
            if (mastered.size >= 2) 3 else (if (mastered.isNotEmpty()) 2 else 1)
        }
        val prereqs = PREREQ_MAP.entries.firstOrNull { nextTopic.contains(it.key) }?.value ?: PREREQ_DEFAULT
        val masteredList = strListFrom(mastery, "mastered")
        val missing = prereqs.filter { pr ->
            masteredList.none { m -> m.contains(pr) || pr.contains(m) } && level < 4
        }
        val score = ScoreEngine.clamp(level / 5.0 - 0.12 * missing.size, 0.0, 1.0)
        val isReady = score >= 0.6 && missing.isEmpty()
        val gapTime = missing.size * 4 + (if (isReady) 0 else 5)
        var recommendation =
            if (isReady) "可以进入下一阶段"
            else "建议先巩固当前内容" + (if (missing.isNotEmpty()) "，补齐前置缺口" else "")
        val tc = if (args.has("time_constraint") && !args.isNull("time_constraint")) {
            num(args, "time_constraint", 0.0)
        } else null
        if (tc != null && tc < gapTime) {
            recommendation += "（剩余时间 $tc 分钟 < 预计需要 $gapTime 分钟）"
        }
        return out(
            "is_ready" to isReady,
            "readiness_score" to rd2(score),
            "missing_prerequisites" to jarr(missing),
            "estimated_gap_time" to gapTime,
            "recommendation" to recommendation,
            "next_topic" to nextTopic
        )
    }

    private fun identifyLearningStyle(args: JSONObject): JSONObject {
        val interactionLog = strList(args, "interaction_log")
        val joined = interactionLog.joinToString(" ").lowercase()
        val scores = linkedMapOf<String, Double>()
        for ((style, words) in STYLE_SIGNALS) {
            // joined.count(w)：出现次数（对齐 str.count），非布尔
            val hits = words.sumOf { occurrences(joined, it) }
            scores[style] = ScoreEngine.clamp(hits / max(interactionLog.size, 1) * 0.4, 0.0, 1.0)
        }
        val patterns = args.optJSONObject("response_patterns")
        if (patterns != null && patterns.length() > 0) {
            val it = patterns.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = patterns.opt(k)
                if (k in scores && v is Number) {
                    scores[k] = ScoreEngine.clamp((scores.getValue(k) + v.toDouble()) / 2, 0.0, 1.0)
                }
            }
        }
        // Python max(dict, key=...)：插入序首个最大者胜
        var dominant = "logical"
        var bestScore = -1.0
        for ((k, v) in scores) {
            if (v > bestScore) {
                bestScore = v
                dominant = k
            }
        }
        val top = scores[dominant] ?: 0.0
        val confidence = ScoreEngine.clamp(0.4 + top * 0.6, 0.3, 0.95)
        val effectiveness = JSONObject()
        for ((k, v) in scores) effectiveness.put(k, rd2(v))
        return out(
            "dominant_style" to dominant,
            "effectiveness_scores" to effectiveness,
            "recommended_teaching_approach" to STYLE_APPROACH.getValue(dominant),
            "confidence" to rd2(confidence)
        )
    }

    // ==================================================================
    // 4. 教学策略（strategy.py）
    // ==================================================================

    private fun generateProbingQuestions(args: JSONObject): JSONObject {
        val blindSpots = strList(args, "blind_spots")
        val style = str(args, "questioning_style", "guided")
        if (style !in listOf("direct", "guided", "socratic")) {
            throw ToolExecutionException("questioning_style 非法: '$style'")
        }
        val maxQuestions = intArg(args, "max_questions", 3)
        val difficulty = intArg(args, "difficulty", 3)
        val questions = mutableListOf<String>()
        for (bs in blindSpots.take(maxQuestions)) {
            // Python: (k != "_default" and any(w in bs for w in k.split("与"))) or k in bs
            val key = TopicKnowledgeBase.PROBING_QUESTIONS.keys.firstOrNull { k ->
                (k != "_default" && k.split("与").any { bs.contains(it) }) || bs.contains(k)
            }
            val set = TopicKnowledgeBase.PROBING_QUESTIONS[key ?: "_default"]
                ?: TopicKnowledgeBase.PROBING_QUESTIONS.getValue("_default")
            val q = when (style) {
                "direct" -> set.direct
                "guided" -> set.guided
                "socratic" -> set.socratic
                else -> set.generic
            }.ifEmpty { set.generic }
            if (q !in questions) questions.add(q)
        }
        if (questions.isEmpty()) {
            // 回退无 generic 兜底：直接取 _default 的对应风格
            val d = TopicKnowledgeBase.PROBING_QUESTIONS.getValue("_default")
            questions.add(
                when (style) {
                    "direct" -> d.direct
                    "guided" -> d.guided
                    "socratic" -> d.socratic
                    else -> d.generic
                }
            )
        }
        return out(
            "questions" to jarr(questions.take(maxQuestions)),
            "reasoning" to "针对盲区 ${pyList(blindSpots)} 用 $style 风格提问，暴露理解深度",
            "expected_duration" to (2 * questions.size + difficulty),
            "alternative_questions" to jarr(
                listOf(
                    TopicKnowledgeBase.PROBING_QUESTIONS.getValue("_default").guided,
                    "老师，你能用 x = 1 验证一下你说的式子吗？"
                )
            )
        )
    }

    private fun createTeachingLadder(args: JSONObject): JSONObject {
        val level = clampInt(num(args, "student_level", 3.0), 1, 5)
        val steps = TopicKnowledgeBase.TEACHING_LADDER_QUADRATIC.toMutableList()
        if (level >= 4) { // skip basics for strong students
            steps.removeAll { it.stepId <= 1 }
        }
        val budget = if (args.has("time_budget") && !args.isNull("time_budget")) {
            num(args, "time_budget", 0.0)
        } else null
        var outSteps = steps
        if (budget != null) {
            var total = outSteps.sumOf { it.estimatedTime }
            while (total > budget && outSteps.size > 1) {
                total -= outSteps.removeAt(0).estimatedTime
            }
            outSteps = outSteps.map { s ->
                s.copy(estimatedTime = max(2, PyCompat.round(s.estimatedTime * 0.8).toInt()))
            }.toMutableList()
        }
        outSteps = outSteps.mapIndexed { idx, s -> s.copy(stepId = idx + 1) }.toMutableList()
        val stepsJson = JSONArray()
        for (s in outSteps) {
            stepsJson.put(
                out(
                    "step_id" to s.stepId,
                    "description" to s.description,
                    "activities" to jarr(s.activities),
                    "estimated_time" to s.estimatedTime,
                    "success_criteria" to s.successCriteria
                )
            )
        }
        return out(
            "start_point" to str(args, "start_point"),
            "end_goal" to str(args, "end_goal"),
            "steps" to stepsJson,
            "checkpoints" to jarr(outSteps.map { "步骤 ${it.stepId} 完成后请老师复述要点" }),
            "total_estimated_time" to outSteps.sumOf { it.estimatedTime },
            "contingency_plan" to "如果老师卡在某步，回到上一步换一个更小的数字例子"
        )
    }

    private fun designCounterexample(args: JSONObject): JSONObject {
        val claim = str(args, "student_claim")
        val misconceptionType = str(args, "misconception_type")
        val ce = TopicKnowledgeBase.COUNTEREXAMPLES.firstOrNull { e ->
            e.match.any { claim.lowercase().contains(it.lowercase()) }
        }
        val counterexample: String
        val correctVersion: String
        val followUpHint: String
        if (ce == null) {
            // generic numeric-verification counterexample
            counterexample = "我们取一个具体数值代入检验：如果取 x = 1 两边不相等，说明这个说法不能对所有情况成立"
            correctVersion = "需要限定成立条件，或修正表达式后才对一般情形成立"
            followUpHint = "请老师代入一个具体数值（如 x = 1）验证"
        } else {
            counterexample = ce.counterexample
            correctVersion = ce.correctVersion
            followUpHint = ce.followUpHint
        }
        return out(
            "counterexample" to counterexample,
            "explanation" to "老师声称：“${claim.take(80)}”，可以用反例引导其自我修正",
            "follow_up_hint" to followUpHint,
            "correct_version" to correctVersion,
            "misconception_type" to misconceptionType.ifEmpty { "unspecified" }
        )
    }

    private fun suggestAnalogy(args: JSONObject): JSONObject {
        val abstractConcept = str(args, "abstract_concept")
        val (name, _) = TopicKnowledgeBase.findConcept(abstractConcept)
        var entry = TopicKnowledgeBase.ANALOGIES[name]
            ?: TopicKnowledgeBase.ANALOGIES.getValue("_default")
        val used = strList(args, "previous_analogies")
        if (name != "_default" && entry.analogy in used) {
            entry = TopicKnowledgeBase.ANALOGIES.getValue("_default")
        }
        return out(
            "analogy" to entry.analogy,
            "mapping" to jobj(entry.mapping),
            "limitations" to jarr(entry.limitations),
            "extension_questions" to jarr(entry.extensionQuestions),
            "matched_concept" to name,
            "background_note" to str(args, "student_background").ifEmpty { "未提供背景，使用通用类比" }
        )
    }

    private fun scaffoldProblem(args: JSONObject): JSONObject {
        val style = str(args, "scaffolding_style", "moderate")
        if (style !in listOf("minimal", "moderate", "heavy")) {
            throw ToolExecutionException("scaffolding_style 非法: '$style'")
        }
        val prob = str(args, "target_problem")
        // build sub-problems from the problem content
        val regexX2 = Regex("x\\s*[²2]")
        var subs: List<String>
        var hints: List<String>
        if (regexX2.containsMatchIn(prob) || prob.contains("配方")) {
            subs = listOf(
                "先试试把 x² + 6x 配方：补上多少能变成完全平方式？",
                "补上之后，减掉多少式子才和原来相等？",
                "对照 y = a(x - h)² + k，这个式子的顶点是多少？"
            )
            hints = listOf(
                "提示：想想 (x + ?)² 展开后的中间项是 2·?·x",
                "提示：补上的角是借来的，要减回去（(b/2)²）",
                "提示：(x - h)² 的顶点横坐标是 h；x + 3 = x - (-3)"
            )
        } else if (prob.contains("判别式") || prob.contains("Δ") || prob.lowercase().contains("delta")) {
            subs = listOf(
                "先算出 Δ = b² - 4ac 的数值",
                "Δ 的符号和 x 轴交点个数是什么关系？",
                "把这个关系说成一般规律"
            )
            hints = listOf("提示：Δ>0、=0、<0 三种情况", "提示：联立 y = 0 看方程的根", "提示：从具体例子归纳")
        } else {
            subs = listOf(
                "先用一个最简单的具体例子走一遍",
                "指出这一步依赖的前置结论",
                "把例子推广到一般形式"
            )
            hints = listOf("提示：数字小一点更容易看清结构", "提示：每一步都要说清“为什么”", "提示：用字母代替具体数字")
        }
        val cap = clampInt(num(args, "student_capability", 3.0), 1, 5)
        if (style == "minimal" && cap >= 3) {
            subs = subs.takeLast(1)
            hints = hints.takeLast(1)
        } else if (style == "heavy" || cap <= 2) {
            subs = listOf("先复习需要的公式/定义") + subs
            hints = listOf("提示：先回忆相关定义") + hints
        }
        return out(
            "sub_problems" to jarr(subs),
            "hints_per_step" to jarr(hints.take(subs.size)),
            "final_synthesis" to "把每个子问题的答案串起来，就能自己走通整条推导",
            "estimated_difficulty_reduction" to rd2(
                when (style) {
                    "minimal" -> 0.3
                    "heavy" -> 0.8
                    else -> 0.6
                }
            )
        )
    }

    // ==================================================================
    // 5. 决策路由（routing.py）
    // ==================================================================

    private fun decideNextAction(args: JSONObject): JSONObject {
        val progress = args.optJSONObject("current_progress") ?: JSONObject()
        val level = clampInt(
            pyFloatOrFallback(progress.opt("understanding_level"), 3.0), 1, 5
        )
        val sig = strList(args, "student_signals").map { it.lowercase() }
        val t = num(args, "session_time_elapsed", 0.0)

        // design v1.1 rule set (suggestion only — LLM makes the final call)
        val action: String
        val reason: String
        when {
            "tired" in sig || t > 40 -> {
                action = "wrap_up"; reason = "时间较长或老师显出疲惫，建议收尾"
            }
            "stuck" in sig && t > 15 -> {
                action = "switch_strategy"; reason = "老师陷入停滞且耗时较长，建议换教学方式"
            }
            level >= 4 && "confident" in sig -> {
                action = "switch_topic"; reason = "理解到位且自信，可推进新内容"
            }
            level >= 4 -> {
                action = "continue"; reason = "理解已接近掌握，继续当前主线巩固"
            }
            level == 3 && "improving" in sig -> {
                action = "continue"; reason = "老师正在取得进展，趁热打铁"
            }
            "stuck" in sig -> {
                action = "switch_strategy"; reason = "老师卡住，建议换类比/脚手架方式"
            }
            else -> {
                action = "continue"; reason = "默认继续当前策略"
            }
        }
        return out(
            "action" to action,
            "reasoning" to reason,
            "confidence" to rd2(ScoreEngine.clamp(0.5 + 0.1 * sig.size, 0.4, 0.9)),
            "next_target" to "understanding_level=$level 下的下一步教学焦点",
            "note" to "本工具仅返回建议，最终决策由 LLM 结合上下文做出"
        )
    }

    private data class RouteScore(val score: Double, val route: String, val cost: Int)

    private fun selectTeachingRoute(args: JSONObject): JSONObject {
        val routes = strList(args, "available_routes")
        if (routes.isEmpty()) {
            throw ToolExecutionException("参数校验失败: available_routes 不能为空")
        }
        val state = args.optJSONObject("student_state") ?: JSONObject()
        val level = clampInt(pyFloatOrFallback(state.opt("level"), 3.0), 1, 5)
        val style = str(state, "style")
        val fatigue = ScoreEngine.clamp(pyFloatOrFallback(state.opt("fatigue"), 0.0), 0.0, 1.0)
        val constraints = args.optJSONObject("constraints")
        val budget = if (constraints != null && constraints.has("time_budget") && !constraints.isNull("time_budget")) {
            when (val raw = constraints.opt("time_budget")) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                    ?: throw ToolExecutionException("工具内部异常: ValueError: could not convert string to float: '$raw'")
                else -> throw ToolExecutionException("工具内部异常: TypeError: float() argument must be a string or a number")
            }
        } else null

        val scored = mutableListOf<RouteScore>()
        for (r in routes) {
            val rl = r.lowercase()
            val cost = ROUTE_COST.entries.firstOrNull { rl.contains(it.key) }?.value ?: 12
            var score = 0.5
            if (rl.contains("analogy") && style == "analogical") score += 0.3
            if (rl.contains("visual") && style == "visual") score += 0.3
            if (rl.contains("deep")) score += if (level >= 3) 0.15 else -0.2
            if (rl.contains("quick")) score += if (level >= 4 || fatigue > 0.6) 0.1 else -0.05
            if (budget != null && cost > budget) score -= 0.4
            score -= if (rl.contains("deep")) fatigue * 0.1 else 0.0
            scored.add(RouteScore(rd2(ScoreEngine.clamp(score, 0.0, 1.0)), r, cost))
        }
        // Python: scored.sort(reverse=True) —— (score, route, cost) 元组全降序
        scored.sortWith(
            compareByDescending<RouteScore> { it.score }
                .thenByDescending { it.route }
                .thenByDescending { it.cost }
        )
        val best = scored[0]
        val routeScores = JSONArray()
        for (s in scored) {
            routeScores.put(out("route" to s.route, "score" to s.score, "est_minutes" to s.cost))
        }
        return out(
            "chosen_route" to best.route,
            "alternative_routes" to jarr(scored.drop(1).map { it.route }),
            "route_scores" to routeScores,
            "tradeoffs" to "选定 ${best.route}（约 ${best.cost} 分钟，匹配度 ${best.score}）；更快的路径理解更浅，更深的路径耗时更长",
            "expected_outcome" to "学生能沿该路径达成当前教学目标（水平 $level → 更高）",
            "note" to "本工具仅返回建议，最终决策由 LLM 做出"
        )
    }

    private fun determineInterventionUrgency(args: JSONObject): JSONObject {
        val emotion = str(args, "student_emotion", "neutral")
        val severity = intArg(args, "error_severity", 2)
        val timeWasted = num(args, "time_wasted", 0.0)
        val attempts = intArg(args, "self_correction_attempts", 0)
        val emotionBase = EMOTION_BASE[emotion] ?: 1.5
        val raw = emotionBase + severity * 0.5 + timeWasted * 0.2 - attempts * 0.8
        val urgency = clampInt(raw, 1, 5)
        return out(
            "urgency_level" to urgency,
            "suggested_action" to URGENCY_ACTIONS.getValue(urgency),
            "reason" to "情绪=$emotion，错误严重度=$severity，已停留 $timeWasted 分钟，自我纠正 $attempts 次",
            "wait_time" to max(0, 3 - urgency)
        )
    }

    private fun planSessionClosure(args: JSONObject): JSONObject {
        val goalsAchieved = strList(args, "goals_achieved")
        val goalsPending = strList(args, "goals_pending")
        val duration = num(args, "session_duration", 0.0)
        val fatigue = ScoreEngine.clamp(num(args, "student_fatigue", 0.3), 0.0, 1.0)
        val shouldClose = goalsAchieved.isNotEmpty() &&
            (fatigue >= 0.6 || goalsAchieved.size >= 2 || duration >= 30)
        val homework = goalsAchieved.take(2).map { "练习：$it 的变式题（换一组数字做一遍）" }.toMutableList()
        if (homework.isEmpty()) {
            homework.add("练习：求 y = x² + 4x + 3 的顶点坐标（提示：配方为 (x + 2)² - 1）")
        }
        if (goalsPending.isNotEmpty()) {
            homework.add("思考：${goalsPending[0]}——下次从这里继续")
        }
        return out(
            "should_close" to shouldClose,
            "summary_points" to jarr(
                goalsAchieved.map { "已达成：$it" }.ifEmpty { listOf("本次以建立基础理解为主") }
            ),
            "homework" to jarr(homework),
            "next_session_preview" to (
                if (goalsPending.isEmpty()) "下次可以学习判别式与图像交点的几何意义"
                else "下次优先解决：${goalsPending[0]}"
                ),
            "encouragement" to "你今天的讲解思路很清晰，继续保持！",
            "fatigue" to fatigue,
            "note" to "本工具仅返回收尾建议，是否结束由 LLM/用户决定"
        )
    }

    // ==================================================================
    // 6. 记忆管理（memory.py）
    // ==================================================================

    private fun logBreakthrough(args: JSONObject, context: ToolContext): JSONObject {
        val sink = sinkOf(context)
        val concept = str(args, "concept")
        val (_, entry) = TopicKnowledgeBase.findConcept(concept)
        val related = listOf(concept) + entry.terms.take(2)
        val eventId = sink.nextEventId()
        sink.events.add(
            out(
                "event_id" to eventId,
                "event_type" to "breakthrough",
                "concept" to concept,
                "before" to str(args, "before_state"),
                "after" to str(args, "after_state"),
                "trigger" to str(args, "trigger"),
                "round" to context.session.currentRound
            )
        )
        return out(
            "event_id" to eventId,
            "saved_to" to "session memory（阶段 3 迁 Room events 表）",
            "related_concepts" to jarr(related)
        )
    }

    private fun logStrugglePoint(args: JSONObject, context: ToolContext): JSONObject {
        val sink = sinkOf(context)
        val topic = str(args, "topic")
        val attempts = intArg(args, "failed_attempts", 0).coerceIn(0, 100)
        val severity = if (attempts <= 1) "low" else (if (attempts <= 3) "medium" else "high")
        val eventId = sink.nextEventId()
        sink.events.add(
            out(
                "event_id" to eventId,
                "event_type" to "struggle",
                "concept" to topic,
                "failed_attempts" to attempts,
                "attempted_strategies" to jarr(strList(args, "attempted_strategies")),
                "error_pattern" to str(args, "error_pattern"),
                "severity" to severity,
                "round" to context.session.currentRound
            )
        )
        return out(
            "struggle_id" to eventId,
            "severity" to severity,
            "recommended_intervention" to (
                if (attempts >= 2) "改用类比或脚手架，而不是重复代数推导"
                else "继续观察，先换一种提问方式"
                ),
            // 阶段 3 接 Room query_events 后补齐历史相似案例
            "similar_historical_cases" to jarr(emptyList<JSONObject>())
        )
    }

    private fun updateKnowledgeGraph(args: JSONObject, context: ToolContext): JSONObject {
        val catalog = catalogOf(context)
        val sink = sinkOf(context)
        val session = context.session
        val builder = KnowledgeGraphBuilder(catalog)
        val relationships = objList(args, "relationships").map { e ->
            KnowledgeGraphBuilder.KgEdge(e.optString("from"), e.optString("to"), e.optString("type"))
        }
        val result = builder.updateGraph(
            KnowledgeGraphBuilder.GraphUpdateInput(
                graph = KnowledgeGraphBuilder.KgGraph(
                    nodes = session.knowledgeGraphNodes.map { n ->
                        KnowledgeGraphBuilder.KgNodeFull(n.id, n.status, n.level)
                    }
                ),
                oldMastered = session.masteredConcepts.toList(),
                oldConfused = session.confusedConcepts.toList(),
                addMastered = strList(args, "mastered_concepts"),
                addPending = strList(args, "pending_concepts"),
                relationships = relationships
            )
        )
        // 记入 sink，由 ToolLoop 在轮末合并回写 RTSession
        sink.masteredUpdates.addAll(result.masteredConcepts)
        sink.confusedUpdates.addAll(result.confusedConcepts)
        sink.latestGraphNodes.clear()
        for (n in result.graph.nodes) {
            sink.latestGraphNodes.add(
                com.reversetutor.feature.reversetutor.session.KgNode(
                    id = n.id,
                    status = n.status,
                    level = n.level
                )
            )
        }
        val nodesJson = JSONArray()
        for (n in result.graph.nodes) {
            nodesJson.put(
                out(
                    "id" to n.id,
                    "status" to n.status,
                    "level" to n.level,
                    "grade" to n.grade,
                    "difficulty" to n.difficulty,
                    "prerequisites" to (n.prerequisites?.let { jarr(it) })
                )
            )
        }
        val edgesJson = JSONArray()
        for (e in result.graph.edges) {
            edgesJson.put(out("from" to e.from, "to" to e.to, "type" to e.type))
        }
        return out(
            "graph_snapshot" to out("nodes" to nodesJson, "edges" to edgesJson),
            "next_optimal_concepts" to jarr(result.nextOptimalConcepts),
            "learning_progress" to result.learningProgress,
            "weak_areas" to jarr(result.weakAreas)
        )
    }

    // ==================================================================
    // 7. 评估反馈（evaluation.py）
    // ==================================================================

    private fun evaluateSessionQuality(args: JSONObject, context: ToolContext): JSONObject {
        val session = context.session
        val transcriptArr = args.optJSONArray("session_transcript")
        // Python or 链：p.session_transcript or session.get("messages") or []——
        // 显式空数组同样视为 falsy 回退；RTSession 暂无消息列表，由 ToolLoop 经
        // extra["userMsgCount"] 提供事实
        val userMsgCount: Int = if (transcriptArr != null && transcriptArr.length() > 0) {
            var c = 0
            for (i in 0 until transcriptArr.length()) {
                val m = transcriptArr.optJSONObject(i) ?: continue
                if (m.optString("role") == "user") c++
            }
            c
        } else {
            (context.extra["userMsgCount"] as? Int) ?: 0
        }
        val toolsUsed = strList(args, "tools_used")
        val toolDiversity =
            (if (toolsUsed.isNotEmpty()) toolsUsed else session.toolTrace.map { it.tool }).distinct().size
        val result = ScoreEngine.evaluateSessionQuality(
            ScoreEngine.QualityInput(
                currentRound = session.currentRound,
                userMsgCount = userMsgCount,
                toolDiversity = toolDiversity,
                breakthroughs = session.breakthroughEvents.size,
                struggles = session.struggleEvents.size,
                understandingLevel = session.understandingLevel,
                toolsUsed = toolsUsed
            )
        )
        return out(
            "quality_score" to rd2(result.qualityScore),
            "dimension_scores" to out(
                "goal_achievement" to rd2(result.goalAchievement),
                "student_engagement" to rd2(result.studentEngagement),
                "teaching_efficiency" to rd2(result.teachingEfficiency),
                "conceptual_depth" to rd2(result.conceptualDepth)
            ),
            "highlights" to jarr(result.highlights),
            "missed_opportunities" to jarr(result.missedOpportunities),
            "improvement_suggestions" to jarr(
                listOf(
                    "顿悟后立即给一个变式题巩固",
                    "在 confidence 偏低时优先追问边界而不是推进新内容"
                )
            )
        )
    }

    private fun measureLearningGain(args: JSONObject): JSONObject {
        val preRaw = args.opt("pre_assessment")
        val postRaw = args.opt("post_assessment")
        val pre = levelOfAssessment(preRaw)
        val post = levelOfAssessment(postRaw)
        val gain = ScoreEngine.clamp((post - pre) / 5.0 + (if (post > pre) 0.02 else 0.0), 0.0, 1.0)
        val hours = max(num(args, "time_spent", 0.0), 1.0) / 60.0
        val efficiency = if (gain > 0) rd2(gain / hours) else 0.0
        val preDims = (preRaw as? JSONObject)?.optJSONObject("dimension_scores")
        val postDims = (postRaw as? JSONObject)?.optJSONObject("dimension_scores")
        val keys = linkedSetOf<String>()
        preDims?.let { keys.addAll(it.keys().asSequence().toList()) }
        postDims?.let { keys.addAll(it.keys().asSequence().toList()) }
        val growth = mutableListOf<String>()
        val regression = mutableListOf<String>()
        for (k in keys) {
            val a = preDims?.opt(k)?.let { pyFloatOrZero(it) } ?: 0.0
            val b = postDims?.opt(k)?.let { pyFloatOrZero(it) } ?: 0.0
            if (b - a >= 0.1) growth.add(k) else if (a - b >= 0.1) regression.add(k)
        }
        if (growth.isEmpty() && post > pre) growth.add("整体理解水平")
        return out(
            "gain_score" to rd2(gain),
            "pre_level" to pre,
            "post_level" to post,
            "growth_areas" to jarr(growth),
            "regression_areas" to jarr(regression),
            "learning_efficiency" to efficiency,
            "retention_prediction" to rd2(
                ScoreEngine.clamp(0.4 + gain * 0.8 - (if (post - pre >= 2) 0.1 else 0.0), 0.1, 0.95)
            )
        )
    }

    private fun detectTeachingFailure(args: JSONObject): JSONObject {
        val log = objList(args, "interaction_log")
        val items = log.map { m ->
            val lvlRaw = when {
                m.has("understanding_level") && !m.isNull("understanding_level") -> m.opt("understanding_level")
                m.has("level") && !m.isNull("level") -> m.opt("level")
                else -> null
            }
            ScoreEngine.InteractionItem(
                understandingLevel = lvlRaw?.let { levelOfAssessment(it) },
                emotion = str(m, "emotion")
            )
        }
        val result = ScoreEngine.detectTeachingFailure(items, str(args, "current_strategy"))
        return out(
            "is_failing" to result.isFailing,
            "failure_points" to jarr(result.failurePoints),
            "probable_causes" to jarr(result.probableCauses),
            "corrective_actions" to jarr(result.correctiveActions),
            "alternative_strategies" to jarr(
                listOf(
                    "suggest_analogy（用类比）", "scaffold_problem（拆解问题）",
                    "create_teaching_ladder（小步爬梯）"
                )
            ),
            "stall_rounds" to result.stallRounds,
            "current_strategy" to result.currentStrategy
        )
    }

    // ==================================================================
    // 辅助：JSON 构造
    // ==================================================================

    /** 统一输出构造：null → JSONObject.NULL；List/Map → JSONArray/JSONObject。 */
    private fun out(vararg fields: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in fields) o.put(k, wrapJson(v))
        return o
    }

    private fun wrapJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is JSONObject -> v
        is JSONArray -> v
        is List<*> -> jarr(v)
        is Map<*, *> -> jobj(v)
        else -> v
    }

    private fun jarr(items: List<*>): JSONArray {
        val a = JSONArray()
        for (it in items) a.put(wrapJson(it))
        return a
    }

    private fun jobj(m: Map<*, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), wrapJson(v))
        return o
    }

    // ---- schema 构造 ----

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

    private fun nProp(desc: String, min: Double? = null, max: Double? = null): JSONObject {
        val o = JSONObject().put("type", "number").put("description", desc)
        if (min != null) o.put("minimum", min)
        if (max != null) o.put("maximum", max)
        return o
    }

    private fun bProp(desc: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", desc)

    private fun arrProp(desc: String, itemType: String = "string"): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", JSONObject().put("type", itemType))
            .put("description", desc)

    private fun oProp(desc: String): JSONObject =
        JSONObject().put("type", "object").put("description", desc)

    // ---- 参数读取 ----

    private fun str(args: JSONObject, key: String, default: String = ""): String {
        val v = args.opt(key) ?: return default
        if (v === JSONObject.NULL) return default
        return v.toString()
    }

    private fun num(args: JSONObject, key: String, default: Double): Double {
        if (!args.has(key)) return default
        val v = args.opt(key)
        return when {
            v == null || v === JSONObject.NULL -> default
            v is Number -> v.toDouble()
            v is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun intArg(args: JSONObject, key: String, default: Int): Int =
        PyCompat.round(num(args, key, default.toDouble())).toInt()

    private fun bool(args: JSONObject, key: String, default: Boolean): Boolean {
        if (!args.has(key)) return default
        val v = args.opt(key)
        return when {
            v == null || v === JSONObject.NULL -> default
            v is Boolean -> v
            v is Number -> v.toDouble() != 0.0
            v is String -> v.equals("true", ignoreCase = true) || v == "1" || v == "True"
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

    private fun strListFrom(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.opt(i)
            if (v == null || v === JSONObject.NULL) null else v.toString()
        }
    }

    private fun objList(args: JSONObject, key: String): List<JSONObject> {
        val arr = args.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    // ---- 数值/格式化 ----

    /** 对齐 base.py clamp_int：先 round（银行家舍入）再 coerce 到界内。 */
    private fun clampInt(v: Double, lo: Int, hi: Int): Int =
        PyCompat.round(v).toInt().coerceIn(lo, hi)

    private fun clampInt(v: Int, lo: Int, hi: Int): Int = v.coerceIn(lo, hi)

    private fun rd2(v: Double): Double = PyCompat.round(v, 2)

    /** Python `_level_of(v, default=1)`：dict → v["level"]；float 失败 → default；clamp_int(·,1,5)。 */
    private fun levelOfAssessment(v: Any?, default: Int = 1): Int {
        val raw = when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> if (v.has("level") && !v.isNull("level")) v.opt("level") else null
            else -> v
        }
        return when (raw) {
            null -> default
            is Number -> ScoreEngine.levelOf(raw.toDouble(), default)
            is String -> if (raw.toDoubleOrNull() == null) default else ScoreEngine.levelOf(raw.toDouble(), default)
            else -> default
        }
    }

    /** 对齐 Python `float(x) or fallback` 的 `or` 语义：0/None/"" → fallback；非数字字符串 → ValueError。 */
    private fun pyFloatOrFallback(raw: Any?, fallback: Double): Double {
        val d = when {
            raw == null || raw === JSONObject.NULL -> fallback
            raw is Number -> raw.toDouble()
            raw is Boolean -> if (raw) 1.0 else 0.0
            raw is String ->
                if (raw.isEmpty()) fallback
                else raw.toDoubleOrNull()
                    ?: throw ToolExecutionException("工具内部异常: ValueError: could not convert string to float: '$raw'")
            else -> throw ToolExecutionException("工具内部异常: TypeError: float() argument must be a string or a number")
        }
        return if (d == 0.0) fallback else d
    }

    /** 对齐 Python `float(pre_dims.get(k, 0) or 0)`：falsy → 0；非数字字符串/容器 → ValueError。 */
    private fun pyFloatOrZero(raw: Any?): Double = when {
        raw == null || raw === JSONObject.NULL -> 0.0
        raw is Number -> raw.toDouble()
        raw is Boolean -> if (raw) 1.0 else 0.0
        raw is String ->
            if (raw.isEmpty()) 0.0
            else raw.toDoubleOrNull()
                ?: throw ToolExecutionException("工具内部异常: ValueError: could not convert string to float: '$raw'")
        else -> throw ToolExecutionException("工具内部异常: TypeError: float() argument must be a string or a number")
    }

    /** 对齐 str.count（重叠不计数，与 split 法一致）。 */
    private fun occurrences(haystack: String, needle: String): Int =
        if (needle.isEmpty()) 0 else haystack.split(needle).size - 1

    /** Python f-string 中 list 的 repr（['a', 'b']；空 → []）。 */
    private fun pyList(items: List<String>): String =
        if (items.isEmpty()) "[]" else items.joinToString("', '", prefix = "['", postfix = "']")

    private fun normalizeText(text: String): String =
        text.replace("−", "-").replace("–", "-").replace("—", "-")
            .replace(" ", "").replace("\u3000", "")

    /** 稳定 RNG 种子（Python 侧 hash 随机化本就不跨进程稳定；此处可复现）。 */
    private fun rngOf(vararg parts: String): Random {
        var h = 1125899906842597L
        for (c in parts.joinToString("|")) h = h * 31 + c.code
        return Random(h)
    }

    // ---- context 取件 ----

    private fun catalogOf(context: ToolContext): CurriculumCatalog =
        context.curriculumCatalog
            ?: throw ToolExecutionException("课标目录不可用（ctx 缺 curriculumCatalog）")

    private fun storeOf(context: ToolContext): MaterialStore =
        context.materialStore
            ?: throw ToolExecutionException("store 不可用（ctx 缺 store）")

    private fun sinkOf(context: ToolContext): SessionStateSink =
        (context.extra[KEY_SESSION_STATE_SINK] as? SessionStateSink) ?: SessionStateSink()
}

/**
 * 会话状态汇聚（阶段 3 迁 Room）：记忆工具把事件与掌握/困惑更新写入此处，
 * ToolLoop 在每轮结束后合并回写 RTSession（mastered/confused 并集、事件入列）。
 * ToolContext.extra 未注入时，各记忆工具退化为一次性 sink（事件即弃）。
 */
class SessionStateSink {
    val events = mutableListOf<JSONObject>()
    val masteredUpdates = linkedSetOf<String>()
    val confusedUpdates = linkedSetOf<String>()
    val latestGraphNodes = mutableListOf<com.reversetutor.feature.reversetutor.session.KgNode>()
    private var eventCounter = 0

    /** 事件自增 ID（对齐 SQLite events 表 rowid 语义）。 */
    fun nextEventId(): Int = ++eventCounter
}
