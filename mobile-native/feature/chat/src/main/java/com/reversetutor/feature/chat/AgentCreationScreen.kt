package com.reversetutor.feature.chat

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalElevations
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import kotlinx.coroutines.launch

/**
 * C1 · Agent 对话式创建屏（设计方案 v3 · 第二章）。
 *
 * 顶部极简行（返回 + 右上「创建并进入聊天」）+「了解程度」进度条
 * + 对话流（助手/用户/文件卡/草案卡）+ 底部消息输入框（附件按钮 + 发送）。
 * R-A 由 FakeAgentCreationGateway 驱动；R-B 起换生产网关。
 */
@Composable
fun AgentCreationRoute(
    createPort: NewSessionCreatePort,
    persistence: NewSessionPersistence,
    onCreated: (SessionListItem) -> Unit,
    onBack: () -> Unit = {},
    openPickerOnStart: Boolean = false,
    onOpenPickerConsumed: () -> Unit = {},
    gateway: AgentCreationGateway? = null,
    stateStore: AgentCreationStateStore? = null,
    /** R100 方案B 状态机灰度（App 端经 BuildConfig 下发）。 */
    graphEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeGateway = remember(gateway) { gateway ?: FakeAgentCreationGateway() }
    // R85：注入 stateStore 后创建进度跨页面 / 跨进程持久化，回来自动续聊。
    val coordinator = remember(activeGateway, stateStore, graphEnabled) {
        AgentCreationCoordinator(activeGateway, stateStore = stateStore, graphEnabled = graphEnabled)
    }
    val lifecycle = remember(createPort, persistence) {
        NewSessionLifecycleCoordinator(persistence = persistence, createPort = createPort)
    }
    var state by remember { mutableStateOf(coordinator.state) }
    var input by remember { mutableStateOf("") }
    var pendingLowUnderstandingCreate by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val feedState = rememberLazyListState()
    val type = LocalFormalTypeScale.current

    fun sync() {
        state = coordinator.state
    }

    LaunchedEffect(coordinator) {
        // R91：协调器每次状态变更即时同步 UI——用户消息发出后气泡立刻上屏，
        // 不等整轮生成结束才和回复一起出现（2026-09-26 真机反馈）。
        coordinator.onStateChanged = { sync() }
        coordinator.start()
        sync()
    }

    fun attachPicked(uri: android.net.Uri?) {
        if (uri == null) return
        var name = "文档"
        var size = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    val bytes = cursor.getLong(sizeIndex)
                    size = if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024f / 1024f)
                    else "${bytes / 1024.coerceAtLeast(1)} KB"
                }
            }
        }
        scope.launch {
            coordinator.attachDocument(name, size.ifBlank { "大小未知" })
            sync()
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> attachPicked(uri) }

    fun launchPicker() {
        runCatching { documentPicker.launch(arrayOf("*/*")) }
    }

    LaunchedEffect(openPickerOnStart) {
        if (openPickerOnStart) {
            onOpenPickerConsumed()
            launchPicker()
        }
    }

    fun performCreate() {
        if (creating) return
        creating = true
        createError = null
        scope.launch {
            if (lifecycle.state.currentDraft == null && !lifecycle.startBlankDraft()) {
                creating = false
                createError = "草稿箱已满，无法保存这份会话。"
                return@launch
            }
            lifecycle.updateConfiguration { coordinator.configuration() }
            lifecycle.saveBoundary()
            val outcome = lifecycle.createSession()
            if (outcome is CreateSessionOutcome.Success) {
                coordinator.markCreated()
                sync()
                creating = false
                onCreated(outcome.created.session)
            } else {
                creating = false
                createError = when (outcome) {
                    is CreateSessionOutcome.Invalid -> outcome.errors.joinToString("；")
                    is CreateSessionOutcome.Duplicate -> "正在创建中，请稍候。"
                    else -> "暂时无法创建会话，所有编辑均已保留。请重试。"
                }
            }
        }
    }

    fun handleCreateClick() {
        if (!state.canCreate) return
        if (!state.understandingHigh) {
            pendingLowUnderstandingCreate = true
            return
        }
        performCreate()
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || state.busy) return
        input = ""
        scope.launch {
            coordinator.sendUserText(text)
            sync()
        }
    }

    BackHandler(onBack = onBack)

    // R92：检测到上次未完成的创建——先问「继续上次还是创建新会话」，
    // 不静默恢复（2026-09-26 用户拍板：直接默认进上次的，想新建太费劲）。
    if (state.resumeAvailable) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("继续上次的创建？") },
            text = { Text("检测到上次有一份没创建完的会话草案。可以接着上次的聊，也可以从零创建新会话。") },
            confirmButton = {
                TextButton(onClick = {
                    coordinator.resumePending()
                    sync()
                }) { Text("继续上次") }
            },
            dismissButton = {
                TextButton(onClick = {
                    coordinator.startFresh()
                    sync()
                }) { Text("创建新会话") }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag("agent_creation_screen")
    ) {
        Column(
            modifier = Modifier
                .width(maxWidth.coerceAtMost(390.dp))
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            CreationTopRow(
                onBack = onBack,
                canCreate = state.canCreate,
                creating = creating,
                onCreate = ::handleCreateClick
            )
            UnderstandingBar(understanding = state.displayedUnderstanding)
            // R99 滚动修复（2026-10-04 用户真机反馈「这两个你都没做」后的拍板规格）：
            // ① 停在最底 → 新条目与流式生长都自动跟随并钉到最底（满偏移 scrollToItem）；
            // ② 向上翻看历史 → 原地不动，浮出「新消息」胶囊，点了才滑到最底；
            // ③ 自己发出的消息一律回到底部；
            // ④ 弹起键盘前停在最新一条 → 键盘起来后仍钉住最新一条。
            // 跟随态 followBottom 只在滚动进行中/刚结束时采样——键盘压缩视口、
            // 追加条目这类纯重排不会改写它，从根上消除 R98 的「在底部」误判。
            var followBottom by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                var wasScrolling = false
                snapshotFlow {
                    val layout = feedState.layoutInfo
                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val near = layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1
                    near to feedState.isScrollInProgress
                }.collect { (near, scrolling) ->
                    if (scrolling || wasScrolling) followBottom = near
                    wasScrolling = scrolling
                }
            }
            var showNewMessagePill by remember { mutableStateOf(false) }
            var prevFeedCount by remember { mutableStateOf(0) }
            val lastAssistantLength = (state.feed.lastOrNull() as? AgentCreationFeedEntry.Assistant)
                ?.text?.length ?: 0
            LaunchedEffect(state.feed.size, lastAssistantLength) {
                if (state.feed.isEmpty()) {
                    prevFeedCount = 0
                    return@LaunchedEffect
                }
                val appended = state.feed.size != prevFeedCount
                prevFeedCount = state.feed.size
                val mine = state.feed.lastOrNull() is AgentCreationFeedEntry.User
                if (mine || followBottom) {
                    if (appended) {
                        feedState.animateScrollToItem(state.feed.size - 1, Int.MAX_VALUE)
                    } else {
                        // 流式生长：条数不变也要钉底，长气泡最新文字始终可见
                        feedState.scrollToItem(state.feed.size - 1, Int.MAX_VALUE)
                    }
                    showNewMessagePill = false
                } else if (appended) {
                    showNewMessagePill = true
                }
            }
            LaunchedEffect(followBottom) {
                if (followBottom) showNewMessagePill = false
            }
            val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
            LaunchedEffect(imeOpen) {
                if (imeOpen && followBottom && state.feed.isNotEmpty()) {
                    // adjustResize 压缩视口伴随键盘动画，重试三次顶住重排
                    repeat(3) {
                        feedState.scrollToItem(state.feed.size - 1, Int.MAX_VALUE)
                        kotlinx.coroutines.delay(120)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = feedState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("agent_creation_feed"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.feed, key = { it.id }) { entry ->
                        when (entry) {
                            is AgentCreationFeedEntry.Assistant -> AssistantBubble(entry)
                            is AgentCreationFeedEntry.User -> UserBubble(entry.text)
                            is AgentCreationFeedEntry.FileCard -> FileCard(
                                card = entry,
                                onRetry = {
                                    scope.launch {
                                        coordinator.retryDocumentAnalysis(entry.id)
                                        sync()
                                    }
                                }
                            )
                            is AgentCreationFeedEntry.DraftCard -> DraftSummaryCard(entry.configuration)
                            is AgentCreationFeedEntry.IncubationDraftCard -> IncubationDraftCardView(
                                card = entry,
                                onConfirm = {
                                    scope.launch {
                                        coordinator.confirmIncubation()
                                        sync()
                                    }
                                }
                            )
                            is AgentCreationFeedEntry.LearningFlowCard -> LearningFlowCardView(entry.flow)
                        }
                    }
                }
                if (showNewMessagePill) {
                    Surface(
                        onClick = {
                            showNewMessagePill = false
                            scope.launch {
                                if (state.feed.isNotEmpty()) {
                                    feedState.animateScrollToItem(state.feed.size - 1, Int.MAX_VALUE)
                                    followBottom = true
                                }
                            }
                        },
                        color = FormalColors.Primary,
                        shape = RoundedCornerShape(FormalShapes.PillRadius),
                        shadowElevation = FormalElevations.Panel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .testTag("agent_creation_new_messages")
                    ) {
                        Text(
                            text = "新消息 ▾",
                            style = type.style(11f, 15f, FontWeight.Bold, androidx.compose.ui.graphics.Color.White),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            BottomComposer(
                input = input,
                busy = state.busy,
                requestDocumentActive = state.requestDocumentActive,
                onInputChange = { input = it },
                onSend = ::send,
                onAttach = ::launchPicker,
                createError = createError,
                onInputFocused = {
                    // R101（2026-10-05 用户拍板）：聚焦输入框即钉底最新消息，
                    // 不依赖 IME inset 送达。
                    if (state.feed.isNotEmpty()) {
                        scope.launch {
                            repeat(3) {
                                feedState.scrollToItem(state.feed.size - 1, Int.MAX_VALUE)
                                kotlinx.coroutines.delay(120)
                            }
                        }
                    }
                }
            )
        }
    }

    if (pendingLowUnderstandingCreate) {
        AlertDialog(
            onDismissRequest = { pendingLowUnderstandingCreate = false },
            title = { Text("了解程度还不高") },
            text = { Text("可以直接创建碰碰运气，也可以再聊几句让草案更准。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLowUnderstandingCreate = false
                    performCreate()
                }) { Text("仍然创建") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLowUnderstandingCreate = false }) { Text("再聊几句") }
            }
        )
    }
}

@Composable
private fun CreationTopRow(
    onBack: () -> Unit,
    canCreate: Boolean,
    creating: Boolean,
    onCreate: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("agent_creation_back"),
            color = FormalColors.Surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FormalColors.Ink
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "新建会话",
            style = type.style(15f, 20f, FontWeight.Bold, FormalColors.Ink)
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onCreate,
            enabled = canCreate && !creating,
            modifier = Modifier
                .height(40.dp)
                .testTag("agent_creation_create"),
            color = if (canCreate) FormalColors.Primary else FormalColors.SurfaceSubtle,
            shape = RoundedCornerShape(FormalShapes.PillRadius),
            border = if (canCreate) null else BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (creating) "创建中…" else "创建并进入聊天",
                    style = type.style(
                        12f, 16f, FontWeight.Bold,
                        if (canCreate) androidx.compose.ui.graphics.Color.White else FormalColors.Muted
                    )
                )
            }
        }
    }
}

