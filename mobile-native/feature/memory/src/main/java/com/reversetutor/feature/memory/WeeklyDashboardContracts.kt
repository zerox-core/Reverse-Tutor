package com.reversetutor.feature.memory

import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.TokenUsageRecord
import java.util.Calendar
import java.util.TimeZone

enum class WeeklyWidgetCategory(val label: String) {
    Learning("学习"),
    Data("数据"),
    Session("会话"),
    Challenge("挑战"),
    Decoration("装饰")
}

enum class WeeklyWidgetSize(val columns: Int, val rows: Int, val label: String) {
    OneByOne(1, 1, "1x1"),
    TwoByOne(2, 1, "2x1"),
    OneByTwo(1, 2, "1x2"),
    TwoByTwo(2, 2, "2x2")
}

enum class WeeklyWidgetKind(
    val title: String,
    val category: WeeklyWidgetCategory,
    val defaultSize: WeeklyWidgetSize,
    val supportsSource: Boolean
) {
    TodayPlan("今日计划", WeeklyWidgetCategory.Learning, WeeklyWidgetSize.TwoByTwo, true),
    WeeklyMainline("本周主线", WeeklyWidgetCategory.Learning, WeeklyWidgetSize.OneByOne, true),
    WeakPoints("薄弱点", WeeklyWidgetCategory.Learning, WeeklyWidgetSize.OneByOne, true),
    IncompleteTasks("未完成任务", WeeklyWidgetCategory.Learning, WeeklyWidgetSize.OneByOne, true),
    CompletedMilestones("已完成里程碑", WeeklyWidgetCategory.Learning, WeeklyWidgetSize.OneByOne, true),
    TokenUsage("Token 用量", WeeklyWidgetCategory.Data, WeeklyWidgetSize.TwoByOne, true),
    LearningDuration("学习时长", WeeklyWidgetCategory.Data, WeeklyWidgetSize.OneByOne, true),
    CompletionRate("完成率", WeeklyWidgetCategory.Data, WeeklyWidgetSize.OneByOne, true),
    UnresolvedQuestions("待解决问题", WeeklyWidgetCategory.Session, WeeklyWidgetSize.OneByOne, true),
    RecentSessions("最近会话", WeeklyWidgetCategory.Session, WeeklyWidgetSize.OneByOne, true),
    PinnedSessions("置顶会话", WeeklyWidgetCategory.Session, WeeklyWidgetSize.OneByOne, true),
    ActiveChallenge("当前挑战", WeeklyWidgetCategory.Challenge, WeeklyWidgetSize.OneByOne, true),
    DecorationBackground("背景", WeeklyWidgetCategory.Decoration, WeeklyWidgetSize.TwoByOne, false),
    DecorationDivider("分隔", WeeklyWidgetCategory.Decoration, WeeklyWidgetSize.TwoByOne, false),
    DecorationHeading("标题", WeeklyWidgetCategory.Decoration, WeeklyWidgetSize.TwoByOne, false),
    DecorationImage("图片", WeeklyWidgetCategory.Decoration, WeeklyWidgetSize.OneByOne, false)
}

enum class WeeklySourceMode {
    Global,
    Sessions,
    None
}

data class WeeklyWidgetSource(
    val mode: WeeklySourceMode,
    val sessionIds: Set<String> = emptySet()
) {
    fun normalized(): WeeklyWidgetSource = when (mode) {
        WeeklySourceMode.Global,
        WeeklySourceMode.None -> copy(sessionIds = emptySet())
        WeeklySourceMode.Sessions -> copy(sessionIds = sessionIds.filterTo(linkedSetOf()) { it.isNotBlank() })
    }

    companion object {
        val Global = WeeklyWidgetSource(WeeklySourceMode.Global)
        val None = WeeklyWidgetSource(WeeklySourceMode.None)
    }
}

data class WeeklyWidgetConfiguration(
    val id: String,
    val kind: WeeklyWidgetKind,
    val size: WeeklyWidgetSize,
    val visible: Boolean,
    val source: WeeklyWidgetSource?
)

