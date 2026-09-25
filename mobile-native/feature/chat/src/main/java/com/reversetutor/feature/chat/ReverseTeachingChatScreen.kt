package com.reversetutor.feature.chat

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reversetutor.core.domain.ChapterTransitionPolicy
import com.reversetutor.core.domain.ChapterTransitionProposal
import com.reversetutor.core.domain.PathMove
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged

internal object ChatComposerLayout {
    val Height = 60.dp
    val SendSize = 52.dp
    val Gap = 4.dp
}

enum class ChatOverflowAction(val label: String) {
    GlobalSettings("全局设置"),
    KnowledgeAnchors("知识锚点"),
    WindowBranches("管理分支"),
    SessionSettings("窗口设置"),
    Export("导出会话")
}

@Composable
internal fun ReverseTeachingChatScreen(
    state: ChatUiState,
    sessionContract: SessionConversationContract? = null,
    onAssistantInteraction: (SessionAssistantInteraction) -> Unit = {},
    onComposerTextChange: (String) -> Unit,
    voiceInputState: VoiceInputState = VoiceInputState(),
    onVoiceInputClick: () -> Unit = {},
    onVoicePressStart: () -> Unit = {},
    onVoicePressStop: () -> Unit = {},
    onSendMessage: () -> Unit,
    onCancelQuote: () -> Unit,
    onCreateImageDraft: () -> Unit,
    onCancelImageDraft: () -> Unit,
    onMessageAction: (ChatTimelineItem, ChatMessageAction) -> Unit,
    memoryDraft: ChatMemoryDraft? = null,
    memoryError: String? = null,
    onMemoryDraftChange: (ChatMemoryDraft) -> Unit = {},
    onDismissMemory: () -> Unit = {},
    onConfirmMemory: () -> Unit = {},
    deleteConfirmation: ChatDeleteConfirmation? = null,
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onRetryDelete: () -> Unit = {},
    onCopyRichSource: (String) -> ChatClipboardResult = { ChatClipboardResult.Unavailable },
    onSaveImage: (ChatAttachmentUi) -> Unit = {},
    onShareImage: (ChatAttachmentUi) -> Unit = {},
    onOpenSessionSource: (String?) -> Unit = {},
    onReselectInvalidSource: (String, String) -> Unit = { _, _ -> },
    onOpenExternalLink: (String) -> Unit = {},
    onComposerFocusChanged: (Boolean) -> Unit,
    webSearchEnabled: Boolean = false,
    onWebSearchChange: (Boolean) -> Unit = {},
    onOpenContextHub: () -> Unit,
    onOpenWindowBranches: () -> Unit = {},
    onOpenGlobalGraph: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    llmProfiles: List<LlmProfile> = emptyList(),
    onActivateLlmProfile: (String) -> Unit = {},
    onOpenSources: () -> Unit = {},
    onExport: () -> Unit = {},
    onBack: () -> Unit,
    evidenceTargetMessageId: String?,
    availableSourceAttachments: List<ChatDraftAttachment> = emptyList(),
    cameraPermissionState: ChatPermissionState = ChatPermissionState.Requestable,
    onOpenSearch: () -> Unit = {},
    onPickImages: () -> Unit = onCreateImageDraft,
    onGalleryImagePicked: (Uri) -> Unit = {},
    onPickLocalSource: () -> Unit = {},
    onSelectSource: (ChatDraftAttachment) -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRequestCameraPermission: () -> Unit = {},
    onOpenCameraSettings: () -> Unit = {},
    onOpenSessionSources: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onMoveAttachment: (Int, Int) -> Unit = { _, _ -> },
    onRetrySend: () -> Unit = {},
    onOpenSessionSettings: () -> Unit = {},
    onRetryGeneration: (() -> Unit)? = null,
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    onScrollPositionChanged: (ChatScrollPosition) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMessageId by remember(state.sessionTitle) { mutableStateOf<String?>(null) }
    // 2026-09-21 思考链展开状态会话内共享：用户展开一次，后续消息（含流式中
    // 的抽屉）都保持展开；收起同理。切会话回落默认折叠。
    var monologueExpanded by remember(state.sessionTitle) { mutableStateOf(false) }
    var actionMessage by remember(state.sessionTitle) { mutableStateOf<ChatTimelineItem?>(null) }
    var locateSourceMessage by remember(state.sessionTitle) { mutableStateOf<ChatTimelineItem?>(null) }
    var viewerAttachment by remember(state.sessionTitle) { mutableStateOf<ChatAttachmentUi?>(null) }
    var showAttachmentActions by remember(state.sessionTitle) { mutableStateOf(false) }
    // 相册缩略图勾选状态（2026-09-24）：勾选即把图片作为草稿附件加入输入区，
    // 发送按钮随 canSend 自动亮起；取消勾选按 uri 找回附件并移除。
    var selectedGalleryUris by remember(state.sessionTitle) { mutableStateOf(setOf<String>()) }
    var showWebSearchConfirm by remember(state.sessionTitle) { mutableStateOf(false) }
    var showSourcePicker by remember(state.sessionTitle) { mutableStateOf(false) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.index,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.offset
    )

    LaunchedEffect(listState) {
        // 2026-09-21 修复：消息异步加载期间列表为空，(0,0) 发射会覆盖掉
        // ChatScrollMemory 记住的位置（恢复自我破坏）——空列表时不向外发射。
        snapshotFlow {
            listState.layoutInfo.totalItemsCount to ChatScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
            .distinctUntilChanged()
            .collect { (count, position) ->
                if (count > 0) onScrollPositionChanged(position)
            }
    }

    LaunchedEffect(evidenceTargetMessageId, state.messages) {
        val targetIndex = state.messages.indexOfFirst { it.id == evidenceTargetMessageId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex + 1)
            selectedMessageId = evidenceTargetMessageId
            kotlinx.coroutines.delay(1_800L)
            if (selectedMessageId == evidenceTargetMessageId) selectedMessageId = null
        }
    }

    LaunchedEffect(selectedMessageId) {
        val selected = selectedMessageId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(3_000L)
        if (selectedMessageId == selected) selectedMessageId = null
    }

    // 自动滚动：用户发送消息后强制滚动到最新内容；新内容（回复到达 / 流式文本增长）
    // 仅在视口本来就贴近底部时跟随，不打断向上翻阅历史。
    var followNextAppend by remember(state.sessionTitle) { mutableStateOf(false) }
    val streamingLength = (state.generation as? ChatGenerationUiState.Streaming)?.text?.length ?: 0
    val timelineItemCount = run {
        val messageItems = if (state.messages.isEmpty()) 1 else buildChatTimelineEntries(state.messages).size
        messageItems + (if (sessionContract != null) 1 else 0)
    }
    var previousTimelineItemCount by remember(state.sessionTitle) { mutableStateOf(timelineItemCount) }
    var scrollRestoreGuard by remember(state.sessionTitle) { mutableStateOf(true) }
    // 2026-09-21 修复：没有记住的位置时（默认 (0,0)），进会话应落在底部
    // 最新一条，而不是停在列表顶部；消息可能异步到达，等内容出现后再落底。
    var pendingInitialBottom by remember(state.sessionTitle) {
        mutableStateOf(initialScrollPosition.index == 0 && initialScrollPosition.offset == 0)
    }
    LaunchedEffect(timelineItemCount, streamingLength, state.generationStatusLabel != null) {
        val countChanged = timelineItemCount != previousTimelineItemCount
        previousTimelineItemCount = timelineItemCount
        if (scrollRestoreGuard) {
            // 首次组合跳过，保留 initialScrollPosition 的位置恢复语义
            scrollRestoreGuard = false
            if (pendingInitialBottom && timelineItemCount > 1) {
                pendingInitialBottom = false
                listState.scrollToItem(timelineItemCount - 1, Int.MAX_VALUE)
            }
            return@LaunchedEffect
        }
        if (pendingInitialBottom) {
            if (timelineItemCount > 1) {
                pendingInitialBottom = false
                listState.scrollToItem(timelineItemCount - 1, Int.MAX_VALUE)
            }
            return@LaunchedEffect
        }
        if (timelineItemCount <= 0) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val nearEnd = layoutInfo.totalItemsCount == 0 || lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        if (!followNextAppend && !nearEnd) return@LaunchedEffect
        followNextAppend = false
        if (countChanged) {
            listState.animateScrollToItem(timelineItemCount - 1, Int.MAX_VALUE)
        } else {
            listState.scrollToItem(timelineItemCount - 1, Int.MAX_VALUE)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChatPageBackground)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ReverseTeachingChatHeader(
            state = state,
            onBack = onBack,
            onOpenSearch = onOpenSearch,
            onOpenSources = onOpenSources,
            onOpenGlobalGraph = onOpenGlobalGraph,
            onOpenSessionSettings = onOpenSessionSettings,
            onOverflowAction = { action ->
                when (action) {
                    ChatOverflowAction.GlobalSettings -> onOpenModelSettings()
                    ChatOverflowAction.KnowledgeAnchors -> onOpenSessionSources()
                    ChatOverflowAction.WindowBranches -> onOpenWindowBranches()
                    ChatOverflowAction.SessionSettings -> onOpenSessionSettings()
                    ChatOverflowAction.Export -> onExport()
                }
            }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    LearnerOpening(
                        learnerName = state.learnerName,
                        learnerStatus = state.learnerStatus,
                        learnerAvatarReference = state.learnerAvatarReference,
                        avatarVisible = state.avatarVisible
                    )
                }
            } else {
                val entries = buildChatTimelineEntries(state.messages)
                items(entries, key = { entry ->
                    when (entry) {
                        is ChatTimelineEntry.DateSeparator -> "date-${entry.label}"
                        is ChatTimelineEntry.Message -> entry.item.id
                    }
                }) { entry ->
                    if (entry is ChatTimelineEntry.DateSeparator) {
                        Text(
                            text = entry.label,
                            modifier = Modifier.fillMaxWidth(),
                            color = ChatMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        return@items
                    }
                    val item = (entry as ChatTimelineEntry.Message).item
                    // 2026-09-24 排版改版：气泡化后行距收紧，避免消息松散漂浮
                    val timelineSpacing = when (item.role) {
                        MessageRole.Assistant -> Modifier.padding(bottom = 8.dp)
                        MessageRole.User -> Modifier
                        MessageRole.System,
                        MessageRole.Tool -> Modifier
                    }
                    Box(modifier = timelineSpacing) {
                        ReverseTeachingMessage(
                            item = item,
                            learnerName = state.learnerName,
                            learnerAvatarReference = state.learnerAvatarReference,
                            avatarVisible = state.avatarVisible,
                            sources = state.sources,
                            selected = selectedMessageId == item.id,
                            onTap = {
                                selectedMessageId = if (selectedMessageId == item.id) null else item.id
                            },
                            onLongPress = { actionMessage = item },
                            onOpenImage = { viewerAttachment = it },
                            onOpenSource = onOpenSessionSource,
                            onReselectInvalidSource = onReselectInvalidSource,
                            onOpenExternalLink = onOpenExternalLink,
                            onCopyRichSource = onCopyRichSource,
                            monologueExpanded = monologueExpanded,
                            onMonologueExpandedChange = { monologueExpanded = it }
                        )
                    }
                }
            }
            sessionContract?.let { contract ->
                item {
                    SessionAssistantReplyHint(
                        contract = contract,
                        onInteraction = onAssistantInteraction
                    )
                }
            }
        }
        // 生成状态行固定在输入框上方（2026-09-20 拍板）：此前是 LazyColumn 尾部
        // item；列表只组合可视区 item，指示器一旦滚出视口就不渲染，表现为间歇性
        // 消失。挪出列表后始终可见；流式气泡限高内滚、随文本增长自动贴尾。
        state.generationStatusLabel?.let { label ->
            val streaming = state.generation as? ChatGenerationUiState.Streaming
            val partial = streaming?.text
            val streamingMonologue = streaming?.monologue
            val generationRowScroll = rememberScrollState()
            LaunchedEffect(partial?.length) {
                if (generationRowScroll.maxValue > 0) {
                    generationRowScroll.scrollTo(generationRowScroll.maxValue)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEAF3F5FA))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(generationRowScroll)
            ) {
                // 2026-09-21 思考链流式透出：抽屉置顶、流式正文在下；展开状态与
                // 已完成消息共享，生成结束落抽屉时不再闪断。
                if (!streamingMonologue.isNullOrBlank()) {
                    MonologueDrawer(
                        monologue = streamingMonologue,
                        expanded = monologueExpanded,
                        onExpandedChange = { monologueExpanded = it },
                        streaming = true
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (partial.isNullOrBlank()) {
                    GenerationRow(
                        learnerName = state.learnerName,
                        learnerAvatarReference = state.learnerAvatarReference,
                        avatarVisible = state.avatarVisible,
                        label = label,
                        onOpenSettings = onOpenModelSettings,
                        onRetry = onRetryGeneration
                    )
                } else {
                    StreamingGenerationRow(
                        learnerName = state.learnerName,
                        learnerAvatarReference = state.learnerAvatarReference,
                        avatarVisible = state.avatarVisible,
                        text = partial
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xEAF3F5FA)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.composer.orderedAttachments.isNotEmpty()) {
                ComposerAttachmentStrip(
                    attachments = state.composer.orderedAttachments,
                    onRemove = onRemoveAttachment,
                    onRetry = onRetryAttachment,
                    onMove = onMoveAttachment
                )
            }
            state.composer.quoteTarget?.let { quote ->
                ComposerPreview(
                    label = "回复 ${quote.sourceIdentity}：${quote.excerpt}",
                    onDismiss = onCancelQuote
                )
            }
            // Douyin-style attachment panel: the media strip rides above
            // the composer and the action row replaces the keyboard area
            // below it while the panel is open.
            val focusManager = LocalFocusManager.current
            if (showAttachmentActions) {
                ChatAttachmentMediaStrip(
                    selectedUris = selectedGalleryUris,
                    onToggle = { uri ->
                        val key = uri.toString()
                        if (key in selectedGalleryUris) {
                            selectedGalleryUris = selectedGalleryUris - key
                            state.composer.orderedAttachments
                                .firstOrNull { it.uri == key }
                                ?.let { onRemoveAttachment(it.id) }
                        } else {
                            selectedGalleryUris = selectedGalleryUris + key
                            onGalleryImagePicked(uri)
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WebSearchToggle(
                    enabled = webSearchEnabled,
                    onToggle = { wantEnabled ->
                        if (wantEnabled) {
                            showWebSearchConfirm = true
                        } else {
                            onWebSearchChange(false)
                        }
                    }
                )
                ChatModelSelectorChip(
                    profiles = llmProfiles,
                    onActivate = onActivateLlmProfile
                )
            }
            ReverseTeachingComposer(
                text = state.composer.text,
                canSend = state.composer.canSend,
                isSending = state.composer.isSending,
                onTextChange = onComposerTextChange,
        voiceInputState = voiceInputState,
        onVoiceInputClick = onVoiceInputClick,
        onVoicePressStart = onVoicePressStart,
        onVoicePressStop = onVoicePressStop,
                onAdd = {
                    if (showAttachmentActions) {
                        showAttachmentActions = false
                    } else {
                        focusManager.clearFocus()
                        showAttachmentActions = true
                    }
                },
                onSend = {
                    followNextAppend = true
                    onSendMessage()
                },
                onFocusChanged = { focused ->
                    if (focused) showAttachmentActions = false
                    onComposerFocusChanged(focused)
                }
            )
            if (showWebSearchConfirm) {
                AlertDialog(
                    onDismissRequest = { showWebSearchConfirm = false },
                    modifier = Modifier.width(320.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    title = {
                        Text(
                            text = "开启联网搜索",
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            text = "联网搜索会大幅增加额度消耗，每次提问的消耗可能增加到原来的几十倍。确定要开启吗？",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = Color(0xFF5A6478),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showWebSearchConfirm = false
                                onWebSearchChange(true)
                            },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "开启",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3478F6)
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showWebSearchConfirm = false },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "取消",
                                fontSize = 16.sp,
                                color = ChatMuted
                            )
                        }
                    }
                )
            }
            if (showAttachmentActions) {
                ChatAttachmentPanelActions(
                    onPickImages = {
                        showAttachmentActions = false
                        onPickImages()
                    },
                    onPickLocalSource = {
                        showAttachmentActions = false
                        onPickLocalSource()
                    }
                )
            }
            state.composer.sendFailure?.let { failure ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(failure, modifier = Modifier.weight(1f), color = Color(0xFF9D3340), fontSize = 11.sp)
                    TextButton(onClick = onRetrySend) { Text("重试") }
                }
            }
            state.composer.notice?.let { notice ->
                Text(notice, modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFF7A5B16), fontSize = 11.sp)
            }
            if (state.pendingDeletion != null) {
                Snackbar(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    action = {
                        TextButton(
                            onClick = if (state.pendingDeletionRetryRequired) onRetryDelete else onUndoDelete
                        ) {
                            Text(if (state.pendingDeletionRetryRequired) "重试" else "撤销")
                        }
                    }
                ) {
                    Text(
                        if (state.pendingDeletionRetryRequired) "删除未完成" else "消息已删除",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    ChatCornerRadialMenu(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 1.dp, end = 0.dp),
        onOpenSearch = onOpenSearch,
        onOpenSources = onOpenSources,
        onOpenGlobalGraph = onOpenGlobalGraph,
        onOpenSessionSettings = onOpenSessionSettings
    )
    }


    if (showSourcePicker) {
        ChatSourcePickerSheet(
            sources = availableSourceAttachments,
            onDismiss = { showSourcePicker = false },
            onSelect = {
                showSourcePicker = false
                onSelectSource(it)
            },
            onOpenSources = onOpenSources
        )
    }
    actionMessage?.let { item ->
        ChatMessageActionSheet(
            item = item,
            onDismiss = { actionMessage = null },
            onAction = { action ->
                actionMessage = null
                if (action !in ChatMessageActionPolicy.actionsFor(item)) return@ChatMessageActionSheet
                if (action == ChatMessageAction.LocateSource && item.attachments.mapNotNull { it.sourceId }.distinct().size > 1) {
                    locateSourceMessage = item
                } else {
                    onMessageAction(item, action)
                }
            }
        )
    }
    memoryDraft?.let { draft ->
        RememberMessageDialog(
            draft = draft,
            sources = state.sources.filter { it.id in state.currentSessionSourceIds },
            error = memoryError,
            onChange = onMemoryDraftChange,
            onDismiss = onDismissMemory,
            onConfirm = onConfirmMemory
        )
    }
    deleteConfirmation?.let { confirmation ->
        DeleteMessageDialog(
            confirmation = confirmation,
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
    viewerAttachment?.let { attachment ->
        ChatImageViewer(
            attachment = attachment,
            onDismiss = { viewerAttachment = null },
            onSave = { onSaveImage(attachment) },
            onShare = { onShareImage(attachment) }
        )
    }
    locateSourceMessage?.let { item ->
        ChatLocateSourceSheet(
            item = item,
            sources = state.sources,
            onDismiss = { locateSourceMessage = null },
            onSelect = { sourceId ->
                locateSourceMessage = null
                onOpenSessionSource(sourceId)
            }
        )
    }
}

@Composable
private fun ReverseTeachingChatHeader(
    state: ChatUiState,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenGlobalGraph: () -> Unit,
    onOpenSessionSettings: () -> Unit,
    onOverflowAction: (ChatOverflowAction) -> Unit
) {
    Surface(
        color = Color(0xFFFAFCFE),
        contentColor = ChatInk,
        border = BorderStroke(1.dp, Color(0xFFDDE3ED))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormalHeaderIconButton(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回会话首页",
                onClick = onBack,
                filled = false
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 3.dp, end = 8.dp),
                onClick = onOpenSessionSettings,
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.avatarVisible) {
                        LearnerAvatar(state.learnerName, state.learnerAvatarReference)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = state.sessionTitle,
                            color = ChatInk,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                    }
                }
            }
        }
    }
}