@Composable
private fun UnderstandingBar(understanding: Int) {
    val type = LocalFormalTypeScale.current
    val animated by animateFloatAsState(
        targetValue = understanding / 100f,
        animationSpec = tween(durationMillis = 420),
        label = "understanding"
    )
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "了解程度",
                    style = type.style(13f, 18f, FontWeight.SemiBold, FormalColors.Ink)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$understanding",
                    style = type.style(15f, 20f, FontWeight.Bold, FormalColors.Primary)
                )
                Spacer(Modifier.weight(1f))
                if (understanding >= 85) {
                    Surface(
                        color = FormalColors.SuccessSoft,
                        shape = RoundedCornerShape(FormalShapes.PillRadius)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = FormalColors.Success,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "推荐创建",
                                style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Success)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .testTag("agent_creation_understanding"),
                color = if (understanding >= 85) FormalColors.Success else FormalColors.Primary,
                trackColor = FormalColors.Divider
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "进度越高，对设定的任务执行的越准确；低置信度下也有出乎意料的效果哦",
                style = type.style(10f, 14f, color = FormalColors.Muted)
            )
        }
    }
}

@Composable
private fun AssistantBubble(entry: AgentCreationFeedEntry.Assistant) {
    // R98：阶段化思考块挂在正文气泡上方——进行期占位正文为空，
    // 只显示阶段块；正文到达后阶段块收起为汇总行、正文气泡接管。
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StageBlock(entry)
        if (entry.text.isNotEmpty()) {
            Surface(
                color = FormalColors.Surface,
                shape = RoundedCornerShape(
                    topStart = 4.dp, topEnd = FormalShapes.CardRadius,
                    bottomStart = FormalShapes.CardRadius, bottomEnd = FormalShapes.CardRadius
                ),
                border = BorderStroke(1.dp, FormalColors.Border),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("agent_creation_assistant")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    AssistantRichText(entry.text)
                }
            }
        }
    }
}

