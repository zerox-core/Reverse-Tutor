package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.ContentRepository
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.protocol.NativeSessionPresetValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SessionsRoute(
    sessionHomePort: SessionHomePort,
    avatarVisible: Boolean,
    contentRepository: ContentRepository? = null,
    challengeJoined: Boolean = false,
    challengeProgress: Int = 0,
    challengeTotal: Int = 21,
    onOpenSession: (SessionListItem) -> Unit,
    onNewSession: () -> Unit = {},
    onOpenChallenge: () -> Unit = {},
    onOpenPublicContent: (FormalPublicContentUi) -> Unit = {},
    onOpenWeekly: () -> Unit = {},
    showSpatialIndicator: Boolean = true,
    onWorkspaceChromeObscuredChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val homeViewModel = remember(sessionHomePort) {
        SessionHomeViewModel(port = sessionHomePort, scope = scope)
    }
    val homeState by homeViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewSessionSheet by remember { mutableStateOf(false) }
    var publicContent by remember(contentRepository) {
        mutableStateOf(
            if (contentRepository == null) {
                FormalPublicContentUi.offline()
            } else {
                FormalPublicContentUi.loading()
            }
        )
    }

    var sessionActionOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(showNewSessionSheet, homeState.pendingDelete, sessionActionOverlayVisible) {
        onWorkspaceChromeObscuredChanged(
            showNewSessionSheet || homeState.pendingDelete != null || sessionActionOverlayVisible
        )
    }
    DisposableEffect(Unit) {
        onDispose { onWorkspaceChromeObscuredChanged(false) }
    }

    LaunchedEffect(contentRepository) {
        val repository = contentRepository ?: return@LaunchedEffect
        publicContent = try {
            when (val result = repository.feed(limit = 4, types = setOf("public_interest"))) {
                is OnlineData.Content -> result.value.toFormalPublicContentUi()
                    ?: FormalPublicContentUi.unavailable()
                is OnlineData.Failure -> FormalPublicContentUi.unavailable()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FormalPublicContentUi.unavailable()
        }
    }

    val sessionListState = SessionListUiState.from(
        sessions = homeState.sessions,
        query = "",
        filter = SessionListFilter.All,
        avatarVisible = avatarVisible
    )
    val challenge = if (challengeJoined) {
        val safeTotal = challengeTotal.coerceAtLeast(1)
        val safeProgress = challengeProgress.coerceIn(0, safeTotal)
        FormalJoinedChallengeUi(
            id = "challenge-21-days",
            title = "21 天学习挑战",
            dayLabel = "挑战进行中 · 第 ${safeProgress.coerceAtLeast(1)} 天",
            todayPrompt = "今天：用三句话讲清楚机会成本",
            progressFraction = safeProgress.toFloat() / safeTotal.toFloat()
        )
    } else {
        null
    }

    LaunchedEffect(homeState.undo?.session?.id) {
        val undo = homeState.undo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已删除“${undo.session.title}”",
            actionLabel = "撤销",
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) homeViewModel.undoDelete()
    }

    Box(modifier = modifier.fillMaxSize()) {
        FormalHomeScreen(
            state = sessionListState.toFormalHomeUiState(
                publicContent = publicContent,
                challenge = challenge,
                isNewSessionSheetVisible = showNewSessionSheet,
                sessionSurfaceState = homeState.surfaceState,
                sessionErrorMessage = homeState.errorMessage
            ),
            onPublicContentClick = onOpenPublicContent,
            onSessionClick = { formalSession ->
                sessionListState.visibleSessions
                    .firstOrNull { it.id == formalSession.id }
                    ?.let(onOpenSession)
            },
            onRenameSession = homeViewModel::rename,
            onTogglePinned = homeViewModel::togglePinned,
            onRequestDelete = homeViewModel::requestDelete,
            onRetrySessions = homeViewModel::refresh,
            onActionOverlayChanged = { sessionActionOverlayVisible = it },
            onOpenChallenge = onOpenChallenge,
            onShowNewSessionSheet = { showNewSessionSheet = true },
            onDismissNewSessionSheet = { showNewSessionSheet = false },
            onStartLearningSetup = {
                showNewSessionSheet = false
                onNewSession()
            },
            onOpenWeekly = onOpenWeekly,
            showSpatialIndicator = showSpatialIndicator
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    homeState.pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = homeViewModel::dismissDelete,
            title = { Text("删除“${session.title}”？") },
            text = {
                Text("将删除此会话的消息、设置和学习进度。共享资料与收藏不会被删除。")
            },
            confirmButton = {
                TextButton(onClick = homeViewModel::confirmDelete) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = homeViewModel::dismissDelete) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SessionsScreen(
    state: SessionListUiState,
    challengeJoined: Boolean = false,
    onOpenSession: (SessionListItem) -> Unit,
    onNewSession: () -> Unit,
    onOpenChallenge: () -> Unit,
    onOpenMenu: () -> Unit,
    onRenameSession: (SessionListItem) -> Unit,
    onTogglePinned: (SessionListItem) -> Unit,
    onDeleteSession: (SessionListItem) -> Unit,
    onExportSession: (SessionListItem) -> Unit,
    onAvatarSession: (SessionListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstSession = state.visibleSessions.firstOrNull()
    val secondSession = state.visibleSessions.drop(1).firstOrNull()
    val thirdSession = state.visibleSessions.drop(2).firstOrNull()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        val cardWidth = (maxWidth - 32.dp).coerceAtMost(358.dp)
        val cardStart = (maxWidth - cardWidth) / 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(Color(0xB8F4F6FB))
        )
        Box(
            modifier = Modifier
                .offset(y = 54.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x8CDCE2EE))
        )
        HomeTopBar(
            onOpenChallenge = onOpenChallenge,
            onNewSession = onNewSession,
            onOpenMenu = onOpenMenu
        )
        if (challengeJoined) {
            Text(
                text = "最近学习",
                color = HomeInk,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 16.dp, y = 86.dp)
            )
            JoinedChallengeSessionCard(
                onOpenChallenge = onOpenChallenge,
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 122.dp)
            )
            FigmaSessionCard(
                title = firstSession?.title ?: "宏观经济学基础",
                time = "10 分钟前",
                body = firstSession?.statusLabel ?: "复习了 GDP、国内生产总值和供需关系。",
                onOpen = { (firstSession ?: secondSession ?: thirdSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 226.dp)
            )
            FigmaSessionCard(
                title = secondSession?.title ?: "英语写作专场：议论文结构",
                time = "昨天",
                body = secondSession?.statusLabel ?: "重点讨论 thesis statement 和反例段落。",
                onOpen = { (secondSession ?: firstSession ?: thirdSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 308.dp)
            )
            FigmaSessionCard(
                title = thirdSession?.title ?: "机器学习入门",
                time = "周二",
                body = thirdSession?.statusLabel ?: "整理过拟合、正则化和交叉验证的对比。",
                onOpen = { (thirdSession ?: secondSession ?: firstSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 390.dp)
            )
        } else {
            FigmaContinueCard(
                title = firstSession?.title ?: "宏观经济学基础",
                body = firstSession?.statusLabel ?: "上次停在 GDP 与财政政策推演，可以直接接着问。",
                onOpen = {
                    firstSession?.let(onOpenSession)
                },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 84.dp)
            )
            Text(
                text = "最近学习",
                color = HomeInk,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 16.dp, y = 188.dp)
            )
            FigmaSessionCard(
                title = firstSession?.title ?: "宏观经济学基础",
                time = "10 分钟前",
                body = firstSession?.statusLabel ?: "复习了 GDP、国内生产总值和供需关系。",
                onOpen = { (firstSession ?: secondSession ?: thirdSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 224.dp)
            )
            FigmaSessionCard(
                title = secondSession?.title ?: "英语写作专场：议论文结构",
                time = "昨天",
                body = secondSession?.statusLabel ?: "重点讨论 thesis statement 和反例段落。",
                onOpen = { (secondSession ?: firstSession ?: thirdSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 306.dp)
            )
            FigmaSessionCard(
                title = thirdSession?.title ?: "机器学习入门",
                time = "周二",
                body = thirdSession?.statusLabel ?: "整理过拟合、正则化和交叉验证的对比。",
                onOpen = { (thirdSession ?: secondSession ?: firstSession)?.let(onOpenSession) },
                cardWidth = cardWidth,
                modifier = Modifier.offset(x = cardStart, y = 388.dp)
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    onOpenChallenge: () -> Unit,
    onNewSession: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = 4.dp)
                .size(44.dp)
                .clickable(onClick = onOpenMenu),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(15.dp),
                contentAlignment = Alignment.Center
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .offset(y = ((index - 1) * 5).dp)
                            .width(16.dp)
                            .height(2.dp)
                            .background(Color(0xFF1F2737), RoundedCornerShape(999.dp))
                    )
                }
            }
        }
        Text(
            text = "会话",
            color = Color(0xFF1F2737),
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 48.dp, y = 8.dp)
        )
        Text(
            text = "会话列表 · 本地优先",
            color = Color(0xFF768093),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.offset(x = 48.dp, y = 33.dp)
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 13.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomePillButton(
                label = "挑战",
                onClick = onOpenChallenge,
                modifier = Modifier,
                width = 48.dp,
                container = Color(0xFFF7F8FF),
                content = Color(0xFF4F55D7),
                border = Color(0xFFDDE2F0),
                shadow = false
            )
            HomePillButton(
                label = "新建",
                onClick = onNewSession,
                modifier = Modifier,
                width = 46.dp,
                container = PrimaryPurple,
                content = Color.White,
                border = Color.Transparent,
                shadow = true
            )
        }
    }
}

