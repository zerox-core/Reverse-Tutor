package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FormalWeeklyMetric(
    val value: String?,
    val label: String
)

data class FormalWeeklyPlanItem(
    val id: String,
    val timeLabel: String,
    val title: String,
    val sessionLabel: String,
    val completed: Boolean
)

data class FormalWeeklyMainline(
    val title: String,
    val concepts: String,
    val sourceCount: Int
)

data class FormalWeeklyWeakPoint(
    val title: String,
    val sourceLabel: String
)

data class FormalWeeklyQuestion(
    val id: String,
    val title: String,
    val sessionLabel: String
)

data class FormalWeeklySuggestion(
    val id: String,
    val title: String,
    val relationLabel: String
)

data class FormalWeeklyActivity(
    val timeLabel: String,
    val title: String
)

data class FormalWeeklyChallenge(
    val sessionId: String,
    val title: String,
    val dayLabel: String,
    val taskTitle: String,
    val progress: Int,
    val total: Int,
    val scheduleLabel: String
)

data class FormalWeeklyMilestone(
    val title: String,
    val sourceLabel: String
)

data class FormalWeeklyUsageSegment(
    val label: String,
    val tokensLabel: String,
    val fraction: Float
)

data class FormalWeeklyModelUsage(
    val modelName: String,
    val totalTokensLabel: String?,
    val segments: List<FormalWeeklyUsageSegment>
)

data class FormalWeeklySessionOption(
    val id: String,
    val title: String,
    val detail: String,
    val activeThisWeek: Boolean = true,
    val pinned: Boolean = false
)

data class FormalWeeklyUiState(
    val periodLabel: String,
    val narrative: String,
    val unresolvedSummary: String,
    val metrics: List<FormalWeeklyMetric>,
    val plans: List<FormalWeeklyPlanItem>,
    val mainlines: List<FormalWeeklyMainline>,
    val weakPoints: List<FormalWeeklyWeakPoint>,
    val questions: List<FormalWeeklyQuestion>,
    val suggestions: List<FormalWeeklySuggestion>,
    val rhythm: List<Float>,
    val rhythmTotalLabel: String?,
    val rhythmComparisonLabel: String,
    val challenge: FormalWeeklyChallenge?,
    val milestones: List<FormalWeeklyMilestone>,
    val modelUsage: FormalWeeklyModelUsage?,
    val activities: List<FormalWeeklyActivity>,
    val sessionOptions: List<FormalWeeklySessionOption>,
    val selectedSessionIds: Set<String>,
    val isLoading: Boolean,
    val isOnline: Boolean,
    val errorMessage: String?
) {
    val normalizedSelectedSessionIds: Set<String>
        get() {
            val availableIds = sessionOptions.mapTo(linkedSetOf()) { it.id }
            return selectedSessionIds.filterTo(linkedSetOf()) { it in availableIds }
        }

    companion object {
        fun preview(): FormalWeeklyUiState = FormalWeeklyUiState(
            periodLabel = "7月13日 - 7月19日",
            narrative = "你正在把宏观经济与函数变化，\n连接成同一条理解路径。",
            unresolvedSummary = "仍有 2 个问题等待回到原会话继续确认",
            metrics = listOf(
                FormalWeeklyMetric("5 天", "连续学习"),
                FormalWeeklyMetric("6 个", "活跃会话"),
                FormalWeeklyMetric("8 项", "完成任务"),
                FormalWeeklyMetric("6h20m", "学习时长")
            ),
            plans = listOf(
                FormalWeeklyPlanItem(
                    id = "plan-gdp",
                    timeLabel = "09:30",
                    title = "复述 GDP 核算边界",
                    sessionLabel = "宏观经济学基础",
                    completed = true
                ),
                FormalWeeklyPlanItem(
                    id = "plan-derivative",
                    timeLabel = "15:00",
                    title = "完成导数与变化率对照",
                    sessionLabel = "高中数学",
                    completed = false
                ),
                FormalWeeklyPlanItem(
                    id = "plan-question",
                    timeLabel = "20:30",
                    title = "回答小P留下的追问",
                    sessionLabel = "线性代数入门",
                    completed = false
                )
            ),
            mainlines = listOf(
                FormalWeeklyMainline("宏观经济", "GDP 核算范围 · 名义与实际 GDP", 2),
                FormalWeeklyMainline("高中数学", "导数 · 变化率 · 函数趋势", 1),
                FormalWeeklyMainline("Python", "函数定义 · 参数传递", 1)
            ),
            weakPoints = listOf(
                FormalWeeklyWeakPoint("旧物价值与新增服务价值仍会混淆", "宏观经济学基础 · 今天"),
                FormalWeeklyWeakPoint("能计算导数，但现实含义解释不稳定", "高中数学 · 今天"),
                FormalWeeklyWeakPoint("参数作用域需要更多反例", "Python · 昨天")
            ),
            questions = listOf(
                FormalWeeklyQuestion("question-gdp", "平台补贴应如何计入 GDP？", "宏观经济学基础"),
                FormalWeeklyQuestion("question-limit", "瞬时变化率为什么需要极限？", "高中数学")
            ),
            suggestions = listOf(
                FormalWeeklySuggestion(
                    "suggestion-gdp",
                    "用二手交易案例继续验证 GDP 核算边界",
                    "关联：宏观经济学基础"
                ),
                FormalWeeklySuggestion(
                    "suggestion-growth",
                    "将导数概念与经济增长率建立关联",
                    "跨会话建议"
                )
            ),
            rhythm = listOf(0.45f, 0.69f, 0.29f, 0.86f, 0.57f, 0.98f, 0.40f),
            rhythmTotalLabel = "6h20m",
            rhythmComparisonLabel = "较上周增加 48 分钟",
            challenge = FormalWeeklyChallenge(
                sessionId = "challenge-python",
                title = "21天 Python 学习挑战",
                dayLabel = "第 6 天",
                taskTitle = "用函数封装 GDP 增长率计算",
                progress = 6,
                total = 21,
                scheduleLabel = "今晚 20:30"
            ),
            milestones = listOf(
                FormalWeeklyMilestone("GDP 核算范围节点已确认", "宏观经济学基础"),
                FormalWeeklyMilestone("完成 Python 挑战第 5 天", "挑战会话"),
                FormalWeeklyMilestone("连续学习达到 5 天", "全局记录")
            ),
            modelUsage = FormalWeeklyModelUsage(
                modelName = "DeepSeek V3.2",
                totalTokensLabel = "43.8k tokens",
                segments = listOf(
                    FormalWeeklyUsageSegment("宏观经济", "19.4k", 0.44f),
                    FormalWeeklyUsageSegment("数学", "13.2k", 0.30f),
                    FormalWeeklyUsageSegment("Python", "11.2k", 0.26f)
                )
            ),
            activities = listOf(
                FormalWeeklyActivity("14:32", "世界树新增「GDP 核算范围」"),
                FormalWeeklyActivity("昨天", "完成 Python 挑战第 5 天")
            ),
            sessionOptions = listOf(
                FormalWeeklySessionOption("session-economics", "宏观经济学基础", "学习模式 · 今天 14:32"),
                FormalWeeklySessionOption("session-math", "高中数学 · 导数", "学习模式 · 今天 11:08"),
                FormalWeeklySessionOption("challenge-python", "21天 Python 学习挑战", "挑战会话 · 昨天"),
                FormalWeeklySessionOption("session-algebra", "线性代数入门", "学习模式 · 3 天前", activeThisWeek = false)
            ),
            selectedSessionIds = linkedSetOf(
                "session-economics",
                "session-math",
                "challenge-python"
            ),
            isLoading = false,
            isOnline = true,
            errorMessage = null
        )
    }
}

