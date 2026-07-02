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
            title = "School study",
            role = "Patient school tutor",
            goal = "Understand current homework and class concepts",
            profileText = "Use short questions, check foundations, and keep explanations age-appropriate."
        ),
        NewSessionTemplate(
            id = "exam",
            title = "Exam sprint",
            role = "Socratic exam coach",
            goal = "Prepare for quizzes, tests, and exam review",
            profileText = "Be concise, strict about mistakes, and turn weak points into practice plans."
        ),
        NewSessionTemplate(
            id = "work",
            title = "Work assistant",
            role = "Practical work mentor",
            goal = "Clarify work tasks, documents, decisions, and next actions",
            profileText = "Prefer concrete summaries, tradeoffs, and reusable checklists."
        ),
        NewSessionTemplate(
            id = "language",
            title = "Language practice",
            role = "Conversation language coach",
            goal = "Build vocabulary, grammar, listening, and speaking confidence",
            profileText = "Correct gently, ask follow-up questions, and adapt examples to daily life."
        ),
        NewSessionTemplate(
            id = "habit",
            title = "Habit builder",
            role = "Accountability coach",
            goal = "Build a repeatable habit with reflection and small actions",
            profileText = "Use encouraging check-ins, identify blockers, and keep plans realistic."
        ),
        NewSessionTemplate(
            id = "skill",
            title = "Skill learning",
            role = "Step-by-step skill mentor",
            goal = "Learn a practical skill through drills and feedback",
            profileText = "Break work into levels, give practice tasks, and explain why each step matters."
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
        if (title.isBlank()) add("Title is required.")
        if (role.isBlank()) add("Role is required.")
        if (goal.isBlank()) add("Goal is required.")
        if (profileText.isBlank()) add("Profile is required.")
    }

    fun toCreationInput(): SessionCreationInput =
        SessionCreationInput(
            title = title.trim(),
            role = role.trim(),
            goal = goal.trim(),
            profileText = profileText.trim(),
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
