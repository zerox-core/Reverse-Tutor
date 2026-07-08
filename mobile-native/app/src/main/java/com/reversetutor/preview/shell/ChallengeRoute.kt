package com.reversetutor.preview.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChallengeRoute(
    joined: Boolean,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChallengeBackground)
    ) {
        ChallengeContent(
            joined = joined,
            onBack = onBack,
            onOpenDetails = { showDetails = true },
            onJoin = onJoin,
            modifier = if (showDetails) Modifier.blur(12.dp) else Modifier
        )
        if (showDetails) {
            ModalBottomSheet(
                onDismissRequest = { showDetails = false },
                sheetState = sheetState,
                containerColor = Color(0xFFF7F9FB),
                contentColor = Ink,
                scrimColor = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                dragHandle = null
            ) {
                ChallengeDetailSheet(
                    joined = joined,
                    onJoin = {
                        showDetails = false
                        onJoin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 812.dp)
                )
            }
        }
    }
}

@Composable
private fun ChallengeContent(
    joined: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 28.dp)
        ) {
            item {
                ChallengeHeader()
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                ) {
                    MainChallengeCard(
                        onOpenDetails = onOpenDetails,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    FeedbackPill(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-2).dp)
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                LeaderboardSection()
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
            item {
                ChallengeFooterCta(
                    joined = joined,
                    onJoin = onJoin
                )
            }
        }
        Surface(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 7.dp, top = 8.dp)
                .size(38.dp)
                .shadow(9.dp, CircleShape, ambientColor = Color(0x29423873), spotColor = Color(0x29423873)),
            color = Color.White.copy(alpha = 0.94f),
            contentColor = Ink,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFFE1E6F0))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "‹",
                    color = Color(0xFF202637),
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(label = "线上活动", color = Color(0xFFEAF7F3), content = Color(0xFF14936F))
            StatusPill(label = "已同步", color = Color(0xFF21263A), content = Color.White)
        }
    }
}

@Composable
private fun ChallengeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "挑战活动",
            color = Ink,
            fontSize = 33.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp
        )
        Column {
            Text(
                text = "达成活动可发放成就徽章",
                color = Muted,
                fontSize = 17.sp,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x99FACC15))
            )
        }
    }
}

@Composable
private fun MainChallengeCard(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenDetails,
        modifier = modifier
            .fillMaxWidth()
            .height(411.dp)
            .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x0D7C3AED), spotColor = Color(0x0D7C3AED)),
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            StatusTag(
                modifier = Modifier.offset(x = 29.dp, y = 30.dp)
            )
            Text(
                text = "点击查看挑战详情",
                color = Color(0x997A829E),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .offset(x = 199.dp, y = 64.dp)
                    .width(150.dp)
            )
            Column(
                modifier = Modifier.offset(x = 29.dp, y = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "21天",
                    color = Ink,
                    fontSize = 34.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Python\n学习挑战",
                    color = Ink,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "◷  距离结束 15 天",
                    color = Color(0xFF8A90A0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            ChallengeCourseArt(
                modifier = Modifier.offset(x = 178.dp, y = 80.dp)
            )
            Column(
                modifier = Modifier
                    .offset(x = 29.dp, y = 276.dp)
                    .width(292.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "学习进度",
                    color = Color(0xFF8A90A0),
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "12",
                        color = Color(0xFF6657F6),
                        fontSize = 27.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " / 21 天",
                        color = Color(0xFF747B8E),
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                ProgressBar()
                Text(
                    text = "⦿",
                    color = Color(0xFF8A90A0),
                    fontSize = 18.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StatusTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFEEF2FF),
        contentColor = Color(0xFF6B5CFF),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1))
            )
            Text(
                text = "进行中",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChallengeCourseArt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 144.dp, height = 176.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.40f))
                .border(1.dp, Color.White.copy(alpha = 0.60f), RoundedCornerShape(16.dp))
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFE0E7FF))
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = 30.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC7D2FE))
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 53.dp, height = 80.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(Color(0xFFC7D2FE))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 53.dp, height = 56.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(Color(0xFFA5B4FC))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset(x = (-8).dp, y = 144.dp)
                .size(32.dp)
                .shadow(15.dp, CircleShape, ambientColor = Color(0x667C3AED), spotColor = Color(0x667C3AED))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))))
        )
    }
}

@Composable
private fun ProgressBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1A7C3AED))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.57f)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))))
        )
    }
}

