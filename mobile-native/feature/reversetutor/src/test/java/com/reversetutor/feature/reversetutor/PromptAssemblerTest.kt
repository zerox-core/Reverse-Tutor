package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.prompt.PromptAssembler
import com.reversetutor.feature.reversetutor.session.KgNode
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.session.StudentProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PromptAssemblerTest {

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir")
        val assets = "$userDir/src/main/assets"
        com.reversetutor.feature.reversetutor.card.StudentCards.setDefault(
            com.reversetutor.feature.reversetutor.card.StudentCards(File("$assets/student_cards.json").readText())
        )
    }

    @Test
    fun historySummaryIsRenderedInTheSystemPrompt() {
        val prompt = PromptAssembler.buildSystemPrompt(
            RTSession(
                sessionId = "summary",
                topic = "二次函数",
                historySummary = "老师之前讲过配方法，学生在减去平方项这一步卡住。"
            )
        )

        assertTrue(prompt.contains("前期对话摘要"))
        assertTrue(prompt.contains("减去平方项"))
    }

    @Test
    fun blankHistorySummaryDoesNotAddAnEmptyBlock() {
        val prompt = PromptAssembler.buildSystemPrompt(
            RTSession(sessionId = "summary-empty", topic = "二次函数")
        )

        assertFalse(prompt.contains("前期对话摘要"))
    }

    @Test
    fun studentProfileBlockCarriesTheCreationWindowPersona() {
        val session = RTSession(
            sessionId = "s4",
            topic = "趣味心理学基础",
            grade = "大一",
            studentProfile = StudentProfile(
                role = "对日常生活现象充满好奇的大一学生",
                profile = "喜欢用生活里的例子提问",
                persona = "性格活泼好奇，理解卡壳时会诚实说困惑",
                dialogueStrategy = "先让他举例，再一起命名现象",
                goal = "日常生活中的趣味心理学"
            )
        )

        val prompt = PromptAssembler.buildSystemPrompt(session)

        // 2026-10-18：创建窗口采集的人设必须进内核提示词，否则同一个用户在两条链路上
        // 会看到两个不同性格的学生。
        assertTrue(prompt.contains("【你的学生人设（本次会话创建时确定"))
        assertTrue(prompt.contains("身份：对日常生活现象充满好奇的大一学生"))
        assertTrue(prompt.contains("画像：喜欢用生活里的例子提问"))
        assertTrue(prompt.contains("性格与说话习惯：性格活泼好奇，理解卡壳时会诚实说困惑"))
        assertTrue(prompt.contains("互动方式：先让他举例，再一起命名现象"))
        assertTrue(prompt.contains("本次学习目标：日常生活中的趣味心理学"))
        // 学段来自内核会话（personaOverride 缺席时由此段渲染）
        assertTrue(prompt.contains("学段：大一（课标按学段划定知识范围，见下方工具）"))
        // 身份铁律不被人设段削弱
        assertTrue(prompt.contains("【身份铁律】"))
        assertTrue(prompt.contains("你永远是学生，绝不以教师口吻说话"))
    }

    @Test
    fun studentProfileBlockIsAbsentWhenNothingWasCollected() {
        val prompt = PromptAssembler.buildSystemPrompt(
            RTSession(sessionId = "s5", topic = "配方法")
        )

        // 未采集 = 提示词口径与接入前完全一致（不出现空段与空项目符号）
        assertFalse(prompt.contains("【你的学生人设（本次会话创建时确定"))
        assertFalse(prompt.contains("身份："))
        assertFalse(prompt.contains("学段："))
    }

    @Test
    fun profileBlockDoesNotDuplicateThePersonaOverrideGrade() {
        val session = RTSession(
            sessionId = "s6",
            topic = "配方法",
            grade = "初中",
            studentProfile = StudentProfile(role = "初二学生")
        )
        val persona = JSONObject().apply {
            put("persona_id", "p_low")
            put("grade", "初三")
            put("speaking_patterns", JSONObject())
            put("emotional_traits", JSONObject())
        }

        val prompt = PromptAssembler.buildSystemPrompt(session, persona)

        // personaOverride 自带学段时以它为准，人设段不再重复渲染学段行
        assertTrue(prompt.contains("学段：初三"))
        assertFalse(prompt.contains("学段：初中"))
        assertTrue(prompt.contains("身份：初二学生"))
    }

    @Test
    fun openingInstructionConstant() {
        assertTrue(PromptAssembler.OPENING_INSTRUCTION.contains("[会话开始]"))
        assertTrue(PromptAssembler.OPENING_INSTRUCTION.contains("以学生身份发起第一句对话"))
    }

    @Test
    fun basicPromptStructure() {
        val session = RTSession(
            sessionId = "s1",
            topic = "二次函数顶点公式",
            understandingLevel = 2,
            emotion = "neutral",
            currentRound = 1,
            masteredConcepts = listOf("完全平方式"),
            confusedConcepts = listOf("配方法")
        )

        val prompt = PromptAssembler.buildSystemPrompt(session)

        // 身份铁律
        assertTrue(prompt.contains("【身份铁律】"))
        assertTrue(prompt.contains("你永远是学生，绝不以教师口吻说话"))
        assertTrue(prompt.contains("绝不暴露你是 AI 或存在工具系统"))
        assertTrue(prompt.contains("称呼对方为\"老师\""))
        // 交互密度约束：每轮最多 1 个问题（用户拍板 2026-10-11）
        assertTrue(prompt.contains("每次回复最多提 1 个问题"))
        // 内心独白约定（SPEC §4.7 路线 C；2026-10-12 真机反馈补「用中文」）
        assertTrue(prompt.contains("<thinking> 和 </thinking>"))
        assertTrue(prompt.contains("用中文写"))

        // 事实记录
        assertTrue(prompt.contains("【你的知识状态（事实记录）】"))
        assertTrue(prompt.contains("总体水平：2/5"))
        assertTrue(prompt.contains("已掌握：完全平方式"))
        assertTrue(prompt.contains("困惑中：配方法"))
        assertTrue(prompt.contains("以上清单之外的知识点/技巧：你没有任何学习记录"))

        // 2026-10-27：原先这里断言硬编码的「数学正确性」三行补丁（配方法/顶点/判别式）。
        // 那是对二次函数这一具体知识点写的提示词胶带，换学科即失效，违反
        // 「禁止拆东墙补西墙」红线，已删除。取而代之的是结构化推理骨架，见下方断言。
        assertFalse("硬编码的学科补丁不得回归", prompt.contains("【数学正确性（最高优先级）】"))
        assertFalse("硬编码的学科补丁不得回归", prompt.contains("x² + bx = (x + b/2)²"))
        // 结构化推理骨架：输出契约（写在 <thinking> 内）+ 骨架菜单
        assertTrue(prompt.contains("【每轮必须先完成的判定】"))
        assertTrue(prompt.contains("【认知骨架菜单（判定时从这里选，选完填槽位）】"))
        assertTrue(prompt.contains("F2.warrant"))
        assertTrue(prompt.contains("F6.fluency"))
        assertTrue(prompt.contains("填不完整就不要输出正文"))
    }

    @Test
    fun promptWithPersonaAndCard() {
        val session = RTSession(
            sessionId = "s2",
            topic = "配方法",
            cardId = "proud_average",
            understandingLevel = 1,
            grade = "初中"
        )

        val persona = JSONObject().apply {
            put("persona_id", "p_quick_tempered")
            put("grade", "初三")
            put("completely_unknown", JSONArray().apply { put("配方法"); put("判别式") })
            put("speaking_patterns", JSONObject().apply {
                put("uncertainty_words", JSONArray().apply { put("好像是"); put("大概") })
                put("help_seeking_style", "老师快告诉我！")
                put("breakthrough_style", "哇我知道了！")
            })
            put("emotional_traits", JSONObject().apply {
                put("enthusiasm_level", 0.9)
                put("persistence", 0.6)
                put("frustration_threshold", 3)
            })
        }

        val prompt = PromptAssembler.buildSystemPrompt(session, persona)

        // 人设段
        assertTrue(prompt.contains("【你的学生人设】(p_quick_tempered)"))
        assertTrue(prompt.contains("好像是、大概"))
        assertTrue(prompt.contains("老师快告诉我！"))
        assertTrue(prompt.contains("哇我知道了！"))
        assertTrue(prompt.contains("热情 0.9"))
        assertTrue(prompt.contains("学段：初三"))

        // 完全没学过
        assertTrue(prompt.contains("完全没学过：配方法, 判别式"))

        // 角色卡段（陈小磊）
        assertTrue(prompt.contains("【你的角色卡：陈小磊】") || prompt.contains("陈小磊"))
    }

    @Test
    fun promptWithKnowledgeGraph() {
        val session = RTSession(
            sessionId = "s3",
            topic = "二次函数",
            knowledgeGraphNodes = listOf(
                KgNode(id = "完全平方式", status = "mastered", level = 0.9),
                KgNode(id = "配方法", status = "learning", level = 0.4),
                KgNode(id = "顶点公式", status = "not_started", level = 0.0)
            )
        )

        val prompt = PromptAssembler.buildSystemPrompt(session)
        assertTrue(prompt.contains("【知识图谱记录】"))
        assertTrue(prompt.contains("- 完全平方式：已掌握（掌握度 0.9）"))
        assertTrue(prompt.contains("- 配方法：学习中（掌握度 0.4）"))
        assertTrue(prompt.contains("- 顶点公式：未开始（掌握度 0.0）"))
    }
}