private data class ChatRadialEntry(
    val angleDeg: Float,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String?,
    val onClick: () -> Unit
)

/** 顶栏圆盘快捷菜单：按住中心，向左下四个方向拖动选中，松手触发 */
@Composable
private fun ChatCornerRadialMenu(
    onOpenSearch: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenGlobalGraph: () -> Unit,
    onOpenSessionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(-1) }
    val entries = remember(onOpenSearch, onOpenSources, onOpenGlobalGraph, onOpenSessionSettings) {
        listOf(
            ChatRadialEntry(180f, "搜索", Icons.Rounded.Search, "radial-search", onOpenSearch),
            ChatRadialEntry(210f, "资料", Icons.Rounded.MenuBook, "radial-sources", onOpenSources),
            ChatRadialEntry(240f, "图谱", GraphNetworkIcon, "chat-global-graph", onOpenGlobalGraph),
            ChatRadialEntry(270f, "设置", Icons.Rounded.Settings, "chat-session-settings", onOpenSessionSettings)
        )
    }
    val progress by animateFloatAsState(
        targetValue = if (pressed && !cancelled) 1f else 0f,
        animationSpec = if (pressed) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "radial-progress"
    )
    val discScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "radial-disc-scale"
    )

    val screenHalfY = with(LocalDensity.current) {
        (LocalConfiguration.current.screenHeightDp / 2).dp.toPx()
    }
    var haloTopInWindow by remember { mutableStateOf(0f) }

    // 外层 70dp 是不可见的触控热区：起手不必精确按在圆盘上，圆盘四周都能起手；
    // 起手后 Compose 会把同一根手指的后续事件持续路由到本节点，拖出导航栏、
    // 拖到屏幕任意位置都不会丢手势。
    Box(
        modifier = modifier
            .size(70.dp)
            .onGloballyPositioned { haloTopInWindow = it.boundsInWindow().top }
            .pointerInput(entries) {
                val activatePx = 26.dp.toPx()
                val cancelPx = 18.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    cancelled = false
                    selected = -1
                    var currentIndex = -1
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // 误触判定一：拖到屏幕下半部分，整个手势作废并锁定到松手，
                        // 菜单收起作为反馈（progress 跟随 pressed && !cancelled）
                        if (haloTopInWindow + change.position.y > screenHalfY) {
                            cancelled = true
                        }
                        if (cancelled) {
                            currentIndex = -1
                        } else {
                            val dist = hypot(dx, dy)
                            currentIndex = when {
                                // 误触判定二：滑回圆盘附近 = 主动取消，松手不触发
                                dist < cancelPx -> -1
                                dist > activatePx -> {
                                    // atan2 返回 -180..180，左下/正下方向是负角度，
                                    // 必须先归一到 0..360 再按环形差值比较，
                                    // 否则 180° 以外的三个方向永远匹配不上
                                    val angle = (Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
                                    var best = -1
                                    var bestDiff = 28.0
                                    entries.forEachIndexed { i, e ->
                                        val rawDiff = abs(angle - e.angleDeg.toDouble())
                                        val diff = minOf(rawDiff, 360.0 - rawDiff)
                                        if (diff < bestDiff) {
                                            bestDiff = diff
                                            best = i
                                        }
                                    }
                                    best
                                }
                                // 滞回带：18..26dp 之间保持上一个状态，防止边界抖动
                                else -> currentIndex
                            }
                        }
                        selected = currentIndex
                        change.consume()
                    }
                    val fired = currentIndex
                    pressed = false
                    cancelled = false
                    selected = -1
                    if (fired >= 0) entries[fired].onClick()
                }
            }
    ) {
        // 可视层：46dp 圆盘（与返回键同尺寸，居中于 72dp 顶栏）+ 四个 44dp 菜单项，
        // 都以热区右上角为锚点；展开半径 120dp，下方项会探入聊天区但不压顶栏小字
        Box(modifier = Modifier.align(Alignment.Center).size(46.dp)) {
            entries.forEachIndexed { index, entry ->
                val isSelected = selected == index
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            val rad = Math.toRadians(entry.angleDeg.toDouble())
                            val dist = 120.dp.toPx() * progress
                            val base = 1.dp.roundToPx()
                            IntOffset(
                                (dist * cos(rad)).roundToInt() - base,
                                (-dist * sin(rad)).roundToInt() - base
                            )
                        }
                        .graphicsLayer {
                            alpha = progress
                            val scale = 0.5f + 0.5f * progress
                            val bump = if (isSelected) 1.22f else 1f
                            scaleX = scale * bump
                            scaleY = scale * bump
                        }
                        .testTag(entry.testTag ?: "radial-item")
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ChatInk else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ChatInk else Color(0xFFE5E5EA),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.label,
                        tint = if (isSelected) Color.White else ChatInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = if (pressed) Color(0xFFEDEFF5) else Color.White,
                border = BorderStroke(1.dp, if (pressed) Color(0xFFC9CEDA) else Color(0xFFE5E5EA)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        scaleX = discScale
                        scaleY = discScale
                    }
                    .size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (selected >= 0) entries[selected].icon else Icons.Rounded.Apps,
                        contentDescription = "快捷入口",
                        tint = ChatInk,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormalHeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        color = if (filled) Color(0xFFFFFFFF) else Color.Transparent,
        contentColor = Color(0xFF171C27),
        shape = CircleShape,
        border = if (filled) BorderStroke(1.dp, Color(0xFFE5E5EA)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(if (filled) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
private fun LearnerOpening(
    learnerName: String,
    learnerStatus: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (avatarVisible) LearnerAvatar(learnerName, learnerAvatarReference)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$learnerName · $learnerStatus",
                color = ChatMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "我先从一个容易混淆的地方问起：函数打印出的结果，和 return 返回的值，真的一样吗？",
                color = ChatInk,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReverseTeachingMessage(
    item: ChatTimelineItem,
    learnerName: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean,
    sources: List<ChatSourceUi>,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenImage: (ChatAttachmentUi) -> Unit,
    onOpenSource: (String?) -> Unit,
    onReselectInvalidSource: (String, String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onCopyRichSource: (String) -> ChatClipboardResult,
    monologueExpanded: Boolean = false,
    onMonologueExpandedChange: (Boolean) -> Unit = {}
) {
    when (item.role) {
        MessageRole.System -> ChapterTransitionPolicy.parseCardText(item.text)
            ?.let { ChapterTransitionCard(it) }
            ?: SystemTimelineMessage(item.text)
        MessageRole.Tool -> EvidenceTimelineMessage(item.text)
        MessageRole.User -> UserTimelineMessage(
            item, sources, selected, onTap, onLongPress, onOpenImage, onOpenSource, onReselectInvalidSource, onOpenExternalLink, onCopyRichSource
        )
        MessageRole.Assistant -> LearnerTimelineMessage(
            item,
            learnerName,
            learnerAvatarReference,
            avatarVisible,
            sources,
            selected,
            onTap,
            onLongPress,
            onOpenImage,
            onOpenSource,
            onReselectInvalidSource,
            onOpenExternalLink,
            onCopyRichSource,
            monologueExpanded,
            onMonologueExpandedChange
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserTimelineMessage(
    item: ChatTimelineItem,
    sources: List<ChatSourceUi>,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenImage: (ChatAttachmentUi) -> Unit,
    onOpenSource: (String?) -> Unit,
    onReselectInvalidSource: (String, String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onCopyRichSource: (String) -> ChatClipboardResult
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 2026-09-24 排版改版（QQ 参考）：用户消息右对齐实底蓝气泡、白字、
    // 尾角在右下的小圆角，替代原「裸文本 + 绿色竖条」的排版。
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            modifier = Modifier
                .widthIn(max = 286.dp)
                .graphicsLayer {
                    scaleX = if (pressed) 0.99f else 1f
                    scaleY = if (pressed) 0.99f else 1f
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongPress
                ),
            color = Color(0xFF4287E8),
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item.quoteLabel?.let { quote ->
                    Text(
                        text = quote,
                        color = Color(0xCCFFFFFF),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.End
                    )
                }
                RichMessageContent(
                    source = item.text,
                    userAligned = true,
                    onOpenExternalLink = onOpenExternalLink,
                    onMessageTap = onTap,
                    onMessageLongPress = onLongPress,
                    onCopySource = onCopyRichSource,
                    textColor = Color.White
                )
                item.attachments.forEach {
                    MessageAttachment(it, sources, onOpenImage, onOpenSource, onReselectInvalidSource)
                }
                if (item.inheritedReadOnly) InheritedMessageLabel()
                if (item.remembered) RememberedMessageLabel()
            }
        }
        if (selected) MessageMetadataRow(item)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LearnerTimelineMessage(
    item: ChatTimelineItem,
    learnerName: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean,
    sources: List<ChatSourceUi>,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenImage: (ChatAttachmentUi) -> Unit,
    onOpenSource: (String?) -> Unit,
    onReselectInvalidSource: (String, String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onCopyRichSource: (String) -> ChatClipboardResult,
    monologueExpanded: Boolean = false,
    onMonologueExpandedChange: (Boolean) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (avatarVisible) LearnerAvatar(learnerName, learnerAvatarReference)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = learnerName,
                color = Color(0xFF3D6EB2),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            // 2026-09-24 排版改版（QQ 参考）：学习者消息进白色圆角气泡，
            // 宽度随内容自适应、不再撑满整行；尾角在左下。思考链抽屉留气泡外。
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .graphicsLayer {
                        scaleX = if (pressed) 0.99f else 1f
                        scaleY = if (pressed) 0.99f else 1f
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onTap,
                        onLongClick = onLongPress
                    ),
                color = Color.White,
                contentColor = ChatInk,
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                border = BorderStroke(1.dp, Color(0xFFE3E9F2))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item.quoteLabel?.let { quote ->
                        Text(quote, color = ChatMuted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    RichMessageContent(
                        source = item.text,
                        userAligned = false,
                        onOpenExternalLink = onOpenExternalLink,
                        onMessageTap = onTap,
                        onMessageLongPress = onLongPress,
                        onCopySource = onCopyRichSource
                    )
                    item.attachments.forEach {
                        MessageAttachment(it, sources, onOpenImage, onOpenSource, onReselectInvalidSource)
                    }
                    if (item.inheritedReadOnly) InheritedMessageLabel()
                    if (item.remembered) RememberedMessageLabel()
                }
            }
            // Expression-loop slice 4 (SPEC section 4.7 route C): collapsed
            // thinking-chain drawer under the spoken bubble; it consumes
            // its own taps and never triggers bubble selection.
            item.monologue?.let { monologue ->
                MonologueDrawer(
                    monologue = monologue,
                    expanded = monologueExpanded,
                    onExpandedChange = onMonologueExpandedChange
                )
            }
            if (selected) MessageMetadataRow(item)
        }
    }
}

@Composable
private fun InheritedMessageLabel() {
    Text("来自父分支历史", color = ChatMuted, fontSize = 10.sp)
}

@Composable
private fun LearnerAvatar(learnerName: String, reference: LearnerAvatarReference?) {
    val context = LocalContext.current
    val contentUri = (reference as? LearnerAvatarReference.ContentUri)?.uri
    val packagedDrawable = remember(reference, context) {
        (reference as? LearnerAvatarReference.PackagedDrawable)?.resourceId?.takeIf { resourceId ->
            runCatching { context.resources.getResourceTypeName(resourceId) == "drawable" }.getOrDefault(false)
        }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, contentUri) {
        value = contentUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val modifier = Modifier
        .size(30.dp)
        .shadow(3.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
        .clip(CircleShape)
    val current = bitmap
    if (packagedDrawable != null) {
        Image(
            painter = painterResource(packagedDrawable),
            contentDescription = "学习者$learnerName",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else if (current == null) {
        Image(
            painter = painterResource(R.drawable.chat_learner_linche),
            contentDescription = "学习者$learnerName",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Image(
            bitmap = current,
            contentDescription = "学习者$learnerName",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
private fun MessageBody(item: ChatTimelineItem) {
    Column(
        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item.quoteLabel?.let {
            Text(
                text = it,
                color = Color(0xFF66738A),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x667D8FB5), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        Text(
            text = item.text,
            color = ChatInk,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        item.attachments.forEach { attachment ->
            MessageAttachment(attachment)
        }
    }
}

@Composable
private fun MessageAttachment(
    attachment: ChatAttachmentUi,
    sources: List<ChatSourceUi> = emptyList(),
    onOpenImage: (ChatAttachmentUi) -> Unit = {},
    onOpenSource: (String?) -> Unit = {},
    onReselectInvalidSource: (String, String) -> Unit = { _, _ -> }
) {
    if (attachment.isImage) {
        MessageImageAttachment(attachment, onOpenImage)
    } else {
        AttachmentReference(attachment, sources, onOpenSource, onReselectInvalidSource)
    }
}

@Composable
private fun MessageImageAttachment(
    attachment: ChatAttachmentUi,
    onOpen: (ChatAttachmentUi) -> Unit
) {
    val context = LocalContext.current
    var retryKey by remember(attachment.uri) { mutableStateOf(0) }
    val imageState by produceState<ChatImageLoadState>(
        initialValue = ChatImageLoadState.Loading,
        key1 = attachment.uri,
        key2 = retryKey
    ) {
        val uri = attachment.uri
        value = if (uri.isNullOrBlank()) {
            ChatImageLoadState.Failed("消息里没有图片地址")
        } else {
            withContext(Dispatchers.IO) {
                when (val loaded = ChatImageLoader.load(context, Uri.parse(uri), ChatImageTargets.Thumbnail)) {
                    is ChatImageLoadResult.Ready -> ChatImageLoadState.Ready(loaded.bitmap)
                    is ChatImageLoadResult.Failed -> ChatImageLoadState.Failed(loaded.reason)
                }
            }
        }
    }
    val ratio = (imageState as? ChatImageLoadState.Ready)?.bitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    } ?: 1.35f

    Surface(
        onClick = { if (imageState is ChatImageLoadState.Ready) onOpen(attachment) },
        modifier = if (imageState is ChatImageLoadState.Ready) {
            Modifier.fillMaxWidth().aspectRatio(ratio)
        } else {
            Modifier.fillMaxWidth().height(64.dp)
        },
        color = Color(0xFFE8ECF3),
        contentColor = ChatMuted,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.10f))
    ) {
        when (val current = imageState) {
            ChatImageLoadState.Loading -> Box(contentAlignment = Alignment.Center) {
                Text("正在读取图片…", fontSize = 11.sp)
            }
            is ChatImageLoadState.Failed -> Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图片暂时无法显示 · ${attachment.name}\n${current.reason}", modifier = Modifier.weight(1f), fontSize = 11.sp)
                TextButton(onClick = { retryKey += 1 }) { Text("重试") }
            }
            is ChatImageLoadState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AttachmentReference(
    attachment: ChatAttachmentUi,
    sources: List<ChatSourceUi>,
    onOpenSource: (String?) -> Unit,
    onReselectInvalidSource: (String, String) -> Unit
) {
    val source = resolveChatSourceAttachment(attachment, sources)
    Surface(
        onClick = {
            if (source.valid) {
                onOpenSource(source.sourceId)
            } else {
                source.sourceId?.let { onReselectInvalidSource(it, source.displayName) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF4F6FA),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, if (source.valid) Color(0xFFD8DEE9) else Color(0xFFE2B8BE))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = Color(0xFF4D66A6), modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, color = Color(0xFF3D4657), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${source.typeLabel} · ${source.stateLabel}", color = if (source.valid) ChatMuted else Color(0xFF9D3340), fontSize = 10.sp)
            }
            Text(if (source.valid) "查看" else "重新选择", color = Color(0xFF4D66A6), fontSize = 10.sp)
        }
    }
}

private sealed interface ChatImageLoadState {
    data object Loading : ChatImageLoadState
    data class Failed(val reason: String) : ChatImageLoadState
    data class Ready(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ChatImageLoadState
}

@Composable
private fun MessageMetadataRow(item: ChatTimelineItem) {
    val metadata = chatMessageMetadata(item)
    Text(
        text = "${metadata.exactTime} · ${metadata.deliveryLabel}",
        modifier = Modifier.padding(top = 5.dp),
        color = ChatMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
}

@Composable
private fun RememberedMessageLabel() {
    Text("✓ 已记住", color = Color(0xFF2B7A57), fontSize = 10.sp, lineHeight = 14.sp)
}

@Composable
private fun RichMessageContent(
    source: String,
    userAligned: Boolean,
    onOpenExternalLink: (String) -> Unit,
    onMessageTap: () -> Unit,
    onMessageLongPress: () -> Unit,
    onCopySource: (String) -> ChatClipboardResult,
    // 2026-09-24 排版改版：正文颜色可注入（用户蓝气泡传白字），
    // 列宽从撑满改为内容自适应上限，配合气泡排版。
    textColor: Color = ChatInk
) {
    val blocks = remember(source) { ChatRichContentParser.parse(source) }
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        horizontalAlignment = if (userAligned) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatRichBlock.Heading -> Text(
                    block.text,
                    color = textColor,
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp },
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                is ChatRichBlock.Paragraph -> RichInlineRow(
                    inlines = block.inlines,
                    onOpenExternalLink = onOpenExternalLink,
                    onMessageTap = onMessageTap,
                    onMessageLongPress = onMessageLongPress,
                    textColor = textColor
                )
                is ChatRichBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (block.ordered) "${block.index ?: 1}." else "•", color = ChatMuted, fontSize = 16.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        RichInlineRow(
                            inlines = ChatRichContentParser.parseInlines(block.text),
                            onOpenExternalLink = onOpenExternalLink,
                            onMessageTap = onMessageTap,
                            onMessageLongPress = onMessageLongPress,
                            textColor = textColor
                        )
                    }
                }
                is ChatRichBlock.Quote -> Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE9EDF4), RoundedCornerShape(4.dp)).padding(8.dp)
                ) {
                    RichInlineRow(
                        inlines = ChatRichContentParser.parseInlines(block.text),
                        onOpenExternalLink = onOpenExternalLink,
                        onMessageTap = onMessageTap,
                        onMessageLongPress = onMessageLongPress,
                        textColor = Color(0xFF4C586B)
                    )
                }
                is ChatRichBlock.Code -> RichSourceBlock(
                    label = block.language?.let { "代码 · $it" } ?: "代码",
                    source = block.source,
                    displayText = remember(block.language, block.source) {
                        highlightedCodeAnnotatedString(block.language, block.source)
                    },
                    onCopySource = onCopySource
                )
                is ChatRichBlock.Formula -> {
                    val formulaNodes = remember(block.source) {
                        (LatexMiniParser.parse(block.source) as? LatexParseResult.Success)?.nodes
                    }
                    if (formulaNodes != null) {
                        RichFormulaBlock(
                            source = block.source,
                            nodes = formulaNodes,
                            onCopySource = onCopySource
                        )
                    } else {
                        // 解析失败（含流式半完整块）回退原始源码展示，不炸屏
                        RichSourceBlock("公式", block.source, onCopySource = onCopySource)
                    }
                }
                is ChatRichBlock.Table -> RichTable(block)
                is ChatRichBlock.PlainText -> Text(block.source, color = textColor, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun RichInlineRow(
    inlines: List<ChatRichInline>,
    onOpenExternalLink: (String) -> Unit,
    onMessageTap: () -> Unit,
    onMessageLongPress: () -> Unit,
    textColor: Color = ChatInk
) {
    val text = remember(inlines) { buildRichInlineAnnotatedString(inlines) }
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier = Modifier.pointerInput(text, onOpenExternalLink, onMessageTap, onMessageLongPress) {
            detectTapGestures(
                onTap = { position ->
                    val target = layoutResult
                        ?.getOffsetForPosition(position)
                        ?.let { resolveChatRichInlineTapTarget(text, it) }
                        ?: ChatRichInlineTapTarget.Message
                    when (target) {
                        is ChatRichInlineTapTarget.Link -> onOpenExternalLink(target.url)
                        ChatRichInlineTapTarget.Message -> onMessageTap()
                    }
                },
                onLongPress = { onMessageLongPress() }
            )
        },
        color = textColor,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        onTextLayout = { layoutResult = it }
    )
}

internal const val ChatRichInlineLinkTag = "chat-rich-link"

internal sealed interface ChatRichInlineTapTarget {
    data object Message : ChatRichInlineTapTarget
    data class Link(val url: String) : ChatRichInlineTapTarget
}

internal fun resolveChatRichInlineTapTarget(
    text: AnnotatedString,
    offset: Int
): ChatRichInlineTapTarget {
    if (offset !in 0 until text.length) return ChatRichInlineTapTarget.Message
    return text.getStringAnnotations(
        tag = ChatRichInlineLinkTag,
        start = offset,
        end = offset + 1
    ).firstOrNull()?.let { ChatRichInlineTapTarget.Link(it.item) }
        ?: ChatRichInlineTapTarget.Message
}

internal fun buildRichInlineAnnotatedString(inlines: List<ChatRichInline>): AnnotatedString = buildAnnotatedString {
    inlines.forEach { inline ->
        when (inline) {
            is ChatRichInline.Text -> append(inline.source)
            is ChatRichInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.source) }
            is ChatRichInline.Code -> withStyle(
                SpanStyle(
                    color = Color(0xFF33415B),
                    background = Color(0xFFE8EBF1),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            ) { append(inline.source) }
            is ChatRichInline.Formula -> {
                val renderedFormula = latexInlineAnnotatedString(inline.source)
                if (renderedFormula != null) {
                    append(renderedFormula)
                } else {
                    // 解析失败（含流式半完整块）回退原始源码样式
                    withStyle(
                        SpanStyle(color = Color(0xFF334D7A), fontWeight = FontWeight.Medium)
                    ) { append(inline.source) }
                }
            }
            is ChatRichInline.Link -> {
                pushStringAnnotation(ChatRichInlineLinkTag, inline.url)
                withStyle(
                    SpanStyle(color = Color(0xFF315FA3), textDecoration = TextDecoration.Underline)
                ) { append(inline.label) }
                pop()
            }
        }
    }
}

@Composable
private fun RichSourceBlock(
    label: String,
    source: String,
    displayText: AnnotatedString? = null,
    onCopySource: (String) -> ChatClipboardResult
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF202631),
        contentColor = Color(0xFFF1F4F8),
        shape = RoundedCornerShape(7.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), color = Color(0xFFBBC5D4), fontSize = 11.sp)
                IconButton(onClick = { onCopySource(source) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制$label", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = displayText ?: AnnotatedString(source),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                color = Color(0xFFF1F4F8),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false
            )
        }
    }
}

@Composable
private fun RichTable(table: ChatRichBlock.Table) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        val columns = table.headers.indices
        columns.forEach { column ->
            Column(modifier = Modifier.widthIn(min = 104.dp).border(BorderStroke(1.dp, Color(0xFFD8DEE9)))) {
                Text(table.headers[column], modifier = Modifier.padding(7.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                table.rows.forEach { row ->
                    Text(row.getOrElse(column) { "" }, modifier = Modifier.padding(7.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatMessageActionSheet(
    item: ChatTimelineItem,
    onDismiss: () -> Unit,
    onAction: (ChatMessageAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(item.roleLabel, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = ChatMuted, fontSize = 12.sp)
        ChatMessageActionPolicy.actionsFor(item).forEach { action ->
            Surface(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
                Text(
                    action.label,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
                    color = if (action == ChatMessageAction.Delete) Color(0xFFB24B55) else ChatInk,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatLocateSourceSheet(
    item: ChatTimelineItem,
    sources: List<ChatSourceUi>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("选择关联资料", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
        item.attachments.filter { it.sourceId != null }.distinctBy { it.sourceId }.forEach { attachment ->
            val source = resolveChatSourceAttachment(attachment, sources)
            Surface(onClick = { source.sourceId?.let(onSelect) }, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(source.displayName, fontSize = 15.sp)
                    Text("${source.typeLabel} · ${source.stateLabel}", color = if (source.valid) ChatMuted else Color(0xFF9D3340), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RememberMessageDialog(
    draft: ChatMemoryDraft,
    sources: List<ChatSourceUi>,
    error: String?,
    onChange: (ChatMemoryDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记住这条") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = { onChange(draft.copy(text = it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    label = { Text("记忆内容") }
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatMemoryCategory.entries.forEach { category ->
                        Button(
                            onClick = { onChange(draft.copy(category = category)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (draft.category == category) Color(0xFF4169A1) else Color(0xFFE7EBF1),
                                contentColor = if (draft.category == category) Color.White else ChatInk
                            )
                        ) {
                            Text(
                                if (category.capability is ChatMemoryCategoryCapability.Supported) {
                                    category.label
                                } else {
                                    "${category.label}（不可用）"
                                }
                            )
                        }
                    }
                }
                Text(
                    when (val capability = draft.category.capability) {
                        ChatMemoryCategoryCapability.Supported -> "${draft.category.label}：可保存，并保留消息与资料出处。"
                        is ChatMemoryCategoryCapability.Unavailable -> "不可用：${capability.reason}"
                    },
                    color = if (draft.category.capability is ChatMemoryCategoryCapability.Supported) {
                        Color(0xFF2B7A57)
                    } else {
                        Color(0xFF9D3340)
                    },
                    fontSize = 12.sp
                )
                Text("关联资料", color = ChatMuted, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TextButton(onClick = { onChange(draft.copy(sourceId = null)) }) { Text("无") }
                    sources.filter(ChatSourceUi::valid).forEach { source ->
                        TextButton(onClick = { onChange(draft.copy(sourceId = source.id)) }) {
                            Text(if (draft.sourceId == source.id) "✓ ${source.displayName}" else source.displayName)
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFF9D3340), fontSize = 12.sp) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = onConfirm, enabled = draft.text.isNotBlank()) { Text("保存") } }
    )
}

@Composable
private fun DeleteMessageDialog(
    confirmation: ChatDeleteConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除消息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(confirmation.messagePreview, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (confirmation.impact.hasDerivatives) {
                    Text("受影响的关联内容", fontWeight = FontWeight.Bold)
                    (confirmation.impact.memories + confirmation.impact.graphItems).forEach { Text("• ${it.label}", fontSize = 13.sp) }
                }
                Text(
                    confirmation.impact.deletionBoundary,
                    color = if (confirmation.impact.canDeleteMessageOnly) ChatMuted else Color(0xFF9D3340),
                    fontSize = 12.sp
                )
                Surface(color = Color(0xFFE8ECF3), shape = RoundedCornerShape(6.dp)) {
                    Text("仅删除消息（默认）", modifier = Modifier.padding(10.dp), fontSize = 13.sp)
                }
                Text("同时删除关联内容（当前不可用）", color = ChatMuted.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmation.impact.canDeleteMessageOnly,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB24B55))
            ) { Text("删除") }
        }
    )
}

@Composable
private fun ChatImageViewer(
    attachment: ChatAttachmentUi,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val viewerTarget = remember(context) {
        context.resources.displayMetrics.let { metrics ->
            ChatImageTarget(
                widthPixels = metrics.widthPixels.coerceAtLeast(1),
                heightPixels = metrics.heightPixels.coerceAtLeast(1)
            )
        }
    }
    var retryKey by remember(attachment.uri) { mutableStateOf(0) }
    val imageState by produceState<ChatImageLoadState>(
        ChatImageLoadState.Loading,
        attachment.uri,
        viewerTarget,
        retryKey
    ) {
        value = withContext(Dispatchers.IO) {
            when (val loaded = ChatImageLoader.load(context, Uri.parse(attachment.uri), viewerTarget)) {
                is ChatImageLoadResult.Ready -> ChatImageLoadState.Ready(loaded.bitmap)
                is ChatImageLoadResult.Failed -> ChatImageLoadState.Failed(loaded.reason)
            }
        }
    }
    var scale by remember(attachment.uri) { mutableStateOf(1f) }
    var offset by remember(attachment.uri) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = imageState) {
            ChatImageLoadState.Loading -> Text("正在读取图片…", modifier = Modifier.align(Alignment.Center), color = Color.White)
            is ChatImageLoadState.Failed -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("图片暂时无法显示\n${current.reason}", color = Color.White)
                TextButton(onClick = { retryKey += 1 }) { Icon(Icons.Filled.Refresh, contentDescription = null); Text("重试") }
            }
            is ChatImageLoadState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                }.transformable(transform)
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭图片", tint = Color.White)
        }
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            IconButton(onClick = onSave, enabled = imageState is ChatImageLoadState.Ready) {
                Icon(Icons.Filled.Download, contentDescription = "保存图片", tint = Color.White)
            }
            IconButton(onClick = onShare, enabled = imageState is ChatImageLoadState.Ready) {
                Icon(Icons.Filled.Share, contentDescription = "分享图片", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SystemTimelineMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        color = Color(0xFF66738A),
        fontSize = 11.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center
    )
}

/**
 * R88 章节切换卡片：AI 学生提议 + 用户认可后才出现的时间线卡片。
 * 白底细边框（8dp 圆角）+ 品牌蓝点缀，正文带"上一章 ✓ → 新章节"与
 * 教学进度（第 X / Y 节）——进度展示收在卡片里，不做全局进度条。
 */
@Composable
private fun ChapterTransitionCard(proposal: ChapterTransitionProposal) {
    val accent = Color(0xFF2F5DDF)
    val ink = Color(0xFF121722)
    val muted = Color(0xFF6D778C)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE3E8F3))
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "✦ 章节更新",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "第 ${proposal.position + 1} / ${proposal.size} 节",
                    color = muted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (proposal.move) {
                PathMove.Advance -> {
                    if (proposal.fromLabel.isNotBlank()) {
                        Text(text = "✓ ${proposal.fromLabel} · 已掌握", color = muted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "→ 进入新章节：${proposal.toLabel}", color = ink, fontSize = 13.sp)
                    } else {
                        Text(text = "→ 开始学习：${proposal.toLabel}", color = ink, fontSize = 13.sp)
                    }
                }
                PathMove.Regress -> {
                    Text(text = "↩ 回到基础：${proposal.toLabel}", color = ink, fontSize = 13.sp)
                    if (proposal.fromLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${proposal.fromLabel} 先放一放，补牢地基再继续", color = muted, fontSize = 12.sp)
                    }
                }
                else -> {
                    Text(text = "✓ 学习路径全部完成（共 ${proposal.size} 节）", color = ink, fontSize = 13.sp)
                    if (proposal.fromLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "最后一章：${proposal.fromLabel}", color = muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceTimelineMessage(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 44.dp),
        color = Color(0xFFEBF2FC),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFFB9D0EC))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = Color(0xFF3E72A8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = Color(0xFF4F6E94),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("›", color = Color(0xFF607D9F), fontSize = 14.sp)
        }
    }
}

// 表达层提速（2026-09-20 拍板）：预填充首字前的等待指示从静态「•••」改为
// 错相位呼吸点，长等待窗口读起来是「在活动」而不是「卡住了」。
@Composable
private fun BreathingDots() {
    // 当前 Compose 版本的 animateFloat 没有 initialStartOffset 错相位参数，
    // 改用单个 0→1 相位动画 + 三角波数学错相位：效果等价，不依赖新 API。
    val breath = rememberInfiniteTransition(label = "generation-wait")
    val phase by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1240, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "generation-wait-phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { index ->
            val shifted = (phase + index / 3f) % 1f
            val triangle = if (shifted < 0.5f) shifted * 2f else (1f - shifted) * 2f
            val alpha = 0.2f + 0.8f * triangle
            Text("•", color = Color(0xFF6077B3).copy(alpha = alpha), fontSize = 12.sp)
        }
    }
}

@Composable
private fun GenerationRow(
    learnerName: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean,
    label: String,
    onOpenSettings: () -> Unit,
    onRetry: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarVisible) LearnerAvatar(learnerName, learnerAvatarReference)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$learnerName · $label",
                color = Color(0xFF5D6C86),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            val waitingDots = !label.startsWith("未配置模型") &&
                !(label.startsWith("生成失败") && onRetry != null)
            if (waitingDots) BreathingDots()
        }
        when {
            label.startsWith("未配置模型") -> TextButton(onClick = onOpenSettings) {
                Text("去设置", color = Color(0xFF4264C7), fontSize = 11.sp)
            }
            label.startsWith("生成失败") && onRetry != null -> TextButton(onClick = onRetry) {
                Text("重试", color = Color(0xFF4264C7), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StreamingGenerationRow(
    learnerName: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (avatarVisible) LearnerAvatar(learnerName, learnerAvatarReference)
        Surface(
            color = Color(0xFFF7F8FC),
            contentColor = ChatInk,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFDCE2EC))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text, fontSize = 14.sp, lineHeight = 21.sp)
                // 表达层提速（2026-09-20 拍板）：等待期让状态文字呼吸，
                // 长等待窗口读起来是「在活动」而不是「卡住了」。
                val typingBreath = rememberInfiniteTransition(label = "streaming-breath")
                val typingAlpha by typingBreath.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "streaming-typing-alpha"
                )
                Text("正在输入…", color = ChatMuted.copy(alpha = typingAlpha), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun ComposerPreview(label: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        color = Color(0xF7FAFBFE),
        contentColor = ChatInk,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE2EC))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = Color(0xFF59647A),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
                color = Color.Transparent,
                contentColor = ChatMuted,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun ComposerAttachmentStrip(
    attachments: List<ChatDraftAttachment>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            var accumulatedDrag by remember(attachment.id, index) { mutableStateOf(0f) }
            Surface(
                modifier = Modifier
                    .width(168.dp)
                    .height(58.dp)
                    .pointerInput(attachment.id, index, attachments.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                accumulatedDrag += amount.x
                                when {
                                    accumulatedDrag > 72f && index < attachments.lastIndex -> {
                                        onMove(index, index + 1)
                                        accumulatedDrag = 0f
                                    }
                                    accumulatedDrag < -72f && index > 0 -> {
                                        onMove(index, index - 1)
                                        accumulatedDrag = 0f
                                    }
                                }
                            }
                        )
                    },
                color = Color(0xFFF9FBFE),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFD4DCE8))
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${attachment.displayType} · ${attachment.name}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        when (val readiness = attachment.readiness) {
                            ChatAttachmentReadiness.Preparing -> Text("准备中", color = ChatMuted, fontSize = 10.sp)
                            ChatAttachmentReadiness.Ready -> Text("已准备", color = Color(0xFF287A56), fontSize = 10.sp)
                            is ChatAttachmentReadiness.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(readiness.message, modifier = Modifier.weight(1f), maxLines = 1, color = Color(0xFF9D3340), fontSize = 9.sp)
                                if (readiness.retryable) {
                                    TextButton(onClick = { onRetry(attachment.id) }) { Text("重试", fontSize = 9.sp) }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "移除${attachment.name}", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

internal data class ChatAttachmentSheetActionSpec(
    val label: String,
    val subtitle: String?
)

/**
 * NEWMP-V1-006 Task 3: camera entry subtitle guidance, extracted so JVM
 * tests can pin the permission-state copy without Compose.
 */
internal fun chatCameraPermissionSubtitle(permissionState: ChatPermissionState): String? = when (permissionState) {
    ChatPermissionState.Denied -> "相机权限被拒绝，可重新授权"
    ChatPermissionState.PermanentlyDenied -> "相机权限已关闭，前往系统设置"
    else -> null
}

/**
 * Douyin-style attachment panel: the action row entry list as a single
 * source of truth. Tentatively 相册 (system image picker) and 文件 (phone
 * document picker), rendered flush-left in add order; JVM tests pin the
 * phone-document entry ("文件") contract here. More entries will be
 * appended incrementally.
 */
internal fun chatAttachmentSheetActionSpecs(): List<ChatAttachmentSheetActionSpec> = listOf(
    ChatAttachmentSheetActionSpec("相册", null),
    ChatAttachmentSheetActionSpec("文件", null)
)

/**
 * Media strip shown above the composer while the attachment panel is
 * open. Auto-reads the device gallery (most recent first); falls back to
 * placeholder frames when the read permission is missing or the gallery
 * is empty.
 */
@Composable
private fun ChatAttachmentMediaStrip(
    modifier: Modifier = Modifier,
    selectedUris: Set<String> = emptySet(),
    onToggle: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(readImagesPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(readImagesPermission())
    }
    val images by produceState(initialValue = emptyList<Uri>(), hasPermission, context) {
        value = if (hasPermission) queryRecentGalleryImages(context, 12) else emptyList()
    }
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (images.isEmpty()) {
            items(4) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFF2F6FC), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFC7D8EA), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = ChatMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            items(images, key = { it.toString() }) { uri ->
                GalleryMediaThumbnail(
                    uri = uri,
                    selected = uri.toString() in selectedUris,
                    onClick = { onToggle(uri) }
                )
            }
        }
    }
}

private fun readImagesPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun queryRecentGalleryImages(context: Context, limit: Int): List<Uri> {
    val uris = ArrayList<Uri>(limit)
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && uris.size < limit) {
                uris += ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn)
                )
            }
        }
    }
    return uris
}

@Composable
private fun GalleryMediaThumbnail(
    uri: Uri,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                } else {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF2F6FC), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color(0xFF4287E8), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = ChatMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        }
        // 勾选角标：未选=半透明空心圆，已选=蓝底白勾（2026-09-24 拍板交互）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF4287E8) else Color(0x66000000))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Flush-left action row rendered below the composer while the attachment
 * panel is open, replacing the keyboard area. Entries stay in list order
 * and new ones can simply be appended.
 */
@Composable
private fun ChatAttachmentPanelActions(
    onPickImages: () -> Unit,
    onPickLocalSource: () -> Unit
) {
    // Entries are driven by chatAttachmentSheetActionSpecs so JVM tests
    // can pin the phone-document entry ("文件") contract without Compose.
    val entryIcons = listOf(Icons.Rounded.Collections, Icons.Rounded.Description)
    val entryActions = listOf(onPickImages, onPickLocalSource)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        chatAttachmentSheetActionSpecs().forEachIndexed { index, spec ->
            AttachmentPanelAction(
                icon = entryIcons[index],
                label = spec.label,
                onClick = entryActions[index]
            )
        }
    }
}

@Composable
private fun AttachmentPanelAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFF2F6FC), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color(0xFF4287E8),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatSourcePickerSheet(
    sources: List<ChatDraftAttachment>,
    onDismiss: () -> Unit,
    onSelect: (ChatDraftAttachment) -> Unit,
    onOpenSources: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择应用内资料",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (sources.isEmpty()) {
            Text(
                "本会话没有可引用的资料",
                modifier = Modifier.padding(20.dp),
                color = ChatMuted
            )
        } else {
            sources.forEach { source ->
                Surface(
                    onClick = { onSelect(source) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("应用内资料", color = ChatMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        TextButton(
            onClick = {
                onDismiss()
                onOpenSources()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text("打开资料中心", color = ChatMuted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WebSearchToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = if (enabled) Color(0xFFE4EEFC) else Color(0xFFF6F8FD),
        contentColor = if (enabled) Color(0xFF2E66C7) else ChatMuted,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF4283D9) else Color(0xFFC7D8EA)),
        modifier = Modifier
            .padding(start = 14.dp, top = 10.dp)
            .clickable { onToggle(!enabled) }
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Rounded.Public,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (enabled) "联网搜索·已开启" else "联网搜索",
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReverseTeachingComposer(
    text: String,
    canSend: Boolean,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    voiceInputState: VoiceInputState = VoiceInputState(),
    onVoiceInputClick: () -> Unit = {},
    onVoicePressStart: () -> Unit = {},
    onVoicePressStop: () -> Unit = {},
    onAdd: () -> Unit,
    onSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    // 2026-09-24 拍板语音交互：点按麦克风 → 输入栏变「按住 说话」语音栏
    // （长按录制、松手即停）；长按麦克风 → 语音对话小弹窗。
    var voiceMode by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    // pointerInput 不能以录音状态为 key：状态翻转会在手势中途取消协程、丢掉松手
    // 事件（2026-09-24 用户反馈「松手即停没做好」的根因），回调用 rememberUpdatedState。
    val currentVoicePressStart by rememberUpdatedState(onVoicePressStart)
    val currentVoicePressStop by rememberUpdatedState(onVoicePressStop)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 3.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(ChatComposerLayout.Gap)
    ) {
        Surface(
            onClick = onAdd,
            modifier = Modifier.size(ChatComposerLayout.SendSize),
            color = Color.Transparent,
            contentColor = Color(0xFF577394),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "添加图片或资料", modifier = Modifier.size(28.dp))
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ChatComposerLayout.Height, max = 136.dp),
            color = Color(0xFFF2F6FC),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFC7D8EA))
        ) {
            if (voiceMode) {
                // 语音栏：整栏按住说话、松手即停。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ChatComposerLayout.Height, max = 136.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                currentVoicePressStart()
                                waitForUpOrCancellation()
                                currentVoicePressStop()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (voiceInputState.active) "松手结束" else "按住 说话",
                        color = if (voiceInputState.active) Color(0xFF4287E8) else ChatInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ChatComposerLayout.Height, max = 136.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    textStyle = TextStyle(color = ChatInk, fontSize = 14.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(Color(0xFF4283D9)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChanged(it.isFocused) }
                )
                if (text.isBlank()) {
                    Text(
                        text = "继续讲解，或提出一个问题",
                        color = Color(0xFF8A96A9),
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            }
        }
        // 麦克风：点按切换语音栏/键盘，长按打开语音对话弹窗。
        Box(
            modifier = Modifier
                .size(ChatComposerLayout.SendSize)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = { voiceMode = !voiceMode },
                    onLongClick = { showVoiceDialog = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (voiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                contentDescription = if (voiceMode) "切换回键盘输入" else "语音输入，长按打开语音对话",
                tint = if (voiceInputState.active) Color(0xFF4287E8) else Color(0xFF577394),
                modifier = Modifier.size(24.dp)
            )
        }
        Surface(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(ChatComposerLayout.SendSize),
            color = if (canSend) Color(0xFF4287E8) else Color(0xFFAFB9C8),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = if (isSending) "正在发送" else "发送",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
    if (showVoiceDialog) {
        VoiceConversationDialog(
            voiceInputState = voiceInputState,
            onPressStart = onVoicePressStart,
            onPressStop = onVoicePressStop,
            onDismiss = { showVoiceDialog = false }
        )
    }
}

/**
 * 语音对话小弹窗（2026-09-24 拍板）：长按输入区麦克风打开。
 * 按住底部按钮说话、松手即停；识别文本由语音接线层实时写回输入框。
 */
@Composable
private fun VoiceConversationDialog(
    voiceInputState: VoiceInputState,
    onPressStart: () -> Unit,
    onPressStop: () -> Unit,
    onDismiss: () -> Unit
) {
    // 同语音栏：回调走 rememberUpdatedState，手势协程用固定 key，避免松手丢失。
    val currentPressStart by rememberUpdatedState(onPressStart)
    val currentPressStop by rememberUpdatedState(onPressStop)
    Dialog(onDismissRequest = onDismiss) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val dialogScale by animateFloatAsState(
            targetValue = if (entered) 1f else 0.82f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "voiceDialogScale"
        )
        val dialogAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "voiceDialogAlpha"
        )
        val listening = voiceInputState.phase == VoiceInputPhase.Starting ||
            voiceInputState.phase == VoiceInputPhase.Listening
        Surface(
            modifier = Modifier
                .width(300.dp)
                .graphicsLayer {
                    scaleX = dialogScale
                    scaleY = dialogScale
                    alpha = dialogAlpha
                },
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "语音对话",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChatInk,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭语音对话",
                        tint = ChatMuted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onDismiss)
                    )
                }
                Spacer(Modifier.height(18.dp))
                val pulseTransition = rememberInfiniteTransition(label = "voicePulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 750, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "voicePulseScale"
                )
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (listening) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                                .clip(CircleShape)
                                .background(Color(0x334287E8))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (listening) Color(0xFF4287E8) else Color(0xFFF2F6FC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (listening) Color.White else Color(0xFF577394),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = when (voiceInputState.phase) {
                        VoiceInputPhase.Starting -> "正在启动…"
                        VoiceInputPhase.Listening -> "正在聆听，松手结束"
                        VoiceInputPhase.Stopping -> "正在识别…"
                        VoiceInputPhase.Failed -> voiceInputState.errorMessage ?: "识别失败，请重试"
                        VoiceInputPhase.Idle -> "按住下方按钮说话"
                    },
                    fontSize = 14.sp,
                    color = if (voiceInputState.phase == VoiceInputPhase.Failed) Color(0xFF9D3340) else ChatMuted,
                    textAlign = TextAlign.Center
                )
                if (voiceInputState.transcript.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = voiceInputState.transcript,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = ChatInk,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (listening) Color(0xFF2F6FD6) else Color(0xFF4287E8))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                currentPressStart()
                                waitForUpOrCancellation()
                                currentPressStop()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (listening) "松手结束" else "按住 说话",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private val ChatPageBackground = Color(0xFFF4F7FC)
private val ChatInk = Color(0xFF171C27)
private val ChatMuted = Color(0xFF6D778C)

/** 自定义「图谱」图标：中心实心节点 + 三个描边卫星节点 + 连线，笔画粗细对齐 Material Rounded（2f/24dp） */
private val GraphNetworkIcon: ImageVector = ImageVector.Builder(
    name = "GraphNetwork",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes("M12,12 m-2.1,0 a2.1,2.1 0 1,0 4.2,0 a2.1,2.1 0 1,0 -4.2,0"),
    fill = SolidColor(Color.Black)
).addPath(
    pathData = addPathNodes("M10.1,10.45 L7.45,8.28 M13.9,10.45 L16.55,8.28 M12,14.45 L12,16.15"),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round
).addPath(
    pathData = addPathNodes("M5.4,6.6 m-2.3,0 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0 M18.6,6.6 m-2.3,0 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0 M12,18.8 m-2.3,0 a2.3,2.3 0 1,0 4.6,0 a2.3,2.3 0 1,0 -4.6,0"),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f
).build()