/**
 * R92：助手气泡富文本渲染——AI 输出的条列 / 标题 / 加粗按结构展示，
 * 不再整段糊成一坨（2026-09-26 用户真机反馈：「高中数学学哪些」的
 * 一二三条列输出应渲染成条列）。复用主聊天的 ChatRichContentParser，
 * 代码 / 公式 / 表格在创建页降级为等宽 / 纯文本，不搬整套主聊渲染。
 */
@Composable
private fun AssistantRichText(text: String) {
    val type = LocalFormalTypeScale.current
    val blocks = remember(text) { ChatRichContentParser.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChatRichBlock.Heading -> Text(
                    text = block.text,
                    style = type.style(14f, 20f, weight = FontWeight.Bold, color = FormalColors.Ink)
                )
                is ChatRichBlock.Paragraph -> Text(
                    text = buildRichInlineAnnotatedString(block.inlines),
                    style = type.style(13f, 20f, color = FormalColors.Ink)
                )
                is ChatRichBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (block.ordered) "${block.index ?: 1}." else "•",
                        style = type.style(13f, 20f, color = FormalColors.Muted)
                    )
                    Text(
                        text = buildRichInlineAnnotatedString(ChatRichContentParser.parseInlines(block.text)),
                        style = type.style(13f, 20f, color = FormalColors.Ink),
                        modifier = Modifier.weight(1f)
                    )
                }
                is ChatRichBlock.Quote -> Text(
                    text = block.text,
                    style = type.style(13f, 20f, color = FormalColors.Muted)
                )
                is ChatRichBlock.Code -> Text(
                    text = block.source,
                    style = type.style(12f, 18f, color = FormalColors.Muted)
                )
                is ChatRichBlock.Formula -> Text(
                    text = block.source,
                    style = type.style(13f, 20f, color = FormalColors.Ink)
                )
                is ChatRichBlock.Table -> Text(
                    text = (listOf(block.headers) + block.rows).joinToString("\n") { row -> row.joinToString(" · ") },
                    style = type.style(12f, 18f, color = FormalColors.Muted)
                )
                is ChatRichBlock.PlainText -> Text(
                    text = block.source,
                    style = type.style(13f, 20f, color = FormalColors.Ink)
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val type = LocalFormalTypeScale.current
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = FormalColors.PrimarySoft,
            shape = RoundedCornerShape(
                topStart = FormalShapes.CardRadius, topEnd = 4.dp,
                bottomStart = FormalShapes.CardRadius, bottomEnd = FormalShapes.CardRadius
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .testTag("agent_creation_user")
        ) {
            Text(
                text = text,
                style = type.style(13f, 20f, color = FormalColors.Ink),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun FileCard(
    card: AgentCreationFeedEntry.FileCard,
    onRetry: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    val statusColor = when (card.status) {
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzing -> FormalColors.Warning
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzed -> FormalColors.Success
        AgentCreationFeedEntry.FileCard.FileStatus.Failed -> FormalColors.Danger
    }
    val statusContainer = when (card.status) {
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzing -> FormalColors.WarningSoft
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzed -> FormalColors.SuccessSoft
        AgentCreationFeedEntry.FileCard.FileStatus.Failed -> FormalColors.SurfaceSubtle
    }
    Surface(
        onClick = if (card.status == AgentCreationFeedEntry.FileCard.FileStatus.Failed) onRetry else ({ }),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .widthIn(max = 330.dp)
            .testTag("agent_creation_file_card")
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = FormalColors.PrimarySoft,
                shape = RoundedCornerShape(FormalShapes.CompactRadius),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = FormalColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.fileName,
                    style = type.style(13f, 18f, FontWeight.SemiBold, FormalColors.Ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${card.sizeLabel} · ${card.status.label}",
                    style = type.style(10f, 14f, color = statusColor)
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(color = statusContainer, shape = RoundedCornerShape(FormalShapes.PillRadius)) {
                Text(
                    text = card.status.label,
                    style = type.style(9f, 13f, FontWeight.Medium, statusColor),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DraftSummaryCard(configuration: NewSessionConfiguration) {
    // R84：档案卡视觉——色带头 + 白身 + 字段表，与聊天气泡/输入框拉开辨识度。
    // 2026-09-26 用户拍板：草案卡主色由 Primary 蓝改为橙色（Warning/WarningSoft）。
    val type = LocalFormalTypeScale.current
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Warning.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_creation_draft_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        FormalColors.WarningSoft,
                        RoundedCornerShape(
                            topStart = FormalShapes.CardRadius,
                            topEnd = FormalShapes.CardRadius
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = FormalColors.Warning,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "会话草案 · 实时更新",
                    style = type.style(12f, 17f, FontWeight.SemiBold, FormalColors.Warning)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.PillRadius)
                ) {
                    Text(
                        text = "完成度 ${configuration.completionPercent}%",
                        style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Warning),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            DraftRow("会话名称", configuration.title.ifBlank { "待补充" })
            DraftRow("AI 学生", configuration.learnerDisplayName)
            DraftRow("学习者角色", configuration.learnerRole.ifBlank { "待补充" })
            DraftRow("人物性格", configuration.persona.ifBlank { "待补充" })
            DraftRow("目标", configuration.goal.ifBlank { "待补充" })
            if (configuration.plan.isNotBlank()) DraftRow("计划", configuration.plan)
            if (configuration.stageMilestones != "未设置") DraftRow("阶段里程碑", configuration.stageMilestones)
            if (configuration.openingMessage.isNotBlank() &&
                configuration.openingMessage != "准备好后，请开始讲给我听吧。"
            ) DraftRow("开场消息", configuration.openingMessage)
            }
        }
    }
}

@Composable
private fun DraftRow(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = type.style(10f, 15f, color = FormalColors.Muted),
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = type.style(11f, 16f, color = FormalColors.Ink),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * R100 孵化草案卡（方案B）：待确认时给「确认草案」按钮；
 * 批注打回走普通消息回复（协调器把待确认期间的非确认文字当批注）。
 */
@Composable
private fun IncubationDraftCardView(
    card: AgentCreationFeedEntry.IncubationDraftCard,
    onConfirm: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    val status = card.status
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(
            1.dp,
            when (status) {
                AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm ->
                    FormalColors.Primary.copy(alpha = 0.45f)
                AgentCreationFeedEntry.IncubationDraftCard.Status.Confirmed ->
                    FormalColors.Primary.copy(alpha = 0.25f)
                AgentCreationFeedEntry.IncubationDraftCard.Status.Superseded ->
                    FormalColors.Border
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_creation_incubation_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        FormalColors.Primary.copy(alpha = 0.10f),
                        RoundedCornerShape(
                            topStart = FormalShapes.CardRadius,
                            topEnd = FormalShapes.CardRadius
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = FormalColors.Primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (status) {
                        AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm -> "养成草案 · 待你确认"
                        AgentCreationFeedEntry.IncubationDraftCard.Status.Confirmed -> "养成草案 · 已确认"
                        AgentCreationFeedEntry.IncubationDraftCard.Status.Superseded -> "养成草案 · 已按批注作废"
                    },
                    style = type.style(12f, 17f, FontWeight.SemiBold, FormalColors.Primary)
                )
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                DraftRow("人物性格", card.incubation.personaHypothesis)
                DraftRow("教学方式", card.incubation.teachingStyle)
                card.incubation.stageGoals.forEachIndexed { index, goal ->
                    DraftRow(if (index == 0) "阶段目标" else "", (index + 1).toString() + ". " + goal)
                }
                card.incubation.milestones.forEachIndexed { index, milestone ->
                    DraftRow(if (index == 0) "里程碑" else "", (index + 1).toString() + ". " + milestone)
                }
                if (status == AgentCreationFeedEntry.IncubationDraftCard.Status.PendingConfirm) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        onClick = onConfirm,
                        color = FormalColors.Primary,
                        shape = RoundedCornerShape(FormalShapes.PillRadius),
                        modifier = Modifier.testTag("agent_creation_incubation_confirm")
                    ) {
                        Text(
                            text = "确认草案",
                            style = type.style(12f, 17f, FontWeight.Bold, androidx.compose.ui.graphics.Color.White),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "有要改的？直接回复，我按你的批注改",
                        style = type.style(10f, 15f, color = FormalColors.Muted)
                    )
                }
            }
        }
    }
}

/** R100 学习流程图卡（方案B）：主题按序排列 + 依赖关系提示。 */
@Composable
private fun LearningFlowCardView(flow: AgentCreationLearningFlow) {
    val type = LocalFormalTypeScale.current
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_creation_learning_flow_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        FormalColors.Primary.copy(alpha = 0.10f),
                        RoundedCornerShape(
                            topStart = FormalShapes.CardRadius,
                            topEnd = FormalShapes.CardRadius
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = FormalColors.Primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "学习流程图 · 先基础后提升",
                    style = type.style(12f, 17f, FontWeight.SemiBold, FormalColors.Primary)
                )
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                flow.topics.forEachIndexed { index, topic ->
                    val deps = flow.edges.filter { it.toTitle == topic.title }.map { it.fromTitle }
                    DraftRow(
                        "阶段 " + (index + 1).toString(),
                        topic.title + if (topic.subSkills.isNotEmpty()) {
                            "（" + topic.subSkills.joinToString("、") + "）"
                        } else {
                            ""
                        }
                    )
                    if (deps.isNotEmpty()) {
                        DraftRow("", "需先完成：" + deps.joinToString("、"))
                    }
                }
            }
        }
    }
}

/**
 * R98 阶段化思考块（2026-09-28 用户拍板）：展示「产品在做什么」的阶段流——
 * 理解输入 → 连接模型 → 深度思考 → 生成回复 →（自检修正），各段带耗时、
 * 活动段高亮 + 三点呼吸；模型原始推理完整收进「查看完整思考过程」二级折叠
 * （衬线字体，与口语气泡区分）。OFF 轮（无思考）落定后整块消失，不残留。
 */
@Composable
private fun StageBlock(entry: AgentCreationFeedEntry.Assistant) {
    val streaming = entry.stages.any { it.active }
    if (entry.stages.isEmpty() && entry.reasoning == null) return
    if (!streaming && entry.reasoning == null) return
    val type = LocalFormalTypeScale.current
    var expanded by remember(entry.id) { mutableStateOf(streaming) }
    var reasoningExpanded by remember(entry.id) { mutableStateOf(false) }
    // 进行期默认展开；轮次落定自动收起一次（DeepSeek 行为）。
    LaunchedEffect(streaming) {
        if (!streaming) expanded = false
    }
    val doneLabel = entry.stages.lastOrNull { it.key == "done" }?.label
        ?: ("已思考 · " + entry.reasoningElapsedSeconds + "s")
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .testTag("agent_creation_reasoning")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (streaming) {
                    StageStreamingHeader(entry)
                } else {
                    Text(
                        text = (if (expanded) "▾ " else "▸ ") + doneLabel,
                        style = type.style(11f, 16f, color = FormalColors.Muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                entry.stages.forEach { stage ->
                    StageRow(stage)
                }
                if (entry.reasoning != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = (if (reasoningExpanded) "▾ " else "▸ ") + "查看完整思考过程",
                        style = type.style(10f, 14f, color = FormalColors.Muted),
                        modifier = Modifier.clickable { reasoningExpanded = !reasoningExpanded }
                    )
                    if (reasoningExpanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.reasoning,
                            style = type.style(11f, 17f, color = FormalColors.Muted)
                                .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

/** 单行阶段：活动段主色高亮 + 三点呼吸；完成段灰字打勾带耗时（亚秒段不显示 0s）。 */
@Composable
private fun StageRow(stage: AgentCreationStageEvent) {
    val type = LocalFormalTypeScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        if (stage.active) {
            Text(
                text = stage.label,
                style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            WorkingDots()
        } else {
            val prefix = if (stage.key == "done") "" else "✓ "
            val suffix = if (stage.key != "done" && stage.elapsedSeconds > 0) " · " + stage.elapsedSeconds + "s" else ""
            Text(
                text = prefix + stage.label + suffix,
                style = type.style(11f, 16f, color = FormalColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 进行期头部：当前阶段名 + 本地每秒跳动的全程计时 + 三点呼吸。 */
@Composable
private fun StageStreamingHeader(entry: AgentCreationFeedEntry.Assistant) {
    val type = LocalFormalTypeScale.current
    val activeLabel = entry.stages.lastOrNull { it.active }?.label ?: "正在处理…"
    val startEpoch = remember(entry.id) {
        System.currentTimeMillis() / 1000L - entry.reasoningElapsedSeconds
    }
    var nowEpoch by remember(entry.id) { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(entry.id) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowEpoch = System.currentTimeMillis() / 1000L
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = activeLabel + " · " + (nowEpoch - startEpoch) + "s",
            style = type.style(11f, 16f, color = FormalColors.Muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        WorkingDots()
    }
}

/** R84 遗产：三点呼吸动画，从原输入框上方状态条迁到思考块头部。 */
@Composable
private fun WorkingDots() {
    val dotsTransition = rememberInfiniteTransition(label = "working-dots")
    val dotAlphas = List(3) { index ->
        dotsTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 200)
            ),
            label = "working-dot-$index"
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        dotAlphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(FormalColors.Primary.copy(alpha = alpha.value), CircleShape)
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun BottomComposer(
    input: String,
    busy: Boolean,
    requestDocumentActive: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    createError: String?,
    onInputFocused: () -> Unit = {}
) {
    val type = LocalFormalTypeScale.current
    val attachHighlight by animateDpAsState(
        targetValue = if (requestDocumentActive) 2.dp else 1.dp,
        animationSpec = tween(240),
        label = "attach-border"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FormalColors.Surface)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FormalColors.Divider)
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (createError != null) {
                Text(
                    text = createError,
                    style = type.style(10f, 15f, color = FormalColors.Danger),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onAttach,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("agent_creation_attach"),
                    color = if (requestDocumentActive) FormalColors.WarningSoft else FormalColors.SurfaceSubtle,
                    shape = CircleShape,
                    border = BorderStroke(attachHighlight, if (requestDocumentActive) FormalColors.Warning else FormalColors.Border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "发送文件",
                            tint = if (requestDocumentActive) FormalColors.Warning else FormalColors.Muted
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) onInputFocused() }
                        .testTag("agent_creation_input"),
                    placeholder = { Text("说说你想学什么…", style = type.style(13f, 18f, color = FormalColors.Tertiary)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(FormalShapes.CardRadius)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.size(44.dp),
                    color = FormalColors.Primary,
                    shape = CircleShape,
                    shadowElevation = FormalElevations.Panel
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.testTag("agent_creation_send")) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank() && !busy) androidx.compose.ui.graphics.Color.White else FormalColors.Border
                        )
                    }
                }
            }
        }
    }
}