data class WeeklyDashboardConfiguration(
    val widgets: List<WeeklyWidgetConfiguration>,
    val defaultScope: WeeklyWidgetSource = WeeklyWidgetSource.Global,
    val planOrderIds: List<String> = emptyList()
) {
    val visibleWidgets: List<WeeklyWidgetConfiguration>
        get() = widgets.filter(WeeklyWidgetConfiguration::visible)

    val libraryWidgets: List<WeeklyWidgetConfiguration>
        get() = widgets.filterNot(WeeklyWidgetConfiguration::visible)

    fun normalized(): WeeklyDashboardConfiguration {
        val unique = LinkedHashMap<String, WeeklyWidgetConfiguration>()
        widgets.forEach { widget ->
            if (widget.id.isNotBlank() && widget.id !in unique) {
                unique[widget.id] = widget.copy(
                    source = if (widget.kind.supportsSource) widget.source?.normalized() else null
                )
            }
        }
        WeeklyWidgetKind.entries.forEach { kind ->
            val id = kind.name
            if (id !in unique) {
                unique[id] = WeeklyWidgetConfiguration(
                    id = id,
                    kind = kind,
                    size = kind.defaultSize,
                    visible = false,
                    source = null
                )
            }
        }
        return copy(
            widgets = unique.values.toList(),
            defaultScope = defaultScope.normalized(),
            planOrderIds = planOrderIds.filter(String::isNotBlank).distinct()
        )
    }

    companion object {
        fun defaults(): WeeklyDashboardConfiguration {
            val visible = setOf(
                WeeklyWidgetKind.TodayPlan,
                WeeklyWidgetKind.WeeklyMainline,
                WeeklyWidgetKind.WeakPoints,
                WeeklyWidgetKind.TokenUsage
            )
            return WeeklyDashboardConfiguration(
                widgets = WeeklyWidgetKind.entries.map { kind ->
                    WeeklyWidgetConfiguration(
                        id = kind.name,
                        kind = kind,
                        size = kind.defaultSize,
                        visible = kind in visible,
                        source = if (kind.supportsSource && kind in visible) WeeklyWidgetSource.Global else null
                    )
                }
            )
        }
    }
}

sealed interface WeeklyConfigurationLoadResult {
    data object Missing : WeeklyConfigurationLoadResult
    data class Loaded(val configuration: WeeklyDashboardConfiguration) : WeeklyConfigurationLoadResult
    data class Failure(val message: String) : WeeklyConfigurationLoadResult
}

sealed interface WeeklyConfigurationSaveResult {
    data object Saved : WeeklyConfigurationSaveResult
    data class Failure(val message: String) : WeeklyConfigurationSaveResult
}

interface WeeklyDashboardConfigurationStore {
    fun load(): WeeklyConfigurationLoadResult
    fun save(configuration: WeeklyDashboardConfiguration): WeeklyConfigurationSaveResult
}

data class WeeklyGridPlacement(
    val widgetId: String,
    val row: Int,
    val column: Int,
    val size: WeeklyWidgetSize
)

data class WeeklyGridDropBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float
)

data class WeeklyGridDropTarget(val row: Int, val column: Int)

fun resolveWeeklyGridDropTarget(
    xPx: Float,
    yPx: Float,
    bounds: WeeklyGridDropBounds,
    columnStepPx: Float,
    rowStepPx: Float,
    widgetSize: WeeklyWidgetSize
): WeeklyGridDropTarget? {
    if (xPx !in bounds.leftPx..bounds.rightPx || yPx !in bounds.topPx..bounds.bottomPx) return null
    if (columnStepPx <= 0f || rowStepPx <= 0f) return null
    val column = ((xPx - bounds.leftPx) / columnStepPx).toInt()
        .coerceIn(0, WeeklyGridEngine.ColumnCount - widgetSize.columns)
    val row = ((yPx - bounds.topPx) / rowStepPx).toInt().coerceAtLeast(0)
    return WeeklyGridDropTarget(row, column)
}

object WeeklyGridEngine {
    const val ColumnCount = 2

