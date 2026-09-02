package com.reversetutor.feature.chat

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
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
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged

internal object ChatComposerLayout {
    val Height = 54.dp
    val SendSize = 40.dp
    val TrailingInset = 7.dp
}

enum class ChatOverflowAction(val label: String) {
    SessionSettings("会话设置"),
    Sources("资料库"),
    Export("导出会话")
}

@Composable
internal fun ReverseTeachingChatScreen(
    state: ChatUiState,
    sessionContract: SessionConversationContract? = null,
    onAssistantInteraction: (SessionAssistantInteraction) -> Unit = {},
    onComposerTextChange: (String) -> Unit,
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
    onOpenContextHub: () -> Unit,
    onOpenWindowBranches: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onExport: () -> Unit = {},
    onBack: () -> Unit,
    evidenceTargetMessageId: String?,
    availableSourceAttachments: List<ChatDraftAttachment> = emptyList(),
    cameraPermissionState: ChatPermissionState = ChatPermissionState.Requestable,
    onOpenSearch: () -> Unit = {},
    onPickImages: () -> Unit = onCreateImageDraft,
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
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    onScrollPositionChanged: (ChatScrollPosition) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMessageId by remember(state.sessionTitle) { mutableStateOf<String?>(null) }
    var actionMessage by remember(state.sessionTitle) { mutableStateOf<ChatTimelineItem?>(null) }
    var locateSourceMessage by remember(state.sessionTitle) { mutableStateOf<ChatTimelineItem?>(null) }
    var viewerAttachment by remember(state.sessionTitle) { mutableStateOf<ChatAttachmentUi?>(null) }
    var showAttachmentActions by remember(state.sessionTitle) { mutableStateOf(false) }
    var showSourcePicker by remember(state.sessionTitle) { mutableStateOf(false) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.index,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.offset
    )

    LaunchedEffect(listState) {
        snapshotFlow { ChatScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect(onScrollPositionChanged)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChatPageBackground)
            .imePadding()
    ) {
        ReverseTeachingChatHeader(
            state = state,
            onBack = onBack,
            onOpenSettings = onOpenContextHub,
            onOpenWindowBranches = onOpenWindowBranches,
            onOpenSearch = onOpenSearch,
            onOpenSessionSettings = onOpenSessionSettings,
            onOverflowAction = { action ->
                when (action) {
                    ChatOverflowAction.SessionSettings -> onOpenSessionSettings()
                    ChatOverflowAction.Sources -> onOpenSources()
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
                    val timelineSpacing = when (item.role) {
                        MessageRole.Assistant -> Modifier.padding(bottom = 28.dp)
                        MessageRole.User -> Modifier.heightIn(min = 63.dp)
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
                            onCopyRichSource = onCopyRichSource
                        )
                    }
                }
            }
            state.generationStatusLabel?.let { label ->
                item {
                    GenerationRow(
                        learnerName = state.learnerName,
                        learnerAvatarReference = state.learnerAvatarReference,
                        avatarVisible = state.avatarVisible,
                        label = label,
                        onOpenSettings = onOpenModelSettings,
                        onRetry = null
                    )
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
            ReverseTeachingComposer(
                text = state.composer.text,
                canSend = state.composer.canSend,
                isSending = state.composer.isSending,
                onTextChange = onComposerTextChange,
                onAdd = { showAttachmentActions = true },
                onSend = onSendMessage,
                onFocusChanged = onComposerFocusChanged
            )
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

    if (showAttachmentActions) {
        ChatAttachmentActionSheet(
            permissionState = cameraPermissionState,
            onDismiss = { showAttachmentActions = false },
            onPickImages = {
                showAttachmentActions = false
                onPickImages()
            },
            onPickSource = {
                showAttachmentActions = false
                showSourcePicker = true
            },
            onTakePhoto = {
                showAttachmentActions = false
                when (cameraPermissionState) {
                    ChatPermissionState.Granted -> onTakePhoto()
                    ChatPermissionState.PermanentlyDenied -> onOpenCameraSettings()
                    ChatPermissionState.Requestable,
                    ChatPermissionState.Denied -> onRequestCameraPermission()
                }
            },
            onOpenSessionSources = {
                showAttachmentActions = false
                onOpenSessionSources()
            }
        )
    }
    if (showSourcePicker) {
        ChatSourcePickerSheet(
            sources = availableSourceAttachments,
            onDismiss = { showSourcePicker = false },
            onSelect = {
                showSourcePicker = false
                onSelectSource(it)
            }
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
    onOpenSettings: () -> Unit,
    onOpenWindowBranches: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSessionSettings: () -> Unit,
    onOverflowAction: (ChatOverflowAction) -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }
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
                        Text(
                            text = "${state.learnerName} · ${state.learnerStatus}",
                            color = ChatMuted,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            FormalHeaderIconButton(
                imageVector = Icons.Rounded.Search,
                contentDescription = "资料与引用",
                onClick = onOpenSearch,
                filled = true
            )
            Spacer(Modifier.width(6.dp))
            FormalHeaderIconButton(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = "当前会话图谱",
                onClick = onOpenSettings,
                filled = true
            )
            Spacer(Modifier.width(6.dp))
            FormalHeaderIconButton(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = "管理分支",
                onClick = onOpenWindowBranches,
                filled = false,
                modifier = Modifier.testTag("chat-window-branches")
            )
            Spacer(Modifier.width(6.dp))
            Box {
                FormalHeaderIconButton(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多会话操作",
                    onClick = { overflowExpanded = true },
                    filled = false
                )
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    ChatOverflowAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                overflowExpanded = false
                                onOverflowAction(action)
                            }
                        )
                    }
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
        modifier = modifier
            .size(44.dp)
            .then(
                if (filled) {
                    Modifier.shadow(
                        3.dp,
                        CircleShape,
                        ambientColor = Color(0x12000000),
                        spotColor = Color(0x12000000)
                    )
                } else {
                    Modifier
                }
            ),
        color = if (filled) Color(0xFFEEF5FC) else Color.Transparent,
        contentColor = Color(0xFF395575),
        shape = CircleShape,
        border = if (filled) BorderStroke(1.dp, Color(0xFFCAD9EA)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(if (filled) 18.dp else 24.dp)
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
    onCopyRichSource: (String) -> ChatClipboardResult
) {
    when (item.role) {
        MessageRole.System -> SystemTimelineMessage(item.text)
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
            onCopyRichSource
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
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
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
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item.quoteLabel?.let { quote ->
                    Text(
                        text = quote,
                        color = ChatMuted,
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
                    onCopySource = onCopyRichSource
                )
                item.attachments.forEach {
                    MessageAttachment(it, sources, onOpenImage, onOpenSource, onReselectInvalidSource)
                }
                if (item.inheritedReadOnly) InheritedMessageLabel()
                if (item.remembered) RememberedMessageLabel()
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1A8C61), RoundedCornerShape(2.dp))
            )
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
    onCopyRichSource: (String) -> ChatClipboardResult
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
            ChatImageLoadState.Failed
        } else {
            withContext(Dispatchers.IO) {
                runCatching { ChatImageLoader.load(context, Uri.parse(uri), ChatImageTargets.Thumbnail) }
                    .getOrNull()
                    ?.let(ChatImageLoadState::Ready)
                    ?: ChatImageLoadState.Failed
            }
        }
    }
    val ratio = (imageState as? ChatImageLoadState.Ready)?.bitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    }?.coerceIn(0.72f, 1.8f) ?: 1.35f

    Surface(
        onClick = { if (imageState is ChatImageLoadState.Ready) onOpen(attachment) },
        modifier = if (imageState is ChatImageLoadState.Ready) {
            Modifier.fillMaxWidth().heightIn(max = 220.dp).aspectRatio(ratio)
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
            ChatImageLoadState.Failed -> Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图片暂时无法显示 · ${attachment.name}", modifier = Modifier.weight(1f), fontSize = 11.sp)
                TextButton(onClick = { retryKey += 1 }) { Text("重试") }
            }
            is ChatImageLoadState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
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
    data object Failed : ChatImageLoadState
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
    onCopySource: (String) -> ChatClipboardResult
) {
    val blocks = remember(source) { ChatRichContentParser.parse(source) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (userAligned) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatRichBlock.Heading -> Text(
                    block.text,
                    color = ChatInk,
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp },
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                is ChatRichBlock.Paragraph -> RichInlineRow(
                    inlines = block.inlines,
                    onOpenExternalLink = onOpenExternalLink,
                    onMessageTap = onMessageTap,
                    onMessageLongPress = onMessageLongPress
                )
                is ChatRichBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (block.ordered) "${block.index ?: 1}." else "•", color = ChatMuted, fontSize = 16.sp)
                    Text(block.text, modifier = Modifier.weight(1f), color = ChatInk, fontSize = 16.sp, lineHeight = 24.sp)
                }
                is ChatRichBlock.Quote -> Text(
                    block.text,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE9EDF4), RoundedCornerShape(4.dp)).padding(8.dp),
                    color = Color(0xFF4C586B),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                is ChatRichBlock.Code -> RichSourceBlock(
                    label = block.language?.let { "代码 · $it" } ?: "代码",
                    source = block.source,
                    onCopySource = onCopySource
                )
                is ChatRichBlock.Formula -> RichSourceBlock("公式", block.source, onCopySource)
                is ChatRichBlock.Table -> RichTable(block)
                is ChatRichBlock.PlainText -> Text(block.source, color = ChatInk, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun RichInlineRow(
    inlines: List<ChatRichInline>,
    onOpenExternalLink: (String) -> Unit,
    onMessageTap: () -> Unit,
    onMessageLongPress: () -> Unit
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
        color = ChatInk,
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
            is ChatRichInline.Code -> withStyle(
                SpanStyle(
                    color = Color(0xFF33415B),
                    background = Color(0xFFE8EBF1),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            ) { append(inline.source) }
            is ChatRichInline.Formula -> withStyle(
                SpanStyle(color = Color(0xFF334D7A), fontWeight = FontWeight.Medium)
            ) { append(inline.source) }
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
                source,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                color = Color(0xFFF1F4F8),
                fontSize = 14.sp,
                lineHeight = 21.sp,
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
            runCatching { ChatImageLoader.load(context, Uri.parse(attachment.uri), viewerTarget) }
                .getOrNull()
                ?.let(ChatImageLoadState::Ready)
                ?: ChatImageLoadState.Failed
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
            ChatImageLoadState.Failed -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("图片暂时无法显示", color = Color.White)
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
        Text(
            text = "$learnerName · $label",
            modifier = Modifier.weight(1f),
            color = Color(0xFF5D6C86),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        when {
            label.startsWith("未配置模型") -> TextButton(onClick = onOpenSettings) {
                Text("去设置", color = Color(0xFF4264C7), fontSize = 11.sp)
            }
            label.startsWith("生成失败") && onRetry != null -> TextButton(onClick = onRetry) {
                Text("重试", color = Color(0xFF4264C7), fontSize = 11.sp)
            }
            else -> Text("•••", color = Color(0xFF6077B3), fontSize = 12.sp)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatAttachmentActionSheet(
    permissionState: ChatPermissionState,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickSource: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenSessionSources: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "添加到消息",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        AttachmentSheetAction(Icons.Rounded.Collections, "选择图片", null, onPickImages)
        AttachmentSheetAction(Icons.Rounded.Description, "选择应用内资料", null, onPickSource)
        AttachmentSheetAction(
            Icons.Rounded.CameraAlt,
            "拍照",
            when (permissionState) {
                ChatPermissionState.Denied -> "相机权限被拒绝，可重新授权"
                ChatPermissionState.PermanentlyDenied -> "相机权限已关闭，前往系统设置"
                else -> null
            },
            onTakePhoto
        )
        AttachmentSheetAction(Icons.Rounded.Description, "查看本会话资料", null, onOpenSessionSources)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = ChatMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatSourcePickerSheet(
    sources: List<ChatDraftAttachment>,
    onDismiss: () -> Unit,
    onSelect: (ChatDraftAttachment) -> Unit
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReverseTeachingComposer(
    text: String,
    canSend: Boolean,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
            .heightIn(min = ChatComposerLayout.Height, max = 136.dp),
        color = Color(0xFFF2F6FC),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFC7D8EA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = ChatComposerLayout.TrailingInset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(44.dp),
                color = Color.Transparent,
                contentColor = Color(0xFF577394),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "添加图片或资料", modifier = Modifier.size(19.dp))
                }
            }
            Box(modifier = Modifier.weight(1f)) {
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
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Surface(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(ChatComposerLayout.SendSize)
                    .shadow(4.dp, CircleShape),
                color = if (canSend) Color(0xFF4287E8) else Color(0xFFAFB9C8),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = if (isSending) "正在发送" else "发送",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

private val ChatPageBackground = Color(0xFFF4F7FC)
private val ChatInk = Color(0xFF171C27)
private val ChatMuted = Color(0xFF6D778C)
