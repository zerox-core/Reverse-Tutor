package com.reversetutor.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalGlossyIcon
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import kotlinx.coroutines.launch

private enum class FormalNewSessionPage {
    PresetLibrary,
    CustomTree,
    PresetDetail
}

@Composable
fun FigmaNewSessionRoute(
    sessionRepository: SessionRepository,
    onCreated: (SessionListItem) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(FormalNewSessionPage.PresetLibrary) }
    var selectedPreset by remember { mutableStateOf(FormalLearningPresets.all.first()) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = page != FormalNewSessionPage.PresetLibrary) {
        page = FormalNewSessionPage.PresetLibrary
        error = null
    }

    fun createFromPreset() {
        if (creating) return
        creating = true
        error = null
        scope.launch {
            runCatching {
                sessionRepository.createSession(
                    input = selectedPreset.toDraft().toCreationInput(),
                    nowEpochMillis = System.currentTimeMillis()
                )
            }.onSuccess { created ->
                onCreated(created.session.toSessionListItem(avatarVisible = true))
            }.onFailure {
                creating = false
                error = "暂时无法创建会话，请稍后重试。"
            }
        }
    }

    when (page) {
        FormalNewSessionPage.PresetLibrary -> FormalPresetLibraryScreen(
            onBack = onBack,
            onCustom = { page = FormalNewSessionPage.CustomTree },
            onPreset = { preset ->
                selectedPreset = preset
                page = FormalNewSessionPage.PresetDetail
            },
            modifier = modifier
        )

        FormalNewSessionPage.CustomTree -> FormalCustomTreeScreen(
            onBack = { page = FormalNewSessionPage.PresetLibrary },
            modifier = modifier
        )

        FormalNewSessionPage.PresetDetail -> FormalPresetDetailScreen(
            preset = selectedPreset,
            creating = creating,
            error = error,
            onBack = {
                page = FormalNewSessionPage.PresetLibrary
                error = null
            },
            onUsePreset = ::createFromPreset,
            modifier = modifier
        )
    }
}

@Composable
fun FormalPresetLibraryScreen(
    onBack: () -> Unit,
    onCustom: () -> Unit,
    onPreset: (FormalLearningPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    val presets = remember(query, category) {
        FormalLearningPresets.all.filter { preset ->
            (category == "全部" || preset.category == category) &&
                (query.isBlank() || preset.title.contains(query, ignoreCase = true) ||
                    preset.learnerName.contains(query, ignoreCase = true))
        }
    }

    FormalPageFrame(modifier = modifier.testTag("formal-preset-library-716-611")) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(4) }) {
                FormalTopBar(title = "学习预设", onBack = onBack, trailingText = "12 个场景")
            }
            item(span = { GridItemSpan(4) }) { Spacer(Modifier.height(6.dp)) }
            item(span = { GridItemSpan(4) }) {
                SearchField(query = query, onQueryChange = { query = it })
            }
            item(span = { GridItemSpan(4) }) { Spacer(Modifier.height(4.dp)) }
            item(span = { GridItemSpan(4) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetActionCard(
                        title = "自定义",
                        subtitle = "从空白建立",
                        icon = Icons.Filled.AccountTree,
                        color = FormalColors.Primary,
                        onClick = onCustom,
                        modifier = Modifier.weight(1f)
                    )
                    PresetActionCard(
                        title = "导入配置",
                        subtitle = "读取世界树",
                        icon = Icons.Filled.CloudUpload,
                        color = Color(0xFF4E6B9B),
                        modifier = Modifier.weight(1f)
                    )
                    PresetActionCard(
                        title = "资料源",
                        subtitle = "上传与管理",
                        icon = Icons.Filled.Folder,
                        color = FormalColors.Success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(span = { GridItemSpan(4) }) { Spacer(Modifier.height(2.dp)) }
            item(span = { GridItemSpan(4) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "升学考试", "语言", "技能", "兴趣").forEach { label ->
                        LibraryFilterChip(
                            label = label,
                            selected = category == label,
                            onClick = { category = label }
                        )
                    }
                }
            }
            if (presets.isEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Text(
                        text = "没有匹配的学习预设",
                        style = type.style(12f, 18f, color = FormalColors.Muted),
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(presets, key = { it.id }) { preset ->
                    PresetLibraryCard(preset = preset, onClick = { onPreset(preset) })
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = type.style(11f, 17f, color = FormalColors.Ink),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text("搜索考试、语言、技能或兴趣", style = type.style(11f, 17f, color = FormalColors.Muted))
                        }
                        inner()
                    }
                }
            )
        }
    }
}