    fun place(widgets: List<WeeklyWidgetConfiguration>): List<WeeklyGridPlacement> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        return widgets.filter(WeeklyWidgetConfiguration::visible).map { widget ->
            var row = 0
            var found: Pair<Int, Int>? = null
            while (found == null) {
                for (column in 0..(ColumnCount - widget.size.columns)) {
                    if (isFree(occupied, row, column, widget.size)) {
                        found = row to column
                        break
                    }
                }
                if (found == null) row += 1
            }
            val (targetRow, targetColumn) = requireNotNull(found)
            occupy(occupied, targetRow, targetColumn, widget.size)
            WeeklyGridPlacement(widget.id, targetRow, targetColumn, widget.size)
        }
    }

    fun move(
        widgets: List<WeeklyWidgetConfiguration>,
        widgetId: String,
        targetRow: Int,
        targetColumn: Int
    ): List<WeeklyWidgetConfiguration> {
        val visible = widgets.filter(WeeklyWidgetConfiguration::visible).toMutableList()
        val moving = visible.firstOrNull { it.id == widgetId } ?: return widgets
        visible.removeAll { it.id == widgetId }
        val placements = place(visible)
        val clampedColumn = targetColumn.coerceIn(0, ColumnCount - moving.size.columns)
        val target = placements.indexOfFirst { placement ->
            cellsOverlap(
                firstRow = targetRow.coerceAtLeast(0),
                firstColumn = clampedColumn,
                firstSize = moving.size,
                secondRow = placement.row,
                secondColumn = placement.column,
                secondSize = placement.size
            )
        }.let { if (it < 0) visible.size else it }
        visible.add(target, moving)
        val hidden = widgets.filterNot(WeeklyWidgetConfiguration::visible)
        return visible + hidden
    }

    private fun isFree(
        occupied: Set<Pair<Int, Int>>,
        row: Int,
        column: Int,
        size: WeeklyWidgetSize
    ): Boolean = cells(row, column, size).none(occupied::contains)

    private fun occupy(
        occupied: MutableSet<Pair<Int, Int>>,
        row: Int,
        column: Int,
        size: WeeklyWidgetSize
    ) {
        occupied += cells(row, column, size)
    }

    private fun cells(row: Int, column: Int, size: WeeklyWidgetSize): List<Pair<Int, Int>> =
        buildList {
            repeat(size.rows) { rowOffset ->
                repeat(size.columns) { columnOffset -> add(row + rowOffset to column + columnOffset) }
            }
        }

    private fun cellsOverlap(
        firstRow: Int,
        firstColumn: Int,
        firstSize: WeeklyWidgetSize,
        secondRow: Int,
        secondColumn: Int,
        secondSize: WeeklyWidgetSize
    ): Boolean = firstRow < secondRow + secondSize.rows &&
        firstRow + firstSize.rows > secondRow &&
        firstColumn < secondColumn + secondSize.columns &&
        firstColumn + firstSize.columns > secondColumn
}

data class WeeklyLayoutEditorState(
    val persisted: WeeklyDashboardConfiguration,
    val draft: WeeklyDashboardConfiguration = persisted,
    val editing: Boolean = false,
    val libraryExpanded: Boolean = false,
    val saveInProgress: Boolean = false
) {
    val hasChanges: Boolean get() = draft != persisted
}

sealed interface WeeklyLayoutAction {
    data object EnterEdit : WeeklyLayoutAction
    data object Save : WeeklyLayoutAction
    data object Discard : WeeklyLayoutAction
    data object PageDeparted : WeeklyLayoutAction
    data object ToggleLibrary : WeeklyLayoutAction
    data object RestoreDefaults : WeeklyLayoutAction
    data class Move(val widgetId: String, val row: Int, val column: Int) : WeeklyLayoutAction
    data class Resize(val widgetId: String, val size: WeeklyWidgetSize) : WeeklyLayoutAction
    data class Remove(val widgetId: String) : WeeklyLayoutAction
    data class Restore(val widgetId: String) : WeeklyLayoutAction
    data class RestoreAt(val widgetId: String, val row: Int, val column: Int) : WeeklyLayoutAction
    data class ChangeSource(val widgetId: String, val source: WeeklyWidgetSource) : WeeklyLayoutAction
    data class ChangeDefaultScope(val source: WeeklyWidgetSource) : WeeklyLayoutAction
    data class CommitSucceeded(val configuration: WeeklyDashboardConfiguration) : WeeklyLayoutAction
    data object CommitFailed : WeeklyLayoutAction
}

