package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SessionGoalDashboardTest {

    private val todayMillis: Long = Calendar.getInstance().apply {
        set(2026, 8, 19, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // —— 清单编解码 ——

    @Test
    fun checklistEncodeDecodeRoundTripsWithCheckboxState() {
        val items = listOf(
            GoalChecklistItem("看完第三章", done = true),
            GoalChecklistItem("做完课后习题", done = false)
        )
        val encoded = encodeGoalChecklist(items)
        assertEquals("[x] 看完第三章\n[ ] 做完课后习题", encoded)
        assertEquals(items, decodeGoalChecklist(encoded))
    }

    @Test
    fun legacyPlainTextDecodesAsUncheckedItems() {
        val decoded = decodeGoalChecklist("第一章 集合与逻辑\n第二章 函数")
        assertEquals(
            listOf(GoalChecklistItem("第一章 集合与逻辑"), GoalChecklistItem("第二章 函数")),
            decoded
        )
    }

    @Test
    fun legacyBulletSymbolsAreStrippedWhenDecoding() {
        val decoded = decodeGoalChecklist("- 看书\n• 做题\n· 复习\n* 总结")
        assertEquals(listOf("看书", "做题", "复习", "总结"), decoded.map { it.text })
        assertEquals(listOf(false, false, false, false), decoded.map { it.done })
    }

    @Test
    fun placeholderAndBlankTextDecodeToEmptyList() {
        assertEquals(emptyList<GoalChecklistItem>(), decodeGoalChecklist("未设置"))
        assertEquals(emptyList<GoalChecklistItem>(), decodeGoalChecklist(""))
        assertEquals(emptyList<GoalChecklistItem>(), decodeGoalChecklist("   \n  "))
    }

    @Test
    fun encodeDropsBlankItemsAndTrimsText() {
        val encoded = encodeGoalChecklist(
            listOf(GoalChecklistItem("  第一节  "), GoalChecklistItem(""), GoalChecklistItem("   "))
        )
        assertEquals("[ ] 第一节", encoded)
    }

    @Test
    fun checklistProgressCountsDoneFraction() {
        val items = listOf(
            GoalChecklistItem("a", done = true),
            GoalChecklistItem("b"),
            GoalChecklistItem("c"),
            GoalChecklistItem("d")
        )
        assertEquals(0.25f, goalChecklistProgress(items), 0.0001f)
        assertEquals(0f, goalChecklistProgress(emptyList()), 0.0001f)
    }

    // —— 截止时间解析 ——

    @Test
    fun fullDateFormatsParseToDaysLeft() {
        assertEquals(12, parseGoalDeadlineDaysLeft("2026-10-01", todayMillis))
        assertEquals(12, parseGoalDeadlineDaysLeft("2026/10/1", todayMillis))
        assertEquals(12, parseGoalDeadlineDaysLeft("2026.10.01", todayMillis))
        assertEquals(12, parseGoalDeadlineDaysLeft("2026年10月1日", todayMillis))
    }

    @Test
    fun todayAndPastDatesReportZeroAndNegative() {
        assertEquals(0, parseGoalDeadlineDaysLeft("2026-09-19", todayMillis))
        assertEquals(-1, parseGoalDeadlineDaysLeft("2026-09-18", todayMillis))
    }

    @Test
    fun yearlessDateUsesThisYearThenRollsToNext() {
        assertEquals(12, parseGoalDeadlineDaysLeft("10月1日", todayMillis))
        assertEquals(1, parseGoalDeadlineDaysLeft("9月20日", todayMillis))
        // 1月1日已过 → 顺延到 2027-01-01：9月剩11天 + 10月31 + 11月30 + 12月31 + 1 = 104
        assertEquals(104, parseGoalDeadlineDaysLeft("1月1日", todayMillis))
    }

    @Test
    fun leadingTextFallsBackToRegexExtraction() {
        assertEquals(12, parseGoalDeadlineDaysLeft("截止日：2026-10-01", todayMillis))
    }

    @Test
    fun unparseableAndPlaceholderTextReturnNull() {
        assertNull(parseGoalDeadlineDaysLeft("下周再说吧", todayMillis))
        assertNull(parseGoalDeadlineDaysLeft("未设置", todayMillis))
        assertNull(parseGoalDeadlineDaysLeft("", todayMillis))
    }

    // —— 倒计时文案与紧急度 ——

    @Test
    fun deadlineLabelAndToneMatchUrgencyBands() {
        assertEquals("已超期 3 天", goalDeadlineLabel(-3))
        assertEquals("今天截止", goalDeadlineLabel(0))
        assertEquals("仅剩 2 天", goalDeadlineLabel(2))
        assertEquals("还剩 12 天", goalDeadlineLabel(12))
        assertEquals(GoalDeadlineTone.Overdue, goalDeadlineTone(-1))
        assertEquals(GoalDeadlineTone.Soon, goalDeadlineTone(0))
        assertEquals(GoalDeadlineTone.Soon, goalDeadlineTone(3))
        assertEquals(GoalDeadlineTone.Calm, goalDeadlineTone(4))
    }

    // —— coordinator 即时应用 ——

    @Test
    fun applyGoalPlanImmediateAppliesToAppliedFormSnapshotAndPersists() {
        val store = InMemorySessionSettingsStore()
        val coordinator = newCoordinator(store)

        coordinator.applyGoalPlanImmediate { it.copy(currentState = "卡住了") }

        assertEquals("卡住了", coordinator.state.applied.goalPlan.currentState)
        assertEquals("卡住了", coordinator.state.form.goalPlan.currentState)
        assertEquals("卡住了", coordinator.state.applied.snapshot.currentState)

        val restored = newCoordinator(store)
        assertEquals("卡住了", restored.state.applied.goalPlan.currentState)
    }

    @Test
    fun checklistTogglePersistsThroughSnapshotMirror() {
        val store = InMemorySessionSettingsStore()
        val coordinator = newCoordinator(store)

        val items = listOf(GoalChecklistItem("第一站"), GoalChecklistItem("第二站"))
        coordinator.applyGoalPlanImmediate { it.copy(stageMilestones = encodeGoalChecklist(items)) }
        assertEquals("[ ] 第一站\n[ ] 第二站", coordinator.state.applied.snapshot.stageMilestones)

        val toggled = decodeGoalChecklist(coordinator.state.applied.goalPlan.stageMilestones)
            .mapIndexed { index, item -> if (index == 0) item.copy(done = true) else item }
        coordinator.applyGoalPlanImmediate { it.copy(stageMilestones = encodeGoalChecklist(toggled)) }

        val restored = newCoordinator(store)
        assertEquals("[x] 第一站\n[ ] 第二站", restored.state.applied.goalPlan.stageMilestones)
        assertEquals("[x] 第一站\n[ ] 第二站", restored.state.applied.snapshot.stageMilestones)
    }

    @Test
    fun `next pending item is first unchecked weekly entry`() {
        val items = decodeGoalChecklist("[x] 已做完\n[ ] 第一件没做的\n[ ] 第二件没做的")
        assertEquals("第一件没做的", goalNextPendingItem(items)?.text)
    }

    @Test
    fun `next pending item is null when all done or empty`() {
        assertEquals(null, goalNextPendingItem(decodeGoalChecklist("[x] 全做完")))
        assertEquals(null, goalNextPendingItem(emptyList()))
    }

    @Test
    fun `milestone stage reflects done in-progress and upcoming`() {
        val items = decodeGoalChecklist("[x] 第一站\n[ ] 第二站\n[ ] 第三站")
        assertEquals("已完成", goalMilestoneStage(items, 0))
        assertEquals("进行中", goalMilestoneStage(items, 1))
        assertEquals("未开始", goalMilestoneStage(items, 2))
    }

    @Test
    fun `checklist percent floors progress and handles empty`() {
        assertEquals(33, goalChecklistPercent(decodeGoalChecklist("[x] a\n[ ] b\n[ ] c")))
        assertEquals(0, goalChecklistPercent(emptyList()))
    }

    private fun newCoordinator(store: InMemorySessionSettingsStore): SessionSettingsCoordinator =
        SessionSettingsCoordinator(
            sessionId = "session-a",
            initial = SessionSettingsDocument.fromSnapshot(
                NewSessionConfiguration(
                    title = "离散数学",
                    learnerRole = "谨慎的初学者",
                    goal = "掌握图论"
                )
            ),
            initialSources = emptyList(),
            store = store,
            deleteCapability = SourceFileDeleteCapability.Unavailable
        )
}