@Composable
private fun FeedbackPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .width(280.dp)
            .height(28.dp),
        color = Color(0xFFF7F5FF),
        contentColor = Color(0xFF6B6F82),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E0FF))
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⌘", color = Color(0xFF6B5CFF), fontSize = 15.sp, lineHeight = 16.sp)
            Text(
                text = "已回传 3 条 Memory · 2 个薄弱节点",
                color = Color(0xFF6B6F82),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color,
    content: Color
) {
    Surface(
        color = color,
        contentColor = content,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LeaderboardSection() {
    val rows = listOf(
        LeaderboardRow(1, "小宇同学", 18, Color(0xFFFACC15), Color(0xFFFEF08A)),
        LeaderboardRow(2, "编程小能手", 16, Color(0xFFBFDBFE), Color(0xFFEFF6FF)),
        LeaderboardRow(3, "算法不秃头", 14, Color(0xFFFED7AA), Color(0xFFFFF7ED))
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x0D7C3AED), spotColor = Color(0x0D7C3AED)),
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))
    ) {
        Column(
            modifier = Modifier.padding(25.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "活动榜单",
                    color = Ink,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "查看全部", color = Color(0xFF8A90A0), fontSize = 12.sp, lineHeight = 16.sp)
                    Text(text = "♧", color = Color(0xFF8A90A0), fontSize = 18.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                rows.forEach { row ->
                    LeaderboardItem(row)
                }
            }
            UserLeaderboardItem()
        }
    }
}

@Composable
private fun LeaderboardItem(row: LeaderboardRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                color = row.rankColor,
                contentColor = Color.White,
                shape = CircleShape,
                border = BorderStroke(2.dp, row.borderColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(row.rank.toString(), fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Avatar()
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            color = Color(0xFF1F2937),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${row.days} 天",
            color = Color(0xFF1F2937),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UserLeaderboardItem() {
    Surface(
        color = Color(0x99EEF2FF),
        contentColor = Color(0xFF4F46E5),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "12",
                modifier = Modifier.width(40.dp),
                color = Color(0xFF4F46E5),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Avatar(tint = Color(0xFFD8ECFF))
            Text(
                text = "我",
                modifier = Modifier.weight(1f),
                color = Color(0xFF4F46E5),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "12 天",
                color = Color(0xFF4F46E5),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Avatar(tint: Color = Color(0xFFC8F2FF)) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(40.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint, Color(0xFF0F5667), Color(0xFF09151D))))
            .border(2.dp, Color.White, CircleShape)
    )
}

@Composable
private fun ChallengeFooterCta(
    joined: Boolean,
    onJoin: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x0D7C3AED), spotColor = Color(0x0D7C3AED)),
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))
    ) {
        Row(
            modifier = Modifier.padding(25.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "▤", color = Color(0xFF6366F1), fontSize = 28.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (joined) "已加入挑战" else "参与挑战",
                    color = Color(0xFF6B5CFF),
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "接收后台下发任务，完成后回传学习数据",
                    color = Color(0xFF8A90A0),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Surface(
                onClick = {
                    if (!joined) onJoin()
                },
                modifier = Modifier.size(48.dp),
                color = Color(0xFF6366F1),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "›", fontSize = 30.sp, lineHeight = 30.sp, modifier = Modifier.offset(y = (-2).dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeDetailSheet(
    joined: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFFF7F9FB))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 78.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                DetailHero()
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailSectionTitle("挑战规则")
                    DetailRuleRow("每日签到", "每天在挑战页完成打卡，记录学习时长。", "⌘")
                    DetailRuleRow("任务完成", "完成后台分配的 Python 基础课程与实践练习。", "▤")
                    DetailRuleRow("数据回传", "完成后回传自评、引用资料、Memory 与薄弱节点，后台可查看活动效果。", "♢")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailSectionTitle("活动反馈")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FeedbackMetric("3条", "Memory", Modifier.weight(1f))
                        FeedbackMetric("2个", "薄弱节点", Modifier.weight(1f))
                        FeedbackMetric("1次", "资料引用", Modifier.weight(1f))
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCCF7F9FB))
                .padding(top = 8.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 48.dp, height = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x66CCC3D8))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "挑战详情",
                color = Ink,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = Color(0xE6F7F9FB),
            contentColor = Color.White
        ) {
            Button(
                enabled = !joined,
                onClick = onJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 32.dp)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color(0xFFE3E7EF)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF64A8FE)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (joined) "已加入挑战" else "加入挑战",
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(text = "→", color = Color.White, fontSize = 24.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(40.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x147C3AED), spotColor = Color(0x147C3AED)),
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color(0xFFE9DDFF).copy(alpha = 0.75f),
                            Color(0xFFD6ECFF).copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(33.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "21天 Python\n学习挑战",
                    color = Ink,
                    fontSize = 31.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.32).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "掌握核心语法，用每日挑战构建稳定的编程学习节奏。",
                    color = Color(0xFF5F6678),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text = text,
        color = Ink,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DetailRuleRow(
    title: String,
    body: String,
    icon: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = Ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                Text(text = body, color = Color(0xFF5F6678), fontSize = 13.sp, lineHeight = 20.sp)
            }
            Text(text = icon, color = Color(0xFF6B5CFF), fontSize = 24.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun FeedbackMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.70f),
        contentColor = Ink,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color(0xFF6B5CFF), fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = Color(0xFF8A90A0), fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

private val ChallengeBackground = Brush.verticalGradient(
    listOf(
        Color(0xFFFDFEFF),
        Color(0xFFE8EDFF),
        Color(0xFFF5F3FF)
    )
)

private val Ink = Color(0xFF202637)
private val Muted = Color(0xFF7D8494)

private data class LeaderboardRow(
    val rank: Int,
    val name: String,
    val days: Int,
    val rankColor: Color,
    val borderColor: Color
)
