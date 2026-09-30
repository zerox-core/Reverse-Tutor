package com.reversetutor.preview.wiring.session

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 今日计划读适配器（V1 方向三「学习计划卡片」读侧增强）单测——全假数据，不调模型。
 */
class LearningOverviewPlanPortAdapterTest {

    private val dayMillis = 86_400_000L

    // 固定「现在」= 第 8 天中午（UTC），今天窗口 = [8d, 9d)
    private val now = 8 * dayMillis + 12 * 3_600_000L
    private val dayStart = 8 * dayMillis

    private fun task(
        id: String,
        state: StudyPlanTaskState = StudyPlanTaskState.Planned,
        dueAt: Long? = null,
        completedAt: Long = 0L,
        createdAt: Long = 0L
    ) = StudyPlanTask(
        id = id,
        spaceId = "space-1",
        title = "任务-$id",
        state = state,
        dueAtEpochMillis = dueAt,
        completedAtEpochMillis = completedAt,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt
    )

    private fun adapter(tasks: List<StudyPlanTask>) = LearningOverviewPlanPortAdapter(
        listPlanTasks = { tasks },
        nowEpochMillis = { now },
        timeZone = TimeZone.getTimeZone("UTC")
    )

    @Test
    fun futureDueTasksAreExcludedFromTodayCard() = runBlocking {
        val result = adapter(
            listOf(
                task("today", dueAt = dayStart + 3_600_000L),
                task("tomorrow", dueAt = dayStart + dayMillis + 3_600_000L),
                task("next-week", dueAt = dayStart + 5 * dayMillis)
            )
        ).listTodayPlan("space-1")

        assertEquals(listOf("today"), result.map { it.id })
    }

    @Test
    fun noDueOverdueAndTodayDueAreIncludedWithCorrectOffsets() = runBlocking {
        val result = adapter(
            listOf(
                task("no-due"),
                task("overdue-2", dueAt = dayStart - 2 * dayMillis + 3_600_000L),
                task("due-today", dueAt = dayStart + 3_600_000L)
            )
        ).listTodayPlan("space-1")

        val byId = result.associateBy { it.id }
        assertEquals(setOf("no-due", "overdue-2", "due-today"), byId.keys)
        assertNull(byId.getValue("no-due").dueDayOffset)
        assertEquals(-2, byId.getValue("overdue-2").dueDayOffset)
        assertEquals(0, byId.getValue("due-today").dueDayOffset)
    }

    @Test
    fun completedTodayIsKeptAsDoneCompletedEarlierIsDropped() = runBlocking {
        val result = adapter(
            listOf(
                task("done-today", state = StudyPlanTaskState.Completed, completedAt = dayStart + 3_600_000L),
                task("done-yesterday", state = StudyPlanTaskState.Completed, completedAt = dayStart - 3_600_000L)
            )
        ).listTodayPlan("space-1")

        assertEquals(listOf("done-today"), result.map { it.id })
        assertEquals("done", result.single().status)
    }

    @Test
    fun cancelledTasksNeverShow() = runBlocking {
        val result = adapter(
            listOf(
                task("cancelled", state = StudyPlanTaskState.Cancelled),
                task("alive")
            )
        ).listTodayPlan("space-1")

        assertEquals(listOf("alive"), result.map { it.id })
        assertEquals("pending", result.single().status)
    }

    @Test
    fun incompleteSortsByDueAscWithNoDueLastCompletedSinksToBottom() = runBlocking {
        val result = adapter(
            listOf(
                task("done", state = StudyPlanTaskState.Completed, completedAt = dayStart + 1_000L),
                task("no-due"),
                task("due-later", dueAt = dayStart + 2 * 3_600_000L),
                task("overdue", dueAt = dayStart - dayMillis)
            )
        ).listTodayPlan("space-1")

        assertEquals(listOf("overdue", "due-later", "no-due", "done"), result.map { it.id })
    }

    @Test
    fun withoutAnyReadSeamReturnsEmptyList() = runBlocking {
        val result = LearningOverviewPlanPortAdapter().listTodayPlan("space-1")
        assertEquals(emptyList<Any>(), result)
    }
}
