package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

enum class FormalLlmProvider(val label: String, val shortLabel: String) {
    DeepSeek("DeepSeek", "鲸"),
    Kimi("Kimi", "K"),
    Qwen("百炼", "千"),
    Glm("GLM", "智"),
    OpenAi("OpenAI", "◎"),
    Custom("自定义", "▦")
}

internal enum class FormalProviderPresetFilter(val label: String) {
    All("全部"),
    OpenAi("OpenAI 格式"),
    Anthropic("Anthropic"),
    Domestic("国内")
}

internal enum class FormalProviderPresetFormat(val label: String) {
    Custom("自定义"),
    OpenAi("OpenAI"),
    Anthropic("Anthropic")
}

internal data class FormalProviderPresetItem(
    val title: String,
    val subtitle: String,
    val format: FormalProviderPresetFormat,
    val mark: String,
    val markColor: Color,
    val domestic: Boolean,
    val destination: FormalLlmProvider? = null
)

internal val formalProviderPresetCatalog: List<FormalProviderPresetItem> = listOf(
    FormalProviderPresetItem(
        title = "手动填写",
        subtitle = "地址、密钥与模型均由用户填写",
        format = FormalProviderPresetFormat.Custom,
        mark = "≡",
        markColor = Color(0xFFF0F4FA),
        domestic = false
    ),
    FormalProviderPresetItem(
        title = "DeepSeek / OpenAI",
        subtitle = "api.deepseek.com",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "鲸",
        markColor = Color(0xFFEEF1FF),
        domestic = true,
        destination = FormalLlmProvider.DeepSeek
    ),
    FormalProviderPresetItem(
        title = "DeepSeek / Anthropic",
        subtitle = "api.deepseek.com/anthropic",
        format = FormalProviderPresetFormat.Anthropic,
        mark = "鲸",
        markColor = Color(0xFFEEF1FF),
        domestic = true,
        destination = FormalLlmProvider.DeepSeek
    ),
    FormalProviderPresetItem(
        title = "Kimi / Moonshot CN",
        subtitle = "api.moonshot.cn/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "K",
        markColor = Color(0xFF182236),
        domestic = true,
        destination = FormalLlmProvider.Kimi
    ),
    FormalProviderPresetItem(
        title = "百炼 / Qwen",
        subtitle = "北京业务空间 compatible-mode/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "千",
        markColor = Color(0xFFF1EDFF),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "智谱 GLM",
        subtitle = "open.bigmodel.cn/api/paas/v4",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "智",
        markColor = Color(0xFFEDF2FF),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "MiniMax / OpenAI",
        subtitle = "api.minimaxi.com/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "M",
        markColor = Color(0xFFFFEEF2),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "MiniMax / Anthropic",
        subtitle = "api.minimaxi.com/anthropic",
        format = FormalProviderPresetFormat.Anthropic,
        mark = "M",
        markColor = Color(0xFFFFEEF2),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "小米 MiMo",
        subtitle = "api.xiaomimimo.com/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "MI",
        markColor = Color(0xFFFFF1E9),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "Claude",
        subtitle = "api.anthropic.com/v1/messages",
        format = FormalProviderPresetFormat.Anthropic,
        mark = "C",
        markColor = Color(0xFFFFEFE9),
        domestic = false
    ),
    FormalProviderPresetItem(
        title = "豆包 / 火山方舟",
        subtitle = "ark.cn-beijing.volces.com/api/v3",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "豆",
        markColor = Color(0xFFF0EEFF),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "硅基流动",
        subtitle = "api.siliconflow.cn/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "▬",
        markColor = Color(0xFFF1ECFF),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "百度千帆",
        subtitle = "选择地域后自动填充地址",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "百",
        markColor = Color(0xFFEDF8F7),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "腾讯混元",
        subtitle = "选择地域后自动填充地址",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "混",
        markColor = Color(0xFFEFF6FF),
        domestic = true
    ),
    FormalProviderPresetItem(
        title = "OpenAI",
        subtitle = "api.openai.com/v1",
        format = FormalProviderPresetFormat.OpenAi,
        mark = "◎",
        markColor = Color(0xFFF0F3F6),
        domestic = false
    )
)

