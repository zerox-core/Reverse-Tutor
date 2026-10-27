package com.reversetutor.feature.reversetutor.prompt

import com.reversetutor.feature.reversetutor.card.StudentCards
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.session.StudentProfile
import org.json.JSONObject

/**
 * 反转家教系统提示词组装器（对应 spike 的 app/engine/prompts.py，设计 v1.1）。
 *
 * 设计原则：
 * 提示词只包含「身份铁律 + 人设/状态注入 + 角色卡 + 事实记录 + 工具架构说明 + 数学正确性」。
 * 不写流程规则（何时必须调什么工具、多少次、何时顿悟）——决策完全由 LLM 自主做出。
 */
object PromptAssembler {

    const val OPENING_INSTRUCTION: String =
        "[会话开始] 请以学生身份发起第一句对话：结合你的知识状态事实（总体水平、已掌握/困惑中/完全没学过的清单）自然地开场——可以表达困惑、可以说说你的目标或现状，姿态要与你的水平一致。用 speak_as_confused_student 或直接生成。"

    val STUDENT_IDENTITY_MARKERS: List<String> = listOf(
        "我来教你",
        "你应该记住",
        "作为老师",
        "让我给你讲",
        "正确做法是你要",
        "让我教你",
        "正确答案是，你要",
        "同学们"
    )

    /**
     * 渲染创建期采集的学生人设段（2026-10-18）。
     *
     * 与角色卡段并存：角色卡给「声线参考」，本段给「这个人是谁、想学什么、怎么互动」。
     * 事实层仍由工具守卫——本段只描述人设，不声明知识状态（知识状态走下方事实记录）。
     */
    private fun renderStudentProfileBlock(
        profile: StudentProfile,
        grade: String,
        includeGrade: Boolean
    ): String {
        if (profile.isBlank && !(includeGrade && grade.isNotBlank())) return ""
        val lines = StringBuilder("\n【你的学生人设（本次会话创建时确定，优先级高于角色卡示例）】\n")
        if (profile.role.isNotBlank()) lines.append("- 身份：${profile.role}\n")
        if (profile.profile.isNotBlank()) lines.append("- 画像：${profile.profile}\n")
        if (profile.persona.isNotBlank()) lines.append("- 性格与说话习惯：${profile.persona}\n")
        if (profile.dialogueStrategy.isNotBlank()) {
            lines.append("- 互动方式：${profile.dialogueStrategy}\n")
        }
        if (profile.goal.isNotBlank()) lines.append("- 本次学习目标：${profile.goal}\n")
        if (includeGrade && grade.isNotBlank()) {
            lines.append("- 学段：$grade（课标按学段划定知识范围，见下方工具）\n")
        }
        return lines.toString()
    }

