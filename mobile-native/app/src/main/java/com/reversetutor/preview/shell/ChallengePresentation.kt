package com.reversetutor.preview.shell

data class ChallengePresentation(
    val statusLabel: String,
    val syncLabel: String,
    val metaLabel: String,
    val metricTitle: String,
    val metricValue: String,
    val metricSuffix: String,
    val progressFraction: Float,
    val showPersonalProgress: Boolean,
    val showFeedback: Boolean
) {
    companion object {
        fun from(joined: Boolean, progress: Int, total: Int): ChallengePresentation {
            val safeTotal = total.coerceAtLeast(1)
            val safeProgress = progress.coerceIn(0, safeTotal)
            return if (joined) {
                ChallengePresentation(
                    statusLabel = "进行中",
                    syncLabel = "已同步",
                    metaLabel = "距离结束 15 天",
                    metricTitle = "学习进度",
                    metricValue = safeProgress.toString(),
                    metricSuffix = " / $safeTotal 天",
                    progressFraction = safeProgress.toFloat() / safeTotal.toFloat(),
                    showPersonalProgress = true,
                    showFeedback = true
                )
            } else {
                ChallengePresentation(
                    statusLabel = "可加入",
                    syncLabel = "招募中",
                    metaLabel = "已有 386 人参与",
                    metricTitle = "挑战周期",
                    metricValue = safeTotal.toString(),
                    metricSuffix = " 天",
                    progressFraction = 0f,
                    showPersonalProgress = false,
                    showFeedback = false
                )
            }
        }
    }
}
