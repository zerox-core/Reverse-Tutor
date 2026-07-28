package com.reversetutor.preview.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalGlossyIcon
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.FormalTypography
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.core.domain.ActivityLeaderboardPage
import com.reversetutor.preview.R
import kotlinx.coroutines.flow.distinctUntilChanged

data class ChallengeListPosition(
    val index: Int = 0,
    val offset: Int = 0
)

data class ChallengeReturnContext(
    val activityList: ChallengeListPosition = ChallengeListPosition(),
    val detailList: ChallengeListPosition = ChallengeListPosition(),
    val detailOpen: Boolean = false
)

internal object ChallengeDetailLayout {
    val FooterHeight = 92.dp
    val FooterClearance = 20.dp
    val ContentBottomPadding = 136.dp
    val HeroColors = listOf(Color(0xFFE7F8FF), Color(0xFFF1ECFF))
    val SheetBackground = Color(0xFFF6F7FA)
    val SheetElevation = 0.dp
    val HeroElevation = 0.dp
    val RuleElevation = 0.dp
    val FooterElevation = 0.dp
    const val UsesOutlinedContentCards = false
}

internal fun challengeExitBoundaryAllowed(
    active: Boolean,
    showDetails: Boolean,
    totalItemsCount: Int,
    canScrollForward: Boolean
): Boolean = active && !showDetails && totalItemsCount > 0 && !canScrollForward

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChallengeRoute(
    joined: Boolean,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Int = 12,
    total: Int = 21,
    runtimeState: ChallengeRuntimeState? = null,
    onRetry: () -> Unit = {},
    onExitBoundaryChanged: (Boolean) -> Unit = {},
    active: Boolean = true,
    initialShowDetails: Boolean = false,
    entryGeneration: Long = 0L,
    restoreContext: ChallengeReturnContext? = null,
    onRestoreConsumed: () -> Unit = {},
    onReturnContextChanged: (ChallengeReturnContext) -> Unit = {}
) {
    val initialReturnContext = remember { restoreContext }
    var pendingRestoreContext by remember { mutableStateOf(initialReturnContext) }
    val initialDetailOpen = initialReturnContext?.detailOpen ?: initialShowDetails
    var showDetails by remember { mutableStateOf(initialDetailOpen) }
    var verticalState by remember {
        mutableStateOf(ChallengeVerticalState(detailOpen = initialDetailOpen))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialReturnContext?.activityList?.index ?: 0,
        initialFirstVisibleItemScrollOffset = initialReturnContext?.activityList?.offset ?: 0
    )
    val detailListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialReturnContext?.detailList?.index ?: 0,
        initialFirstVisibleItemScrollOffset = initialReturnContext?.detailList?.offset ?: 0
    )
    val confirmedJoined = runtimeState?.joined ?: joined
    val detailUiState = ChallengeDetailUiState.from(runtimeState, confirmedJoined)

    fun setDetailOpen(open: Boolean) {
        showDetails = open
        verticalState = reduceChallengeVerticalState(
            verticalState,
            if (open) ChallengeVerticalEvent.DetailOpened else ChallengeVerticalEvent.DetailClosed
        )
    }

    LaunchedEffect(entryGeneration) {
        val contextToRestore = pendingRestoreContext
        verticalState = reduceChallengeVerticalState(
            verticalState,
            ChallengeVerticalEvent.Entered
        )
        if (contextToRestore == null) {
            setDetailOpen(initialShowDetails)
            listState.scrollToItem(0)
            detailListState.scrollToItem(0)
        } else {
            setDetailOpen(contextToRestore.detailOpen)
            pendingRestoreContext = null
            onRestoreConsumed()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            ChallengeVerticalEvent.ContentScrollChanged(
                atBottom = listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward,
                gestureActive = listState.isScrollInProgress
            )
        }.distinctUntilChanged().collect { event ->
            verticalState = reduceChallengeVerticalState(verticalState, event)
        }
    }

    LaunchedEffect(verticalState.outerPagerEnabled, active) {
        onExitBoundaryChanged(active && verticalState.outerPagerEnabled)
    }
    LaunchedEffect(listState, detailListState, showDetails) {
        snapshotFlow {
            ChallengeReturnContext(
                activityList = ChallengeListPosition(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                ),
                detailList = ChallengeListPosition(
                    detailListState.firstVisibleItemIndex,
                    detailListState.firstVisibleItemScrollOffset
                ),
                detailOpen = showDetails
            )
        }.distinctUntilChanged().collect(onReturnContextChanged)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag("formal-challenge-716-237")
    ) {
        ChallengeContent(
            joined = confirmedJoined,
            progress = progress,
            total = total,
            leaderboard = runtimeState?.leaderboard,
            detailUiState = detailUiState,
            onBack = onBack,
            onOpenDetails = { setDetailOpen(true) },
            listState = listState,
            listScrollEnabled = verticalState.listScrollEnabled,
            modifier = if (showDetails) Modifier.blur(8.dp) else Modifier
        )
        if (showDetails) {
            ModalBottomSheet(
                onDismissRequest = { setDetailOpen(false) },
                modifier = Modifier.widthIn(max = 390.dp),
                sheetState = sheetState,
                containerColor = ChallengeDetailLayout.SheetBackground,
                contentColor = FormalColors.Ink,
                scrimColor = Color.Black.copy(alpha = .55f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = ChallengeDetailLayout.SheetElevation,
                dragHandle = {
                    BottomSheetDefaults.DragHandle(color = FormalColors.BorderStrong)
                }
            ) {
                ChallengeDetailSheet(
                    state = detailUiState,
                    onClose = { setDetailOpen(false) },
                    onJoin = onJoin,
                    onRetry = onRetry,
                    listState = detailListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 520.dp, max = 650.dp)
                        .testTag("formal-challenge-detail-716-379")
                )
            }
        }
    }
}

