package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.core.domain.OnlineContentPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class FormalHomeVariant {
    Default,
    NewSessionSheet,
    JoinedChallenge
}

enum class FormalPublicContentAvailability {
    Loading,
    Available,
    Unavailable
}

data class FormalPublicContentUi(
    val id: String,
    val title: String,
    val summary: String,
    val publishedLabel: String,
    val pageLabel: String,
    val eyebrow: String = "公益课堂 · 今日更新",
    val slug: String = id,
    val availability: FormalPublicContentAvailability = FormalPublicContentAvailability.Available
) {
    val canOpen: Boolean
        get() = availability == FormalPublicContentAvailability.Available && slug.isNotBlank()

    companion object {
        fun loading() = FormalPublicContentUi(
            id = "",
            title = "正在获取今日公益内容",
            summary = "请稍候",
            publishedLabel = "连接在线内容服务",
            pageLabel = "",
            eyebrow = "公益课堂 · 正在更新",
            availability = FormalPublicContentAvailability.Loading
        )

        fun offline() = FormalPublicContentUi(
            id = "",
            title = "公益内容当前离线",
            summary = "连接网络后再来查看今日更新",
            publishedLabel = "本地学习功能不受影响",
            pageLabel = "",
            eyebrow = "公益课堂 · 当前离线",
            availability = FormalPublicContentAvailability.Unavailable
        )

        fun unavailable() = FormalPublicContentUi(
            id = "",
            title = "公益内容暂不可用",
            summary = "稍后再来查看今日更新",
            publishedLabel = "本地学习功能不受影响",
            pageLabel = "",
            eyebrow = "公益课堂 · 暂不可用",
            availability = FormalPublicContentAvailability.Unavailable
        )
    }
}

internal fun OnlineContentPage.toFormalPublicContentUi(): FormalPublicContentUi? {
    val item = items.firstOrNull() ?: return null
    val publishedDate = SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(item.publishedAtEpochMillis))
    val publishedLabel = listOfNotNull(
        publishedDate,
        item.publisherName?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    return FormalPublicContentUi(
        id = item.id,
        slug = item.slug,
        title = item.title,
        summary = item.summary,
        publishedLabel = publishedLabel,
        pageLabel = "1 / ${items.size}"
    )
}

data class FormalHomeSessionUi(
    val id: String,
    val title: String,
    val summary: String,
    val timeLabel: String,
    val updatedAtEpochMillis: Long,
    val pinned: Boolean
)

data class FormalJoinedChallengeUi(
    val id: String,
    val title: String,
    val dayLabel: String,
    val todayPrompt: String,
    val progressFraction: Float,
    val actionLabel: String = "继续挑战  ›"
) {
    init {
        require(progressFraction in 0f..1f)
    }
}

data class FormalHomeUiState(
    val publicContent: FormalPublicContentUi,
    val sessions: List<FormalHomeSessionUi> = emptyList(),
    val challenge: FormalJoinedChallengeUi? = null,
    val isNewSessionSheetVisible: Boolean = false
) {
    val visibleSessions: List<FormalHomeSessionUi>
        get() = sessions
            .sortedWith(
                compareByDescending<FormalHomeSessionUi> { it.pinned }
                    .thenByDescending { it.updatedAtEpochMillis }
            )
            .take(4)

    val variant: FormalHomeVariant
        get() = when {
            isNewSessionSheetVisible -> FormalHomeVariant.NewSessionSheet
            challenge != null -> FormalHomeVariant.JoinedChallenge
            else -> FormalHomeVariant.Default
        }
}

fun SessionListUiState.toFormalHomeUiState(
    publicContent: FormalPublicContentUi,
    challenge: FormalJoinedChallengeUi? = null,
    isNewSessionSheetVisible: Boolean = false,
    nowEpochMillis: Long = System.currentTimeMillis()
): FormalHomeUiState = FormalHomeUiState(
    publicContent = publicContent,
    sessions = visibleSessions.map { item ->
        FormalHomeSessionUi(
            id = item.id,
            title = item.title,
            summary = item.statusLabel,
            timeLabel = relativeTimeLabel(item.updatedAtEpochMillis, nowEpochMillis),
            updatedAtEpochMillis = item.updatedAtEpochMillis,
            pinned = item.pinned
        )
    },
    challenge = challenge,
    isNewSessionSheetVisible = isNewSessionSheetVisible
)