@Composable
private fun HomePillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    width: androidx.compose.ui.unit.Dp,
    container: Color,
    content: Color,
    border: Color,
    shadow: Boolean
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(30.dp)
            .then(
                if (shadow) {
                    Modifier.shadow(7.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
                } else {
                    Modifier
                }
            ),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FigmaContinueCard(
    title: String,
    body: String,
    onOpen: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .then(modifier)
            .width(cardWidth)
            .height(78.dp)
            .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)),
        color = CardWhite,
        contentColor = HomeInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 13.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "继续：$title",
                    color = HomeInk,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = body,
                    color = HomeBody,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                onClick = onOpen,
                modifier = Modifier
                    .width(52.dp)
                    .height(32.dp)
                    .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)),
                color = PrimaryPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("继续", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FigmaSessionCard(
    title: String,
    time: String,
    body: String,
    onOpen: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        modifier = modifier
            .width(cardWidth)
            .height(70.dp)
            .shadow(9.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)),
        color = CardWhite,
        contentColor = HomeInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = HomeInk,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = time,
                    color = Color(0xFF768093),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Right,
                    maxLines = 1
                )
            }
            Text(
                text = body,
                color = HomeBody,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JoinedChallengeSessionCard(
    onOpenChallenge: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenChallenge,
        modifier = modifier
            .width(cardWidth)
            .height(88.dp)
            .shadow(9.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)),
        color = CardWhite,
        contentColor = HomeInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val trailingWidth = 118.dp
            Text(
                text = "21天 Python 学习挑战",
                color = HomeInk,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = 13.dp, y = 11.dp)
                    .width((maxWidth - trailingWidth - 13.dp).coerceAtLeast(96.dp))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 60.dp)
                    .width(58.dp)
                    .height(24.dp),
                color = Color(0xFFF0F3FF),
                contentColor = Color(0xFF5057D8),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFCCD4FF))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("挑战中", fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(
                text = "◷",
                color = Color(0xFF667085),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 13.dp, end = 48.dp)
            )
            Text(
                text = "剩 15 天",
                color = Color(0xFF667085),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 13.dp)
            )
            Text(
                text = "今日任务：完成 Python 基础练习，并回传学习数据。",
                color = HomeBody,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .offset(x = 13.dp, y = 41.dp)
                    .width((maxWidth - 26.dp).coerceAtLeast(120.dp))
            )
            Box(
                modifier = Modifier
                    .offset(x = 13.dp, y = 67.dp)
                    .width((maxWidth - 95.dp).coerceAtLeast(120.dp))
                    .height(4.dp)
                    .background(Color(0xFFE3E7F4), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .offset(x = 13.dp, y = 67.dp)
                    .width(((maxWidth - 95.dp) * 0.58f).coerceAtLeast(72.dp))
                    .height(4.dp)
                    .background(Color(0xFF5D63E8), RoundedCornerShape(2.dp))
            )
            Text(
                text = "12/21",
                color = Color(0xFF5057D8),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Right,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 13.dp)
            )
        }
    }
}

