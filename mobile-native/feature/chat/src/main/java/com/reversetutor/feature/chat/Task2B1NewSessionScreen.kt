package com.reversetutor.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Composable
fun Task2B1NewSessionRoute(
    createPort: NewSessionCreatePort,
    persistence: NewSessionPersistence,
    /**
     * 2026-10-22 用户拍板（方案B）：新建会话只有一条路——和 AI 直接聊。
     * 这个页面已经不含任何表单：只剩「内置预设 / 收藏」两种取材入口，
     * 选中后把配置交给对话式创建当开场素材。
     *
     * 保留 [tagLibraryPersistence] / [onCreated] 只为不打断既有调用方（含仪器测试）；
     * 会话真正由对话式创建链路落地，本页不再自己创建。
     */
    @Suppress("UNUSED_PARAMETER")
    tagLibraryPersistence: TagLibraryPersistence = InMemoryTagLibraryPersistence(),
    @Suppress("UNUSED_PARAMETER")
    onCreated: (SessionListItem) -> Unit = {},
    onBack: () -> Unit = {},
    initialPrefill: NewSessionPrefillRequest? = null,
    onStartCreation: (NewSessionConfiguration) -> Unit = {},
    modifier: Modifier = Modifier,
    nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    val coordinator = remember(createPort, persistence) {
        NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = createPort,
            nowEpochMillis = nowEpochMillis
        )
    }
    var state by remember { mutableStateOf(coordinator.state) }

    fun sync() {
        state = coordinator.state
    }

    LaunchedEffect(coordinator, initialPrefill?.requestId) {
        coordinator.load()
        sync()
        // 挑战活动带素材进来：不落表单，直接进对话式创建。
        initialPrefill?.let { onStartCreation(it.configuration) }
    }

    fun handleBack() {
        if (state.selectedPresetId != null) {
            coordinator.closePresetDetail()
            sync()
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::handleBack)

    FormalPageFrame(modifier) {
        if (state.selectedPresetId != null) {
            val preset = FormalLearningPresets.byId(checkNotNull(state.selectedPresetId))
            if (preset != null) {
                Task2B1PresetDetailScreen(
                    preset = preset,
                    creating = false,
                    error = null,
                    favorite = state.favorites.any { it.originBuiltInPresetId == preset.id },
                    onBack = ::handleBack,
                    onToggleFavorite = {
                        coordinator.toggleBuiltInFavorite(preset)
                        sync()
                    },
                    onUsePreset = {
                        // 用预设内容开场：进对话式创建，边聊边补，不再填表。
                        onStartCreation(NewSessionConfiguration.fromPreset(preset))
                    }
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                HubHeader(onBack = ::handleBack)
                HubTabs(
                    selected = state.tab,
                    onSelect = {
                        coordinator.selectTab(it)
                        sync()
                    }
                )
                when (state.tab) {
                    NewSessionHubTab.BuiltIn -> FormalPresetLibraryContent(
                        favorites = state.favorites.mapNotNull { it.originBuiltInPresetId }.toSet(),
                        onPreset = {
                            coordinator.openPresetDetail(it.id)
                            sync()
                        },
                        onToggleFavorite = {
                            coordinator.toggleBuiltInFavorite(it)
                            sync()
                        }
                    )
                    else -> FavoritesContent(
                        favorites = state.favorites,
                        onUse = { favoriteId ->
                            state.favorites.firstOrNull { it.id == favoriteId }
                                ?.let { onStartCreation(it.configuration) }
                        },
                        onRemove = {
                            coordinator.removeFavorite(it)
                            sync()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HubHeader(onBack: () -> Unit) {
    FormalTopBar(title = "新建会话", onBack = onBack)
}

@Composable
private fun HubTabs(selected: NewSessionHubTab, onSelect: (NewSessionHubTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 2026-10-22 方案B：新建会话不再有「自定义（填表）」入口，只剩内置预设 / 收藏。
        NewSessionHubTab.entries.filter { it != NewSessionHubTab.Custom }.forEach { tab ->
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).height(44.dp).testTag("new-session-tab-${tab.name}"),
                color = if (selected == tab) FormalColors.Primary else FormalColors.Surface,
                shape = RoundedCornerShape(FormalShapes.CompactRadius),
                border = if (selected == tab) null else BorderStroke(1.dp, FormalColors.Border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        tab.label,
                        style = LocalFormalTypeScale.current.style(
                            11f, 16f, FontWeight.Medium,
                            if (selected == tab) Color.White else FormalColors.Ink
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FormalPresetLibraryContent(
    favorites: Set<String>,
    onPreset: (FormalLearningPreset) -> Unit,
    onToggleFavorite: (FormalLearningPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("new-session-built-in-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(FormalLearningPresets.all, key = { it.id }) { preset ->
            PresetSnapshotCard(
                title = preset.title,
                role = "AI 学生 ${preset.learnerName}",
                goal = preset.goal,
                plan = preset.schedule,
                sourceCount = preset.connectedSources,
                favorite = preset.id in favorites,
                onClick = { onPreset(preset) },
                onToggleFavorite = { onToggleFavorite(preset) }
            )
        }
    }
}

@Composable
private fun PresetSnapshotCard(
    title: String,
    role: String,
    goal: String,
    plan: String,
    sourceCount: Int,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = type.style(13f, 18f, FontWeight.Bold, FormalColors.Ink))
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) FormalColors.Primary else FormalColors.Muted
                    )
                }
            }
            Text("学习者 · $role", style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Primary))
            Text("目标 · $goal", style = type.style(10f, 15f, color = FormalColors.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("计划 · $plan", style = type.style(9f, 14f, color = FormalColors.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("资料 · $sourceCount 项", style = type.style(9f, 14f, color = FormalColors.Muted))
        }
    }
}

@Composable
fun Task2B1PresetDetailScreen(
    preset: FormalLearningPreset,
    creating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onUsePreset: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val type = LocalFormalTypeScale.current
    Column(modifier.fillMaxSize().testTag("formal-preset-detail-${preset.figmaNodeId.replace(':', '-')}")) {
        FormalTopBar(
            title = "预设详情",
            onBack = onBack,
            trailing = {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏"
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PresetSnapshotCard(
                    title = preset.title,
                    role = "AI 学生 ${preset.learnerName}：${preset.learnerProfile}",
                    goal = preset.goal,
                    plan = preset.schedule,
                    sourceCount = preset.connectedSources,
                    favorite = favorite,
                    onClick = {},
                    onToggleFavorite = onToggleFavorite
                )
            }
            item { DetailBlock("学习范围", preset.scopeSummary) }
            item { DetailBlock("世界树", "${preset.episodeTitle}：${preset.episodeBody}") }
            item { DetailBlock("资料摘要", "${preset.sourceTitle} · ${preset.sourceSummary}") }
            error?.let { item { Text(it, style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Danger)) } }
        }
        Button(
            onClick = onUsePreset,
            enabled = !creating,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
            shape = RoundedCornerShape(FormalShapes.CardRadius)
        ) { Text(if (creating) "创建中…" else "使用此预设") }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = type.style(11f, 16f, FontWeight.Bold, FormalColors.Ink))
            Spacer(Modifier.height(5.dp))
            Text(body, style = type.style(10f, 16f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun FavoritesContent(
    favorites: List<NewSessionFavorite>,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val type = LocalFormalTypeScale.current
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有收藏的完整配置", style = type.style(11f, 17f, color = FormalColors.Muted))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("new-session-favorites-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(favorites, key = { it.id }) { favorite ->
            PresetSnapshotCard(
                title = favorite.name,
                role = favorite.configuration.learnerRole,
                goal = favorite.configuration.goal.ifBlank { "未填写" },
                plan = favorite.configuration.plan.ifBlank { "未填写" },
                sourceCount = favorite.configuration.sourceSelections.size,
                favorite = true,
                onClick = { onUse(favorite.id) },
                onToggleFavorite = { onRemove(favorite.id) }
            )
        }
    }
}

@Composable
fun SessionConfigurationTextField(
    label: String,
    value: String,
    testTag: String? = null,
    singleLine: Boolean = false,
    onBoundary: () -> Unit = {},
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .onFocusChanged {
                if (focused && !it.isFocused) onBoundary()
                focused = it.isFocused
            },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 8,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onBoundary() })
    )
}

private class InMemoryTagLibraryPersistence : TagLibraryPersistence {
    private var snapshot: TagLibrarySnapshot? = null

    override fun loadTagLibrary(): TagLibrarySnapshot? = snapshot?.deepCopy()
    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        this.snapshot = snapshot.deepCopy()
    }
}

@Composable
private fun FormalTopBar(
    title: String,
    onBack: () -> Unit,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            color = FormalColors.Surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink))
        when {
            trailing != null -> trailing()
            trailingText != null -> Text(trailingText, style = type.style(9f, 13f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun FormalPageFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(FormalColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.width(maxWidth.coerceAtMost(390.dp)).fillMaxHeight()) { content() }
    }
}
