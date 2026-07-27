package com.reversetutor.feature.memory

import android.content.Context
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun WeeklyDashboardGridScreen(
    state: WeeklyDashboardUiState,
    sessionOptions: List<FormalWeeklySessionOption>,
    onAction: (WeeklyDashboardUiAction) -> Unit,
    isPageActive: Boolean,
    onWidgetDragChanged: (Boolean) -> Unit,
    onInnerHorizontalControlChanged: (Boolean) -> Unit,
    onEditSurfaceChanged: (Boolean, () -> Unit) -> Unit,
    onOpenWidget: (WeeklyWidgetKind) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val editor = state.layout
    val configuration = if (editor.editing) editor.draft else editor.persisted
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var sourceWidgetId by remember { mutableStateOf<String?>(null) }
    var sizeWidgetId by remember { mutableStateOf<String?>(null) }
    var destinationWidget by remember { mutableStateOf<WeeklyWidgetKind?>(null) }
    var showHeaderScope by remember { mutableStateOf(false) }
    var gridMetrics by remember { mutableStateOf<WeeklyGridUiMetrics?>(null) }
    var planEditorTask by remember { mutableStateOf<StudyPlanDraft?>(null) }
    val context = LocalContext.current
    var reducedMotion by remember(context) { mutableStateOf(context.reducedMotionEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = context.reducedMotionEnabled()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(isPageActive) {
        if (!isPageActive && editor.editing) {
            onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.PageDeparted))
        }
    }
    DisposableEffect(editor.editing) {
        if (editor.editing) {
            onEditSurfaceChanged(true) {
                selectedWidgetId = null
                onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Discard))
            }
        } else {
            onEditSurfaceChanged(false) {}
        }
        onDispose { onEditSurfaceChanged(false) {} }
    }
    DisposableEffect(Unit) {
        onDispose {
            onWidgetDragChanged(false)
            onInnerHorizontalControlChanged(false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WeeklyDashboardBackground)
            .navigationBarsPadding()
            .semantics { contentDescription = "本周学习组件面板" }
    ) {
        WeeklyFixedInformationBar(
            state = state,
            configuration = configuration,
            sessionOptions = sessionOptions,
            editing = editor.editing,
            onOpenScope = { if (!editor.editing) showHeaderScope = true },
            onEdit = {
                selectedWidgetId = configuration.visibleWidgets.firstOrNull()?.id
                onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit))
            },
            onSave = {
                selectedWidgetId = null
                onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Save))
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable(enabled = editor.editing) {
                    selectedWidgetId = null
                    onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Save))
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WeeklyStatus(state, onAction)
            WeeklyEditableGrid(
                configuration = configuration,
                dashboardState = state,
                selectedWidgetId = selectedWidgetId,
                editing = editor.editing,
                reducedMotion = reducedMotion,
                onLongPress = {
                    selectedWidgetId = it
                    onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit))
                },
                onSelect = { id ->
                    selectedWidgetId = id
                    if (editor.editing) sourceWidgetId = id
                },
                onMove = { id, row, column ->
                    onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Move(id, row, column)))
                },
                onRemove = { id ->
                    selectedWidgetId = null
                    onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Remove(id)))
                },
                onResizeRequest = { sizeWidgetId = it },
                onOpenWidget = { kind ->
                    onOpenWidget(kind)
                    when (kind.normalDestination()) {
                        WeeklyWidgetDestination.PlanList,
                        WeeklyWidgetDestination.DurationTrend,
                        WeeklyWidgetDestination.CompletionTrend -> destinationWidget = kind
                        WeeklyWidgetDestination.Session -> {
                            val source = configuration.widgets.firstOrNull { it.kind == kind }?.source
                            val candidates = sessionOptions.filter { option ->
                                val sourceMatch = when (source?.mode) {
                                    WeeklySourceMode.Global, null -> true
                                    WeeklySourceMode.None -> false
                                    WeeklySourceMode.Sessions -> option.id in source.sessionIds
                                }
                                sourceMatch && (kind != WeeklyWidgetKind.PinnedSessions || option.pinned)
                            }
                            candidates.firstOrNull()?.let { onOpenSession(it.id) }
                        }
                        WeeklyWidgetDestination.LearningGraph,
                        WeeklyWidgetDestination.Challenge,
                        null -> Unit
                    }
                },
                sessionOptions = sessionOptions,
                onGridMetricsChanged = { gridMetrics = it },
                onWidgetDragChanged = onWidgetDragChanged,
                onInnerHorizontalControlChanged = onInnerHorizontalControlChanged,
                onPlanAction = onAction,
                onEditPlan = { task -> planEditorTask = StudyPlanDraft.from(task) },
                onAddPlan = { planEditorTask = StudyPlanDraft() }
            )

            if (editor.editing) {
                WeeklyWidgetLibrary(
                    configuration = configuration,
                    expanded = editor.libraryExpanded,
                    onToggle = {
                        onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.ToggleLibrary))
                    },
                    onRestore = { id ->
                        selectedWidgetId = id
                        onAction(WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.Restore(id)))
                    },
                    onDrop = { id, rootPosition ->
                        val widget = configuration.widgets.firstOrNull { it.id == id }
                        val metrics = gridMetrics
                        val target = if (widget != null && metrics != null) {
                            resolveWeeklyGridDropTarget(
                                xPx = rootPosition.x,
                                yPx = rootPosition.y,
                                bounds = WeeklyGridDropBounds(
                                    metrics.bounds.left,
                                    metrics.bounds.top,
                                    metrics.bounds.right,
                                    metrics.bounds.bottom + metrics.rowStepPx
                                ),
                                columnStepPx = metrics.columnStepPx,
                                rowStepPx = metrics.rowStepPx,
                                widgetSize = widget.size
                            )
                        } else null
                        if (target != null) {
                            selectedWidgetId = id
                            onAction(
                                WeeklyDashboardUiAction.EditLayout(
                                    WeeklyLayoutAction.RestoreAt(id, target.row, target.column)
                                )
                            )
                            true
                        } else {
                            false
                        }
                    },
                    onRestoreDefaults = {
                        selectedWidgetId = WeeklyWidgetKind.TodayPlan.name
                        onAction(
                            WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.RestoreDefaults)
                        )
                    },
                    onDefaultScope = { sourceWidgetId = DefaultScopeEditorId },
                    onWidgetDragChanged = onWidgetDragChanged
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    sourceWidgetId?.let { id ->
        val widget = if (id == DefaultScopeEditorId) {
            WeeklyWidgetConfiguration(
                id = DefaultScopeEditorId,
                kind = WeeklyWidgetKind.WeeklyMainline,
                size = WeeklyWidgetSize.OneByOne,
                visible = false,
                source = configuration.defaultScope
            )
        } else {
            configuration.widgets.firstOrNull { it.id == id }
        }
        widget?.let {
            WeeklySourceSheet(
                widget = it,
                sessions = sessionOptions,
                onDismiss = { sourceWidgetId = null },
                onApply = { source ->
                    onAction(
                        WeeklyDashboardUiAction.EditLayout(
                            if (id == DefaultScopeEditorId) {
                                WeeklyLayoutAction.ChangeDefaultScope(source)
                            } else {
                                WeeklyLayoutAction.ChangeSource(it.id, source)
                            }
                        )
                    )
                    sourceWidgetId = null
                }
            )
        }
    }
    if (showHeaderScope) {
        WeeklyDefaultScopeSheet(
            source = configuration.defaultScope,
            sessions = sessionOptions,
            onDismiss = { showHeaderScope = false },
            onApply = { source ->
                onAction(WeeklyDashboardUiAction.ChangeDefaultScope(source))
                showHeaderScope = false
            }
        )
    }
    sizeWidgetId?.let { id ->
        configuration.widgets.firstOrNull { it.id == id }?.let { widget ->
            WeeklySizeSheet(
                widget = widget,
                onDismiss = { sizeWidgetId = null },
                onSelect = { size ->
                    onAction(
                        WeeklyDashboardUiAction.EditLayout(
                            WeeklyLayoutAction.Resize(widget.id, size)
                        )
                    )
                    sizeWidgetId = null
                }
            )
        }
    }
    destinationWidget?.let { kind ->
        WeeklyLocalDestinationSheet(
            kind = kind,
            state = state,
            source = configuration.widgets.firstOrNull { it.kind == kind }?.source,
            onDismiss = { destinationWidget = null },
            onPlanAction = onAction,
            onEditPlan = { task ->
                destinationWidget = null
                planEditorTask = StudyPlanDraft.from(task)
            }
        )
    }
    planEditorTask?.let { draft ->
        WeeklyPlanEditorSheet(
            draft = draft,
            sessions = sessionOptions,
            onDismiss = { planEditorTask = null },
            onSave = { updated ->
                val id = updated.id
                if (id == null) {
                    onAction(
                        WeeklyDashboardUiAction.AddPlan(
                            title = updated.title,
                            detail = updated.detail,
                            dueAtEpochMillis = updated.dueAtEpochMillis,
                            sourceSessionId = updated.sessionId
                        )
                    )
                } else {
                    onAction(
                        WeeklyDashboardUiAction.EditPlan(
                            id = id,
                            title = updated.title,
                            detail = updated.detail,
                            dueAtEpochMillis = updated.dueAtEpochMillis,
                            sourceSessionId = updated.sessionId
                        )
                    )
                }
                planEditorTask = null
            }
        )
    }
}