data class WeeklyLayoutReduction(
    val state: WeeklyLayoutEditorState,
    val configurationToPersist: WeeklyDashboardConfiguration? = null,
    val exitEditingOnSuccess: Boolean = false
)

object WeeklyLayoutReducer {
    fun reduce(state: WeeklyLayoutEditorState, action: WeeklyLayoutAction): WeeklyLayoutReduction = when (action) {
        WeeklyLayoutAction.EnterEdit -> WeeklyLayoutReduction(state.copy(editing = true))
        WeeklyLayoutAction.ToggleLibrary -> WeeklyLayoutReduction(
            state.copy(libraryExpanded = !state.libraryExpanded)
        )
        WeeklyLayoutAction.RestoreDefaults -> WeeklyLayoutReduction(
            state.copy(
                draft = WeeklyDashboardConfiguration.defaults().copy(
                    planOrderIds = state.draft.planOrderIds
                ),
                editing = true
            )
        )
        WeeklyLayoutAction.Save,
        WeeklyLayoutAction.PageDeparted -> {
            if (!state.editing) {
                WeeklyLayoutReduction(state)
            } else {
                val candidate = state.draft.normalized()
                WeeklyLayoutReduction(
                    state = state.copy(saveInProgress = true),
                    configurationToPersist = candidate,
                    exitEditingOnSuccess = true
                )
            }
        }
        WeeklyLayoutAction.Discard -> WeeklyLayoutReduction(
            state.copy(
                draft = state.persisted,
                editing = false,
                libraryExpanded = false,
                saveInProgress = false
            )
        )
        is WeeklyLayoutAction.CommitSucceeded -> {
            val saved = action.configuration.normalized()
            WeeklyLayoutReduction(
                state.copy(
                    persisted = saved,
                    draft = saved,
                    editing = false,
                    libraryExpanded = false,
                    saveInProgress = false
                )
            )
        }
        WeeklyLayoutAction.CommitFailed -> WeeklyLayoutReduction(
            state.copy(saveInProgress = false)
        )
        is WeeklyLayoutAction.Move -> WeeklyLayoutReduction(
            state.copy(
                draft = state.draft.copy(
                    widgets = WeeklyGridEngine.move(
                        state.draft.widgets,
                        action.widgetId,
                        action.row,
                        action.column
                    )
                )
            )
        )
        is WeeklyLayoutAction.Resize -> WeeklyLayoutReduction(
            state.copy(draft = state.draft.updateWidget(action.widgetId) { it.copy(size = action.size) })
        )
        is WeeklyLayoutAction.Remove -> WeeklyLayoutReduction(
            state.copy(draft = state.draft.updateWidget(action.widgetId) { it.copy(visible = false) })
        )
        is WeeklyLayoutAction.Restore -> WeeklyLayoutReduction(
            state.copy(
                draft = state.draft.updateWidget(action.widgetId) { widget ->
                    widget.copy(
                        visible = true,
                        source = if (widget.kind.supportsSource && widget.source == null) {
                            state.draft.defaultScope
                        } else {
                            widget.source
                        }
                    )
                }
            )
        )
        is WeeklyLayoutAction.RestoreAt -> {
            val restored = state.draft.updateWidget(action.widgetId) { widget ->
                widget.copy(
                    visible = true,
                    source = if (widget.kind.supportsSource && widget.source == null) {
                        state.draft.defaultScope
                    } else {
                        widget.source
                    }
                )
            }
            WeeklyLayoutReduction(
                state.copy(
                    draft = restored.copy(
                        widgets = WeeklyGridEngine.move(
                            restored.widgets,
                            action.widgetId,
                            action.row,
                            action.column
                        )
                    )
                )
            )
        }
        is WeeklyLayoutAction.ChangeSource -> WeeklyLayoutReduction(
            state.copy(
                draft = state.draft.updateWidget(action.widgetId) {
                    if (it.kind.supportsSource) it.copy(source = action.source.normalized()) else it
                }
            )
        )
        is WeeklyLayoutAction.ChangeDefaultScope -> WeeklyLayoutReduction(
            state.copy(draft = state.draft.copy(defaultScope = action.source.normalized()))
        )
    }