@Composable
private fun PresetActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = modifier
            .height(70.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = color.copy(alpha = .07f),
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, color.copy(alpha = .32f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(title, style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Ink), maxLines = 1)
            }
            Text(subtitle, style = type.style(9f, 13f, color = FormalColors.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun LibraryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onClick,
        color = if (selected) FormalColors.Primary else FormalColors.Surface,
        contentColor = if (selected) Color.White else FormalColors.Muted,
        shape = RoundedCornerShape(FormalShapes.CompactRadius),
        border = if (selected) null else BorderStroke(1.dp, FormalColors.Border)
    ) {
        Text(
            text = label,
            style = type.style(9f, 14f, FontWeight.Medium, if (selected) Color.White else FormalColors.Muted),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun PresetLibraryCard(preset: FormalLearningPreset, onClick: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(104.dp),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(preset.avatarRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(45.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                preset.title,
                style = type.style(9f, 12f, FontWeight.Medium, FormalColors.Ink),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Text(preset.learnerName, style = type.style(8f, 11f, color = FormalColors.Muted), maxLines = 1)
        }
    }
}

@Composable
fun FormalPresetDetailScreen(
    preset: FormalLearningPreset,
    creating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onUsePreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    FormalPageFrame(
        modifier = modifier.testTag("formal-preset-detail-${preset.figmaNodeId.replace(':', '-')}")
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 106.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { FormalTopBar(title = "预设详情", onBack = onBack, trailingIcon = Icons.Filled.BookmarkBorder) }
                item { PresetIdentityCard(preset) }
                item {
                    Column {
                        Text("学习剧本", style = type.style(14f, 20f, FontWeight.Bold, FormalColors.Ink))
                        Text("目标、范围和剧情会共同决定每次讲解任务", style = type.style(9f, 13f, color = FormalColors.Muted))
                    }
                }
                item { PresetGoalCard(preset) }
                item { PresetScopeCard(preset) }
                item { PresetEpisodeCard(preset) }
                item { EpisodeDots() }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("资料库", style = type.style(14f, 20f, FontWeight.Bold, FormalColors.Ink))
                        Text("已连接 ${preset.connectedSources} 项", style = type.style(9f, 13f, color = FormalColors.Muted))
                    }
                }
                item { PresetSourceCard(preset) }
                error?.let { message ->
                    item {
                        Text(message, style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Danger))
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = FormalColors.Background.copy(alpha = .97f),
                shadowElevation = 8.dp
            ) {
                Button(
                    enabled = !creating,
                    onClick = onUsePreset,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp).fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FormalColors.Primary,
                        disabledContainerColor = FormalColors.Primary.copy(alpha = .45f)
                    )
                ) {
                    Text(
                        if (creating) "正在创建" else "使用这个预设",
                        style = type.style(13f, 18f, FontWeight.Bold, Color.White)
                    )
                    Spacer(Modifier.width(28.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetIdentityCard(preset: FormalLearningPreset) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(154.dp),
        color = FormalColors.PrimarySoft,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = .3f))
    ) {
        Box(Modifier.padding(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(end = 68.dp)) {
                Text(preset.title, style = type.style(18f, 25f, FontWeight.Bold, FormalColors.Ink), maxLines = 2)
                Text(
                    "你负责讲解，${preset.learnerName}会追问、复述并等待你纠正。",
                    style = type.style(10f, 15f, color = FormalColors.Muted),
                    maxLines = 2
                )
            }
            Image(
                painter = painterResource(preset.avatarRes),
                contentDescription = "${preset.learnerName}的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).size(58.dp).clip(CircleShape)
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(preset.learnerName, style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Ink))
                    Text(preset.learnerProfile, style = type.style(9f, 13f, color = FormalColors.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(preset.schedule, style = type.style(8f, 12f, FontWeight.Medium, FormalColors.Primary), maxLines = 1)
                }
                Text("更换", style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PresetGoalCard(preset: FormalLearningPreset) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            FormalGlossyIcon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = null,
                size = 34.dp,
                glyphSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("学习目标", style = type.style(9f, 13f, color = FormalColors.Muted))
                Text(preset.goal, style = type.style(11f, 17f, FontWeight.Medium, FormalColors.Ink), maxLines = 2)
                Spacer(Modifier.height(5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    preset.goalTags.forEach { tag ->
                        Surface(color = FormalColors.PrimarySoft, shape = RoundedCornerShape(FormalShapes.CompactRadius)) {
                            Text(tag, style = type.style(8f, 12f, color = FormalColors.Primary), modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetScopeCard(preset: FormalLearningPreset) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text("学习范围", style = type.style(9f, 13f, color = FormalColors.Muted))
                Spacer(Modifier.width(28.dp))
                Text(preset.scopeSummary, style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary), maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                preset.scopeModules.forEachIndexed { index, module ->
                    Column(Modifier.weight(1f)) {
                        Text(module.title, style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(module.count, style = type.style(8f, 12f, color = FormalColors.Muted))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(FormalColors.Border)) {
                            Box(
                                Modifier.fillMaxWidth(module.progress).fillMaxHeight()
                                    .background(if (index == 2) FormalColors.Success else FormalColors.Primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetEpisodeCard(preset: FormalLearningPreset) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(119.dp),
        color = FormalColors.PrimarySoft,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = .3f))
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Surface(color = FormalColors.Primary, shape = RoundedCornerShape(FormalShapes.CompactRadius)) {
                    Text("第 1 幕", style = type.style(8f, 12f, FontWeight.Bold, Color.White), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(preset.episodeTitle, style = type.style(13f, 18f, FontWeight.Bold, FormalColors.Ink))
                Text(preset.episodeBody, style = type.style(9f, 14f, color = FormalColors.Muted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text("1 / 3", style = type.style(8f, 12f, FontWeight.Medium, FormalColors.Primary))
            }
            Image(
                painter = painterResource(preset.storyRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(122.dp).fillMaxHeight().clip(RoundedCornerShape(FormalShapes.CardRadius))
            )
        }
    }
}

@Composable
private fun EpisodeDots() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(3) { index ->
            Box(
                Modifier.padding(horizontal = 3.dp).size(if (index == 0) 16.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (index == 0) FormalColors.Primary else FormalColors.Border)
            )
        }
    }
}

@Composable
private fun PresetSourceCard(preset: FormalLearningPreset) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceTypeStack()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.sourceTitle, style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preset.sourceSummary, style = type.style(8f, 12f, color = FormalColors.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = FormalColors.SuccessSoft, shape = RoundedCornerShape(FormalShapes.CompactRadius)) {
                Text("管理 / 上传", style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Success), modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SourceTypeStack() {
    Row {
        listOf("题", "卷", "记").forEachIndexed { index, label ->
            Surface(
                modifier = Modifier.size(30.dp),
                color = listOf(FormalColors.PrimarySoft, FormalColors.SuccessSoft, FormalColors.WarningSoft)[index],
                shape = RoundedCornerShape(FormalShapes.CompactRadius)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = LocalFormalTypeScale.current.style(
                            8f,
                            12f,
                            FontWeight.Medium,
                            listOf(FormalColors.Primary, FormalColors.Success, FormalColors.Warning)[index]
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FormalCustomTreeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalFormalTypeScale.current
    val fields = listOf(
        CustomTreeField("学生角色", "头像、姓名、性格、认知与互动习惯", "未填写", Icons.Filled.Person, FormalColors.Primary),
        CustomTreeField("学习目标", "用自己的话写下希望抵达的结果", "已填写", Icons.Filled.MyLocation, FormalColors.Warning),
        CustomTreeField("学习计划时间", "周期、每周节奏与关键阶段节点", "未填写", Icons.Filled.Event, FormalColors.Success),
        CustomTreeField("画像系统", "自由增加能力、习惯与情绪等维度", "1 项", Icons.Filled.Badge, Color(0xFF805CC7)),
        CustomTreeField("故事剧情", "背景、人物关系与可自由增删的剧情阶段", "未填写", Icons.Filled.AutoStories, FormalColors.Danger),
        CustomTreeField("资料文档", "上传教材、笔记、错题、图片或其他文件", "4 份", Icons.Filled.Description, Color(0xFF4C8EB5))
    )

    FormalPageFrame(modifier = modifier.testTag("formal-custom-tree-716-494")) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FormalTopBar(
                        title = "自定义世界树",
                        onBack = onBack,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(FormalColors.Success))
                                Text("草稿已保存", style = type.style(9f, 13f, color = FormalColors.Muted))
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
                item {
                    Text("把你的世界写下来", style = type.style(22f, 30f, FontWeight.Bold, FormalColors.Ink))
                    Text("按你的方式填写，所有内容都可以随时修改或补充。", style = type.style(10f, 16f, color = FormalColors.Muted))
                }
                item { CustomTreeNameCard() }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("世界树内容", style = type.style(14f, 20f, FontWeight.Bold, FormalColors.Ink))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = FormalColors.Primary, modifier = Modifier.size(16.dp))
                            Text("新增栏目", style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary))
                        }
                    }
                }
                items(fields.size) { index -> CustomTreeFieldRow(fields[index]) }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = FormalColors.Surface,
                        shape = RoundedCornerShape(FormalShapes.CardRadius),
                        border = BorderStroke(1.dp, FormalColors.Border)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = FormalColors.Primary)
                            Spacer(Modifier.width(10.dp))
                            Text("新增自定义栏目", style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Primary))
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = FormalColors.Background.copy(alpha = .97f),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp)) {
                    Text("未填写完整也可以预览和保存", style = type.style(9f, 13f, color = FormalColors.Muted))
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        color = FormalColors.Primary,
                        shape = RoundedCornerShape(FormalShapes.CardRadius)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("预览世界树", style = type.style(13f, 18f, FontWeight.Bold, Color.White))
                            Spacer(Modifier.width(26.dp))
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class CustomTreeField(
    val title: String,
    val summary: String,
    val status: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@Composable
private fun CustomTreeNameCard() {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("世界树草稿", style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary))
                Spacer(Modifier.height(4.dp))
                Text("高三数学讲题冲刺", style = type.style(14f, 20f, FontWeight.Bold, FormalColors.Ink))
                Text("已填写 2 项 · 7 个内容栏目", style = type.style(9f, 13f, color = FormalColors.Muted))
            }
            Surface(color = FormalColors.PrimarySoft, shape = RoundedCornerShape(FormalShapes.CompactRadius)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = FormalColors.Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑名称", style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary))
                }
            }
        }
    }
}

@Composable
private fun CustomTreeFieldRow(field: CustomTreeField) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FormalGlossyIcon(field.icon, null, color = field.color, size = 40.dp, glyphSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(field.title, style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink))
                    Text(
                        field.status,
                        style = type.style(
                            8f,
                            12f,
                            FontWeight.Medium,
                            if (field.status == "已填写") FormalColors.Primary else FormalColors.Muted
                        )
                    )
                }
                Text(field.summary, style = type.style(9f, 13f, color = FormalColors.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.size(28.dp),
                color = FormalColors.SurfaceElevated,
                shape = CircleShape,
                border = BorderStroke(1.dp, FormalColors.Border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FormalTopBar(
    title: String,
    onBack: () -> Unit,
    trailingText: String? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            color = FormalColors.Surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, FormalColors.Border),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = FormalColors.Ink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink), modifier = Modifier.weight(1f), maxLines = 1)
        when {
            trailing != null -> trailing()
            trailingText != null -> Text(trailingText, style = type.style(9f, 13f, color = FormalColors.Muted))
            trailingIcon != null -> Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(trailingIcon, contentDescription = "收藏", tint = FormalColors.Ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FormalPageFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(FormalColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.width(maxWidth.coerceAtMost(390.dp)).fillMaxHeight()) {
            content()
        }
    }
}
