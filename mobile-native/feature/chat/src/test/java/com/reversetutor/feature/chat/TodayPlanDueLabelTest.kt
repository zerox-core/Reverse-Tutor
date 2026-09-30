package com.reversetutor.feature.chat

import com.reversetutor.core.domain.TodayPlanTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到期标签纯函数测试（V1 方向三「学习计划卡片」读侧增强）。
 */
class TodayPlanDueLabelTest {

    private fun task(status: String = "pending", offset: Int? = null) =
        TodayPlanTask(id = "t1", title = "背单词", status = status, dueDayOffset = offset)

    @Test
    fun doneTaskShowsNoDueLabel() {
        assertEquals("", task(status = "done", offset = 0).dueLabel())
        assertFalse(task(status = "done", offset = -3).isDueOverdue())
    }

    @Test
    fun noDueDateShowsNoLabel() {
        assertEquals("", task(offset = null).dueLabel())
        assertFalse(task(offset = null).isDueOverdue())
    }

    @Test
    fun dueTodayShowsTodayLabel() {
        assertEquals("今天到期", task(offset = 0).dueLabel())
        assertFalse(task(offset = 0).isDueOverdue())
    }

    @Test
    fun overdueShowsOverdueDays() {
        assertEquals("逾期 1 天", task(offset = -1).dueLabel())
        assertEquals("逾期 3 天", task(offset = -3).dueLabel())
        assertTrue(task(offset = -1).isDueOverdue())
    }

    @Test
    fun futureOffsetFallsBackToDaysLater() {
        assertEquals("明天到期", task(offset = 1).dueLabel())
        assertEquals("3 天后到期", task(offset = 3).dueLabel())
    }
}