internal fun formalProviderPresetRows(
    query: String,
    filter: FormalProviderPresetFilter
): List<FormalProviderPresetItem> {
    val normalizedQuery = query.trim()
    return formalProviderPresetCatalog.filter { item ->
        val matchesFilter = when (filter) {
            FormalProviderPresetFilter.All -> true
            FormalProviderPresetFilter.OpenAi -> item.format == FormalProviderPresetFormat.OpenAi
            FormalProviderPresetFilter.Anthropic -> item.format == FormalProviderPresetFormat.Anthropic
            FormalProviderPresetFilter.Domestic -> item.domestic
        }
        val matchesQuery = normalizedQuery.isEmpty() || listOf(
            item.title,
            item.subtitle,
            item.format.label
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
        matchesFilter && matchesQuery
    }
}

@Composable
fun FormalLlmConfigurationScreen(
    state: LlmProfileSettingsUiState,
    onBack: () -> Unit,
    onActivateProfile: (String) -> Unit,
    onTestProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialProvider: FormalLlmProvider = FormalLlmProvider.DeepSeek,
    initialShowPresets: Boolean = false
) {
    var provider by remember(initialProvider) { mutableStateOf(initialProvider) }
    var showPresets by remember(initialShowPresets) { mutableStateOf(initialShowPresets) }

    if (showPresets) {
        FormalProviderPresetScreen(
            onBack = { showPresets = false },
            onProviderSelected = {
                provider = it
                showPresets = false
            },
            modifier = modifier
        )
        return
    }

    val type = LocalFormalTypeScale.current
    val profiles = remember(state.profileItems, provider) {
        state.profileItems.filter { item ->
            when (provider) {
                FormalLlmProvider.DeepSeek -> item.providerModelLabel.contains("DeepSeek", ignoreCase = true)
                FormalLlmProvider.Kimi -> item.providerModelLabel.contains("Kimi", ignoreCase = true) ||
                    item.providerModelLabel.contains("Moonshot", ignoreCase = true)
                else -> item.providerModelLabel.contains(provider.label, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag(
                if (provider == FormalLlmProvider.Kimi) {
                    "formal-llm-kimi-718-199"
                } else {
                    "formal-llm-deepseek-718-114"
                }
            )
    ) {
        FormalLlmTopBar(title = "LLM API", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProviderStrip(provider = provider, onSelect = { selected ->
                    if (selected == FormalLlmProvider.Custom) showPresets = true else provider = selected
                })
                Spacer(Modifier.height(14.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.label, style = type.style(20f, 28f, FontWeight.Bold, FormalColors.Ink))
                        Text("${profiles.size} 个已保存配置", style = type.style(10f, 15f, color = FormalColors.Muted))
                    }
                    Surface(
                        onClick = {},
                        modifier = Modifier.size(44.dp),
                        color = FormalColors.PrimarySoft,
                        contentColor = FormalColors.Primary,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFF90B9F1))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "新增 ${provider.label} 配置", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            if (profiles.isEmpty()) {
                item {
                    EmptyProviderCard(provider.label)
                }
            } else {
                items(profiles.size, key = { profiles[it].id }) { index ->
                    ProviderAccountCard(
                        item = profiles[index],
                        selected = profiles[index].active,
                        onSelect = { onActivateProfile(profiles[index].id) },
                        onTest = { onTestProfile(profiles[index].id) }
                    )
                }
            }
            item {
                Surface(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    border = BorderStroke(1.dp, Color(0xFF6FA3EB))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = FormalColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("新增 ${provider.label} 配置", modifier = Modifier.weight(1f), style = type.style(12f, 18f, FontWeight.Bold, FormalColors.Primary))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FormalColors.Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                Text(
                    "不同厂家和账户的密钥分别加密保存，切换配置不会覆盖历史数据。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = type.style(9f, 15f, color = FormalColors.Muted)
                )
            }
        }
    }
}

@Composable
private fun ProviderStrip(provider: FormalLlmProvider, onSelect: (FormalLlmProvider) -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FormalLlmProvider.entries.forEach { item ->
                val selected = provider == item
                Column(
                    modifier = Modifier
                        .width(46.dp)
                        .background(if (selected) Color(0xFFEAF2FE) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { onSelect(item) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProviderLogo(item.shortLabel, item)
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, maxLines = 1, overflow = TextOverflow.Clip, style = type.style(7f, 11f, color = if (selected) FormalColors.Primary else FormalColors.Muted))
                }
            }
        }
    }
}

@Composable
private fun ProviderLogo(label: String, provider: FormalLlmProvider) {
    val colors = when (provider) {
        FormalLlmProvider.DeepSeek -> listOf(Color(0xFF7D8CFF), Color(0xFF3D67E9))
        FormalLlmProvider.Kimi -> listOf(Color(0xFF26344D), Color(0xFF101828))
        FormalLlmProvider.Qwen -> listOf(Color(0xFFAD7BFF), Color(0xFF7558EF))
        FormalLlmProvider.Glm -> listOf(Color(0xFF6A91FF), Color(0xFF315CE4))
        FormalLlmProvider.OpenAi -> listOf(Color(0xFFEEF1F5), Color(0xFFDEE3EA))
        FormalLlmProvider.Custom -> listOf(Color(0xFFF2F4F7), Color(0xFFE3E8EF))
    }
    Surface(
        modifier = Modifier.size(30.dp).shadow(4.dp, RoundedCornerShape(8.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(label, color = if (provider == FormalLlmProvider.OpenAi || provider == FormalLlmProvider.Custom) Color(0xFF344054) else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProviderAccountCard(
    item: LlmProfileItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) Color(0xFFEEF5FF) else FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, if (selected) FormalColors.Primary else FormalColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, modifier = Modifier.weight(1f), style = type.style(15f, 22f, FontWeight.Bold, FormalColors.Ink))
                if (selected) Text("当前使用", style = type.style(9f, 15f, FontWeight.Medium, FormalColors.Primary))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .background(if (selected) FormalColors.Primary else Color.Transparent, CircleShape)
                        .border(
                            1.dp,
                            if (selected) FormalColors.Primary else Color(0xFF9CB0C9),
                            CircleShape
                        )
                ) {
                    if (selected) Text("✓", modifier = Modifier.align(Alignment.Center), color = Color.White)
                }
            }
            AccountDetail("默认模型", item.providerModelLabel.substringAfter(" · ", item.providerModelLabel))
            AccountDetail("服务地址", item.baseUrlLabel.removePrefix("https://"))
            AccountDetail("密钥尾号", item.keyStatusLabel)
            FormalLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (selected) FormalColors.Success else Color(0xFF9AA5B5), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(if (selected) "连接正常" else stateLabel(item), modifier = Modifier.weight(1f), style = type.style(9f, 15f, color = if (selected) FormalColors.Success else FormalColors.Muted))
                Text(
                    "测试连接",
                    modifier = Modifier.clickable(onClick = onTest).padding(8.dp),
                    style = type.style(9f, 15f, FontWeight.Medium, FormalColors.Primary)
                )
            }
        }
    }
}

private fun stateLabel(item: LlmProfileItem): String =
    if (item.keyStatusLabel.contains("未保存")) "缺少密钥" else "尚未测试"

@Composable
private fun AccountDetail(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(96.dp), style = type.style(9f, 15f, color = FormalColors.Muted))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.style(9f, 15f, color = Color(0xFF4A5568)))
    }
}

