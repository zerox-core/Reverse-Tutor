package com.reversetutor.feature.chat

/**
 * 窗口设置「系统操作」区的导出包构建器。
 * 纯字符串拼装，不引入序列化库；字段与 NEWMP-V2 记忆分层契约保持同形，
 * 分层记忆（0~3 层）接线后在记忆库包内追加 layers 字段即可。
 */
data class SessionExportPayload(
    val fileName: String,
    val json: String
)

object SessionSettingsExport {

    fun buildConfigPayload(
        document: SessionSettingsDocument,
        exportedAtEpochMillis: Long = System.currentTimeMillis()
    ): SessionExportPayload = SessionExportPayload(
        fileName = exportFileName(document, "窗口配置"),
        json = buildString {
            append("{\n")
            append(metaBlock("session-config", exportedAtEpochMillis))
            append(",\n")
            append(profileBlock(document.profile))
            append(",\n")
            append(goalPlanBlock(document.goalPlan))
            append(",\n")
            append(strategyBlock(document.strategy))
            append("\n}")
        }
    )

    fun buildMemoryPayload(
        document: SessionSettingsDocument,
        exportedAtEpochMillis: Long = System.currentTimeMillis()
    ): SessionExportPayload = SessionExportPayload(
        fileName = exportFileName(document, "窗口记忆库"),
        json = buildString {
            append("{\n")
            append(metaBlock("session-memory", exportedAtEpochMillis))
            append(",\n")
            append(profileBlock(document.profile))
            append(",\n")
            append(goalPlanBlock(document.goalPlan))
            append(",\n")
            append(strategyBlock(document.strategy))
            append(",\n")
            append(snapshotBlock(document.snapshot))
            append(",\n")
            append(quickTagsBlock(document.quickTags))
            append("\n}")
        }
    )

    private fun metaBlock(type: String, exportedAtEpochMillis: Long): String =
        "  \"type\": ${jsonString(type)},\n" +
            "  \"version\": 1,\n" +
            "  \"exportedAtEpochMillis\": $exportedAtEpochMillis"

    private fun profileBlock(profile: SessionSettingsProfile): String = buildString {
        append("  \"profile\": {\n")
        append("    \"title\": ${jsonString(profile.title)},\n")
        append("    \"learnerDisplayName\": ${jsonString(profile.learnerDisplayName)},\n")
        append("    \"learnerRole\": ${jsonString(profile.learnerRole)},\n")
        append("    \"avatarVisible\": ${profile.avatarVisible},\n")
        append("    \"personality\": ${jsonString(profile.personality)},\n")
        append("    \"interactionHabits\": ${jsonString(profile.interactionHabits)}\n")
        append("  }")
    }

    private fun goalPlanBlock(goalPlan: SessionGoalPlan): String = buildString {
        append("  \"goalPlan\": {\n")
        append("    \"primaryGoal\": ${jsonString(goalPlan.primaryGoal)},\n")
        append("    \"deadline\": ${jsonString(goalPlan.deadline)},\n")
        append("    \"learningScope\": ${jsonString(goalPlan.learningScope)},\n")
        append("    \"modules\": ${jsonString(goalPlan.modules)},\n")
        append("    \"stageMilestones\": ${jsonString(goalPlan.stageMilestones)},\n")
        append("    \"weeklyPlan\": ${jsonString(goalPlan.weeklyPlan)},\n")
        append("    \"currentState\": ${jsonString(goalPlan.currentState)}\n")
        append("  }")
    }

    private fun strategyBlock(strategy: ConversationStrategy): String = buildString {
        append("  \"strategy\": {\n")
        append("    \"feedbackIntensity\": ${strategy.feedbackIntensity},\n")
        append("    \"probingIntensity\": ${strategy.probingIntensity},\n")
        append("    \"scaffoldingIntensity\": ${strategy.scaffoldingIntensity},\n")
        append("    \"correctionPersistence\": ${jsonString(strategy.correctionPersistence)},\n")
        append("    \"reviewFrequency\": ${jsonString(strategy.reviewFrequency)},\n")
        append("    \"speakingTone\": ${jsonString(strategy.speakingTone)}\n")
        append("  }")
    }

    private fun snapshotBlock(snapshot: NewSessionConfiguration): String = buildString {
        append("  \"snapshot\": {\n")
        append("    \"story\": ${jsonString(snapshot.story)},\n")
        append("    \"builtInPresetId\": ${jsonString(snapshot.builtInPresetId)},\n")
        append("    \"learnerImageRef\": ${jsonString(snapshot.learnerImageRef)},\n")
        append("    \"storyImageRef\": ${jsonString(snapshot.storyImageRef)},\n")
        append("    \"sourceSelections\": ${jsonArray(snapshot.sourceSelections)},\n")
        append("    \"customColumnCount\": ${snapshot.customColumns.size}\n")
        append("  }")
    }

    private fun quickTagsBlock(quickTags: Map<String, TagFieldSelection>): String {
        if (quickTags.isEmpty()) return "  \"quickTags\": {}"
        val entries = quickTags.entries.joinToString(",\n") { (field, selection) ->
            "    ${jsonString(field)}: ${jsonArray(selection.values.map { it.text })}"
        }
        return "  \"quickTags\": {\n$entries\n  }"
    }

    private fun exportFileName(document: SessionSettingsDocument, kind: String): String {
        val title = document.profile.title.ifBlank { "未命名会话" }
        return sanitizeFileName("反转家教-$kind-$title.json")
    }

    internal fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimEnd('.')
        return cleaned.ifBlank { "导出.json" }
    }

    private fun jsonArray(values: List<String>): String =
        values.joinToString(", ", "[", "]") { jsonString(it) }

    private fun jsonString(value: String?): String =
        value?.let { "\"${escape(it)}\"" } ?: "null"

    private fun escape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
