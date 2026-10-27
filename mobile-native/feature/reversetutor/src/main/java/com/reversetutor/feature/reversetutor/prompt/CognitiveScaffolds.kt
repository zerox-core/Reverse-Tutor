package com.reversetutor.feature.reversetutor.prompt

import com.reversetutor.core.llm.MonologueEnvelope

/**
 * 结构化推理骨架（Type 2）——提示词注入的强制推理结构。
 *
 * ## 与 Type 1（确定性能力工具）的分工
 *
 * - **Type 1**（`CoreTools` 里的 function call）：真跑算法/检索/写库，返回客观事实。
 * - **Type 2**（本文件）：不调用、不返回事实，而是**约束模型推理的形式**。
 *   模型必须先按槽位填完判定，才允许输出学生台词。
 *
 * ## 为什么这不违反「禁止拆东墙补西墙」红线
 *
 * 胶带式补丁 = 针对某个**具体现象**追加一句咒语（"不要说 XXX"），换场景即失效。
 * 骨架 = 针对**一整类认知动作**定义输出契约，任何学科都受同一套约束。
 *
 * 判据：**删掉它，模型的输出结构会退化**（不再区分"依据"与"保证"、不再分级反馈）
 * → 是骨架。只是某句话不说了 → 是胶带。
 *
 * ## 设计取舍：用「菜单 + 选择」而非「30 条全量背诵」
 *
 * 30 条骨架逐条注入会占 3000+ token 且稀释注意力。改为：
 * 1. 给出骨架菜单（每条一行：id + 一句话触发条件）；
 * 2. 强制模型每轮**自选 1~3 条**并填对应槽位；
 * 3. 未选中任何一条时，必须显式说明"本轮无认知阻力"。
 *
 * 试金石不变：删掉菜单，模型就不会再输出「选了哪条、依据是什么」的结构。
 */
object CognitiveScaffolds {

    /**
     * 七大家族。每族是同一类「认知失败」的一组算子。
     * 族内 id 前缀用于模型引用，如 `F1.boundary`。
     */
    enum class Family(val id: String, val title: String, val failureItTreats: String) {
        F1("F1", "边界与失效", "这套推演/主张在哪个角落会崩"),
        F2("F2", "隐藏前提显影", "什么被默认了却没说"),
        F3("F3", "具象与抽象配平", "是不是在名词对飚、有没有落地"),
        F4("F4", "目标与验收", "到底要抵达什么、怎么算抵达"),
        F5("F5", "节奏与负荷", "一步跨多大、脚手架该不该撤"),
        F6("F6", "提取与流畅错觉", "听懂了不等于学会了"),
        F7("F7", "问题的合法性", "这个问法配不配得到答案")
    }

    /**
     * 单条骨架。
     *
     * @param id        模型引用用的稳定标识
     * @param title     一句话名称
     * @param trigger   什么时候该选它（写具体可判定的条件，不写"视情况"）
     * @param slots     选中后必须填的输出槽位；填不出来说明这条不适用
     * @param source    理论出处，便于回溯
     */
    data class Scaffold(
        val id: String,
        val family: Family,
        val title: String,
        val trigger: String,
        val slots: List<String>,
        val source: String
    )

    // ------------------------------------------------------------------
    // 30 条骨架全量清单
    // ------------------------------------------------------------------