@Composable
private fun WeeklyFixedInformationBar(
    state: WeeklyDashboardUiState,
    configuration: WeeklyDashboardConfiguration,
    sessionOptions: List<FormalWeeklySessionOption>,
    editing: Boolean,
    onOpenScope: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit
) {
    val activeTasks = state.tasks.filter { it.state != com.reversetutor.core.model.StudyPlanTaskState.Cancelled }
    val completed = activeTasks.count { it.state == com.reversetutor.core.model.StudyPlanTaskState.Completed }
    val progress = if (activeTasks.isEmpty()) 0f else completed.toFloat() / activeTasks.size
    Surface(
        modifier = Modifier.fillMaxWidth().height(112.dp).shadow(2.dp).testTag("weekly-fixed-header"),
        color = Color(0xFFFAFCFF),
        contentColor = WeeklyDashboardInk
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本周学习", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        weeklyRangeLabel(state.summary),
                        color = WeeklyDashboardMuted,
                        fontSize = 9.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.testTag("weekly-range")
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onOpenScope,
                        enabled = !editing,
                        modifier = Modifier.height(32.dp).testTag("weekly-default-scope"),
                        color = Color(0xFFEAF2FC),
                        contentColor = Color(0xFF315F9F),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "范围 · ${sourceLabel(configuration.defaultScope)}",
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "本周活跃 ${sessionOptions.count(FormalWeeklySessionOption::activeThisWeek)}",
                        color = WeeklyDashboardMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.testTag("weekly-active-scope")
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(96.dp).height(4.dp)
                            .background(Color(0xFFD8E1EC), RoundedCornerShape(2.dp))
                    ) {
                        if (progress > 0f) {
                            Box(
                                Modifier.fillMaxWidth(progress).height(4.dp)
                                    .background(Color(0xFF3972BD), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (activeTasks.isEmpty()) "暂无计划进度" else "$completed / ${activeTasks.size} 已完成",
                        color = WeeklyDashboardMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.testTag("weekly-overall-progress")
                    )
                }
            }
            Surface(
                onClick = if (editing) onSave else onEdit,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (editing) "保存组件布局" else "编辑组件布局"
                },
                color = if (editing) Color(0xFF285FAD) else Color(0xFFEAF2FC),
                contentColor = if (editing) Color.White else Color(0xFF315F9F),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (editing) "✓" else "▦", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WeeklyStatus(
    state: WeeklyDashboardUiState,
    onAction: (WeeklyDashboardUiAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    when {
        state.isLoading && state.tasks.isEmpty() -> Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("读取本地数据", color = WeeklyDashboardMuted, fontSize = 11.sp)
        }
    }
    state.snapshotFailure?.let { failure -> WeeklyFailureStrip(
        label = "周学习数据读取失败：${failure.message}",
        tag = "weekly-snapshot-failure",
        onRetry = { onAction(WeeklyDashboardUiAction.RefreshLocal) }
    ) }
    state.planFailure?.let { failure -> WeeklyFailureStrip(
        label = "计划保存失败：${failure.message}",
        tag = "weekly-plan-failure",
        onRetry = { onAction(WeeklyDashboardUiAction.RetryPlanMutation) }
    ) }
    state.configurationFailure?.let { failure -> WeeklyFailureStrip(
        label = "组件配置失败：${failure.message}",
        tag = "weekly-configuration-failure",
        onRetry = {
            onAction(
                if (state.pendingConfigurationRetry != null) {
                    WeeklyDashboardUiAction.RetryConfigurationSave
                } else {
                    WeeklyDashboardUiAction.RetryConfigurationLoad
                }
            )
        }
    ) }
    if (!state.isOnline) Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF0F5FA),
            shape = RoundedCornerShape(7.dp),
        ) {
            Text(
                "当前离线，显示设备上的学习记录",
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color(0xFF52677D),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun WeeklyFailureStrip(label: String, tag: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        color = Color(0xFFFFF3F1),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFFE6B9B2))
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f), color = Color(0xFF8C3E38), fontSize = 10.sp)
            TextButton(onClick = onRetry, modifier = Modifier.height(44.dp)) { Text("重试") }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WeeklyEditableGrid(
    configuration: WeeklyDashboardConfiguration,
    dashboardState: WeeklyDashboardUiState,
    selectedWidgetId: String?,
    editing: Boolean,
    reducedMotion: Boolean,
    onLongPress: (String) -> Unit,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    onResizeRequest: (String) -> Unit,
    onOpenWidget: (WeeklyWidgetKind) -> Unit,
    sessionOptions: List<FormalWeeklySessionOption>,
    onGridMetricsChanged: (WeeklyGridUiMetrics) -> Unit,
    onWidgetDragChanged: (Boolean) -> Unit,
    onInnerHorizontalControlChanged: (Boolean) -> Unit,
    onPlanAction: (WeeklyDashboardUiAction) -> Unit,
    onEditPlan: (com.reversetutor.core.model.StudyPlanTask) -> Unit,
    onAddPlan: () -> Unit
) {
    val placements = remember(configuration.widgets) { WeeklyGridEngine.place(configuration.widgets) }
    val visible = configuration.visibleWidgets
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var placeholderTarget by remember { mutableStateOf<WeeklyGridDropTarget?>(null) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val cellWidth = (maxWidth - gap) / 2
        val cellHeight = 132.dp
        val maxBottom = placements.maxOfOrNull { it.row + it.size.rows } ?: 1
        val gridHeight = cellHeight * maxBottom + gap * (maxBottom - 1).coerceAtLeast(0)
        val density = LocalDensity.current

        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .testTag("weekly-widget-grid")
                .onGloballyPositioned { coordinates ->
                    onGridMetricsChanged(
                        WeeklyGridUiMetrics(
                            bounds = coordinates.boundsInRoot(),
                            columnStepPx = with(density) { (cellWidth + gap).toPx() },
                            rowStepPx = with(density) { (cellHeight + gap).toPx() }
                        )
                    )
                }
        ) {
            val placeholderWidget = visible.firstOrNull { it.id == draggingWidgetId }
            val target = placeholderTarget
            if (placeholderWidget != null && target != null) {
                val placeholderWidth = cellWidth * placeholderWidget.size.columns + gap * (placeholderWidget.size.columns - 1)
                val placeholderHeight = cellHeight * placeholderWidget.size.rows + gap * (placeholderWidget.size.rows - 1)
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (target.column * with(density) { (cellWidth + gap).toPx() }).roundToInt(),
                                (target.row * with(density) { (cellHeight + gap).toPx() }).roundToInt()
                            )
                        }
                        .width(placeholderWidth)
                        .height(placeholderHeight)
                        .border(2.dp, Color(0xFF3972BD), RoundedCornerShape(8.dp))
                        .background(Color(0x223972BD), RoundedCornerShape(8.dp))
                        .testTag("weekly-drag-placeholder")
                )
            }
            visible.forEach { widget ->
                val placement = placements.first { it.widgetId == widget.id }
                var dragX by remember(widget.id) { mutableFloatStateOf(0f) }
                var dragY by remember(widget.id) { mutableFloatStateOf(0f) }
                var invalidTarget by remember(widget.id) { mutableStateOf(false) }
                val haptic = LocalHapticFeedback.current
                val selected = selectedWidgetId == widget.id
                val shakeTransition = rememberInfiniteTransition(label = "weekly-widget-shake")
                val animatedRotation by shakeTransition.animateFloat(
                    initialValue = -0.32f,
                    targetValue = 0.32f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(180),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "weekly-widget-shake-angle"
                )
                val phaseRotation = if (editing && !reducedMotion) animatedRotation else 0f
                val stepX = with(density) { (cellWidth + gap).toPx() }
                val stepY = with(density) { (cellHeight + gap).toPx() }
                val width = cellWidth * widget.size.columns + gap * (widget.size.columns - 1)
                val height = cellHeight * widget.size.rows + gap * (widget.size.rows - 1)
                val currentPlacement by rememberUpdatedState(placement)

                WeeklyWidgetCard(
                    widget = widget,
                    state = dashboardState,
                    selected = selected,
                    editing = editing,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (placement.column * stepX + dragX).roundToInt(),
                                y = (placement.row * stepY + dragY).roundToInt()
                            )
                        }
                        .width(width)
                        .height(height)
                        .testTag("weekly-widget-${widget.kind.name}")
                        .zIndex(if (dragX != 0f || dragY != 0f) 3f else if (selected) 2f else 1f)
                        .graphicsLayer { rotationZ = phaseRotation }
                        .pointerInput(widget.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress(widget.id)
                                    onSelect(widget.id)
                                    draggingWidgetId = widget.id
                                    placeholderTarget = WeeklyGridDropTarget(
                                        currentPlacement.row,
                                        currentPlacement.column
                                    )
                                    onWidgetDragChanged(true)
                                },
                                onDragCancel = {
                                    dragX = 0f
                                    dragY = 0f
                                    draggingWidgetId = null
                                    placeholderTarget = null
                                    onWidgetDragChanged(false)
                                },
                                onDragEnd = {
                                    placeholderTarget?.let { target ->
                                        onMove(widget.id, target.row, target.column)
                                    }
                                    dragX = 0f
                                    dragY = 0f
                                    draggingWidgetId = null
                                    placeholderTarget = null
                                    onWidgetDragChanged(false)
                                }
                            ) { change, amount ->
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                                val rawColumn = (currentPlacement.column + dragX / stepX).roundToInt()
                                val rawRow = (currentPlacement.row + dragY / stepY).roundToInt()
                                val targetColumn = rawColumn.coerceIn(
                                    0,
                                    WeeklyGridEngine.ColumnCount - widget.size.columns
                                )
                                val targetRow = rawRow.coerceIn(0, maxBottom)
                                val outside = rawColumn != targetColumn || rawRow != targetRow
                                if (outside && !invalidTarget) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                invalidTarget = outside
                                val candidate = WeeklyGridDropTarget(targetRow, targetColumn)
                                if (candidate != placeholderTarget) {
                                    placeholderTarget = candidate
                                    onMove(widget.id, candidate.row, candidate.column)
                                    dragX = 0f
                                    dragY = 0f
                                }
                            }
                        }
                        .clickable(enabled = !editing) { onOpenWidget(widget.kind) },
                    configuration = configuration,
                    sessionOptions = sessionOptions,
                    onSource = { onSelect(widget.id) },
                    onRemove = { onRemove(widget.id) },
                    onResize = { onResizeRequest(widget.id) },
                    onInnerHorizontalControlChanged = onInnerHorizontalControlChanged,
                    onPlanAction = onPlanAction,
                    onEditPlan = onEditPlan,
                    onAddPlan = onAddPlan
                )
            }
        }
    }
}