    private fun WeeklyDashboardConfiguration.updateWidget(
        id: String,
        transform: (WeeklyWidgetConfiguration) -> WeeklyWidgetConfiguration
    ): WeeklyDashboardConfiguration = copy(
        widgets = widgets.map { if (it.id == id) transform(it) else it }
    )
}

enum class WeeklyWidgetDestination {
    PlanList,
    LearningGraph,
    DurationTrend,
    CompletionTrend,
    Session,
    Challenge
}

fun WeeklyWidgetKind.normalDestination(): WeeklyWidgetDestination? = when (this) {
    WeeklyWidgetKind.TodayPlan,
    WeeklyWidgetKind.IncompleteTasks,
    WeeklyWidgetKind.CompletedMilestones -> WeeklyWidgetDestination.PlanList
    WeeklyWidgetKind.WeeklyMainline,
    WeeklyWidgetKind.WeakPoints,
    WeeklyWidgetKind.UnresolvedQuestions -> WeeklyWidgetDestination.LearningGraph
    WeeklyWidgetKind.LearningDuration -> WeeklyWidgetDestination.DurationTrend
    WeeklyWidgetKind.CompletionRate -> WeeklyWidgetDestination.CompletionTrend
    WeeklyWidgetKind.RecentSessions,
    WeeklyWidgetKind.PinnedSessions -> WeeklyWidgetDestination.Session
    WeeklyWidgetKind.ActiveChallenge -> WeeklyWidgetDestination.Challenge
    WeeklyWidgetKind.TokenUsage,
    WeeklyWidgetKind.DecorationBackground,
    WeeklyWidgetKind.DecorationDivider,
    WeeklyWidgetKind.DecorationHeading,
    WeeklyWidgetKind.DecorationImage -> null
}

enum class WeeklyTokenGestureOwner {
    Undecided,
    TokenHistory,
    WorkspacePager,
    VerticalContent
}

data class WeeklyTokenGestureState(
    val horizontalDeltaPx: Float = 0f,
    val verticalDeltaPx: Float = 0f,
    val owner: WeeklyTokenGestureOwner = WeeklyTokenGestureOwner.Undecided
)

object WeeklyTokenGesturePolicy {
    fun update(
        state: WeeklyTokenGestureState,
        horizontalDeltaPx: Float,
        verticalDeltaPx: Float,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
        directionThresholdPx: Float,
        outwardThresholdPx: Float
    ): WeeklyTokenGestureState {
        if (state.owner != WeeklyTokenGestureOwner.Undecided) return state
        val nextHorizontal = state.horizontalDeltaPx + horizontalDeltaPx
        val nextVertical = state.verticalDeltaPx + verticalDeltaPx
        val horizontalMagnitude = kotlin.math.abs(nextHorizontal)
        val verticalMagnitude = kotlin.math.abs(nextVertical)
        val owner = when {
            maxOf(horizontalMagnitude, verticalMagnitude) < directionThresholdPx ->
                WeeklyTokenGestureOwner.Undecided
            verticalMagnitude > horizontalMagnitude -> WeeklyTokenGestureOwner.VerticalContent
            nextHorizontal > 0f && !canScrollBackward ->
                if (horizontalMagnitude >= outwardThresholdPx) {
                    WeeklyTokenGestureOwner.WorkspacePager
                } else {
                    WeeklyTokenGestureOwner.Undecided
                }
            nextHorizontal < 0f && !canScrollForward ->
                if (horizontalMagnitude >= outwardThresholdPx) {
                    WeeklyTokenGestureOwner.WorkspacePager
                } else {
                    WeeklyTokenGestureOwner.Undecided
                }
            else -> WeeklyTokenGestureOwner.TokenHistory
        }
        return WeeklyTokenGestureState(nextHorizontal, nextVertical, owner)
    }
}

data class WeeklyTokenUsageEntry(
    val record: TokenUsageRecord,
    val sessionId: String?
)

data class WeeklyTokenDay(
    val dayStartEpochMillis: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheTokens: Long,
    val totalTokens: Long
)

