package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.prompt.PromptAssembler
import com.reversetutor.feature.reversetutor.session.KgNode
import com.reversetutor.feature.reversetutor.session.RTSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

        // 数学正确性
        assertTrue(prompt.contains("【数学正确性（最高优先级）】"))
        assertTrue(prompt.contains("x² + bx = (x + b/2)² - (b/2)²"))
        assertTrue(prompt.contains("顶点横坐标是 -b/(2a)，不是 +b/(2a)"))
        assertTrue(prompt.contains("二次函数要求 a ≠ 0"))
        assertTrue(prompt.contains("你【故意犯错】时不算错误"))
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