private val HomeBackground = Color(0xFFEFF2F8)
private val HomeInk = Color(0xFF20283A)
private val HomeBody = Color(0xFF697184)
private val CardWhite = Color(0xEBFFFFFF)
private val CardBorder = Color(0xE6D7DEEA)
private val PrimaryPurple = Color(0xFF575CE6)

private enum class NewSessionStep {
    Template,
    Custom
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NewSessionRoute(
    sessionRepository: SessionRepository,
    onCreated: (SessionListItem) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(NewSessionStep.Template) }
    var draft by remember {
        mutableStateOf(NewSessionDraft.fromTemplate(BuiltInSessionTemplates.all.first()))
    }
    var presetJson by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var imageTextRatio by remember { mutableStateOf(0.35f) }
    var difficulty by remember { mutableStateOf(0.45f) }
    var followUpStrength by remember { mutableStateOf(0.7f) }
    var selectedTags by remember { mutableStateOf(setOf("拆解", "复盘")) }

    fun createCurrentDraft() {
        val enrichedDraft = draft.copy(
            profileText = buildString {
                append(draft.profileText.trim())
                appendLine()
                append("图文比例：")
                append("${(imageTextRatio * 10).toInt()}:${(10 - imageTextRatio * 10).toInt()}")
                appendLine()
                append("难度：")
                append((difficulty * 100).toInt())
                append("%；追问强度：")
                append((followUpStrength * 100).toInt())
                append("%。")
                if (selectedTags.isNotEmpty()) {
                    appendLine()
                    append("标签：")
                    append(selectedTags.joinToString("、"))
                }
            }
        )
        if (enrichedDraft.validationErrors().isNotEmpty()) {
            errorText = enrichedDraft.validationErrors().joinToString("\n")
            return
        }
        scope.launch {
            val created = sessionRepository.createSession(
                input = enrichedDraft.toCreationInput(),
                nowEpochMillis = System.currentTimeMillis()
            )
            onCreated(created.session.toSessionListItem(avatarVisible = true))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("返回")
            }
            Text(
                text = "新建会话",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepChip(
                label = "模板/导入",
                selected = step == NewSessionStep.Template,
                onClick = { step = NewSessionStep.Template }
            )
            StepChip(
                label = "自定义",
                selected = step == NewSessionStep.Custom,
                onClick = { step = NewSessionStep.Custom }
            )
        }
        if (step == NewSessionStep.Template) {
            Text(
                text = "选择一个起点",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BuiltInSessionTemplates.all.forEach { template ->
                    TemplateCard(
                        template = template,
                        selected = draft.templateId == template.id,
                        onClick = {
                            draft = NewSessionDraft.fromTemplate(template)
                            errorText = null
                        }
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "导入模板",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = presetJson,
                        onValueChange = {
                            presetJson = it
                            errorText = null
                        },
                        minLines = 4,
                        label = { Text("粘贴预设 JSON") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = presetJson.isNotBlank(),
                            onClick = {
                                val result = NativeSessionPresetValidator.validate(presetJson)
                                val preset = result.preset
                                if (result.isValid && preset != null) {
                                    draft = NewSessionDraft.fromPreset(preset)
                                    step = NewSessionStep.Custom
                                } else {
                                    errorText = result.errors.joinToString("\n")
                                }
                            }
                        ) {
                            Text("读取模板")
                        }
                        TextButton(onClick = { step = NewSessionStep.Custom }) {
                            Text("进入自定义")
                        }
                    }
                }
            }
            Button(
                onClick = { createCurrentDraft() },
                enabled = draft.validationErrors().isEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("用当前模板创建")
            }
        } else {
            CustomSessionPanel(
                draft = draft,
                onDraftChange = {
                    draft = it
                    errorText = null
                },
                imageTextRatio = imageTextRatio,
                onImageTextRatioChange = { imageTextRatio = it },
                difficulty = difficulty,
                onDifficultyChange = { difficulty = it },
                followUpStrength = followUpStrength,
                onFollowUpStrengthChange = { followUpStrength = it },
                selectedTags = selectedTags,
                onToggleTag = { tag ->
                    selectedTags = if (tag in selectedTags) {
                        selectedTags - tag
                    } else {
                        selectedTags + tag
                    }
                }
            )
            Button(
                onClick = { createCurrentDraft() },
                enabled = draft.validationErrors().isEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("创建并进入会话")
            }
        }
        val currentError = errorText
        if (currentError != null) {
            Text(
                text = currentError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TemplateCard(
    template: NewSessionTemplate,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (selected) 3.dp else 1.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = template.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = template.goal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CustomSessionPanel(
    draft: NewSessionDraft,
    onDraftChange: (NewSessionDraft) -> Unit,
    imageTextRatio: Float,
    onImageTextRatioChange: (Float) -> Unit,
    difficulty: Float,
    onDifficultyChange: (Float) -> Unit,
    followUpStrength: Float,
    onFollowUpStrengthChange: (Float) -> Unit,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it)) },
            singleLine = true,
            label = { Text("会话名称") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.role,
            onValueChange = { onDraftChange(draft.copy(role = it)) },
            singleLine = true,
            label = { Text("人格/角色") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.goal,
            onValueChange = { onDraftChange(draft.copy(goal = it)) },
            singleLine = true,
            label = { Text("学习目标") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.profileText,
            onValueChange = { onDraftChange(draft.copy(profileText = it)) },
            minLines = 4,
            label = { Text("学生画像与偏好") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = draft.sourceHandoffRequested,
                onCheckedChange = { checked ->
                    onDraftChange(draft.copy(sourceHandoffRequested = checked))
                }
            )
            Text(
                text = "创建后补充文件知识库",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        SliderSetting(
            label = "图文比例",
            valueLabel = "${(imageTextRatio * 10).toInt()}:${(10 - imageTextRatio * 10).toInt()}",
            value = imageTextRatio,
            onValueChange = onImageTextRatioChange
        )
        SliderSetting(
            label = "难度",
            valueLabel = "${(difficulty * 100).toInt()}%",
            value = difficulty,
            onValueChange = onDifficultyChange
        )
        SliderSetting(
            label = "追问强度",
            valueLabel = "${(followUpStrength * 100).toInt()}%",
            value = followUpStrength,
            onValueChange = onFollowUpStrengthChange
        )
        Text(
            text = "能力标签",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("拆解", "复盘", "严格纠错", "例题", "项目制", "口语").forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag) }
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = valueLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (NewSessionDraft) -> Unit
) {
    var draft by remember {
        mutableStateOf(
            NewSessionDraft(
                title = "",
                role = "",
                goal = "",
                profileText = ""
            )
        )
    }
    var presetJson by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "模板",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BuiltInSessionTemplates.all.forEach { template ->
                        TextButton(
                            onClick = {
                                draft = NewSessionDraft.fromTemplate(template)
                                errorText = null
                            }
                        ) {
                            Text(template.title)
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    singleLine = true,
                    label = { Text("会话名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.role,
                    onValueChange = { draft = draft.copy(role = it) },
                    singleLine = true,
                    label = { Text("角色") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.goal,
                    onValueChange = { draft = draft.copy(goal = it) },
                    singleLine = true,
                    label = { Text("目标") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.profileText,
                    onValueChange = { draft = draft.copy(profileText = it) },
                    minLines = 3,
                    label = { Text("学生画像") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = draft.sourceHandoffRequested,
                        onCheckedChange = { checked ->
                            draft = draft.copy(sourceHandoffRequested = checked)
                        }
                    )
                    Text(
                        text = "创建后准备导入资料",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                OutlinedTextField(
                    value = presetJson,
                    onValueChange = { presetJson = it },
                    minLines = 3,
                    label = { Text("预设 JSON") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = presetJson.isNotBlank(),
                    onClick = {
                        val result = NativeSessionPresetValidator.validate(presetJson)
                        val preset = result.preset
                        if (result.isValid && preset != null) {
                            onCreate(NewSessionDraft.fromPreset(preset))
                        } else {
                            errorText = result.errors.joinToString("\n")
                        }
                    }
                ) {
                    Text("从预设创建")
                }
                val currentError = errorText
                if (currentError != null) {
                    Text(
                        text = currentError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.validationErrors().isEmpty(),
                onClick = { onCreate(draft) }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionFilterChips(
    selected: SessionListFilter,
    onFilterChange: (SessionListFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == SessionListFilter.All,
            onClick = { onFilterChange(SessionListFilter.All) },
            label = { Text("全部") }
        )
        FilterChip(
            selected = selected == SessionListFilter.Pinned,
            onClick = { onFilterChange(SessionListFilter.Pinned) },
            label = { Text("置顶") }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionCard(
    item: SessionListItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onAvatar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.statusLabel} · ${item.unreadLabel} · ${item.avatarLabel}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                if (item.pinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "置顶",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = onOpen) {
                    Text("打开")
                }
                TextButton(onClick = onRename) {
                    Text("重命名")
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (item.pinned) "取消置顶" else "置顶")
                }
                TextButton(onClick = onAvatar) {
                    Text("头像")
                }
                TextButton(onClick = onExport) {
                    Text("导出")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun EmptySessions(
    title: String,
    onNewSession: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Button(onClick = onNewSession) {
                Text("新建会话")
            }
        }
    }
}

@Composable
private fun RenameSessionDialog(
    item: SessionListItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("会话名称") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onConfirm(title) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
