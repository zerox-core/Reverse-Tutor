package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

enum class FormalTokenPeriod { Today, Week, Month, All }

private val FormalTokenPeriod.label: String
    get() = when (this) {
        FormalTokenPeriod.Today -> "今日"
        FormalTokenPeriod.Week -> "本周"
        FormalTokenPeriod.Month -> "本月"
        FormalTokenPeriod.All -> "累计"
    }

@Immutable
data class FormalTokenSummary(
    val totalLabel: String,
    val comparisonLabel: String,
    val estimateLabel: String = "估算",
    val inputLabel: String,
    val outputLabel: String,
    val cacheLabel: String,
    val reasoningLabel: String
)

@Immutable
data class FormalTokenTrendPoint(
    val label: String,
    val valueLabel: String,
    val fraction: Float
)

@Immutable
data class FormalTokenOverviewUiState(
    val selectedPeriod: FormalTokenPeriod,
    val summary: FormalTokenSummary,
    val peakLabel: String,
    val averageLabel: String,
    val trend: List<FormalTokenTrendPoint>,
    val modelSummary: String,
    val sessionSummary: String
)

@Composable
fun FormalTokenOverviewScreen(
    state: FormalTokenOverviewUiState,
    onBack: () -> Unit,
    onPeriodSelected: (FormalTokenPeriod) -> Unit,
    onOpenByModel: () -> Unit,
    onOpenBySession: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("Token 统计", "用量与模型分布", onBack, modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                TokenSummaryCard("本周总消耗", state.summary, Icons.Rounded.ViewInAr, Color(0xFF568FCC))
                Spacer(Modifier.height(16.dp))
                TokenPeriodSelector(state.selectedPeriod, onPeriodSelected)
                Spacer(Modifier.height(16.dp))
                TokenBreakdownCard(state.summary)
                Spacer(Modifier.height(22.dp))
                FormalSectionLabel("7 日趋势", trailing = "估算")
                Spacer(Modifier.height(8.dp))
                TokenTrendCard(state)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("查看明细")
                Spacer(Modifier.height(8.dp))
                TokenDetailLink("按模型", state.modelSummary, Icons.Rounded.AutoAwesome, Color(0xFF557FB7), onOpenByModel)
                Spacer(Modifier.height(9.dp))
                TokenDetailLink("按会话", state.sessionSummary, Icons.Rounded.ChatBubbleOutline, Color(0xFF42A07C), onOpenBySession)
                Spacer(Modifier.height(16.dp))
                FormalInfoBanner("估算数据用于趋势判断，请精确值以模型服务商为准", "")
            }
        }
    }
}

@Composable
private fun TokenSummaryCard(
    eyebrow: String,
    summary: FormalTokenSummary,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    detail: String? = null
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(icon, null, iconColor, size = 43.dp, glyphSize = 20.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(eyebrow, color = colors.muted, style = type.style(9f, 14f))
                Text(summary.totalLabel, color = colors.ink, style = type.style(21f, 27f, FontWeight.SemiBold))
                Text(detail ?: summary.comparisonLabel, color = colors.success, style = type.style(8f, 13f))
            }
            Text(summary.estimateLabel, color = colors.warning, style = type.style(8f, 13f), modifier = Modifier.background(colors.warningSoft, RoundedCornerShape(13.dp)).padding(horizontal = 15.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun TokenPeriodSelector(selected: FormalTokenPeriod, onSelected: (FormalTokenPeriod) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFE7EDF6), RoundedCornerShape(8.dp)).padding(3.dp)) {
        FormalTokenPeriod.values().forEach { period ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab, onClickLabel = period.label) { onSelected(period) },
                color = if (period == selected) colors.surface else Color.Transparent,
                shape = RoundedCornerShape(7.dp),
                shadowElevation = if (period == selected) 2.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(period.label, color = if (period == selected) colors.ink else colors.faint, style = type.style(9f, 14f, if (period == selected) FontWeight.Medium else FontWeight.Normal))
                }
            }
        }
    }
}