private fun relativeTimeLabel(updatedAtEpochMillis: Long, nowEpochMillis: Long): String {
    val elapsed = (nowEpochMillis - updatedAtEpochMillis).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        elapsed < minute -> "刚刚"
        elapsed < hour -> "${elapsed / minute} 分钟前"
        elapsed < day -> "${elapsed / hour} 小时前"
        elapsed < 2 * day -> "昨天"
        else -> "${elapsed / day} 天前"
    }
}

@Composable
fun FormalHomeScreen(
    state: FormalHomeUiState,
    onPublicContentClick: (FormalPublicContentUi) -> Unit,
    onSessionClick: (FormalHomeSessionUi) -> Unit,
    onOpenChallenge: () -> Unit,
    onShowNewSessionSheet: () -> Unit,
    onDismissNewSessionSheet: () -> Unit,
    onStartLearningSetup: () -> Unit,
    onOpenWeekly: () -> Unit,
    showSpatialIndicator: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag("formal-home-screen")
    ) {
        val contentWidth = maxWidth.coerceAtMost(390.dp)
        Box(
            modifier = Modifier
                .width(contentWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
        ) {
            FormalHomeContent(
                state = state,
                onPublicContentClick = onPublicContentClick,
                onSessionClick = onSessionClick,
                onOpenChallenge = onOpenChallenge,
                onShowNewSessionSheet = onShowNewSessionSheet,
                onOpenWeekly = onOpenWeekly,
                showSpatialIndicator = showSpatialIndicator
            )
            if (state.isNewSessionSheetVisible) {
                FormalNewSessionSheet(
                    onDismiss = onDismissNewSessionSheet,
                    onStartLearningSetup = onStartLearningSetup
                )
            }
        }
    }
}

@Composable
private fun FormalHomeContent(
    state: FormalHomeUiState,
    onPublicContentClick: (FormalPublicContentUi) -> Unit,
    onSessionClick: (FormalHomeSessionUi) -> Unit,
    onOpenChallenge: () -> Unit,
    onShowNewSessionSheet: () -> Unit,
    onOpenWeekly: () -> Unit,
    showSpatialIndicator: Boolean
) {
    val headerSpacerHeight = if (state.variant == FormalHomeVariant.Default) 23.dp else 48.dp
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChallengePullHandle(onOpenChallenge = onOpenChallenge)
            Spacer(modifier = Modifier.height(headerSpacerHeight))
            PublicInterestCard(
                content = state.publicContent,
                onClick = { onPublicContentClick(state.publicContent) }
            )
            if (state.variant == FormalHomeVariant.JoinedChallenge) {
                state.challenge?.let { challenge ->
                    JoinedChallengeCard(challenge = challenge, onClick = onOpenChallenge)
                }
            }
            SessionEntryList(
                sessions = state.visibleSessions,
                roomySpacing = state.variant != FormalHomeVariant.Default,
                onSessionClick = onSessionClick
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showSpatialIndicator) {
                WeeklyPageIndicator(onClick = onOpenWeekly)
            }
        }
        HomeTitle(modifier = Modifier.offset(x = 16.dp, y = 16.dp))
        NewSessionButton(
            onClick = onShowNewSessionSheet,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun ChallengePullHandle(onOpenChallenge: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(onOpenChallenge) {
                var pullDistance = 0f
                val threshold = 72.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (pullDistance >= threshold) onOpenChallenge()
                        pullDistance = 0f
                    },
                    onDragCancel = { pullDistance = 0f },
                    onVerticalDrag = { change, amount ->
                        if (amount > 0f) {
                            change.consume()
                            pullDistance += amount
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(8.dp)
                .background(Color(0xFFC8CFDB), RoundedCornerShape(FormalShapes.PillRadius)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Color(0xFFF8FAFE), RoundedCornerShape(FormalShapes.PillRadius))
            )
        }
    }
}

