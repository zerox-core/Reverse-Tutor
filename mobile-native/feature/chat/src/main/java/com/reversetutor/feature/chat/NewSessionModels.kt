package com.reversetutor.feature.chat

import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.protocol.NativeSessionPreset

data class NewSessionTemplate(
    val id: String,
    val title: String,
    val role: String,
    val goal: String,
    val profileText: String
)

object BuiltInSessionTemplates {
    val all: List<NewSessionTemplate> = listOf(
        NewSessionTemplate(
            id = "school",
            title = "校内学习",
            role = "耐心的校内辅导老师",
            goal = "理解当前作业和课堂知识点",
            profileText = "用简短问题检查基础，解释要贴合学生年龄和当前教材。"
        ),
        NewSessionTemplate(
            id = "exam",
            title = "考前冲刺",
            role = "苏格拉底式考试教练",
            goal = "准备测验、考试和复习",
            profileText = "回答要简洁，对错误保持严格，并把薄弱点转成练习计划。"
        ),
        NewSessionTemplate(
            id = "work",
            title = "工作助手",
            role = "务实的工作导师",
            goal = "梳理工作任务、文档、决策和下一步行动",
            profileText = "优先给出具体摘要、取舍说明和可复用清单。"
        ),
        NewSessionTemplate(
            id = "language",
            title = "语言练习",
            role = "对话式语言教练",
            goal = "提升词汇、语法、听力和口语信心",
            profileText = "温和纠错，持续追问，并把例子改成日常生活场景。"
        ),
        NewSessionTemplate(
            id = "habit",
            title = "习惯养成",
            role = "行动监督教练",
            goal = "通过复盘和小行动建立可重复习惯",
            profileText = "用鼓励式检查识别阻碍，计划必须小而可执行。"
        ),
        NewSessionTemplate(
            id = "skill",
            title = "技能学习",
            role = "分步骤技能导师",
            goal = "通过练习和反馈学习一项实用技能",
            profileText = "把任务拆成等级，给出练习，并解释每一步为什么重要。"
        )
    )

    fun byId(id: String): NewSessionTemplate? =
        all.firstOrNull { it.id == id }
}

data class NewSessionDraft(
    val title: String,
    val role: String,
    val goal: String,
    val profileText: String,
    val templateId: String? = null,
    val sourceHandoffRequested: Boolean = false
) {
    fun validationErrors(): List<String> = buildList {
        if (title.isBlank()) add("请填写会话名称。")
        if (role.isBlank()) add("请填写角色。")
    }

    fun toCreationInput(): SessionCreationInput =
        SessionCreationInput(
            title = title.trim(),
            role = role.trim(),
            goal = goal.trim().ifEmpty { "未填写" },
            profileText = profileText.trim().ifEmpty { "未填写" },
            templateId = templateId,
            sourceHandoffRequested = sourceHandoffRequested
        )

    companion object {
        fun fromTemplate(template: NewSessionTemplate): NewSessionDraft =
            NewSessionDraft(
                title = template.title,
                role = template.role,
                goal = template.goal,
                profileText = template.profileText,
                templateId = template.id,
                sourceHandoffRequested = false
            )

        fun fromPreset(preset: NativeSessionPreset): NewSessionDraft =
            NewSessionDraft(
                title = preset.title,
                role = preset.role,
                goal = preset.goal,
                profileText = preset.profile,
                sourceHandoffRequested = preset.sourceHandoff == "deferred"
            )
    }
}