@Composable
private fun TokenBreakdownCard(summary: FormalTokenSummary) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf(
                summary.inputLabel to "输入" to Color(0xFF4F83C5),
                summary.outputLabel to "输出" to Color(0xFF3D9A7C),
                summary.cacheLabel to "缓存" to Color(0xFFC28429),
                summary.reasoningLabel to "推理" to Color(0xFF6979B5)
            ).forEachIndexed { index, triple ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(triple.first.first, color = triple.second, style = type.style(15f, 21f, FontWeight.Medium))
                    Text(triple.first.second, color = colors.faint, style = type.style(8f, 13f))
                }
                if (index != 3) Box(Modifier.width(1.dp).height(52.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun TokenTrendCard(state: FormalTokenOverviewUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text("峰值 ${state.peakLabel}", color = Color(0xFF527DB8), style = type.style(8f, 13f, FontWeight.Medium), modifier = Modifier.weight(1f))
                Text("日均 ${state.averageLabel}", color = colors.muted, style = type.style(11f, 16f, FontWeight.Medium))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(125.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
                state.trend.forEach { point ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.width(20.dp).heightIn(min = 8.dp, max = 105.dp).height((point.fraction.coerceIn(.05f, 1f) * 95).dp)
                                .background(Color(0xFF5B99CE), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(point.label, color = colors.faint, style = type.style(7f, 11f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenDetailLink(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        FormalChevronRow(title, subtitle, icon, color, onClick)
    }
}

@Immutable
data class FormalTokenModelItem(
    val id: String,
    val name: String,
    val tokenLabel: String,
    val percentageLabel: String,
    val breakdownLabel: String,
    val fraction: Float,
    val color: Color
)

@Immutable
data class FormalTokenByModelUiState(
    val selectedPeriod: FormalTokenPeriod,
    val summary: FormalTokenSummary,
    val leadingModelLabel: String,
    val items: List<FormalTokenModelItem>
)

@Composable
fun FormalTokenByModelScreen(
    state: FormalTokenByModelUiState,
    onBack: () -> Unit,
    onPeriodSelected: (FormalTokenPeriod) -> Unit,
    onOpenBySession: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("按模型", "Token 使用分布", onBack, modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                TokenSummaryCard("本周模型用量", state.summary, Icons.Rounded.AutoAwesome, Color(0xFF5577D9), state.leadingModelLabel)
                Spacer(Modifier.height(16.dp))
                TokenPeriodSelector(state.selectedPeriod, onPeriodSelected)
                Spacer(Modifier.height(18.dp))
                FormalSectionLabel("模型分布", trailing = "${state.items.size} 个模型")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp)) {
                    Column {
                        state.items.forEachIndexed { index, item ->
                            TokenModelRow(item)
                            if (index != state.items.lastIndex) TokenDivider()
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                FormalInfoBanner("部分模型不返回缓存或推理 Token", "缺失字段以估算值显示")
                Spacer(Modifier.height(20.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    FormalChevronRow("按会话查看", "定位哪些会话消耗最多", Icons.Rounded.ChatBubbleOutline, Color(0xFF4E98C4), onOpenBySession)
                }
            }
        }
    }
}

@Composable
private fun TokenModelRow(item: FormalTokenModelItem) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(item.color.copy(alpha = .12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = item.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium))
                Text(item.breakdownLabel, color = colors.faint, style = type.style(7.5f, 12f))
            }
            Text("${item.tokenLabel} · ${item.percentageLabel}", color = item.color, style = type.style(9f, 14f, FontWeight.Medium))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().padding(start = 45.dp).height(6.dp).background(colors.divider, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(item.fraction.coerceIn(0f, 1f)).height(6.dp).background(item.color, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Text("占本周 ${item.percentageLabel}", color = colors.faint, style = type.style(7f, 11f), modifier = Modifier.padding(start = 45.dp))
    }
}

@Immutable
data class FormalTokenSessionItem(
    val id: String,
    val title: String,
    val modelName: String,
    val tokenLabel: String,
    val percentageLabel: String,
    val breakdownLabel: String,
    val fraction: Float,
    val color: Color
)

@Immutable
data class FormalTokenBySessionUiState(
    val selectedPeriod: FormalTokenPeriod,
    val summary: FormalTokenSummary,
    val activeSessionCountLabel: String,
    val searchQuery: String,
    val items: List<FormalTokenSessionItem>
)

@Composable
fun FormalTokenBySessionScreen(
    state: FormalTokenBySessionUiState,
    onBack: () -> Unit,
    onPeriodSelected: (FormalTokenPeriod) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("按会话", "Token 使用分布", onBack, modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                TokenSummaryCard("本周会话用量", state.summary, Icons.Rounded.ChatBubbleOutline, Color(0xFF43A17D), state.activeSessionCountLabel)
                Spacer(Modifier.height(16.dp))
                TokenPeriodSelector(state.selectedPeriod, onPeriodSelected)
                Spacer(Modifier.height(16.dp))
                TokenSessionSearch(state.searchQuery, onSearchQueryChange)
                Spacer(Modifier.height(18.dp))
                FormalSectionLabel("会话分布", trailing = "按用量排序")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp)) {
                    Column {
                        state.items.forEachIndexed { index, item ->
                            TokenSessionRow(item, onSessionClick)
                            if (index != state.items.lastIndex) TokenDivider()
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                FormalInfoBanner("单会话统计包含输入、输出、缓存与推理 Token", "列表可继续向下滚动查看其余会话")
            }
        }
    }
}

@Composable
private fun TokenSessionSearch(value: String, onChange: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Surface(Modifier.fillMaxWidth().height(40.dp), color = colors.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.style(9f, 14f, color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) Text("搜索会话", color = colors.faint, style = type.style(9f, 14f))
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TokenSessionRow(item: FormalTokenSessionItem, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = item.title) { onClick(item.id) }.padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.ChatBubbleOutline, null, item.color, size = 31.dp, glyphSize = 14.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium))
                Text("${item.modelName} · ${item.breakdownLabel}", color = colors.faint, style = type.style(7.5f, 12f))
            }
            Text("${item.tokenLabel} · ${item.percentageLabel}", color = item.color, style = type.style(9f, 14f, FontWeight.Medium))
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().padding(start = 41.dp).height(6.dp).background(colors.divider, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(item.fraction.coerceIn(0f, 1f)).height(6.dp).background(item.color, RoundedCornerShape(3.dp)))
        }
        Text("占本周 ${item.percentageLabel}", color = colors.faint, style = type.style(7f, 11f), modifier = Modifier.padding(start = 41.dp, top = 4.dp))
    }
}

@Composable
private fun TokenDivider() {
    val colors = formalBatch6Colors()
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
}