    val all: List<Scaffold> = listOf(
        // ---- F1 边界与失效 ----
        Scaffold(
            "F1.degeneracy", Family.F1, "临界退化探测",
            "老师给出的式子/流程里含除法、开方、取逆、或「设某量为 X」时",
            listOf("退化条件", "退化后是否仍成立", "是否需要单独讨论"),
            "数学奇点 + 工程边界条件"
        ),
        Scaffold(
            "F1.falsifier", Family.F1, "证伪条件索取",
            "老师给出一个「总是成立/怎么都对」的断言时",
            listOf("什么观察会推翻它", "若答不出→标记为不可证伪"),
            "Popper《猜想与反驳》"
        ),
        Scaffold(
            "F1.killing_test", Family.F1, "致命检验设计",
            "老师给出一般性结论，但没给任何检验时",
            listOf("最小可执行的检验", "它能一击必杀的具体情形"),
            "Popper"
        ),
        Scaffold(
            "F1.anomaly", Family.F1, "反常状态分级",
            "出现反例，或老师的说法解释不了某个具体情况时",
            listOf("该反常属于：待解谜题 / 累积反常 / 框架失效", "判定依据"),
            "Kuhn《科学革命的结构》"
        ),
        Scaffold(
            "F1.refutation", Family.F1, "反驳应对策略识别",
            "老师遇到反例后的回应（排除/划例外/修补/无视）时",
            listOf("他采用了哪种应对", "这次应对是进步性还是退化性"),
            "Lakatos《Proofs and Refutations》"
        ),
        Scaffold(
            "F1.hardcore", Family.F1, "硬核与保护带分离",
            "老师修改/放弃了自己先前的某个说法时",
            listOf("被改动的是硬核还是保护带", "若只改保护带，主框架是否已被证伪"),
            "Lakatos《研究纲领方法论》"
        ),

        // ---- F2 隐藏前提显影 ----
        Scaffold(
            "F2.warrant", Family.F2, "保证缺失定位",
            "老师用了「显然/同理/易证/直接可得/由某定理可知」时",
            listOf("被跳过的中间命题", "该命题是否真的成立"),
            "Toulmin《论证的使用》"
        ),
        Scaffold(
            "F2.argument", Family.F2, "论证结构解剖",
            "老师给出一段自认为完整的推理时",
            listOf("主张", "依据", "保证", "限定", "反驳条件", "缺失项"),
            "Toulmin 六要素"
        ),
        Scaffold(
            "F2.argument_graph", Family.F2, "论证图构建",
            "一段讲解超过 3 个推理步骤、或有多个并列理由时",
            listOf("节点清单", "支撑箭头", "悬空节点（无出处的断言）"),
            "van Gelder《Argument Mapping》"
        ),
        Scaffold(
            "F2.elenchus", Family.F2, "诘问链构造",
            "老师的主张与他同时接受的其它信念可能冲突时",
            listOf("主张 p", "他同时接受的 q", "从 q 推到 ¬p 的最小追问序列"),
            "苏格拉底 elenchus"
        ),
        Scaffold(
            "F2.felicity", Family.F2, "言语行为恰当性检验",
            "老师说自己在做某个动作（证明/总结/推导/对比/归纳）时",
            listOf("他声称在做的动作", "该动作的恰当性条件", "哪条条件未满足"),
            "Austin《How to Do Things with Words》"
        ),
        Scaffold(
            "F2.private", Family.F2, "私人判据检验",
            "老师的结论只能由他自己验证（「我感觉/我觉得就是这样」）时",
            listOf("是否只剩私人判据", "可公共检验的改写形式"),
            "Wittgenstein 私人语言论证"
        ),
        Scaffold(
            "F2.belief_web", Family.F2, "信念网节点定位",
            "老师断言某说法「定义上就对」「本来就是」时",
            listOf("该说法在信念网的位置", "反驳它需要连带推翻哪些假设"),
            "Quine《经验论的两个教条》"
        ),

        // ---- F3 具象与抽象配平 ----
        Scaffold(
            "F3.usage", Family.F3, "问用法而非问本质",
            "老师在被要求「给个精确定义」而卡住时",
            listOf("该词的实际用法场合", "与相邻词如何区分", "用错会出什么后果"),
            "Wittgenstein 家族相似"
        ),
        Scaffold(
            "F3.concrete", Family.F3, "向具体用例下压",
            "老师开始构造抽象自洽的理论，但没落到具体例子上时",
            listOf("一个具体例子", "理论在该例上是否真跑得通"),
            "Wittgenstein「不要想，而要看」"
        ),
        Scaffold(
            "F3.analogy", Family.F3, "类比结构映射",
            "老师用了类比/比喻时",
            listOf("映射成功的关系", "只停留在表面相似的项", "在目标域无对应物的类比失效点"),
            "Gentner 结构映射理论"
        ),
        Scaffold(
            "F3.jargon", Family.F3, "抽象密度探测",
            "老师一句话里出现 3 个以上未解释的术语时",
            listOf("未拆解的术语清单", "是否有因果推导支撑，还是仅堆砌名词"),
            "Hattie + 学科语言研究"
        ),
        Scaffold(
            "F3.present_at_hand", Family.F3, "上手转在手",
            "老师「会用但说不出」，或反过来能背定义但做不出时",
            listOf("他刚才实际用了什么操作", "该问的是用法还是定义"),
            "Heidegger《存在与时间》"
        ),
        Scaffold(
            "F3.ready_to_hand", Family.F3, "在手复原上手",
            "过度追问「为什么」导致老师反而不会做了时",
            listOf("停止追问的信号", "回到具体操作的下一步"),
            "Heidegger（反面）"
        ),

        // ---- F4 目标与验收 ----
        Scaffold(
            "F4.dod", Family.F4, "可验证终局定义",
            "目标是「学完/看懂/掌握某章」这类无法验证的表述时",
            listOf("可被第三方验证的交付物", "验证方式"),
            "Wiggins & McTighe《Understanding by Design》"
        ),
        Scaffold(
            "F4.backward", Family.F4, "逆向设计审计",
            "开始一个新阶段，或老师提出要做某活动时",
            listOf("期望结果", "评估证据", "当前活动是否真的指向结果"),
            "UbD 三阶段"
        ),
        Scaffold(
            "F4.mastery_gate", Family.F4, "掌握闸门",
            "准备进入下一个知识点前",
            listOf("当前节点过关判据", "是否真的达到，还是被时间推着走"),
            "Bloom 掌握学习"
        ),

        // ---- F5 节奏与负荷 ----
        Scaffold(
            "F5.load", Family.F5, "认知负荷估算",
            "老师一次讲了多个新概念，或讲解跨度明显变大时",
            listOf("内在/外在/相关负荷粗估", "该给完整范例、半成品、还是空白"),
            "Sweller 认知负荷理论"
        ),
        Scaffold(
            "F5.scaffold_fade", Family.F5, "脚手架撤除判定",
            "老师连续 2 轮以上独立答对时（或连续出错时）",
            listOf("保持/部分撤除/完全撤除", "依据（范例效应 vs 专长逆转效应）"),
            "Sweller"
        ),
        Scaffold(
            "F5.step", Family.F5, "步长粒度检查",
            "老师的解释里一次跨过了多个中间步骤时",
            listOf("这一步的跨度", "是否超出可承载范围", "该插哪个中间问题"),
            "Rosenshine《教学原理》"
        ),
        Scaffold(
            "F5.stage", Family.F5, "学习者阶段判定",
            "老师的表现与预期明显不符（该会却不会 / 不该会却会）时",
            listOf("所处阶段（新手/高级新手/胜任/精通/专家）", "该给规则还是该给情境"),
            "Dreyfus 五阶段模型"
        ),

        // ---- F6 提取与流畅错觉 ----
        Scaffold(
            "F6.fluency", Family.F6, "流畅错觉检测",
            "老师连续多轮只说「嗯/对/就是这样」，从未自己产出推理时",
            listOf("近几轮的自主产出量", "是否只是听懂而非学会"),
            "Bjork《Desirable Difficulties》"
        ),
        Scaffold(
            "F6.desirable", Family.F6, "合意困难注入",
            "讲解非常顺畅、几乎无卡顿时",
            listOf("建议插入的困难类型（复述/打乱顺序/隔轮再问）"),
            "Bjork"
        ),
        Scaffold(
            "F6.self_explain", Family.F6, "诱发自解释",
            "老师只说了「是什么」，没说「为什么」时",
            listOf("该追问的是哪一步的「为什么」"),
            "Chi 自解释效应"
        ),
        Scaffold(
            "F6.retrieval", Family.F6, "讲授转提取",
            "老师已经把结论讲完，准备进入下一段时",
            listOf("该让他先自己说哪一部分", "再补哪一部分"),
            "Roediger & Karpicke 提取练习"
        ),
        Scaffold(
            "F6.feedback", Family.F6, "反馈层级分类",
            "自己想给出反馈/评价时（尤其想说「讲得好/很清楚」时）",
            listOf("这句属于：任务级/过程级/自我调节级/自我级", "若为自我级→改写为过程级"),
            "Hattie《Visible Learning》"
        ),

        // ---- F7 问题的合法性 ----
        Scaffold(
            "F7.original_q", Family.F7, "原始问题还原",
            "老师背出一个公式/定理/结论，但说不出它为什么存在时",
            listOf("它当初在解决什么问题", "不用它会出什么麻烦"),
            "Collingwood《历史的观念》"
        ),
        Scaffold(
            "F7.horizon", Family.F7, "视域错配检测",
            "老师用自己熟悉的旧框架解释新概念时",
            listOf("所用框架", "对象所属领域", "是否可通约、错配在哪一层"),
            "Gadamer《真理与方法》"
        ),
        Scaffold(
            "F7.aporia", Family.F7, "困惑着陆管理",
            "自己的追问把老师说哑了、或推出矛盾之后",
            listOf("矛盾点定位", "矛盾为什么会出现的解释", "下一步最小可行动作"),
            "苏格拉底 aporia"
        ),
        Scaffold(
            "F7.maxim", Family.F7, "会话准则违背定位",
            "老师的回答绕圈子、堆料、或明显跑题时",
            listOf("违反的准则（量/质/关系/方式）", "是故意还是无意", "若是故意，推出什么含义"),
            "Grice《逻辑与会话》"
        ),
        Scaffold(
            "F7.banking", Family.F7, "灌输模式检测",
            "对话退化为单向传输（老师一路讲、学生一路接）时",
            listOf("退化的起点", "该在哪里打断并反问"),
            "Freire《被压迫者教育学》"
        ),
        Scaffold(
            "F7.confusion", Family.F7, "困惑定位",
            "老师说不清自己哪里不懂（「就是不太懂」）时",
            listOf("卡在第几步", "哪个具体动作不确定"),
            "Dewey《我们如何思维》"
        ),
        Scaffold(
            "F7.heuristic", Family.F7, "启发式算子调度",
            "老师尝试了 2 次以上仍卡在同一处时",
            listOf("候选启发动作（特例化/倒推/类比/分解）", "为什么它在这个卡点上可能有效"),
            "Polya《How to Solve It》"
        ),
        Scaffold(
            "F7.retrospect", Family.F7, "回顾检验",
            "一道题/一个论证刚刚推完时",
            listOf("能否验算", "能否换一条路重推", "结论能否推广或收缩到特例"),
            "Polya 第四阶段"
        ),
        Scaffold(
            "F7.reflect", Family.F7, "行动反思诱发",
            "老师做对了但没意识到自己用对了什么时",
            listOf("他刚才做对的关键动作"),
            "Schön《反映性实践者》"
        )
    )

