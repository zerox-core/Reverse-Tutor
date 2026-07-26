package com.reversetutor.feature.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ReverseTeachingChatScreen(
    state: ChatUiState,
    onComposerTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelQuote: () -> Unit,
    onCreateImageDraft: () -> Unit,
    onCancelImageDraft: () -> Unit,
    onMessageAction: (ChatTimelineItem, ChatMessageAction) -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    evidenceTargetMessageId: String?,
    onOpenSessionSettings: () -> Unit = {},
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    onScrollPositionChanged: (ChatScrollPosition) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMessageId by remember(state.sessionTitle) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition.index,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.offset
    )

    LaunchedEffect(listState) {
        snapshotFlow { ChatScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect(onScrollPositionChanged)
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
            onOpenSettings = onOpenSettings,
            onOpenSessionSettings = onOpenSessionSettings
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "今天 14:30",
                    modifier = Modifier.fillMaxWidth(),
                    color = ChatMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
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
                items(state.messages, key = { it.id }) { item ->
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
                            selected = selectedMessageId == item.id,
                            onSelect = {
                                selectedMessageId = if (selectedMessageId == item.id) null else item.id
                            },
                            onAction = { action ->
                                onMessageAction(item, action)
                                selectedMessageId = null
                            }
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
                        onOpenSettings = onOpenSettings,
                        onRetry = state.messages.lastOrNull { it.role == MessageRole.Assistant }?.let { item ->
                            { onMessageAction(item, ChatMessageAction.Regenerate) }
                        }
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
            state.composer.imageDraft?.let { draft ->
                ComposerPreview(
                    label = draft.displayLabel,
                    onDismiss = onCancelImageDraft
                )
            }
            state.composer.quoteTarget?.let { quote ->
                ComposerPreview(
                    label = "回复：${quote.excerpt}",
                    onDismiss = onCancelQuote
                )
            }
            ReverseTeachingComposer(
                text = state.composer.text,
                canSend = state.composer.canSend,
                onTextChange = onComposerTextChange,
                onAdd = onCreateImageDraft,
                onSend = onSendMessage,
                onFocusChanged = onComposerFocusChanged
            )
        }
    }
}

@Composable
private fun ReverseTeachingChatHeader(
    state: ChatUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessionSettings: () -> Unit
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
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = "会话世界树",
                onClick = onOpenSettings,
                filled = true
            )
            Spacer(Modifier.width(6.dp))
            FormalHeaderIconButton(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "会话设置",
                onClick = onOpenSessionSettings,
                filled = true
            )
        }
    }
}

@Composable
private fun FormalHeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(if (filled) 36.dp else 44.dp)
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
    selected: Boolean,
    onSelect: () -> Unit,
    onAction: (ChatMessageAction) -> Unit
) {
    when (item.role) {
        MessageRole.System -> SystemTimelineMessage(item.text)
        MessageRole.Tool -> EvidenceTimelineMessage(item.text)
        MessageRole.User -> UserTimelineMessage(item, selected, onSelect, onAction)
        MessageRole.Assistant -> LearnerTimelineMessage(
            item,
            learnerName,
            learnerAvatarReference,
            avatarVisible,
            selected,
            onSelect,
            onAction
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserTimelineMessage(
    item: ChatTimelineItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onAction: (ChatMessageAction) -> Unit
) {
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
                    .combinedClickable(onClick = {}, onLongClick = onSelect),
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
                Text(
                    text = item.text,
                    color = Color(0xFF11151D),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                item.attachments.forEach { MessageAttachment(it) }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1A8C61), RoundedCornerShape(2.dp))
            )
        }
        if (selected) MessageActionRow(onAction)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LearnerTimelineMessage(
    item: ChatTimelineItem,
    learnerName: String,
    learnerAvatarReference: LearnerAvatarReference?,
    avatarVisible: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onAction: (ChatMessageAction) -> Unit
) {
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
                    .combinedClickable(onClick = {}, onLongClick = onSelect),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item.quoteLabel?.let { quote ->
                    Text(quote, color = ChatMuted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                Text(
                    text = item.text,
                    color = Color(0xFF333D4D),
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
                item.attachments.forEach { MessageAttachment(it) }
            }
            if (selected) MessageActionRow(onAction)
        }
    }
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
private fun MessageAttachment(attachment: ChatAttachmentUi) {
    if (attachment.isImage) {
        MessageImageAttachment(attachment)
    } else {
        AttachmentReference(attachment)
    }
}

@Composable
private fun MessageImageAttachment(attachment: ChatAttachmentUi) {
    val context = LocalContext.current
    val imageState by produceState<ChatImageLoadState>(
        initialValue = ChatImageLoadState.Loading,
        key1 = attachment.uri
    ) {
        val uri = attachment.uri
        value = if (uri.isNullOrBlank()) {
            ChatImageLoadState.Failed
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()?.let(ChatImageLoadState::Ready) ?: ChatImageLoadState.Failed
            }
        }
    }
    val ratio = (imageState as? ChatImageLoadState.Ready)?.bitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    }?.coerceIn(0.72f, 1.8f) ?: 1.35f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .aspectRatio(ratio),
        color = Color(0xFFE8ECF3),
        contentColor = ChatMuted,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.10f))
    ) {
        when (val current = imageState) {
            ChatImageLoadState.Loading -> Box(contentAlignment = Alignment.Center) {
                Text("正在读取图片…", fontSize = 11.sp)
            }
            ChatImageLoadState.Failed -> Box(contentAlignment = Alignment.Center) {
                Text("图片暂时无法显示 · ${attachment.name}", fontSize = 11.sp)
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
private fun AttachmentReference(attachment: ChatAttachmentUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▤", color = Color(0xFF4D66A6), fontSize = 15.sp)
        Text(
            text = attachment.name,
            modifier = Modifier.weight(1f),
            color = Color(0xFF59647A),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("›", color = ChatMuted, fontSize = 16.sp)
    }
}

private sealed interface ChatImageLoadState {
    data object Loading : ChatImageLoadState
    data object Failed : ChatImageLoadState
    data class Ready(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ChatImageLoadState
}

@Composable
private fun MessageActionRow(onAction: (ChatMessageAction) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ChatMessageAction.entries.forEach { action ->
            TextButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
            ) {
                Text(
                    text = action.label,
                    color = if (action == ChatMessageAction.Delete) Color(0xFFB24B55) else ChatMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
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
private fun ReverseTeachingComposer(
    text: String,
    canSend: Boolean,
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
            .height(48.dp),
        color = Color(0xFFF2F6FC),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFC7D8EA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(32.dp),
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
                    singleLine = true,
                    textStyle = TextStyle(color = ChatInk, fontSize = 12.sp, lineHeight = 18.sp),
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
                onClick = { if (canSend) onSend() },
                modifier = Modifier
                    .size(38.dp)
                    .shadow(4.dp, CircleShape),
                color = Color(0xFF4287E8),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "发送",
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
