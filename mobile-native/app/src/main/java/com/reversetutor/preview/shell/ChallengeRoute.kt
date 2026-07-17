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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalGlossyIcon
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.preview.R
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChallengeRoute(
    joined: Boolean,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Int = 12,
    total: Int = 21,
    onExitBoundaryChanged: (Boolean) -> Unit = {},
    initialShowDetails: Boolean = false
) {
    var showDetails by remember(initialShowDetails) { mutableStateOf(initialShowDetails) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    LaunchedEffect(listState, showDetails) {
        snapshotFlow {
            !showDetails && listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward
        }.distinctUntilChanged().collect(onExitBoundaryChanged)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag("formal-challenge-716-237")
    ) {
        ChallengeContent(
            joined = joined,
            progress = progress,
            total = total,
            onBack = onBack,
            onOpenDetails = { showDetails = true },
            listState = listState,
            modifier = if (showDetails) Modifier.blur(8.dp) else Modifier
        )
        if (showDetails) {
            ModalBottomSheet(
                onDismissRequest = { showDetails = false },
                modifier = Modifier.widthIn(max = 390.dp),
                sheetState = sheetState,
                containerColor = FormalColors.Background,
                contentColor = FormalColors.Ink,
                scrimColor = Color.Black.copy(alpha = .55f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = null
            ) {
                ChallengeDetailSheet(
                    joined = joined,
                    onClose = { showDetails = false },
                    onJoin = {
                        showDetails = false
                        onJoin()
                    },
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
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    val presentation = ChallengePresentation.from(joined, progress, total)

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = maxWidth.coerceAtMost(390.dp)
        Box(Modifier.width(contentWidth).fillMaxHeight()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StatusPill("线上活动", FormalColors.SuccessSoft, FormalColors.Success)
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
                        MainChallengeCard(presentation, onOpenDetails)
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
                item { LeaderboardSection(showCurrentUser = joined) }
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
private fun MainChallengeCard(presentation: ChallengePresentation, onOpenDetails: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth().height(411.dp),
        color = FormalColors.SurfaceElevated,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        shadowElevation = 4.dp
    ) {
        Box(Modifier.padding(28.dp)) {
            StatusTag(presentation.statusLabel, presentation.showPersonalProgress, Modifier.align(Alignment.TopStart))
            Text(
                "点击查看挑战详情",
                style = type.style(9f, 13f, color = FormalColors.Muted),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp)
            )
            Column(Modifier.align(Alignment.CenterStart).offset(y = (-52).dp)) {
                Text("21天", style = type.style(32f, 40f, FontWeight.Bold, FormalColors.Ink))
                Text("Python\n学习挑战", style = type.style(19f, 26f, FontWeight.Bold, FormalColors.Ink))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(presentation.metaLabel, style = type.style(12f, 18f, color = FormalColors.Muted))
                }
            }
            ChallengeCourseArt(Modifier.align(Alignment.CenterEnd).offset(y = (-36).dp))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
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
                    Text("每天约 25 分钟 · 支持离线完成", style = type.style(10f, 15f, color = FormalColors.Muted))
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
private fun LeaderboardSection(showCurrentUser: Boolean) {
    val type = LocalFormalTypeScale.current
    val rows = listOf(
        LeaderboardRow(1, "小宇同学", 18, R.drawable.challenge_avatar_1, Color(0xFFF0CA31)),
        LeaderboardRow(2, "编程小能手", 16, R.drawable.challenge_avatar_2, Color(0xFF8EC9EE)),
        LeaderboardRow(3, "算法不秃头", 14, R.drawable.challenge_avatar_3, Color(0xFFF1B27A))
    )
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
            rows.forEach { LeaderboardItem(it) }
            if (showCurrentUser) {
                LeaderboardItem(LeaderboardRow(12, "我", 12, R.drawable.challenge_avatar_you, FormalColors.Primary), highlighted = true)
            }
        }
    }
}

private data class LeaderboardRow(
    val rank: Int,
    val name: String,
    val days: Int,
    val avatarRes: Int,
    val accent: Color
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
    joined: Boolean,
    onClose: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Box(modifier.background(FormalColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("挑战详情", style = type.style(20f, 28f, FontWeight.Bold, FormalColors.Ink), modifier = Modifier.weight(1f))
                    Surface(
                        onClick = onClose,
                        modifier = Modifier.size(38.dp),
                        color = FormalColors.Surface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, FormalColors.Border),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = FormalColors.Ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item { DetailHero() }
            item { Text("挑战规则", style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink), modifier = Modifier.padding(top = 10.dp)) }
            item { DetailRuleRow("每日签到", "每天在挑战页完成打卡，记录学习时长。", Icons.Filled.CheckCircle) }
            item { DetailRuleRow("任务完成", "完成后分配的 Python 基础课程与实践练习。", Icons.AutoMirrored.Filled.ListAlt) }
            item { DetailRuleRow("数据回传", "回传自评、引用资料、Memory 与薄弱节点，用于展示活动效果。", Icons.Filled.Lightbulb) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = FormalColors.Background.copy(alpha = .97f),
            shadowElevation = 8.dp
        ) {
            Button(
                enabled = !joined,
                onClick = onJoin,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp).fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FormalColors.Primary,
                    disabledContainerColor = FormalColors.Primary.copy(alpha = .45f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (joined) "已加入挑战" else "加入挑战", style = type.style(14f, 19f, FontWeight.Medium, Color.White))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailHero() {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(226.dp),
        color = FormalColors.PrimarySoft,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = .18f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = FormalColors.Warning, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text("21天 Python\n学习挑战", style = type.style(26f, 34f, FontWeight.Bold, FormalColors.Ink), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("掌握核心语法，用每日挑战构建稳定的编程学习节奏。", style = type.style(12f, 19f, color = FormalColors.Muted), textAlign = TextAlign.Center)
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
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = type.style(12f, 18f, FontWeight.Medium, FormalColors.Ink))
                Text(body, style = type.style(10f, 16f, color = FormalColors.Muted))
            }
            Spacer(Modifier.width(12.dp))
            FormalGlossyIcon(icon, null, size = 34.dp, glyphSize = 18.dp)
        }
    }
}
