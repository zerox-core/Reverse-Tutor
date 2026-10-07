package com.reversetutor.feature.chat

import androidx.compose.ui.graphics.Color
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.WeakPointContract
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Safe warning mapping — never exposes source or message verbatim
// ---------------------------------------------------------------------------

/**
 * Whitelist of known context warning sources. Each maps to a fixed Chinese
 * label. Unknown sources collapse to a generic safe message.
 *
 * Per RT-2026-013 and handoff §1.3: warnings must NEVER display
 * [ContextWarning.source] or [ContextWarning.message] directly.
 */
private val WARNING_SOURCE_WHITELIST: Map<String, String> = mapOf(
    "memory" to "记忆检索部分不可用",
    "graph" to "知识点图谱部分不可用",
    "source" to "来源证据部分不可用",
    "error_history" to "历史错误记录部分不可用",
    "plan" to "学习计划部分不可用",
    "token" to "Token 用量统计部分不可用"
)

/**
 * Maps a [ContextWarning] to a display-safe fixed Chinese string.
 * The original [ContextWarning.source] and [ContextWarning.message] are
 * never surfaced to the UI.
 */
fun ContextWarning.toSafeWarningText(): String =
    WARNING_SOURCE_WHITELIST[source] ?: "部分学习数据暂不可用"

/**
 * Maps a list of [ContextWarning] to display-safe strings.
 */
fun List<ContextWarning>.toSafeWarningTexts(): List<String> =
    map { it.toSafeWarningText() }.distinct()

// ---------------------------------------------------------------------------
// Scope display helpers
// ---------------------------------------------------------------------------

/**
 * Human-readable label for the current scope selection.
 */
fun LearningOverviewScope.scopeLabel(): String =
    if (sessionIds == null) "本周全部" else "指定会话"

// ---------------------------------------------------------------------------
// Token formatting
// ---------------------------------------------------------------------------

/**
 * Formats token usage for display: large numbers are abbreviated to 万 (ten-thousand).
 */
fun Long.toTokenDisplayLabel(isEstimated: Boolean): String {
    val suffix = if (isEstimated) "（估算）" else ""
    val display = when {
        this >= 10_000 -> {
            // Divide by 1000 first, round to int, then /10 → one decimal place
            // e.g. 15000/1000=15.0 → roundToInt=15 → 15/10.0=1.5 → "1.5万"
            val wan = (this / 1_000.0).roundToInt() / 10.0
            "${wan}万"
        }
        else -> toString()
    }
    return "$display$suffix"
}

// ---------------------------------------------------------------------------
// Progress formatting
// ---------------------------------------------------------------------------

/**
 * Formats mastery percentage with a sign-prefixed weekly change.
 */
fun formatMasteryDisplay(masteryPercent: Int, weeklyChangePercent: Int): Pair<String, String> {
    val masteryLabel = "${masteryPercent}%"
    val changeLabel = if (weeklyChangePercent >= 0) {
        "本周 +${weeklyChangePercent}%"
    } else {
        "本周 ${weeklyChangePercent}%"
    }
    return masteryLabel to changeLabel
}

// ---------------------------------------------------------------------------
// Weak point severity tier
// ---------------------------------------------------------------------------

enum class WeakPointSeverityTier(val displayLabel: String) {
    LOW("关注"),
    MEDIUM("需加强"),
    HIGH("薄弱");

    companion object {
        fun from(severity: Float): WeakPointSeverityTier = when {
            severity >= 0.7f -> HIGH
            severity >= 0.4f -> MEDIUM
            else -> LOW
        }
    }
}

fun WeakPointContract.severityTier(): WeakPointSeverityTier =
    WeakPointSeverityTier.from(severity)

// ---------------------------------------------------------------------------
// Today plan status
// ---------------------------------------------------------------------------

enum class TodayTaskStatus(val displayLabel: String, val dotColor: Color) {
    DONE("已完成", FormalColors.Success),
    PENDING("待完成", FormalColors.BorderStrong);

    companion object {
        fun from(status: String): TodayTaskStatus =
            if (status == "done") DONE else PENDING
    }
}