@Composable
private fun EmptyProviderCard(provider: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Text("尚未保存 $provider 配置", modifier = Modifier.padding(18.dp), style = type.style(11f, 18f, color = FormalColors.Muted))
    }
}

@Composable
private fun FormalProviderPresetScreen(
    onBack: () -> Unit,
    onProviderSelected: (FormalLlmProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FormalProviderPresetFilter.All) }
    val rows = remember(query, selectedFilter) {
        formalProviderPresetRows(query, selectedFilter)
    }
    Column(
        modifier = modifier.fillMaxSize().background(FormalColors.Background).testTag("formal-provider-presets-718-273")
    ) {
        FormalLlmTopBar("选择服务预设", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = FormalColors.Ink,
                                    fontSize = 10.sp,
                                    lineHeight = 16.sp
                                ),
                                cursorBrush = SolidColor(FormalColors.Primary),
                                modifier = Modifier.fillMaxWidth().testTag("formal-provider-preset-search")
                            )
                            if (query.isBlank()) {
                                Text(
                                    "搜索厂家、接口格式或地址",
                                    style = type.style(10f, 16f, color = FormalColors.Muted)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormalProviderPresetFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            color = if (selected) FormalColors.Primary else FormalColors.PrimarySoft,
                            contentColor = if (selected) Color.White else Color(0xFF536178),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("formal-provider-filter-${filter.name}")
                        ) { Text(filter.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = type.style(8f, 12f)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    Column {
                        rows.forEachIndexed { index, preset ->
                            ProviderPresetRow(
                                item = preset,
                                onClick = preset.destination?.let { provider ->
                                    { onProviderSelected(provider) }
                                }
                            )
                            if (index != rows.lastIndex) {
                                FormalLine()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPresetRow(item: FormalProviderPresetItem, onClick: (() -> Unit)?) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 10.dp)
            .testTag("formal-provider-row-${item.title}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).background(item.markColor, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(
                item.mark,
                style = type.style(
                    sizeSp = if (item.mark.length > 1) 7f else 10f,
                    lineHeightSp = 12f,
                    weight = FontWeight.Bold,
                    color = if (item.title == "Kimi / Moonshot CN") Color.White else Color(0xFF5C6F8A)
                )
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Ink))
            Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.style(7f, 11f, color = FormalColors.Muted))
        }
        val anthropic = item.format == FormalProviderPresetFormat.Anthropic
        Surface(
            color = if (anthropic) Color(0xFFFFF6E7) else Color(0xFFEEF5FF),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (anthropic) Color(0xFFF2B64F) else Color(0xFF75A7EB))
        ) {
            Text(
                item.format.label,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                style = type.style(7f, 11f, color = if (anthropic) Color(0xFFB16C00) else FormalColors.Primary)
            )
        }
    }
}

@Composable
private fun FormalLlmTopBar(title: String, onBack: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFAFCFE), border = BorderStroke(1.dp, Color(0xFFE0E5ED))) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onBack, modifier = Modifier.size(52.dp), color = Color.Transparent, shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF435875), modifier = Modifier.size(22.dp))
                }
            }
            Text(title, modifier = Modifier.weight(1f), style = type.style(19f, 27f, FontWeight.Bold, FormalColors.Ink))
            Spacer(Modifier.width(52.dp))
        }
    }
}

@Composable
private fun FormalLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDDE4ED)))
}