fun WeeklyDashboardUiState.toFormalWeeklyUiState(
    sessionOptions: List<FormalWeeklySessionOption> = emptyList(),
    selectedSessionIds: Set<String> = emptySet(),
    timeZone: TimeZone = TimeZone.getDefault()
): FormalWeeklyUiState {
    val availableSessionIds = sessionOptions.mapTo(linkedSetOf()) { it.id }
    val normalizedSelection = selectedSessionIds.filterTo(linkedSetOf()) { it in availableSessionIds }
    val completedTasks = tasks.filter { it.state == StudyPlanTaskState.Completed }
    val activeSessionCount = sessionOptions.count { it.activeThisWeek }
        .takeIf { sessionOptions.isNotEmpty() }
        ?: tasks.mapNotNull(StudyPlanTask::sourceSessionId).distinct().size.takeIf { it > 0 }
    val hasWeeklyContent = summary != null || tasks.isNotEmpty()

    return FormalWeeklyUiState(
        periodLabel = summary?.let { formatWeekRange(it.weekStartEpochMillis, it.weekEndEpochMillis, timeZone) }.orEmpty(),
        narrative = summary?.summary.orEmpty(),
        unresolvedSummary = "",
        metrics = listOf(
            FormalWeeklyMetric(null, "连续学习"),
            FormalWeeklyMetric(activeSessionCount?.let { "$it 个" }, "活跃会话"),
            FormalWeeklyMetric(if (hasWeeklyContent) "${completedTasks.size} 项" else null, "完成任务"),
            FormalWeeklyMetric(null, "学习时长")
        ),
        plans = tasks
            .filter { it.state != StudyPlanTaskState.Cancelled }
            .sortedWith(compareBy<StudyPlanTask> { it.dueAtEpochMillis ?: Long.MAX_VALUE }.thenBy { it.id })
            .take(3)
            .map { task -> task.toFormalPlanItem(timeZone) },
        mainlines = emptyList(),
        weakPoints = emptyList(),
        questions = emptyList(),
        suggestions = emptyList(),
        rhythm = emptyList(),
        rhythmTotalLabel = null,
        rhythmComparisonLabel = "",
        challenge = null,
        milestones = completedTasks.take(3).map { task ->
            FormalWeeklyMilestone(
                title = task.title,
                sourceLabel = task.detail.orEmpty()
            )
        },
        modelUsage = null,
        activities = completedTasks.takeLast(2).map { task ->
            FormalWeeklyActivity(
                timeLabel = task.completedAtEpochMillis?.let { formatTime(it, timeZone) }.orEmpty(),
                title = task.title
            )
        },
        sessionOptions = sessionOptions,
        selectedSessionIds = normalizedSelection,
        isLoading = isLoading,
        isOnline = isOnline,
        errorMessage = errorMessage
    )
}

private fun StudyPlanTask.toFormalPlanItem(timeZone: TimeZone): FormalWeeklyPlanItem =
    FormalWeeklyPlanItem(
        id = id,
        timeLabel = dueAtEpochMillis?.let { formatTime(it, timeZone) }.orEmpty(),
        title = title,
        sessionLabel = detail.orEmpty(),
        completed = state == StudyPlanTaskState.Completed
    )

private fun formatWeekRange(start: Long, end: Long, timeZone: TimeZone): String {
    if (start <= 0L || end <= 0L || end < start) return ""
    val formatter = SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).apply {
        this.timeZone = timeZone
    }
    return "${formatter.format(Date(start))} - ${formatter.format(Date(end))}"
}

private fun formatTime(epochMillis: Long, timeZone: TimeZone): String =
    SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).apply {
        this.timeZone = timeZone
    }.format(Date(epochMillis))