object WeeklyTokenAggregator {
    fun aggregate(
        entries: List<WeeklyTokenUsageEntry>,
        source: WeeklyWidgetSource,
        nowEpochMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<WeeklyTokenDay> {
        val normalizedSource = source.normalized()
        val filtered = when (normalizedSource.mode) {
            WeeklySourceMode.Global -> entries
            WeeklySourceMode.None -> emptyList()
            WeeklySourceMode.Sessions -> entries.filter { it.sessionId in normalizedSource.sessionIds }
        }
        val today = startOfDay(nowEpochMillis, timeZone)
        val earliest = filtered.minOfOrNull { startOfDay(it.record.createdAtEpochMillis, timeZone) }
            ?.coerceAtMost(today)
            ?: addDays(today, -6, timeZone)
        val paddedStart = minOf(earliest, addDays(today, -6, timeZone))
        val byDay = filtered
            .filter { it.record.createdAtEpochMillis <= endOfDay(today, timeZone) }
            .groupBy { startOfDay(it.record.createdAtEpochMillis, timeZone) }
        return generateSequence(paddedStart) { previous ->
            addDays(previous, 1, timeZone).takeIf { it <= today }
        }.map { dayStart ->
            val records = byDay[dayStart].orEmpty().map(WeeklyTokenUsageEntry::record)
            WeeklyTokenDay(
                dayStartEpochMillis = dayStart,
                inputTokens = records.sumNonNegative(TokenUsageRecord::inputTokens),
                outputTokens = records.sumNonNegative(TokenUsageRecord::outputTokens),
                cacheTokens = records.sumNonNegative(TokenUsageRecord::cachedTokens),
                totalTokens = records.sumNonNegative(TokenUsageRecord::totalTokens)
            )
        }.toList()
    }

    private fun List<TokenUsageRecord>.sumNonNegative(selector: (TokenUsageRecord) -> Long): Long =
        fold(0L) { total, record ->
            val value = selector(record).coerceAtLeast(0L)
            if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
        }

    private fun startOfDay(epochMillis: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun endOfDay(dayStart: Long, timeZone: TimeZone): Long =
        addDays(dayStart, 1, timeZone) - 1L

    private fun addDays(epochMillis: Long, days: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
}

fun List<StudyPlanTask>.orderedForToday(configuration: WeeklyDashboardConfiguration): List<StudyPlanTask> {
    val order = configuration.planOrderIds.withIndex().associate { it.value to it.index }
    return filter { it.state != StudyPlanTaskState.Cancelled }
        .sortedWith(
            compareBy<StudyPlanTask> { order[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                .thenBy(StudyPlanTask::createdAtEpochMillis)
                .thenBy(StudyPlanTask::id)
        )
}

fun reorderStudyPlanByNeighbor(
    tasks: List<StudyPlanTask>,
    configuration: WeeklyDashboardConfiguration,
    taskId: String,
    neighborTaskId: String,
    placeAfter: Boolean
): WeeklyDashboardConfiguration {
    if (taskId == neighborTaskId) return configuration
    val order = configuration.planOrderIds.withIndex().associate { it.value to it.index }
    val allIds = tasks.sortedWith(
        compareBy<StudyPlanTask> { order[it.id] ?: Int.MAX_VALUE }
            .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy(StudyPlanTask::createdAtEpochMillis)
            .thenBy(StudyPlanTask::id)
    ).map(StudyPlanTask::id).toMutableList()
    if (taskId !in allIds || neighborTaskId !in allIds) return configuration
    allIds.remove(taskId)
    val neighborIndex = allIds.indexOf(neighborTaskId)
    allIds.add((neighborIndex + if (placeAfter) 1 else 0).coerceIn(0, allIds.size), taskId)
    return configuration.copy(planOrderIds = allIds).normalized()
}

fun List<StudyPlanTask>.visibleInTodayWidget(
    source: WeeklyWidgetSource,
    nowEpochMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): List<StudyPlanTask> {
    val calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    val endExclusive = calendar.timeInMillis
    return filter { task ->
        val dueToday = task.dueAtEpochMillis?.let { it in start until endExclusive } ?: true
        val inSource = when (source.mode) {
            WeeklySourceMode.Global -> true
            WeeklySourceMode.None -> false
            WeeklySourceMode.Sessions -> task.sourceSessionId in source.sessionIds
        }
        dueToday && inSource
    }
}
