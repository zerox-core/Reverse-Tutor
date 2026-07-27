package com.reversetutor.feature.memory

import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyDashboardContractsTest {
    @Test
    fun defaultsExposeFourSpecifiedWidgetsAndAllFourSizes() {
        val configuration = WeeklyDashboardConfiguration.defaults()

        assertEquals(
            listOf(
                WeeklyWidgetKind.TodayPlan,
                WeeklyWidgetKind.WeeklyMainline,
                WeeklyWidgetKind.WeakPoints,
                WeeklyWidgetKind.TokenUsage
            ),
            configuration.visibleWidgets.map(WeeklyWidgetConfiguration::kind)
        )
        assertEquals(WeeklyWidgetSize.TwoByOne, configuration.visibleWidgets.last().size)
        assertTrue(configuration.libraryWidgets.filter { it.kind.supportsSource }.all { it.source == null })
        assertEquals(setOf(1 to 1, 2 to 1, 1 to 2, 2 to 2), WeeklyWidgetSize.entries.map { it.columns to it.rows }.toSet())
    }

    @Test
    fun gridPacksTwoColumnsAndMovingIntoCollisionReordersThenSnaps() {
        val defaults = WeeklyDashboardConfiguration.defaults()
        val moved = WeeklyGridEngine.move(
            defaults.widgets,
            WeeklyWidgetKind.TokenUsage.name,
            targetRow = 0,
            targetColumn = 0
        )
        val placements = WeeklyGridEngine.place(moved)

        assertEquals(WeeklyWidgetKind.TokenUsage.name, placements.first().widgetId)
        assertEquals(0, placements.first().row)
        assertEquals(0, placements.first().column)
        assertTrue(placements.all { it.column + it.size.columns <= WeeklyGridEngine.ColumnCount })
        val occupied = mutableSetOf<Pair<Int, Int>>()
        placements.forEach { placement ->
            repeat(placement.size.rows) { row ->
                repeat(placement.size.columns) { column ->
                    assertTrue(occupied.add(placement.row + row to placement.column + column))
                }
            }
        }
    }

    @Test
    fun editorSavesOnEmptyTapOrPageDepartureAndBackDiscardsDraft() {
        val initial = WeeklyLayoutEditorState(WeeklyDashboardConfiguration.defaults())
        val editing = WeeklyLayoutReducer.reduce(initial, WeeklyLayoutAction.EnterEdit).state
        val resized = WeeklyLayoutReducer.reduce(
            editing,
            WeeklyLayoutAction.Resize(WeeklyWidgetKind.WeakPoints.name, WeeklyWidgetSize.TwoByTwo)
        ).state

        val discarded = WeeklyLayoutReducer.reduce(resized, WeeklyLayoutAction.Discard)
        assertFalse(discarded.state.editing)
        assertEquals(initial.persisted, discarded.state.draft)
        assertEquals(null, discarded.configurationToPersist)

        val departed = WeeklyLayoutReducer.reduce(resized, WeeklyLayoutAction.PageDeparted)
        assertTrue(departed.state.editing)
        assertTrue(departed.state.saveInProgress)
        assertEquals(initial.persisted, departed.state.persisted)
        assertEquals(WeeklyWidgetSize.TwoByTwo, departed.configurationToPersist
            ?.visibleWidgets
            ?.first { it.kind == WeeklyWidgetKind.WeakPoints }
            ?.size)

        val failed = WeeklyLayoutReducer.reduce(departed.state, WeeklyLayoutAction.CommitFailed).state
        assertTrue(failed.editing)
        assertFalse(failed.saveInProgress)
        assertEquals(initial.persisted, failed.persisted)
        assertEquals(resized.draft, failed.draft)

        val committed = WeeklyLayoutReducer.reduce(
            failed,
            WeeklyLayoutAction.CommitSucceeded(requireNotNull(departed.configurationToPersist))
        ).state
        assertFalse(committed.editing)
        assertEquals(committed.persisted, committed.draft)
        assertEquals(WeeklyWidgetSize.TwoByTwo, committed.persisted.visibleWidgets
            .first { it.kind == WeeklyWidgetKind.WeakPoints }.size)
    }

    @Test
    fun removingReturnsWidgetToLibraryAndRestoringUsesDefaultOnlyWhenUnconfigured() {
        val initial = WeeklyLayoutEditorState(WeeklyDashboardConfiguration.defaults(), editing = true)
        val removed = WeeklyLayoutReducer.reduce(
            initial,
            WeeklyLayoutAction.Remove(WeeklyWidgetKind.WeeklyMainline.name)
        ).state
        assertTrue(removed.draft.libraryWidgets.any { it.kind == WeeklyWidgetKind.WeeklyMainline })

        val withoutSource = removed.copy(
            draft = removed.draft.copy(
                widgets = removed.draft.widgets.map {
                    if (it.kind == WeeklyWidgetKind.WeeklyMainline) it.copy(source = null) else it
                },
                defaultScope = WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("session-a"))
            )
        )
        val restored = WeeklyLayoutReducer.reduce(
            withoutSource,
            WeeklyLayoutAction.Restore(WeeklyWidgetKind.WeeklyMainline.name)
        ).state.draft

        assertEquals(
            WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("session-a")),
            restored.visibleWidgets.first { it.kind == WeeklyWidgetKind.WeeklyMainline }.source
        )
        assertEquals(WeeklyWidgetSource.Global, restored.widgets.first { it.kind == WeeklyWidgetKind.TokenUsage }.source)
    }

    @Test
    fun tokenUsageAggregatesByLocalDateWithLayersAndSevenDayMinimum() {
        val zone = TimeZone.getTimeZone("GMT+08:00")
        val now = time(zone, 2026, Calendar.JULY, 27, 12)
        val entries = listOf(
            usage("a", "session-a", time(zone, 2026, Calendar.JULY, 26, 23), 10, 20, 3),
            usage("b", "session-b", time(zone, 2026, Calendar.JULY, 27, 1), 7, 5, 2),
            usage("c", "session-a", time(zone, 2026, Calendar.JULY, 27, 2), 8, 4, 1)
        )

        val global = WeeklyTokenAggregator.aggregate(entries, WeeklyWidgetSource.Global, now, zone)
        val session = WeeklyTokenAggregator.aggregate(
            entries,
            WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("session-a")),
            now,
            zone
        )
        val none = WeeklyTokenAggregator.aggregate(entries, WeeklyWidgetSource.None, now, zone)

        assertEquals(7, global.size)
        assertEquals(15, global.last().inputTokens)
        assertEquals(9, global.last().outputTokens)
        assertEquals(3, global.last().cacheTokens)
        assertEquals(24, global.last().totalTokens)
        assertEquals(8, session.last().inputTokens)
        assertTrue(none.all { it.totalTokens == 0L })
    }

    @Test
    fun tokenHistoryKeepsChronologicalDaysBeyondViewportWithoutJumpOrAllMode() {
        val zone = TimeZone.getTimeZone("UTC")
        val now = time(zone, 2026, Calendar.JULY, 27, 12)
        val old = usage("old", null, time(zone, 2026, Calendar.JULY, 1, 12), 1, 2, 0)

        val days = WeeklyTokenAggregator.aggregate(listOf(old), WeeklyWidgetSource.Global, now, zone)

        assertEquals(27, days.size)
        assertTrue(days.zipWithNext().all { (first, second) -> first.dayStartEpochMillis < second.dayStartEpochMillis })
    }

    @Test
    fun todayPlanFiltersFutureDatesAndHonorsExplicitSessionOrNoSource() {
        val zone = TimeZone.getTimeZone("UTC")
        val now = time(zone, 2026, Calendar.JULY, 27, 12)
        val tasks = listOf(
            StudyPlanTask("today-a", WeeklyDashboardSpaceId, "A", dueAtEpochMillis = now, sourceSessionId = "a"),
            StudyPlanTask("today-b", WeeklyDashboardSpaceId, "B", dueAtEpochMillis = now, sourceSessionId = "b"),
            StudyPlanTask("future", WeeklyDashboardSpaceId, "Future", dueAtEpochMillis = now + 86_400_000L, sourceSessionId = "a"),
            StudyPlanTask("undated", WeeklyDashboardSpaceId, "Undated", sourceSessionId = "a")
        )

        assertEquals(listOf("today-a", "undated"), tasks.visibleInTodayWidget(
            WeeklyWidgetSource(WeeklySourceMode.Sessions, setOf("a")), now, zone
        ).map { it.id })
        assertTrue(tasks.visibleInTodayWidget(WeeklyWidgetSource.None, now, zone).isEmpty())
    }

    @Test
    fun confirmedCatalogDestinationsAndDropBoundsAreExplicit() {
        assertTrue(WeeklyWidgetKind.entries.containsAll(listOf(
            WeeklyWidgetKind.IncompleteTasks,
            WeeklyWidgetKind.CompletedMilestones,
            WeeklyWidgetKind.LearningDuration,
            WeeklyWidgetKind.CompletionRate,
            WeeklyWidgetKind.PinnedSessions,
            WeeklyWidgetKind.DecorationBackground,
            WeeklyWidgetKind.DecorationDivider,
            WeeklyWidgetKind.DecorationHeading,
            WeeklyWidgetKind.DecorationImage
        )))
        assertEquals(WeeklyWidgetDestination.PlanList, WeeklyWidgetKind.IncompleteTasks.normalDestination())
        assertEquals(WeeklyWidgetDestination.LearningGraph, WeeklyWidgetKind.WeakPoints.normalDestination())
        assertEquals(WeeklyWidgetDestination.Session, WeeklyWidgetKind.PinnedSessions.normalDestination())
        assertEquals(null, WeeklyWidgetKind.TokenUsage.normalDestination())

        val bounds = WeeklyGridDropBounds(10f, 20f, 210f, 420f)
        assertEquals(
            WeeklyGridDropTarget(1, 1),
            resolveWeeklyGridDropTarget(180f, 180f, bounds, 100f, 140f, WeeklyWidgetSize.OneByOne)
        )
        assertEquals(null, resolveWeeklyGridDropTarget(5f, 100f, bounds, 100f, 140f, WeeklyWidgetSize.OneByOne))
        assertEquals(
            0,
            resolveWeeklyGridDropTarget(180f, 30f, bounds, 100f, 140f, WeeklyWidgetSize.TwoByOne)?.column
        )
    }

    @Test
    fun tokenGestureUsesDominantAxisBoundariesAndOutwardThreshold() {
        fun update(
            state: WeeklyTokenGestureState = WeeklyTokenGestureState(),
            dx: Float,
            dy: Float = 0f,
            backward: Boolean = true,
            forward: Boolean = true
        ) = WeeklyTokenGesturePolicy.update(state, dx, dy, backward, forward, 6f, 18f)

        assertEquals(WeeklyTokenGestureOwner.TokenHistory, update(dx = -8f).owner)
        assertEquals(WeeklyTokenGestureOwner.VerticalContent, update(dx = 4f, dy = 9f).owner)
        assertEquals(WeeklyTokenGestureOwner.Undecided, update(dx = 12f, backward = false).owner)
        assertEquals(WeeklyTokenGestureOwner.WorkspacePager, update(dx = 20f, backward = false).owner)
        assertEquals(WeeklyTokenGestureOwner.WorkspacePager, update(dx = -20f, forward = false).owner)
        val owned = update(dx = -8f)
        assertEquals(WeeklyTokenGestureOwner.TokenHistory, update(owned, dx = 30f, backward = false).owner)
    }

    @Test
    fun neighborReorderUsesStableIdsAcrossFilteredAndCancelledTasks() {
        val tasks = listOf(
            StudyPlanTask("other", WeeklyDashboardSpaceId, "Other", sourceSessionId = "b", dueAtEpochMillis = 1),
            StudyPlanTask("first", WeeklyDashboardSpaceId, "First", sourceSessionId = "a", dueAtEpochMillis = 2),
            StudyPlanTask("future", WeeklyDashboardSpaceId, "Future", sourceSessionId = "a", dueAtEpochMillis = 99),
            StudyPlanTask("undated", WeeklyDashboardSpaceId, "Undated", sourceSessionId = "a"),
            StudyPlanTask("done", WeeklyDashboardSpaceId, "Done", sourceSessionId = "a", state = StudyPlanTaskState.Completed),
            StudyPlanTask("cancelled", WeeklyDashboardSpaceId, "Cancelled", state = StudyPlanTaskState.Cancelled)
        )
        val reordered = reorderStudyPlanByNeighbor(
            tasks,
            WeeklyDashboardConfiguration.defaults(),
            taskId = "undated",
            neighborTaskId = "first",
            placeAfter = false
        )

        assertTrue(reordered.planOrderIds.indexOf("undated") < reordered.planOrderIds.indexOf("first"))
        assertTrue("other" in reordered.planOrderIds)
        assertTrue("future" in reordered.planOrderIds)
        assertTrue("done" in reordered.planOrderIds)
        assertTrue("cancelled" in reordered.planOrderIds)
    }

    private fun usage(
        id: String,
        sessionId: String?,
        createdAt: Long,
        input: Long,
        output: Long,
        cache: Long
    ) = WeeklyTokenUsageEntry(
        TokenUsageRecord(
            id = id,
            spaceId = WeeklyDashboardSpaceId,
            turnId = "turn-$id",
            attempt = 1,
            inputTokens = input,
            outputTokens = output,
            cachedTokens = cache,
            totalTokens = input + output,
            createdAtEpochMillis = createdAt
        ),
        sessionId
    )

    private fun time(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis
}