@Composable
private fun HomeTitle(modifier: Modifier = Modifier) {
    val type = LocalFormalTypeScale.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "会话",
            style = type.style(26f, 34f, FontWeight.Bold, FormalColors.Ink)
        )
        Text(
            text = "用讲解检验真正的理解",
            style = type.style(12f, 18f, color = FormalColors.Muted)
        )
    }
}

@Composable
private fun NewSessionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = "新建会话" }
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color(0x1F2E3B54),
                spotColor = Color(0x1F2E3B54)
            ),
        shape = RoundedCornerShape(22.dp),
        color = FormalColors.SurfaceElevated.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, FormalColors.Border.copy(alpha = 0.7f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(FormalColors.Primary, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .background(FormalColors.Primary, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun PublicInterestCard(
    content: FormalPublicContentUi,
    onClick: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onClick,
        enabled = content.canOpen,
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                ambientColor = Color(0x122E3B54),
                spotColor = Color(0x122E3B54)
            ),
        color = FormalColors.Surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublicInterestIllustration()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "●  ${content.eyebrow}",
                    style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Primary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = content.title,
                    style = type.style(14f, 20f, FontWeight.Bold, FormalColors.Ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = content.summary,
                    style = type.style(10f, 15f, color = FormalColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = content.publishedLabel,
                        style = type.style(9f, 14f, color = FormalColors.Muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = content.pageLabel,
                        style = type.style(9f, 14f, FontWeight.Medium, Color(0xFF475C94)),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicInterestIllustration() {
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(94.dp)
            .clip(RoundedCornerShape(FormalShapes.CardRadius))
            .background(Color(0xFFE8F0FC))
    ) {
        TextBubble(
            label = "先核对",
            background = FormalColors.SurfaceElevated.copy(alpha = 0.94f),
            foreground = Color(0xFF384D80),
            modifier = Modifier.offset(x = 5.dp, y = 8.dp)
        )
        TextBubble(
            label = "别急点",
            background = Color(0xFF576BE0),
            foreground = Color.White,
            modifier = Modifier.offset(x = 45.dp, y = 28.dp)
        )
        IllustrationPerson(
            bodyColor = Color(0xFF4D6BC2),
            modifier = Modifier.offset(x = 13.dp, y = 45.dp)
        )
        IllustrationPerson(
            bodyColor = Color(0xFF856BC2),
            modifier = Modifier.offset(x = 52.dp, y = 48.dp),
            bodyHeight = 19.dp
        )
        Box(
            modifier = Modifier
                .offset(x = 12.dp, y = 82.dp)
                .width(64.dp)
                .height(2.dp)
                .background(Color(0x335C70A8), RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun TextBubble(
    label: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Box(
        modifier = modifier
            .height(20.dp)
            .background(background, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = type.style(8f, 12f, FontWeight.Medium, foreground),
            maxLines = 1
        )
    }
}

@Composable
private fun IllustrationPerson(
    bodyColor: Color,
    modifier: Modifier = Modifier,
    bodyHeight: Dp = 22.dp
) {
    Box(modifier = modifier.width(24.dp).height(36.dp)) {
        Box(
            modifier = Modifier
                .offset(x = 5.dp)
                .size(14.dp)
                .background(Color(0xFFF3C58E), RoundedCornerShape(7.dp))
        )
        Box(
            modifier = Modifier
                .offset(y = 14.dp)
                .width(24.dp)
                .height(bodyHeight)
                .background(
                    bodyColor,
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                )
        )
    }
}

@Composable
private fun SessionEntryList(
    sessions: List<FormalHomeSessionUi>,
    roomySpacing: Boolean,
    onSessionClick: (FormalHomeSessionUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(322.dp),
        verticalArrangement = Arrangement.spacedBy(if (roomySpacing) 8.dp else 4.dp)
    ) {
        sessions.forEach { session ->
            SessionEntryCard(
                session = session,
                onClick = { onSessionClick(session) }
            )
        }
    }
}

@Composable
private fun SessionEntryCard(
    session: FormalHomeSessionUi,
    onClick: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    val height = if (session.pinned) 82.dp else 72.dp
    val background = if (session.pinned) Color(0xFFFFF3E2) else FormalColors.SurfaceElevated.copy(alpha = 0.78f)
    val border = if (session.pinned) Color(0xADE7C88F) else FormalColors.Border.copy(alpha = 0.45f)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        color = background,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, border)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp, end = 90.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.title,
                    style = type.style(
                        if (session.pinned) 14f else 13f,
                        19f,
                        FontWeight.Medium,
                        FormalColors.Ink
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.summary,
                    style = type.style(11f, 17f, color = FormalColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${session.timeLabel}  ›",
                style = type.style(10f, 16f, color = Color(0xFF616E85)),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
            )
            if (session.pinned) {
                PinnedRibbon(modifier = Modifier.align(Alignment.TopStart))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 7.dp)
                        .width(64.dp)
                        .height(18.dp)
                        .background(FormalColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "● 正在学习",
                        style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedRibbon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        val ribbon = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = ribbon, color = Color(0xFFE5A64E))
    }
}

@Composable
private fun JoinedChallengeCard(
    challenge: FormalJoinedChallengeUi,
    onClick: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                ambientColor = Color(0x3D24338C),
                spotColor = Color(0x3D24338C)
            ),
        color = Color(0xFF293B9E),
        shape = RoundedCornerShape(FormalShapes.CardRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "●  ${challenge.dayLabel}",
                    style = type.style(9f, 14f, FontWeight.Medium, Color(0xFFB8E0FF)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = challenge.title,
                    style = type.style(18f, 25f, FontWeight.Bold, Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = challenge.todayPrompt,
                    style = type.style(10f, 15f, color = Color(0xFFD4DEFA)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(challenge.progressFraction)
                            .height(5.dp)
                            .background(Color(0xFFFFC252), RoundedCornerShape(3.dp))
                    )
                }
                Text(
                    text = challenge.actionLabel,
                    style = type.style(10f, 15f, FontWeight.Medium, Color(0xFFFFD17A)),
                    maxLines = 1
                )
            }
            ChallengeIllustration()
        }
    }
}

@Composable
private fun ChallengeIllustration() {
    Box(
        modifier = Modifier
            .width(94.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(FormalShapes.CardRadius))
            .background(Color(0xFF7A61D6))
    ) {
        Box(
            modifier = Modifier
                .offset(x = 52.dp, y = (-9).dp)
                .size(44.dp)
                .background(Color(0xFFFFC45E), RoundedCornerShape(22.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = 13.dp, y = 54.dp)
                .width(68.dp)
                .height(42.dp)
                .background(Color(0xFFF0F5FF), RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(34.dp)
                    .background(Color(0x593D4FAD), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .offset(x = 7.dp, y = 12.dp)
                    .width(20.dp)
                    .height(2.dp)
                    .background(Color(0x615769B2), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .offset(x = 42.dp, y = 12.dp)
                    .width(18.dp)
                    .height(2.dp)
                    .background(Color(0x615769B2), RoundedCornerShape(1.dp))
            )
        }
        Box(
            modifier = Modifier
                .offset(x = 30.dp, y = 20.dp)
                .size(34.dp)
                .background(Color(0xFFFFC252), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "★", color = Color.White)
        }
        Box(
            modifier = Modifier
                .offset(x = 12.dp, y = 25.dp)
                .size(6.dp)
                .background(Color(0xFFB8E0FF), RoundedCornerShape(3.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = 76.dp, y = 40.dp)
                .size(5.dp)
                .background(Color(0xFFFFD17A), RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun WeeklyPageIndicator(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .width(122.dp)
                    .height(38.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(FormalShapes.PillRadius),
                        ambientColor = Color(0x242E3B54),
                        spotColor = Color(0x242E3B54)
                    ),
                shape = RoundedCornerShape(FormalShapes.PillRadius),
                color = Color(0xFF505766)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(8.dp)
                            .background(Color.White, RoundedCornerShape(FormalShapes.PillRadius))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF8A92A2), RoundedCornerShape(FormalShapes.PillRadius))
                    )
                }
            }
        }
    }
}

@Composable
private fun FormalNewSessionSheet(
    onDismiss: () -> Unit,
    onStartLearningSetup: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x38080A12))
                .clickable(onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(357.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ambientColor = Color(0x2E1A2133),
                    spotColor = Color(0x2E1A2133)
                )
                .pointerInput(onDismiss) {
                    var dragDistance = 0f
                    val threshold = 48.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragDistance >= threshold) onDismiss()
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            if (amount > 0f) {
                                change.consume()
                                dragDistance += amount
                            }
                        }
                    )
                },
            color = Color(0xFFFAFBFE),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            NewSessionSheetContent(
                onDismiss = onDismiss,
                onStartLearningSetup = onStartLearningSetup
            )
        }
    }
}

@Composable
private fun NewSessionSheetContent(
    onDismiss: () -> Unit,
    onStartLearningSetup: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = 20.dp, y = 6.dp)
                .width(36.dp)
                .height(5.dp)
                .background(Color(0x577A8599), RoundedCornerShape(3.dp))
        )
        Row(
            modifier = Modifier
                .offset(x = 20.dp, y = 25.dp)
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "新建会话",
                style = type.style(20f, 28f, FontWeight.Bold, FormalColors.Ink)
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE5EBF5)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "×",
                        style = type.style(18f, 22f, color = Color(0xFF454F63))
                    )
                }
            }
        }
        Text(
            text = "先选择一种模式，下一步再配置专属世界树。",
            style = type.style(11f, 16f, color = FormalColors.Muted),
            modifier = Modifier.offset(x = 20.dp, y = 87.dp)
        )
        Text(
            text = "选择产品方向",
            style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink),
            modifier = Modifier.offset(x = 20.dp, y = 114.dp)
        )
        Row(
            modifier = Modifier
                .offset(x = 20.dp, y = 142.dp)
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .height(82.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NewSessionModeCard(
                title = "学习模式",
                description = "构建路径",
                selected = true,
                modifier = Modifier.weight(1f)
            )
            NewSessionModeCard(
                title = "复盘模式",
                description = "定位薄弱点",
                selected = false,
                modifier = Modifier.weight(1f)
            )
            NewSessionModeCard(
                title = "陪伴模式",
                description = "长期交流",
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "世界树包含学习路径、性格、目标与资料，将在下一步统一配置。",
            style = type.style(10f, 15f, color = FormalColors.Muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = 20.dp, y = 238.dp)
                .widthIn(max = 350.dp)
        )
        Surface(
            onClick = onStartLearningSetup,
            modifier = Modifier
                .offset(x = 20.dp, y = 267.dp)
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .height(50.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    ambientColor = Color(0x382E5CDB),
                    spotColor = Color(0x382E5CDB)
                ),
            color = FormalColors.Primary,
            shape = RoundedCornerShape(FormalShapes.CardRadius)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "开始设置  →",
                    style = type.style(13f, 18f, FontWeight.Bold, Color.White)
                )
            }
        }
    }
}

@Composable
private fun NewSessionModeCard(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    val container = if (selected) FormalColors.Primary else Color(0xFFEBF0FA)
    val titleColor = if (selected) Color.White else Color(0xFF2E384D)
    val detailColor = if (selected) Color(0xFFD1DEFF) else Color(0xFF758094)
    Surface(
        modifier = modifier.height(78.dp),
        color = container,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = if (selected) null else BorderStroke(1.dp, FormalColors.Border.copy(alpha = 0.6f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = title,
                style = type.style(12f, 18f, FontWeight.Medium, titleColor),
                maxLines = 1
            )
            Text(
                text = description,
                style = type.style(9f, 13f, color = detailColor),
                maxLines = 1
            )
        }
    }
}