@Composable
private fun WeeklyWidgetCard(
    widget: WeeklyWidgetConfiguration,
    state: WeeklyDashboardUiState,
    selected: Boolean,
    editing: Boolean,
    configuration: WeeklyDashboardConfiguration,
    sessionOptions: List<FormalWeeklySessionOption>,
    onSource: () -> Unit,
    onRemove: () -> Unit,
    onResize: () -> Unit,
    onInnerHorizontalControlChanged: (Boolean) -> Unit,
    onPlanAction: (WeeklyDashboardUiAction) -> Unit,
    onEditPlan: (com.reversetutor.core.model.StudyPlanTask) -> Unit,
    onAddPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "${widget.kind.title}组件" },
        color = Color(0xFFFCFDFF),
        contentColor = WeeklyDashboardInk,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF3972BD) else Color(0xFFC9D8E8)),
        shadowElevation = if (selected) 3.dp else 1.dp
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    widget.kind.title,
                    Modifier.weight(1f),
                    color = Color(0xFF344B69),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (editing) {
                    Surface(
                        onClick = onRemove,
                        modifier = Modifier.size(44.dp).testTag("weekly-remove-${widget.kind.name}"),
                        color = Color.Transparent,
                        contentColor = Color(0xFFAE4A45),
                        shape = CircleShape
                    ) { Box(contentAlignment = Alignment.Center) { Text("×", fontSize = 19.sp) } }
                }
            }
            if (widget.kind.supportsSource) {
                Surface(
                    onClick = onSource,
                    enabled = editing,
                    color = Color.Transparent,
                    modifier = Modifier.height(28.dp).testTag("weekly-source-${widget.kind.name}")
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            sourceLabel(widget.source),
                            color = WeeklyDashboardMuted,
                            fontSize = 8.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxSize()) {
                when (widget.kind) {
                    WeeklyWidgetKind.TodayPlan -> WeeklyTodayPlanWidget(
                        tasks = state.tasks
                            .visibleInTodayWidget(widget.source ?: WeeklyWidgetSource.None)
                            .orderedForToday(configuration),
                        disabled = state.planMutationInProgress || editing,
                        onAction = onPlanAction,
                        onEdit = onEditPlan,
                        onAdd = onAddPlan
                    )
                    WeeklyWidgetKind.WeeklyMainline -> WeeklySimpleState(
                        widget.source,
                        state.summary?.summary?.takeIf { widget.source?.mode == WeeklySourceMode.Global },
                        if (widget.source?.mode == WeeklySourceMode.Sessions) {
                            "所选会话暂无可汇总主线"
                        } else {
                            "本周还没有形成学习主线"
                        }
                    )
                    WeeklyWidgetKind.WeakPoints -> WeeklySimpleState(
                        widget.source,
                        null,
                        "暂无重复出现的薄弱点"
                    )
                    WeeklyWidgetKind.IncompleteTasks -> WeeklyTaskSummary(
                        tasks = state.tasks.forWeeklySource(widget.source)
                            .filter { it.state != com.reversetutor.core.model.StudyPlanTaskState.Completed },
                        empty = "暂无未完成任务"
                    )
                    WeeklyWidgetKind.CompletedMilestones -> WeeklyTaskSummary(
                        tasks = state.tasks.forWeeklySource(widget.source)
                            .filter { it.state == com.reversetutor.core.model.StudyPlanTaskState.Completed },
                        empty = "暂无已完成里程碑"
                    )
                    WeeklyWidgetKind.TokenUsage -> WeeklyTokenWidget(
                        entries = state.tokenUsage,
                        source = widget.source ?: WeeklyWidgetSource.None,
                        onInnerHorizontalControlChanged = onInnerHorizontalControlChanged
                    )
                    WeeklyWidgetKind.LearningDuration -> WeeklySimpleState(
                        widget.source,
                        null,
                        "当前数据层未提供学习时长记录"
                    )
                    WeeklyWidgetKind.CompletionRate -> {
                        val tasks = state.tasks.forWeeklySource(widget.source)
                        val completed = tasks.count {
                            it.state == com.reversetutor.core.model.StudyPlanTaskState.Completed
                        }
                        WeeklySimpleState(
                            widget.source,
                            if (tasks.isEmpty()) null else "${completed * 100 / tasks.size}% · $completed / ${tasks.size}",
                            "暂无可计算完成率的任务"
                        )
                    }
                    WeeklyWidgetKind.UnresolvedQuestions -> WeeklySimpleState(widget.source, null, "暂无待解决问题")
                    WeeklyWidgetKind.RecentSessions -> WeeklySessionSummary(
                        sessions = sessionOptions.forWeeklySessionSource(widget.source),
                        empty = "暂无符合来源的会话"
                    )
                    WeeklyWidgetKind.PinnedSessions -> WeeklySessionSummary(
                        sessions = sessionOptions.forWeeklySessionSource(widget.source).filter(FormalWeeklySessionOption::pinned),
                        empty = "暂无符合来源的置顶会话"
                    )
                    WeeklyWidgetKind.ActiveChallenge -> WeeklySimpleState(
                        widget.source,
                        null,
                        "挑战数据当前不可用"
                    )
                    WeeklyWidgetKind.DecorationBackground -> WeeklyDecoration("开发者内置背景")
                    WeeklyWidgetKind.DecorationDivider -> WeeklyDecoration("开发者内置分隔")
                    WeeklyWidgetKind.DecorationHeading -> WeeklyDecoration("本周学习")
                    WeeklyWidgetKind.DecorationImage -> WeeklyDecoration("开发者内置图片")
                }
                if (editing) {
                    Surface(
                        onClick = onResize,
                        modifier = Modifier.align(Alignment.BottomEnd).size(44.dp)
                            .testTag("weekly-resize-${widget.kind.name}"),
                        color = Color(0xFFEAF1F8),
                        contentColor = Color(0xFF315F9F),
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("↘", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyTaskSummary(
    tasks: List<com.reversetutor.core.model.StudyPlanTask>,
    empty: String
) {
    if (tasks.isEmpty()) {
        Text(empty, color = WeeklyDashboardMuted, fontSize = 10.sp, lineHeight = 16.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tasks.take(4).forEach { task ->
                Text(
                    "• ${task.title}",
                    color = WeeklyDashboardInk,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeeklySessionSummary(sessions: List<FormalWeeklySessionOption>, empty: String) {
    if (sessions.isEmpty()) {
        Text(empty, color = WeeklyDashboardMuted, fontSize = 10.sp, lineHeight = 16.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sessions.take(4).forEach { session ->
                Text(
                    session.title,
                    color = WeeklyDashboardInk,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeeklyDecoration(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = Color(0xFF536A84), fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun WeeklySimpleState(source: WeeklyWidgetSource?, value: String?, empty: String) {
    val text = when {
        source?.mode == WeeklySourceMode.None -> "未选择数据来源"
        source?.mode == WeeklySourceMode.Sessions && source.sessionIds.isEmpty() -> "尚未选择会话"
        !value.isNullOrBlank() -> value
        else -> empty
    }
    Text(
        text,
        color = if (value.isNullOrBlank()) WeeklyDashboardMuted else WeeklyDashboardInk,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WeeklyTodayPlanWidget(
    tasks: List<com.reversetutor.core.model.StudyPlanTask>,
    disabled: Boolean,
    onAction: (WeeklyDashboardUiAction) -> Unit,
    onEdit: (com.reversetutor.core.model.StudyPlanTask) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            Text("今天还没有计划", color = WeeklyDashboardMuted, fontSize = 10.sp)
        } else {
            tasks.take(4).forEachIndexed { index, task ->
                var dragY by remember(task.id) { mutableFloatStateOf(0f) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(task.id, tasks.size, disabled) {
                            if (!disabled) {
                                detectDragGesturesAfterLongPress(
                                    onDragEnd = {
                                        val target = (index + if (dragY >= 0f) 1 else -1).coerceIn(tasks.indices)
                                        if (target != index) {
                                            onAction(
                                                WeeklyDashboardUiAction.MovePlan(
                                                    taskId = task.id,
                                                    neighborTaskId = tasks[target].id,
                                                    placeAfter = target > index
                                                )
                                            )
                                        }
                                        dragY = 0f
                                    },
                                    onDragCancel = { dragY = 0f }
                                ) { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onAction(WeeklyDashboardUiAction.TogglePlanCompletion(task.id)) },
                        enabled = !disabled,
                        modifier = Modifier.size(44.dp).semantics { contentDescription = "切换${task.title}完成状态" },
                        color = Color.Transparent,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(18.dp)) {
                                drawCircle(
                                    color = if (task.state == com.reversetutor.core.model.StudyPlanTaskState.Completed) Color(0xFF2E9470) else Color(0xFF9AAABC),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                if (task.state == com.reversetutor.core.model.StudyPlanTaskState.Completed) {
                                    drawLine(Color(0xFF2E9470), Offset(size.width * .25f, size.height * .52f), Offset(size.width * .43f, size.height * .7f), 1.5.dp.toPx(), StrokeCap.Round)
                                    drawLine(Color(0xFF2E9470), Offset(size.width * .43f, size.height * .7f), Offset(size.width * .77f, size.height * .3f), 1.5.dp.toPx(), StrokeCap.Round)
                                }
                            }
                        }
                    }
                    Text(
                        task.title,
                        Modifier.weight(1f),
                        color = if (task.state == com.reversetutor.core.model.StudyPlanTaskState.Completed) WeeklyDashboardMuted else WeeklyDashboardInk,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onEdit(task) }, enabled = !disabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) {
                        Text("✎", fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = { onAction(WeeklyDashboardUiAction.DeletePlan(task.id)) },
                        enabled = !disabled,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp)
                    ) { Text("×", color = Color(0xFFB34E49), fontSize = 17.sp) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onAdd,
            enabled = !disabled,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) { Text("+ 添加计划", fontSize = 10.sp) }
    }
}

@Composable
private fun WeeklyTokenWidget(
    entries: List<WeeklyTokenUsageEntry>,
    source: WeeklyWidgetSource,
    onInnerHorizontalControlChanged: (Boolean) -> Unit
) {
    val nowEpochMillis = remember(entries, source) { System.currentTimeMillis() }
    val matchingEntries = remember(entries, source, nowEpochMillis) {
        entries.filter { entry ->
            entry.record.createdAtEpochMillis <= nowEpochMillis && when (source.mode) {
                WeeklySourceMode.Global -> true
                WeeklySourceMode.None -> false
                WeeklySourceMode.Sessions -> entry.sessionId in source.sessionIds
            }
        }
    }
    val days = remember(matchingEntries, source, nowEpochMillis) {
        WeeklyTokenAggregator.aggregate(matchingEntries, source, nowEpochMillis)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (days.size - 7).coerceAtLeast(0))
    LaunchedEffect(days.size) {
        if (days.isNotEmpty()) listState.scrollToItem((days.size - 7).coerceAtLeast(0))
    }
    if (source.mode == WeeklySourceMode.None ||
        (source.mode == WeeklySourceMode.Sessions && source.sessionIds.isEmpty()) ||
        matchingEntries.isEmpty()
    ) {
        WeeklySimpleState(source, null, "当前范围暂无 Token 记录")
        return
    }
    val maxTokens = days.maxOfOrNull(WeeklyTokenDay::totalTokens)?.coerceAtLeast(1L) ?: 1L
    Column(Modifier.fillMaxSize()) {
        val current = days.takeLast(7)
        Text(
            "总量 ${formatTokenCount(current.sumOf(WeeklyTokenDay::totalTokens))}  ·  输入 / 输出 / 缓存",
            color = Color(0xFF536A84),
            fontSize = 8.sp,
            lineHeight = 13.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dayWidth = maxWidth / 7
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("weekly-token-history")
                    .innerHorizontalGesture(listState, onInnerHorizontalControlChanged),
                userScrollEnabled = days.size > 7
            ) {
                itemsIndexed(days, key = { _, day -> day.dayStartEpochMillis }) { _, day ->
                    Column(
                        Modifier.width(dayWidth).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val totalHeight = 54.dp * (day.totalTokens.toFloat() / maxTokens.toFloat()).coerceIn(0f, 1f)
                        val layerTotal = (day.inputTokens + day.outputTokens + day.cacheTokens).coerceAtLeast(1L)
                        val inputShare = day.inputTokens.toFloat() / layerTotal
                        val outputShare = day.outputTokens.toFloat() / layerTotal
                        val cacheShare = day.cacheTokens.toFloat() / layerTotal
                        Column(
                            Modifier.width(12.dp).height(totalHeight.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (day.cacheTokens > 0) Box(Modifier.fillMaxWidth().weight(cacheShare.coerceAtLeast(.01f)).background(Color(0xFFB88A44)))
                            if (day.outputTokens > 0) Box(Modifier.fillMaxWidth().weight(outputShare.coerceAtLeast(.01f)).background(Color(0xFF48A27D)))
                            if (day.inputTokens > 0) Box(Modifier.fillMaxWidth().weight(inputShare.coerceAtLeast(.01f)).background(Color(0xFF4B80C2)))
                            if (day.totalTokens == 0L) Box(Modifier.fillMaxSize().background(Color(0xFFD9E1EA)))
                        }
                        Text(formatDay(day.dayStartEpochMillis), color = WeeklyDashboardMuted, fontSize = 7.sp, lineHeight = 11.sp)
                    }
                }
            }
        }
    }
}

private fun Modifier.innerHorizontalGesture(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onChanged: (Boolean) -> Unit
): Modifier = pointerInput(listState, onChanged) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var gestureState = WeeklyTokenGestureState()
        val directionThreshold = 6.dp.toPx()
        val outwardThreshold = 18.dp.toPx()
        try {
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                val delta = change.positionChange()
                gestureState = WeeklyTokenGesturePolicy.update(
                    state = gestureState,
                    horizontalDeltaPx = delta.x,
                    verticalDeltaPx = delta.y,
                    canScrollBackward = listState.canScrollBackward,
                    canScrollForward = listState.canScrollForward,
                    directionThresholdPx = directionThreshold,
                    outwardThresholdPx = outwardThreshold
                )
                onChanged(gestureState.owner == WeeklyTokenGestureOwner.TokenHistory)
            } while (event.changes.any { it.pressed })
        } finally {
            onChanged(false)
        }
    }
}

@Composable
private fun WeeklyWidgetLibrary(
    configuration: WeeklyDashboardConfiguration,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRestore: (String) -> Unit,
    onDrop: (String, Offset) -> Boolean,
    onRestoreDefaults: () -> Unit,
    onDefaultScope: () -> Unit,
    onWidgetDragChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("weekly-widget-library"),
        color = Color(0xFFF8FAFD),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFC8D7E8))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onToggle).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("组件库", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("${configuration.libraryWidgets.size}  ${if (expanded) "⌃" else "⌄"}", color = WeeklyDashboardMuted, fontSize = 10.sp)
            }
            if (expanded) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(onClick = onDefaultScope, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("新组件来源 · ${sourceLabel(configuration.defaultScope)}", fontSize = 9.sp, maxLines = 1)
                    }
                    TextButton(onClick = onRestoreDefaults, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("恢复默认配置", fontSize = 9.sp)
                    }
                }
                WeeklyWidgetCategory.entries.forEach { category ->
                    val widgets = configuration.libraryWidgets.filter { it.kind.category == category }
                    if (widgets.isNotEmpty()) {
                        Text(
                            category.label,
                            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                            color = Color(0xFF526C8B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                        widgets.forEach { widget ->
                            var itemBounds by remember(widget.id) { mutableStateOf<Rect?>(null) }
                            var dragOrigin by remember(widget.id) { mutableStateOf(Offset.Zero) }
                            var dragOffset by remember(widget.id) { mutableStateOf(Offset.Zero) }
                            val haptic = LocalHapticFeedback.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("weekly-library-${widget.kind.name}")
                                    .onGloballyPositioned { itemBounds = it.boundsInRoot() }
                                    .graphicsLayer {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                    }
                                    .zIndex(if (dragOffset != Offset.Zero) 4f else 1f)
                                    .pointerInput(widget.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { localPosition ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dragOrigin = itemBounds?.topLeft?.plus(localPosition) ?: localPosition
                                                dragOffset = Offset.Zero
                                                onWidgetDragChanged(true)
                                            },
                                            onDragCancel = {
                                                dragOffset = Offset.Zero
                                                onWidgetDragChanged(false)
                                            },
                                            onDragEnd = {
                                                val accepted = onDrop(widget.id, dragOrigin + dragOffset)
                                                if (!accepted) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                dragOffset = Offset.Zero
                                                onWidgetDragChanged(false)
                                            }
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                        }
                                    }
                                    .clickable { onRestore(widget.id) }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("↥", color = Color(0xFF4777B2), fontSize = 14.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(widget.kind.title, Modifier.weight(1f), fontSize = 10.sp)
                                Text(widget.size.label, color = WeeklyDashboardMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WeeklySourceSheet(
    widget: WeeklyWidgetConfiguration,
    sessions: List<FormalWeeklySessionOption>,
    onDismiss: () -> Unit,
    onApply: (WeeklyWidgetSource) -> Unit,
    title: String = "${widget.kind.title} · 数据来源"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember(widget.id) { mutableStateOf(widget.source?.mode ?: WeeklySourceMode.Global) }
    var selected by remember(widget.id) { mutableStateOf(widget.source?.sessionIds.orEmpty()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFBFDFF),
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("不选择来源也是有效配置", color = WeeklyDashboardMuted, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            WeeklySourceMode.entries.forEach { option ->
                Surface(
                    onClick = { mode = option },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    color = if (mode == option) Color(0xFFE8F1FB) else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (mode == option) "●" else "○", color = Color(0xFF3D70B0), fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(option.label(), fontSize = 11.sp)
                    }
                }
            }
            if (mode == WeeklySourceMode.Sessions) {
                sessions.forEach { session ->
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clickable {
                            selected = if (session.id in selected) selected - session.id else selected + session.id
                        }.padding(start = 22.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (session.id in selected) "✓" else "□", color = Color(0xFF3971B5), fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(session.title, Modifier.weight(1f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (sessions.isEmpty()) Text("暂无可选择的会话", color = WeeklyDashboardMuted, fontSize = 10.sp, modifier = Modifier.padding(14.dp))
            }
            Button(
                onClick = {
                    onApply(WeeklyWidgetSource(mode, if (mode == WeeklySourceMode.Sessions) selected else emptySet()))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(7.dp)
            ) { Text("应用来源") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WeeklyDefaultScopeSheet(
    source: WeeklyWidgetSource,
    sessions: List<FormalWeeklySessionOption>,
    onDismiss: () -> Unit,
    onApply: (WeeklyWidgetSource) -> Unit
) {
    WeeklySourceSheet(
        widget = WeeklyWidgetConfiguration(
            id = DefaultScopeEditorId,
            kind = WeeklyWidgetKind.WeeklyMainline,
            size = WeeklyWidgetSize.OneByOne,
            visible = false,
            source = source
        ),
        sessions = sessions,
        onDismiss = onDismiss,
        onApply = onApply,
        title = "默认数据范围"
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WeeklySizeSheet(
    widget: WeeklyWidgetConfiguration,
    onDismiss: () -> Unit,
    onSelect: (WeeklyWidgetSize) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFBFDFF),
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${widget.kind.title} · 组件尺寸", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            WeeklyWidgetSize.entries.forEach { size ->
                Surface(
                    onClick = { onSelect(size) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .testTag("weekly-size-${size.name}"),
                    color = if (widget.size == size) Color(0xFFE5F0FC) else Color.Transparent,
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (widget.size == size) "●" else "○", color = Color(0xFF3971B5))
                        Spacer(Modifier.width(10.dp))
                        Text(size.label, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WeeklyLocalDestinationSheet(
    kind: WeeklyWidgetKind,
    state: WeeklyDashboardUiState,
    source: WeeklyWidgetSource?,
    onDismiss: () -> Unit,
    onPlanAction: (WeeklyDashboardUiAction) -> Unit,
    onEditPlan: (com.reversetutor.core.model.StudyPlanTask) -> Unit
) {
    val tasks = state.tasks.forWeeklySource(source)
    val visibleTasks = when (kind) {
        WeeklyWidgetKind.IncompleteTasks -> tasks.filter {
            it.state != com.reversetutor.core.model.StudyPlanTaskState.Completed
        }
        WeeklyWidgetKind.CompletedMilestones -> tasks.filter {
            it.state == com.reversetutor.core.model.StudyPlanTaskState.Completed
        }
        else -> tasks
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFBFDFF),
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(kind.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            when (kind.normalDestination()) {
                WeeklyWidgetDestination.DurationTrend -> Text(
                    "当前数据层未提供学习时长趋势。",
                    color = WeeklyDashboardMuted,
                    fontSize = 11.sp
                )
                WeeklyWidgetDestination.CompletionTrend -> {
                    val completed = tasks.count {
                        it.state == com.reversetutor.core.model.StudyPlanTaskState.Completed
                    }
                    Text(
                        if (tasks.isEmpty()) "暂无可计算完成率的任务" else "完成率 ${completed * 100 / tasks.size}%（$completed / ${tasks.size}）",
                        color = if (tasks.isEmpty()) WeeklyDashboardMuted else WeeklyDashboardInk,
                        fontSize = 11.sp
                    )
                }
                WeeklyWidgetDestination.PlanList -> {
                    if (visibleTasks.isEmpty()) {
                        Text("当前范围暂无任务", color = WeeklyDashboardMuted, fontSize = 11.sp)
                    }
                    visibleTasks.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.title, Modifier.weight(1f), fontSize = 11.sp)
                            TextButton(
                                onClick = {
                                    onPlanAction(WeeklyDashboardUiAction.TogglePlanCompletion(task.id))
                                },
                                modifier = Modifier.height(44.dp)
                            ) { Text(if (task.state == com.reversetutor.core.model.StudyPlanTaskState.Completed) "恢复" else "完成") }
                            TextButton(onClick = { onEditPlan(task) }, modifier = Modifier.height(44.dp)) {
                                Text("编辑")
                            }
                        }
                    }
                }
                else -> Text("该目的地当前不可用", color = WeeklyDashboardMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private data class StudyPlanDraft(
    val id: String? = null,
    val title: String = "",
    val detail: String = "",
    val dueAtEpochMillis: Long? = null,
    val sessionId: String? = null
) {
    companion object {
        fun from(task: com.reversetutor.core.model.StudyPlanTask) = StudyPlanDraft(
            id = task.id,
            title = task.title,
            detail = task.detail.orEmpty(),
            dueAtEpochMillis = task.dueAtEpochMillis,
            sessionId = task.sourceSessionId
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WeeklyPlanEditorSheet(
    draft: StudyPlanDraft,
    sessions: List<FormalWeeklySessionOption>,
    onDismiss: () -> Unit,
    onSave: (StudyPlanDraft) -> Unit
) {
    var title by remember(draft.id) { mutableStateOf(draft.title) }
    var detail by remember(draft.id) { mutableStateOf(draft.detail) }
    var sessionId by remember(draft.id) { mutableStateOf(draft.sessionId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFBFDFF),
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (draft.id == null) "添加今日计划" else "编辑今日计划", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            WeeklyTextField("计划标题", title, { title = it })
            WeeklyTextField("备注", detail, { detail = it })
            if (sessions.isNotEmpty()) {
                Text("关联会话（可选）", color = WeeklyDashboardMuted, fontSize = 9.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        WeeklySessionChip("不关联", sessionId == null) { sessionId = null }
                    }
                    itemsIndexed(sessions, key = { _, session -> session.id }) { _, session ->
                        WeeklySessionChip(session.title, sessionId == session.id) { sessionId = session.id }
                    }
                }
            }
            Button(
                onClick = { onSave(draft.copy(title = title, detail = detail, sessionId = sessionId)) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315F9F)),
                shape = RoundedCornerShape(7.dp)
            ) { Text("保存") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WeeklyTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = WeeklyDashboardMuted, fontSize = 9.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, Color(0xFFC6D5E5), RoundedCornerShape(7.dp)).padding(12.dp),
            textStyle = TextStyle(color = WeeklyDashboardInk, fontSize = 11.sp, lineHeight = 17.sp)
        )
    }
}

@Composable
private fun WeeklySessionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp).widthIn(max = 150.dp),
        color = if (selected) Color(0xFF315F9F) else Color(0xFFEAF1F8),
        contentColor = if (selected) Color.White else Color(0xFF46617D),
        shape = RoundedCornerShape(22.dp)
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun sourceLabel(source: WeeklyWidgetSource?): String = when (source?.mode) {
    WeeklySourceMode.Global -> "全部会话"
    WeeklySourceMode.Sessions -> if (source.sessionIds.isEmpty()) "未选择会话" else "${source.sessionIds.size} 个会话"
    WeeklySourceMode.None,
    null -> "无来源"
}

private data class WeeklyGridUiMetrics(
    val bounds: Rect,
    val columnStepPx: Float,
    val rowStepPx: Float
)

private fun Context.reducedMotionEnabled(): Boolean = runCatching {
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

private fun weeklyRangeLabel(summary: com.reversetutor.core.model.WeeklySummary?): String {
    if (summary == null || summary.weekStartEpochMillis <= 0L ||
        summary.weekEndEpochMillis < summary.weekStartEpochMillis
    ) {
        return "周范围待生成"
    }
    val formatter = SimpleDateFormat("M月d日", Locale.getDefault())
    return "${formatter.format(Date(summary.weekStartEpochMillis))} - ${formatter.format(Date(summary.weekEndEpochMillis))}"
}

private fun List<com.reversetutor.core.model.StudyPlanTask>.forWeeklySource(
    source: WeeklyWidgetSource?
): List<com.reversetutor.core.model.StudyPlanTask> = filter { task ->
    task.state != com.reversetutor.core.model.StudyPlanTaskState.Cancelled && when (source?.mode) {
        WeeklySourceMode.Global -> true
        WeeklySourceMode.Sessions -> task.sourceSessionId in source.sessionIds
        WeeklySourceMode.None,
        null -> false
    }
}

private fun List<FormalWeeklySessionOption>.forWeeklySessionSource(
    source: WeeklyWidgetSource?
): List<FormalWeeklySessionOption> = filter { session ->
    when (source?.mode) {
        WeeklySourceMode.Global -> true
        WeeklySourceMode.Sessions -> session.id in source.sessionIds
        WeeklySourceMode.None,
        null -> false
    }
}

private fun WeeklySourceMode.label(): String = when (this) {
    WeeklySourceMode.Global -> "全部会话"
    WeeklySourceMode.Sessions -> "选择多个会话"
    WeeklySourceMode.None -> "不选择来源"
}

private fun formatDay(epochMillis: Long): String =
    SimpleDateFormat("M/d", Locale.getDefault()).format(Date(epochMillis))

private fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f)
    value >= 1_000L -> String.format(Locale.getDefault(), "%.1fK", value / 1_000f)
    else -> value.toString()
}

private val WeeklyDashboardBackground = Color(0xFFF3F6FA)
private val WeeklyDashboardInk = Color(0xFF202B3B)
private val WeeklyDashboardMuted = Color(0xFF718097)
private const val DefaultScopeEditorId = "weekly-default-scope"