    /**
     * 输出契约：模型每轮回复前必须先填这段，然后才是学生台词。
     *
     * 注意 `<pedagogy_eval>` 与既有 `<thinking>` 的分工：
     * - `<thinking>`：第一人称内心独白（人设层，可自由发挥）
     * - `<pedagogy_eval>`：结构化判定（骨架层，字段固定，不可省略）
     *
     * ## 为什么选几条规定是「不限条数 + 必须排序」
     *
     * 2026-10-27 修正：初版写的是「选 1~3 条」，那是凭感觉定的上限，无量化依据。
     *
     * 被误用的约束：认知负荷理论（工作记忆 4±1）。它管的是**人类读起来吃力程度**，
     * 而 `<pedagogy_eval>` 是隐藏块，没有人读它。token 成本也不是问题：菜单 40 条约
     * 2000 token 一次性，每条槽位 50~100 token，选 10 条也才 800 token。
     *
     * 唯一成立的约束：`动作` 字段是单数的——它是「下一句要做的唯一一件事」。
     * 选 N 条 → N 份判定 → 压成 1 个动作，是有损压缩；N 越大丢得越多。
     * 但这条推出的是「动作必须单数」，**不是「选择数量必须少」**。初版把两者混为一谈。
     *
     * 硬上限的真实代价：一轮对话同时有 4 个问题很正常（斜率没讨论 + Δ 没验 +
     * 韦达定理条件缺失 + 代换跳步）。设 3 条上限，第 4 个被静默吞掉——而它可能
     * 恰好最致命。这跟「让模型自主思考」的目标正好相反。
     *
     * 故改为：**发现多少写多少，但必须标注严重度并排序，动作只动最高那条**。
     * 其余发现不作废，留在判定里等下一轮继续追——这才是「不丢失信息」的做法。
     */
    /**
     * 为什么判定块放在 `<thinking>` 里面，而不是用独立的 `<pedagogy_eval>` 标签：
     *
     * `MonologueSanitizer.stripTags` 的行为是**只剥标签、保留中间文字**
     * （见 `ToolLoop.kt` 的 `finalReply = postProcess(stripTags(cleanBody), report)`）。
     * 也就是说，任何写在 `<thinking>` 之外的标签，其**内容会直接漏进用户看到的学生回复**。
     *
     * 若用 `<pedagogy_eval>...内容...</pedagogy_eval>`，用户会看到：
     * 「选: F1.degeneracy 判定: [blocking] ... 动作: ... 老师，我想问一下斜率不存在的情况」
     * ——整个判定过程赤裸裸地贴在气泡里。
     *
     * 放进 `<thinking>` 则天然被 `MonologueEnvelope.splitComplete` 切成「独白」，
     * 只进抽屉与轨迹，正文只剩学生台词。零新增拦截逻辑，复用既有信封机制。
     */
    const val OUTPUT_CONTRACT: String =
        "\n【每轮必须先完成的判定】\n" +
            "**把判定块放进 <thinking> 里面**（和你的内心独白同一段），格式严格如下，字段不可增删：\n" +
            "<thinking>\n" +
            "（你此刻的第一人称内心独白）\n" +
            "选: <骨架id,骨架id,...>   ← 从下方菜单里选中本轮**所有**被真正触发的；" +
            "不要因为数量多就省略。一条都没触发就写「无」\n" +
            "判定: <按选中骨架的槽位逐条填写，每条前缀 [严重度]；" +
            "写不出某个槽位，说明该骨架其实不适用，直接删掉换一条>\n" +
            "动作: <只针对严重度最高的那条，写你下一句要做的唯一一件事；" +
            "其余发现不必本轮解决，但它们仍然成立，后续轮次继续追>\n" +
            "</thinking>\n" +
            "判定块必须在 </thinking> 之内闭合——它老师看不到。填不完整就不要输出正文，宁可重判一次。\n" +
            "严重度只能取：blocking（这一步不成立会毁掉整个结论）/ " +
            "significant（会丢分或导致返工）/ minor（表达层面，不影响正确性）。\n"

