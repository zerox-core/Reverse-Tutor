package com.reversetutor.feature.chat

import kotlinx.coroutines.delay

/**
 * R-A 演示用脚本化网关：确定性对话阶梯，驱动「了解程度」从 25 → 95。
 * R-B 换生产实现后本文件保留给测试与离线演示。
 */
class FakeAgentCreationGateway(
    private val latencyMillis: Long = 500L
) : AgentCreationGateway {

    private var userTurns = 0
    private var mentionedGoal = false
    private var mentionedLearner = false
    private var requestedDocument = false
    private var documentSent = false

    override suspend fun converse(
        history: List<AgentCreationHistoryTurn>,
        userText: String,
        currentDraft: NewSessionConfiguration,
        docAnalysis: AgentCreationDocAnalysis?
    ): AgentCreationTurnResult {
        delay(latencyMillis)
        userTurns++
        documentSent = docAnalysis != null
        if (userText.contains(Regex("目标|想学会|想学|学会|考试|冲刺|提分|复习|讲明白|搞懂"))) mentionedGoal = true
        if (userText.contains(Regex("我是|我今年|初一|初二|初三|高一|高二|高三|基础|零基础|水平|学生|年级"))) mentionedLearner = true

        val topic = guessTopic(userText)
        return when {
            userTurns == 1 -> AgentCreationTurnResult(
                understanding = 30,
                assistantNote = "「$topic」是吧，记下了。我先把草案的骨架搭起来。",
                followUpQuestion = "你想达到什么目标？比如补上某个薄弱块、冲刺一次考试，或者单纯把概念讲透。",
                draft = AgentCreationDraftPatch(
                    title = "$topic · 讲学练会话",
                    learnerRole = "对${topic}有基础疑问的学习者，会在关键步骤追问为什么"
                )
            )
            mentionedGoal && !mentionedLearner -> AgentCreationTurnResult(
                understanding = 55,
                assistantNote = "目标明确了，草案的目标和计划我补上了。",
                followUpQuestion = "你现在的基础怎么样？学到哪一块了？",
                draft = AgentCreationDraftPatch(
                    goal = userText.take(80),
                    plan = "先按你的现状定位薄弱点，再分阶段各个讲透，最后用讲题检验。",
                    stageMilestones = "阶段一：概念地图搭起来：能不看书说出知识框架\n阶段二：典型题讲透：每类题独立讲 2 道并答追问\n阶段三：综合检验：讲一道压轴题并接住反问",
                    learningScope = topic,
                    modules = topic
                )
            )
            mentionedGoal && mentionedLearner && !documentSent && !requestedDocument -> {
                requestedDocument = true
                AgentCreationTurnResult(
                    understanding = 65,
                    assistantNote = "基本情况我记住了，画像也更新了。另外——",
                    followUpQuestion = "如果手头有教材、讲义或试卷，发我一份看看？对着材料学，路径会准得多。",
                    requestDocument = true,
                    draft = AgentCreationDraftPatch(
                        learnerProfile = "有目标感但基础有缺口，常见误区在概念衔接处；提问直接，需要被追问出所以然。",
                        learnerDisplayName = "小林",
                        dialogueStrategy = "每轮先复述用户讲法，再挑一个衔接点追问依据；用户卡壳时给台阶式提示。",
                        feedbackIntensity = 4,
                        probingIntensity = 4
                    )
                )
            }
            mentionedGoal && mentionedLearner && (documentSent || userTurns >= 4) -> AgentCreationTurnResult(
                understanding = 85.coerceAtLeast(if (documentSent) 90 else 85),
                assistantNote = if (documentSent) "结合这份材料，学习路径和开场白都补齐了，这版草案可以直接用。" else "信息够了，我把草案补成完整版，可以直接创建。",
                followUpQuestion = null,
                draft = AgentCreationDraftPatch(
                    title = "$topic · 讲学练冲刺",
                    correctionPersistence = "适中",
                    reviewFrequency = "每周",
                    speakingTone = "自然",
                    story = "一间安静的晚自习教室，你是老师，我是坐在第一排、笔记记得飞快但总有疑问的学生。",
                    openingMessage = "老师，${topic}这里我卡住好几天了……你能不能从我最迷糊的地方开始讲？我随时打断你提问哦。"
                )
            )
            else -> AgentCreationTurnResult(
                understanding = 45,
                assistantNote = "嗯，我继续记着。多说一点你的目标或现在的水平，我就能把草案填得更准。",
                followUpQuestion = "这块你最想先解决什么？"
            )
        }
    }

    override suspend fun analyzeDocument(fileName: String): AgentCreationDocAnalysis {
        delay(latencyMillis * 2)
        val cleanName = fileName.substringBeforeLast('.').replace('_', ' ')
        return AgentCreationDocAnalysis(
            materialTitle = cleanName.ifBlank { "未命名资料" },
            materialType = if (fileName.contains(Regex("试卷|exam|test"))) "试卷" else "教材",
            outline = listOf(
                "第 1 章 · 概念与直觉引入",
                "第 2 章 · 核心定义与公式",
                "第 3 章 · 典型例题精讲",
                "第 4 章 · 易错点对比",
                "第 5 章 · 综合应用"
            ),
            knowledgePoints = listOf(
                "概念定义与适用条件",
                "公式的推导来源",
                "典型题型识别",
                "易错陷阱辨析",
                "综合应用与迁移"
            ),
            difficulty = 0.55f,
            prerequisites = listOf("基础代数运算", "上一章基本概念"),
            suggestedPath = listOf(
                "入门：概念直觉 + 定义：能用自己的话复述",
                "筑基：公式推导 + 基础题：讲 3 道给 AI 学生听",
                "强化：易错题辨析：每次讲完接住一个追问",
                "检验：综合卷压轴题：完整讲一遍并答疑"
            ),
            summary = "结构完整的${if (fileName.contains(Regex("试卷|exam|test"))) "试卷" else "教材"}，概念—公式—例题链条清晰，适合讲学练式推进。"
        )
    }

    private fun guessTopic(text: String): String {
        val match = TOPIC_KEYWORDS.firstNotNullOfOrNull { (keyword, label) ->
            if (text.contains(keyword)) label else null
        }
        return match ?: "这个主题"
    }

    private companion object {
        val TOPIC_KEYWORDS = listOf(
            "数学" to "数学", "物理" to "物理", "浮力" to "物理浮力", "化学" to "化学",
            "英语" to "英语", "语文" to "语文", "生物" to "生物", "历史" to "历史",
            "地理" to "地理", "编程" to "编程", "代码" to "编程", "吉他" to "吉他",
            "钢琴" to "钢琴", "乐理" to "乐理"
        )
    }
}
