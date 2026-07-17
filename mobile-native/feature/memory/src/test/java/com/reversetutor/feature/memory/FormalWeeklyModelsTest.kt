package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.WeeklySummary
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalWeeklyModelsTest {
    @Test
    fun emptyDashboardDoesNotInventFigmaMetrics() {
        val formal = WeeklyDashboardUiState().toFormalWeeklyUiState(timeZone = utc)

        assertEquals("", formal.periodLabel)
        assertEquals("", formal.narrative)
        assertTrue(formal.metrics.all { it.value == null })
        assertTrue(formal.plans.isEmpty())
        assertTrue(formal.mainlines.isEmpty())
        assertTrue(formal.rhythm.isEmpty())
        assertNull(formal.challenge)
        assertNull(formal.modelUsage)
    }

    @Test
    fun dashboardContentMapsWithoutCrossingRepositoryBoundaries() {
        val sessions = listOf(
            FormalWeeklySessionOption("session-a", "宏观经济学基础", "今天", activeThisWeek = true),
            FormalWeeklySessionOption("session-b", "高中数学", "昨天", activeThisWeek = true),
            FormalWeeklySessionOption("session-old", "线性代数", "上周", activeThisWeek = false)
        )
        val formal = WeeklyDashboardUiState(
            summary = WeeklySummary(
                id = "weekly-1",
                spaceId = "space-1",
                weekStartEpochMillis = utcMillis(2026, Calendar.JULY, 13, 0, 0),
                weekEndEpochMillis = utcMillis(2026, Calendar.JULY, 19, 23, 59),
                summary = "把两个会话里的变化率知识连接起来。"
            ),
            tasks = listOf(
                StudyPlanTask(
                    id = "task-later",
                    spaceId = "space-1",
                    title = "继续导数讲解",
                    detail = "高中数学",
                    state = StudyPlanTaskState.Planned,
                    dueAtEpochMillis = utcMillis(2026, Calendar.JULY, 15, 15, 0),
                    sourceSessionId = "session-b"
                ),
                StudyPlanTask(
                    id = "task-done",
                    spaceId = "space-1",
                    title = "复述 GDP 核算边界",
                    detail = "宏观经济学基础",
                    state = StudyPlanTaskState.Completed,
                    dueAtEpochMillis = utcMillis(2026, Calendar.JULY, 15, 9, 30),
                    completedAtEpochMillis = utcMillis(2026, Calendar.JULY, 15, 9, 45),
                    sourceSessionId = "session-a"
                ),
                StudyPlanTask(
                    id = "task-cancelled",
                    spaceId = "space-1",
                    title = "已取消",
                    state = StudyPlanTaskState.Cancelled
                )
            )
        ).toFormalWeeklyUiState(
            sessionOptions = sessions,
            selectedSessionIds = linkedSetOf("session-a", "missing-session"),
            timeZone = utc
        )

        assertEquals("7月13日 - 7月19日", formal.periodLabel)
        assertEquals("把两个会话里的变化率知识连接起来。", formal.narrative)
        assertEquals("2 个", formal.metrics.first { it.label == "活跃会话" }.value)
        assertEquals("1 项", formal.metrics.first { it.label == "完成任务" }.value)
        assertEquals(listOf("task-done", "task-later"), formal.plans.map { it.id })
        assertEquals("09:30", formal.plans.first().timeLabel)
        assertEquals(listOf("复述 GDP 核算边界"), formal.milestones.map { it.title })
        assertEquals(setOf("session-a"), formal.normalizedSelectedSessionIds)
        assertTrue(formal.mainlines.isEmpty())
        assertNull(formal.modelUsage)
    }

    @Test
    fun previewSelectionRepresentsThreeAndFourSessionSheetStates() {
        val preview = FormalWeeklyUiState.preview()
        val fourthId = "session-algebra"

        assertEquals(3, preview.normalizedSelectedSessionIds.size)
        assertEquals(4, (preview.normalizedSelectedSessionIds + fourthId).size)
        assertTrue(preview.sessionOptions.any { it.id == fourthId && !it.activeThisWeek })
        assertEquals("43.8k tokens", preview.modelUsage?.totalTokensLabel)
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = GregorianCalendar(utc).apply {
        clear()
        set(year, month, day, hour, minute)
    }.timeInMillis

    private companion object {
        val utc: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