@Composable
private fun ChallengeContent(
    joined: Boolean,
    progress: Int,
    total: Int,
    leaderboard: ActivityLeaderboardPage?,
    detailUiState: ChallengeDetailUiState,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    listState: LazyListState,
    listScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    val presentation = ChallengePresentation.from(joined, progress, total)

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = maxWidth.coerceAtMost(390.dp)
        Box(Modifier.width(contentWidth).fillMaxHeight()) {
            LazyColumn(
                state = listState,
                userScrollEnabled = listScrollEnabled,
                modifier = Modifier.fillMaxSize().testTag("challenge-activity-list"),
                contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StatusPill(
                            if (detailUiState.availability == ChallengeAvailability.Available) {
                                "线上活动"
                            } else {
                                "活动状态未确认"
                            },
                            FormalColors.SuccessSoft,
                            FormalColors.Success
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            presentation.syncLabel,
                            if (joined) FormalColors.Ink else FormalColors.Surface,
                            if (joined) Color.White else FormalColors.Muted
                        )
                    }
                }
                item { Spacer(Modifier.height(2.dp)) }
                item {
                    Text("挑战活动", style = type.style(28f, 36f, FontWeight.Bold, FormalColors.Ink))
                    Text("达成活动可发放成就徽章", style = type.style(14f, 21f, color = FormalColors.Muted))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(48.dp).height(4.dp).clip(CircleShape).background(Color(0xFFF0D34F)))
                }
                item { Spacer(Modifier.height(18.dp)) }
                item {
                    Box(Modifier.fillMaxWidth()) {
                        MainChallengeCard(presentation, detailUiState, onOpenDetails)
                        if (presentation.showFeedback) {
                            FeedbackPill(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 14.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(if (presentation.showFeedback) 18.dp else 2.dp)) }
                item {
                    Box(Modifier.testTag("challenge-activity-bottom")) {
                        LeaderboardSection(
                            leaderboard = leaderboard
                        )
                    }
                }
            }
            Surface(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp).size(44.dp),
                color = FormalColors.Surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, FormalColors.Border),
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = FormalColors.Ink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MainChallengeCard(
    presentation: ChallengePresentation,
    detailState: ChallengeDetailUiState,
    onOpenDetails: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth().height(411.dp).testTag("challenge-open-detail"),
        color = FormalColors.SurfaceElevated,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        shadowElevation = 4.dp
    ) {
        Box(Modifier.padding(28.dp)) {
            StatusTag(
                if (detailState.availability == ChallengeAvailability.Available) {
                    presentation.statusLabel
                } else {
                    detailState.stageLabel
                },
                presentation.showPersonalProgress,
                Modifier.align(Alignment.TopStart)
            )
            Text(
                "点击查看挑战详情",
                style = type.style(9f, 13f, color = FormalColors.Muted),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp)
            )
            Column(Modifier.align(Alignment.CenterStart).offset(y = (-52).dp)) {
                Text(detailState.stageLabel, style = type.style(16f, 24f, FontWeight.Bold, FormalColors.Primary))
                Text(
                    detailState.title,
                    style = type.style(19f, 26f, FontWeight.Bold, FormalColors.Ink),
                    modifier = Modifier.width(136.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        detailState.availabilityLabel,
                        style = type.style(12f, 18f, color = FormalColors.Muted),
                        modifier = Modifier.width(136.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ChallengeCourseArt(Modifier.align(Alignment.CenterEnd).offset(y = (-36).dp))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                if (detailState.availability != ChallengeAvailability.Available) {
                    Text("活动状态", style = type.style(11f, 16f, color = FormalColors.Muted))
                    Text(
                        detailState.availabilityLabel,
                        style = type.style(13f, 20f, FontWeight.Medium, FormalColors.Ink),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(presentation.metricTitle, style = type.style(11f, 16f, color = FormalColors.Muted))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(presentation.metricValue, style = type.style(26f, 34f, FontWeight.Bold, FormalColors.Primary))
                        Text(presentation.metricSuffix, style = type.style(13f, 22f, color = FormalColors.Muted), modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (presentation.showPersonalProgress) {
                        ProgressBar(presentation.progressFraction)
                        Spacer(Modifier.height(8.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(17.dp))
                    } else {
                        Text("活动可用后可查看参与要求", style = type.style(10f, 15f, color = FormalColors.Muted))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCourseArt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(142.dp)
            .height(174.dp)
            .clip(RoundedCornerShape(FormalShapes.CardRadius))
            .background(FormalColors.PrimarySoft)
            .padding(15.dp)
    ) {
        Box(Modifier.offset(y = 30.dp).size(16.dp).clip(CircleShape).background(FormalColors.Primary.copy(alpha = .18f)))
        Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(53.dp).height(80.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(FormalColors.Primary.copy(alpha = .18f)))
            Box(Modifier.width(53.dp).height(56.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(FormalColors.Primary.copy(alpha = .45f)))
        }
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-23).dp, y = 12.dp).size(32.dp)
                .clip(CircleShape).background(FormalColors.Primary)
        )
    }
}

@Composable
private fun StatusTag(label: String, joined: Boolean, modifier: Modifier = Modifier) {
    val type = LocalFormalTypeScale.current
    val color = if (joined) FormalColors.Primary else FormalColors.Success
    Surface(modifier = modifier, color = color.copy(alpha = .09f), shape = RoundedCornerShape(FormalShapes.PillRadius)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, style = type.style(10f, 14f, FontWeight.Medium, color))
        }
    }
}

@Composable
private fun StatusPill(label: String, background: Color, foreground: Color) {
    val type = LocalFormalTypeScale.current
    Surface(color = background, shape = RoundedCornerShape(FormalShapes.PillRadius)) {
        Text(label, style = type.style(9f, 13f, FontWeight.Medium, foreground), modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(FormalColors.Primary.copy(alpha = .10f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(FormalColors.Primary))
    }
}

@Composable
private fun FeedbackPill(modifier: Modifier = Modifier) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = modifier,
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.PillRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = FormalColors.Primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("已回传 3 条 Memory · 2 个薄弱节点", style = type.style(10f, 14f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun LeaderboardSection(
    leaderboard: ActivityLeaderboardPage?
) {
    val type = LocalFormalTypeScale.current
    val avatarResources = listOf(
        R.drawable.challenge_avatar_1,
        R.drawable.challenge_avatar_2,
        R.drawable.challenge_avatar_3,
        R.drawable.challenge_avatar_you
    )
    val accents = listOf(
        Color(0xFFF0CA31),
        Color(0xFF8EC9EE),
        Color(0xFFF1B27A),
        FormalColors.Primary
    )
    val rows = leaderboard?.items?.mapIndexed { index, entry ->
        LeaderboardRow(
            rank = entry.rank.toInt(),
            name = entry.displayName,
            days = entry.progress.toInt(),
            avatarRes = avatarResources[index % avatarResources.size],
            accent = accents[index % accents.size],
            highlighted = entry.isCurrentUser
        )
    }.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.SurfaceElevated,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("活动榜单", style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("查看全部", style = type.style(9f, 13f, color = FormalColors.Muted))
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(20.dp))
                }
            }
            rows.forEach { LeaderboardItem(it, highlighted = it.highlighted) }
            if (rows.isEmpty()) {
                Text("活动榜单暂不可用", style = type.style(11f, 17f, color = FormalColors.Muted))
            }
        }
    }
}

private data class LeaderboardRow(
    val rank: Int,
    val name: String,
    val days: Int,
    val avatarRes: Int,
    val accent: Color,
    val highlighted: Boolean = false
)

@Composable
private fun LeaderboardItem(row: LeaderboardRow, highlighted: Boolean = false) {
    val type = LocalFormalTypeScale.current
    Surface(
        color = if (highlighted) FormalColors.PrimarySoft else Color.Transparent,
        shape = RoundedCornerShape(FormalShapes.CompactRadius)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (highlighted) 8.dp else 0.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(20.dp), color = row.accent, shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) {
                    Text(row.rank.toString(), style = type.style(8f, 12f, FontWeight.Bold, Color.White))
                }
            }
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(row.avatarRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(row.name, style = type.style(12f, 18f, FontWeight.Medium, if (highlighted) FormalColors.Primary else FormalColors.Ink), modifier = Modifier.weight(1f))
            Text("${row.days} 天", style = type.style(12f, 18f, FontWeight.Bold, if (highlighted) FormalColors.Primary else FormalColors.Ink))
        }
    }
}

@Composable
private fun ChallengeDetailSheet(
    state: ChallengeDetailUiState,
    onClose: () -> Unit,
    onJoin: () -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Box(modifier.background(ChallengeDetailLayout.SheetBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("challenge-detail-list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 10.dp,
                end = 20.dp,
                bottom = ChallengeDetailLayout.ContentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "挑战详情",
                        style = FormalTypography.sectionTitle(type, FormalColors.Ink),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onClose,
                        modifier = Modifier.size(38.dp),
                        color = Color(0xFFEDEFF3),
                        shape = CircleShape,
                        shadowElevation = ChallengeDetailLayout.HeroElevation
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = FormalColors.Ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item { DetailHero(state.title, state.goal) }
            item { DetailRuleRow("活动阶段", state.stageLabel, Icons.Filled.Schedule) }
            item { DetailRuleRow("挑战目标", state.goal, Icons.Filled.EmojiEvents) }
            item {
                Text(
                    "挑战规则",
                    style = FormalTypography.cardTitle(type, FormalColors.Ink),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            items(state.rules) { rule ->
                val index = state.rules.indexOf(rule)
                val icon = when (index) {
                    0 -> Icons.Filled.CheckCircle
                    1 -> Icons.AutoMirrored.Filled.ListAlt
                    else -> Icons.Filled.Lightbulb
                }
                DetailRuleRow(rule.title, rule.body, icon)
            }
            item { DetailRuleRow("资料来源", state.sourcesLabel, Icons.Filled.Memory) }
            item { DetailRuleRow("参与状态", state.participationLabel, Icons.Filled.Groups) }
            item { DetailRuleRow("可用状态", state.availabilityLabel, Icons.Filled.Schedule) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = ChallengeDetailLayout.SheetBackground.copy(alpha = .98f),
            shadowElevation = ChallengeDetailLayout.FooterElevation
        ) {
            Button(
                enabled = state.joinAction == ChallengeJoinAction.Join ||
                    state.joinAction == ChallengeJoinAction.Retry,
                onClick = if (state.joinAction == ChallengeJoinAction.Retry) onRetry else onJoin,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("challenge-join-action"),
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FormalColors.Primary,
                    disabledContainerColor = FormalColors.Primary.copy(alpha = .45f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val label = when {
                        state.joinAction == ChallengeJoinAction.Joined -> "已加入挑战"
                        state.joinAction == ChallengeJoinAction.Loading -> "加载中"
                        state.joinAction == ChallengeJoinAction.Retry -> "重试"
                        state.joinAction == ChallengeJoinAction.Unavailable -> "暂不可用"
                        else -> "加入挑战"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = FormalTypography.control(type, Color.White))
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(title: String, goal: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(226.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = if (ChallengeDetailLayout.UsesOutlinedContentCards) {
            BorderStroke(1.dp, FormalColors.BorderStrong)
        } else {
            null
        },
        shadowElevation = ChallengeDetailLayout.HeroElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(ChallengeDetailLayout.HeroColors),
                    shape = RoundedCornerShape(FormalShapes.CardRadius)
                )
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = FormalColors.Warning, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = type.style(26f, 34f, FontWeight.Bold, FormalColors.Ink),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                goal,
                style = FormalTypography.metadata(type, FormalColors.Muted),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRuleRow(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.SurfaceElevated,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = if (ChallengeDetailLayout.UsesOutlinedContentCards) {
            BorderStroke(1.dp, FormalColors.BorderStrong)
        } else {
            null
        },
        shadowElevation = ChallengeDetailLayout.RuleElevation
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = FormalTypography.metadata(type, FormalColors.Ink).copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(body, style = type.style(11f, 17f, color = FormalColors.Muted))
            }
            Spacer(Modifier.width(12.dp))
            FormalGlossyIcon(icon, null, size = 34.dp, glyphSize = 18.dp)
        }
    }
}
