package com.reversetutor.feature.chat

import androidx.annotation.DrawableRes

data class FormalLearningPreset(
    val id: String,
    val figmaNodeId: String,
    val title: String,
    val category: String,
    val learnerName: String,
    val learnerProfile: String,
    val schedule: String,
    val goal: String,
    val goalTags: List<String>,
    val scopeSummary: String,
    val scopeModules: List<FormalScopeModule>,
    val episodeTitle: String,
    val episodeBody: String,
    val connectedSources: Int,
    val sourceTitle: String,
    val sourceSummary: String,
    @DrawableRes val avatarRes: Int,
    @DrawableRes val storyRes: Int
) {
    fun toDraft(): NewSessionDraft = NewSessionDraft(
        title = title,
        role = "AI 学生 $learnerName：$learnerProfile",
        goal = goal,
        profileText = "用户作为老师负责讲解。学习范围：$scopeSummary；节奏：$schedule。",
        templateId = id,
        sourceHandoffRequested = true
    )
}

data class FormalScopeModule(
    val title: String,
    val count: String,
    val progress: Float
)

object FormalLearningPresets {
    val all: List<FormalLearningPreset> = listOf(
        FormalLearningPreset(
            id = "formal-math-sprint",
            figmaNodeId = "716:684",
            title = "高三数学讲题冲刺",
            category = "升学考试",
            learnerName = "小岚",
            learnerProfile = "基础尚可 · 容易跳步骤 · 喜欢追问为什么",
            schedule = "12 周 · 68 个知识节点 · 建议每次讲解 20 分钟",
            goal = "把‘会做的题’讲成别人能听懂的方法",
            goalTags = listOf("讲清概念", "补全推导", "迁移新题"),
            scopeSummary = "围绕三类高频错题逐步讲解",
            scopeModules = listOf(
                FormalScopeModule("函数与图像", "22 节点", .50f),
                FormalScopeModule("数列与递推", "18 节点", .52f),
                FormalScopeModule("导数与综合", "28 节点", .58f)
            ),
            episodeTitle = "错题诊所",
            episodeBody = "小岚总在数学社讲清三道‘看似会做’的错题。你需要帮她补齐推导，并准备接受追问。",
            connectedSources = 3,
            sourceTitle = "错题本、试卷与课堂笔记",
            sourceSummary = "讲题时可随时查阅、引用或补充",
            avatarRes = R.drawable.formal_preset_math_avatar,
            storyRes = R.drawable.formal_preset_math_story
        ),
        FormalLearningPreset(
            id = "formal-python-concepts",
            figmaNodeId = "716:779",
            title = "Python 概念讲解",
            category = "技能",
            learnerName = "小P",
            learnerProfile = "零基础 · 喜欢动手 · 常混淆变量和值",
            schedule = "8 周 · 42 个知识节点 · 建议每次讲解 18 分钟",
            goal = "把能运行的代码讲成可理解、可修改的程序",
            goalTags = listOf("说清数据", "拆分函数", "定位错误"),
            scopeSummary = "从运行结果回到代码结构",
            scopeModules = listOf(
                FormalScopeModule("变量与流程", "14 节点", .50f),
                FormalScopeModule("函数与数据", "16 节点", .52f),
                FormalScopeModule("对象与调试", "12 节点", .58f)
            ),
            episodeTitle = "阳台浇水器",
            episodeBody = "小P的罗勒快蔫了，定时器却一直失灵。邻居的猫趴在窗边，你需要讲清变量和条件。",
            connectedSources = 4,
            sourceTitle = "练习代码、报错截图与项目笔记",
            sourceSummary = "讲解时可运行、标注并继续补充",
            avatarRes = R.drawable.formal_preset_python_avatar,
            storyRes = R.drawable.formal_preset_python_story
        ),
        FormalLearningPreset(
            id = "formal-ielts-speaking",
            figmaNodeId = "716:871",
            title = "雅思口语表达",
            category = "语言",
            learnerName = "Mia",
            learnerProfile = "表达自然 · 爱追问细节 · 不接受空泛答案",
            schedule = "6 周 · 30 个口语场景 · 建议每次讲解 20 分钟",
            goal = "把零散想法组织成清楚、有例证的口语回答",
            goalTags = listOf("先给观点", "补充细节", "自然回应"),
            scopeSummary = "从日常回答过渡到抽象讨论",
            scopeModules = listOf(
                FormalScopeModule("日常问答", "10 场景", .50f),
                FormalScopeModule("两分钟故事", "10 场景", .52f),
                FormalScopeModule("观点讨论", "10 场景", .58f)
            ),
            episodeTitle = "雨天失物",
            episodeBody = "通勤卡不见了，咖啡和早餐还在桌上。Mia 想听你把这段小插曲讲得具体又自然。",
            connectedSources = 3,
            sourceTitle = "口语录音、题库与个人素材卡",
            sourceSummary = "可回听或补充自己的真实经历",
            avatarRes = R.drawable.formal_preset_ielts_avatar,
            storyRes = R.drawable.formal_preset_ielts_story
        ),
        FormalLearningPreset(
            id = "formal-speech-expression",
            figmaNodeId = "716:963",
            title = "演讲表达训练",
            category = "技能",
            learnerName = "聆听者",
            learnerProfile = "专注倾听 · 会追问证据 · 关注现场反应",
            schedule = "5 周 · 24 个表达节点 · 建议每次讲解 15 分钟",
            goal = "让每次表达都有清晰结构、可信证据和现场回应",
            goalTags = listOf("抓住开场", "组织证据", "回答追问"),
            scopeSummary = "从准备到现场问答完整练习",
            scopeModules = listOf(
                FormalScopeModule("开场与结构", "8 节点", .50f),
                FormalScopeModule("故事与证据", "8 节点", .52f),
                FormalScopeModule("互动与回应", "8 节点", .58f)
            ),
            episodeTitle = "书籍交换日",
            episodeBody = "图书馆桌上堆着捐赠书和旧票根，柠檬汽水洒冒着气泡。你要先讲清活动为什么值得参加。",
            connectedSources = 3,
            sourceTitle = "演讲稿、录音与观众反馈",
            sourceSummary = "可对照每次练习查看结构变化",
            avatarRes = R.drawable.formal_preset_speech_avatar,
            storyRes = R.drawable.formal_preset_speech_story
        ),
        FormalLearningPreset(
            id = "formal-aptitude-reasoning",
            figmaNodeId = "716:1055",
            title = "行测推理讲解",
            category = "升学考试",
            learnerName = "阿策",
            learnerProfile = "细心质疑 · 关注隐藏条件 · 容易反问捷径",
            schedule = "10 周 · 56 个推理节点 · 建议每次讲解 20 分钟",
            goal = "把行测解法讲成别人能够复现的推理链",
            goalTags = listOf("识别条件", "解释排除", "控制时间"),
            scopeSummary = "优先练习高频题型和稳定步骤",
            scopeModules = listOf(
                FormalScopeModule("判断推理", "22 节点", .50f),
                FormalScopeModule("数量关系", "16 节点", .52f),
                FormalScopeModule("资料分析", "18 节点", .58f)
            ),
            episodeTitle = "清晨列车",
            episodeBody = "早班车快进站了，早餐还没吃完。阿策拿着时刻表追问：哪些条件真正决定出发顺序？",
            connectedSources = 4,
            sourceTitle = "真题、错题标签与计时记录",
            sourceSummary = "可按题型筛选并记录讲解耗时",
            avatarRes = R.drawable.formal_preset_aptitude_avatar,
            storyRes = R.drawable.formal_preset_aptitude_story
        ),
        FormalLearningPreset(
            id = "formal-frontend-explain",
            figmaNodeId = "716:1147",
            title = "前端代码讲解",
            category = "技能",
            learnerName = "Nova",
            learnerProfile = "新手开发者 · 重视细节 · 常问为什么这样拆",
            schedule = "6 个项目 · 35 个知识节点 · 建议每次讲解 25 分钟",
            goal = "不仅写出界面，还能解释状态、组件和设计取舍",
            goalTags = listOf("说清布局", "解释状态", "兼顾可访问"),
            scopeSummary = "从页面结构进入交互和组件边界",
            scopeModules = listOf(
                FormalScopeModule("布局与响应", "12 节点", .50f),
                FormalScopeModule("状态与事件", "14 节点", .52f),
                FormalScopeModule("组件与可用性", "9 节点", .58f)
            ),
            episodeTitle = "面包店菜单",
            episodeBody = "早晨面包店临时调整价格牌，Nova 拿着平板。你要解释响应式布局和信息层级。",
            connectedSources = 5,
            sourceTitle = "项目代码、设计稿与报错记录",
            sourceSummary = "可关联页面、组件和调试上下文",
            avatarRes = R.drawable.formal_preset_frontend_avatar,
            storyRes = R.drawable.formal_preset_frontend_story
        ),
        FormalLearningPreset(
            id = "formal-chemistry-lab",
            figmaNodeId = "716:1239",
            title = "高中化学实验",
            category = "升学考试",
            learnerName = "元素",
            learnerProfile = "观察细致 · 容易漏条件 · 重视实验安全",
            schedule = "8 周 · 36 个实验节点 · 建议每次讲解 20 分钟",
            goal = "从实验现象出发，讲清原理、条件和安全边界",
            goalTags = listOf("区分现象", "写出依据", "说明安全"),
            scopeSummary = "按观察、解释、推断顺序组织",
            scopeModules = listOf(
                FormalScopeModule("反应原理", "12 节点", .50f),
                FormalScopeModule("实验观察", "14 节点", .52f),
                FormalScopeModule("推断与安全", "10 节点", .58f)
            ),
            episodeTitle = "蓝色样品",
            episodeBody = "社团清理实验室时发现一瓶密封蓝色样品，花纹午餐袋还没带走。你要先讲清可能反应与条件。",
            connectedSources = 4,
            sourceTitle = "实验记录、试剂清单与错题",
            sourceSummary = "可按现象、条件和结论交叉查看",
            avatarRes = R.drawable.formal_preset_chemistry_avatar,
            storyRes = R.drawable.formal_preset_chemistry_story
        ),
        FormalLearningPreset(
            id = "formal-machine-learning",
            figmaNodeId = "716:1331",
            title = "机器学习概念",
            category = "兴趣",
            learnerName = "Echo",
            learnerProfile = "好奇新手 · 喜欢观察自然 · 容易把推理当答案",
            schedule = "7 周 · 38 个概念节点 · 建议每次讲解 22 分钟",
            goal = "把模型术语讲成从数据到判断的完整过程",
            goalTags = listOf("解释数据", "说清训练", "评估局限"),
            scopeSummary = "用植物识别项目串联完整流程",
            scopeModules = listOf(
                FormalScopeModule("数据与特征", "12 节点", .50f),
                FormalScopeModule("训练与损失", "14 节点", .52f),
                FormalScopeModule("评估与偏差", "12 节点", .58f)
            ),
            episodeTitle = "校园植物册",
            episodeBody = "阳光下，Echo 拍摄花叶、松鼠靠近观鸟笔记。你要解释样本、标签和特征从哪里来。",
            connectedSources = 4,
            sourceTitle = "数据样本、实验笔记与评估图",
            sourceSummary = "可连接本次解释对应的数据版本",
            avatarRes = R.drawable.formal_preset_ml_avatar,
            storyRes = R.drawable.formal_preset_ml_story
        )
    )

    fun byId(id: String): FormalLearningPreset? = all.firstOrNull { it.id == id }
}
