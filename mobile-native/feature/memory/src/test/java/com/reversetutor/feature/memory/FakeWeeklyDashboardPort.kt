package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.WeeklySummary

class FakeWeeklyDashboardPort(
    summary: WeeklySummary? = null,
    tasks: List<StudyPlanTask> = emptyList()
) : WeeklyDashboardPort {
    var summary: WeeklySummary? = summary
    var tasks: List<StudyPlanTask> = tasks
    var loadError: Throwable? = null

    override suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot {
        loadError?.let { throw it }
        return WeeklyDashboardSnapshot(summary = summary, tasks = tasks.toList())
    }
}