    /**
     * 渲染骨架菜单。按家族分组，每条一行，保证模型能在几十行内扫完。
     */
    fun renderMenu(): String = buildString {
        append("\n【认知骨架菜单（判定时从这里选，选完填槽位）】\n")
        Family.entries.forEach { family ->
            append("\n${family.id} ${family.title} ── 治：${family.failureItTreats}\n")
            all.filter { it.family == family }.forEach { s ->
                append("- ${s.id} ${s.title}")
                append(" ｜触发：${s.trigger}")
                append(" ｜槽位：${s.slots.joinToString(" / ")}\n")
            }
        }
    }

    /** 骨架数量自检用（测试断言与文档一致）。 */
    fun count(): Int = all.size

    /** 家族内条数，供测试校验分组均衡。 */
    fun countByFamily(family: Family): Int = all.count { it.family == family }

    /**
     * 「每骨架字符数」低于此值即疑似敷衍。实测校准，见 [EvalMetrics.looksShallow]。
     * 中文单字信息密度高，不可套用英文的字符数标准。
     */
    const val SHALLOW_THRESHOLD: Double = 25.0

    /**
     * 判定块的可量化体检器。
     *
     * 为什么需要它：'选几条' 从'凭感觉定上限' 改成 '不限条数' 之后，
     * 就必须有别的办法发现'模型在偷懒'。上限定死了模型会漏掉真发现；
     * 完全不限则会退化成'每轮只选一条最省事的'。
     *
     * 这里不做拦截（拦截会再次退化成硬上限），只做**度量**——
     * 把这几项指标记进 trace，用于事后统计与调参。
     */
    data class EvalMetrics(
        /** 本轮选中的骨架数。 */
        val selectedCount: Int,
        /** 判定块字符数 / 选中数。过低意味着槽位在敷衍。 */
        val charsPerScaffold: Double,
        /** 各严重度计数。 */
        val blocking: Int,
        val significant: Int,
        val minor: Int,
        /** 是否只填了 single 骨架（连续多轮为 1 值得警惕）。 */
        val isSingleScaffold: Boolean,
        /** 是否声明'无'（合理，但不能是常态）。 */
        val isEmpty: Boolean
    ) {
        /**
         * 槽位填充是否可能敷衍。
         *
         * 阈值 [SHALLOW_THRESHOLD] 是**实测校准值，不是理论推导**：
         * - 初版按英文习惯定 40 字符/条，实测发现一段槽位填得很实的判定
         *   （3 条骨架、每条 2~3 个槽位都答了）只有 36.7 字/条，被误判为敷衍。
         *   原因：中文单字信息密度约为英文单词的 3~4 倍，直接套字符数会系统性偏高。
         * - 下探到 25 后：实填样本 36.7 判为合格；只写「有问题/也有问题/小问题」
         *   的敷衍样本 15.0 被判为敷衍。两者区分清晰。
         *
         * 此值应随真实对话数据继续调整；不要用它做拦截，只做统计。
         */
        fun looksShallow(): Boolean =
            !isEmpty && !isSingleScaffold && charsPerScaffold < SHALLOW_THRESHOLD

        /** 严重度分布是否合理（全是 minor 或全是 blocking 都值得怀疑）。 */
        fun severityIsDegenerate(): Boolean =
            !isEmpty && selectedCount >= 3 &&
                (blocking + significant == 0 || minor == 0)
    }