    /**
     * 组装系统提示词。
     *
     * @param session 当前会话状态事实
     * @param personaOverride 可选的学生人设 JSON（来自 generate_student_persona 或外层预置）
     */
    fun buildSystemPrompt(session: RTSession, personaOverride: JSONObject? = null): String {
        val persona = personaOverride
        val mastered = session.masteredConcepts
        val confused = session.confusedConcepts

        val unknown: List<String> = if (persona != null) {
            val arr = persona.optJSONArray("completely_unknown")
            if (arr != null) {
                (0 until arr.length()).mapNotNull { arr.optString(it, null) }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val level = session.understandingLevel
        val emotion = session.emotion
        val roundNo = session.currentRound

        val personaDesc = if (persona != null) {
            val sp = persona.optJSONObject("speaking_patterns")
            val et = persona.optJSONObject("emotional_traits")

            val uncertaintyWordsList = mutableListOf<String>()
            val uwArr = sp?.optJSONArray("uncertainty_words")
            if (uwArr != null) {
                for (i in 0 until uwArr.length()) {
                    uncertaintyWordsList.add(uwArr.optString(i))
                }
            }
            val uncertaintyWords = if (uncertaintyWordsList.isNotEmpty()) {
                uncertaintyWordsList.joinToString("、")
            } else {
                "我觉得、好像"
            }

            val helpSeekingStyle = sp?.optString("help_seeking_style", "老师，能再讲讲吗？") ?: "老师，能再讲讲吗？"
            val breakthroughStyle = sp?.optString("breakthrough_style", "哦！原来是这样！") ?: "哦！原来是这样！"

            val enthusiasmLevel = et?.optDouble("enthusiasm_level", 0.7) ?: 0.7
            val persistence = et?.optDouble("persistence", 0.8) ?: 0.8
            val frustrationThreshold = et?.optInt("frustration_threshold", 5) ?: 5

            val grade = persona.optString("grade").takeIf { it.isNotBlank() } ?: session.grade

            val gradeLine = if (grade.isNotBlank()) {
                "\n- 学段：$grade（课标按学段划定知识范围，见下方工具）"
            } else {
                ""
            }

            "\n【你的学生人设】(${persona.optString("persona_id", "auto")})\n" +
                "- 说话习惯：不确定时常用 $uncertaintyWords；求助时\"$helpSeekingStyle\"；顿悟时\"$breakthroughStyle\"\n" +
                "- 情绪特质：热情 $enthusiasmLevel，坚持度 $persistence，遇挫折容忍约 $frustrationThreshold 轮" +
                gradeLine
        } else {
            ""
        }

        // 角色卡段（P4）：静态人格 + 按会话状态选中的示例台词（文采层）
        val cardPrompt = if (session.cardId.isNotBlank()) {
            val card = StudentCards.getCard(session.cardId)
            if (card != null) {
                "\n" + StudentCards.renderCardPrompt(card, session)
            } else {
                ""
            }
        } else {
            ""
        }

        // 知识状态事实段：总体水平 + 概念清单 + 知识图谱节点。
        // 这是事实陈述（不是行为规则）：清单外=无学习记录。
        val statMap = mapOf("mastered" to "已掌握", "learning" to "学习中", "not_started" to "未开始")
        val kgLines = StringBuilder()
        for (n in session.knowledgeGraphNodes) {
            val st = statMap[n.status] ?: n.status
            kgLines.append("- ${n.id}：$st（掌握度 ${n.level}）\n")
        }
        val kgBlock = if (kgLines.isNotEmpty()) {
            "\n【知识图谱记录】\n$kgLines"
        } else {
            ""
        }

        val masteredStr = if (mastered.isNotEmpty()) mastered.joinToString(", ") else "（无记录）"
        val confusedStr = if (confused.isNotEmpty()) confused.joinToString(", ") else "（无）"
        val unknownStr = if (unknown.isNotEmpty()) unknown.joinToString(", ") else "（无）"

        return buildString {
            append("你是\"反转家教\"中的 AI 学生。正在学习「${session.topic}」的老师是屏幕前的真实用户——你必须扮演【学生】，向老师提问、表达困惑、质疑、顿悟。\n\n")
            append("【身份铁律】\n")
            append("1. 你永远是学生，绝不以教师口吻说话；不说\"你应该\"\"让我教你\"\"正确答案是，你要记住\"。\n")
            append("2. 绝不暴露你是 AI 或存在工具系统；工具调用是内部行为。\n")
            append("3. 称呼对方为\"老师\"。回复保持 1-4 句话为主，自然口语，可以带一点学生气。\n")
            append("4. 每次回复最多提 1 个问题：贴着老师刚才卡住的那个点问，问完就停，等老师回答；不要一次抛出多个问题，也不要跳跃到别的内容。只有老师明确要求时才放宽。\n")
            append("5. 每次回复的最开头，先用 <thinking> 和 </thinking> 包住一两句你此刻的第一人称内心独白（用中文写：卡在哪、想怎么问，不要用英文），紧接着另起一段写给老师的正文；独白不是正文的一部分。\n")
            if (personaDesc.isNotBlank()) {
                append(personaDesc).append("\n")
            }
            if (cardPrompt.isNotBlank()) {
                append(cardPrompt).append("\n")
            }
            // 创建期采集的人设段（2026-10-18）：personaOverride 已自带学段时不再重复渲染学段。
            append(renderStudentProfileBlock(session.studentProfile, session.grade, persona == null))
            if (session.historySummary.isNotBlank()) {
                append("\n【前期对话摘要（早期学习经过）】\n")
                append(session.historySummary.trim())
                append("\n")
            }
            append("\n【你的知识状态（事实记录）】\n")
            append("- 总体水平：$level/5（1=完全没学过，3=会用不会推，5=完全掌握），情绪 $emotion，第 $roundNo 轮\n")
            append("- 已掌握：$masteredStr\n")
            append("- 困惑中：$confusedStr\n")
            append("- 完全没学过：$unknownStr\n")
            append("- 以上清单之外的知识点/技巧：你没有任何学习记录，属于从未学过的内容\n")
            if (kgBlock.isNotBlank()) {
                append(kgBlock)
            }
            append("【工作方式：LLM 决策 + 工具执行】\n")
            append("决策完全由你做出：读上下文 → 判断老师的解释是否到位、语气是否自信、是否卡住 → 自主决定调用哪些工具、调用几次，或完全不调用直接回复。没有固定流程。\n")
            append("- 认知诊断类工具返回\"事实\"（理解水平、维度评分、盲区、语气信心、误解类型）。\n")
            append("- 事实检索类工具返回外部事实（lookup_curriculum：概念的学段归属、难度、先修与标准定义，以及按你的会话推断的应处状态；search_materials：学生录入资料库的命中片段——无命中即\"资料里没有\"；web_search：外部网页摘要，未配置时返回不可用事实）。\n")
            append("- 决策路由类工具返回\"建议\"，仅供参考，最终由你决定。\n")
            append("- 角色扮演类工具提供台词素材，你要用自己的口吻重新表达，不要照抄。\n")
            append("- 记忆与评估类工具负责记录和复盘。\n")
            // 2026-10-27：删除原先硬编码的「数学正确性」三行补丁（配方法/顶点/判别式）。
            // 那是针对二次函数这一具体知识点写的提示词胶带，换学科即失效，
            // 且违反用户明令的「禁止拆东墙补西墙」红钱。此处换成结构化推理骨架：
            // 它约束「怎么判定」，而非「某个知识点该怎么算」。
            append(CognitiveScaffolds.OUTPUT_CONTRACT)
            append(CognitiveScaffolds.renderMenu())
            append("\n最后基于工具返回的事实、你的判定与你的判断，输出一条自然的学生口吻消息。")
        }
    }
}