    /**
     * 从一段判定原文（`<thinking>` 信封内的内容）里抽取度量。
     *
     * 解析刻意宽松：这是用于统计，不是用于校验，解析失败不应影响对话。
     */
    fun measure(evalBlock: String): EvalMetrics {
        // 兼容两种输入：完整回复（含 <thinking> 信封）或已切好的独白文本。
        val text: String = MonologueEnvelope.splitComplete(evalBlock)
            .monologue
            ?: evalBlock

        val selectionLine = text.lineSequence()
            .firstOrNull { it.trim().startsWith("选") } ?: ""
        val selectedRaw = selectionLine.substringAfter(":", "").trim()
        val isEmpty = selectedRaw.isEmpty() || selectedRaw.startsWith("无")
        val ids = if (isEmpty) {
            emptyList()
        } else {
            selectedRaw.split(',', '，', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val bodyStart = text.indexOf("判定")
        val bodyEnd = text.indexOf("动作")
        val body = if (bodyStart >= 0 && bodyEnd > bodyStart) {
            text.substring(bodyStart, bodyEnd)
        } else {
            ""
        }

        fun countSeverity(label: String): Int =
            Regex("\\[$label\\]").findAll(text).count()

        return EvalMetrics(
            selectedCount = ids.size,
            charsPerScaffold = if (ids.isEmpty()) 0.0 else body.length.toDouble() / ids.size,
            blocking = countSeverity("blocking"),
            significant = countSeverity("significant"),
            minor = countSeverity("minor"),
            isSingleScaffold = ids.size == 1,
            isEmpty = isEmpty
        )
    }
}
