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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalSyncConflictItem(
    val id: String,
    val title: String,
    val categoryLabel: String,
    val differenceSummary: String,
    val versionSummary: String,
    val icon: FormalSyncConflictIcon
)

enum class FormalSyncConflictIcon { Tree, Calendar }

@Immutable
data class FormalSyncConflictUiState(
    val conflictCount: Int,
    val detectedAtLabel: String,
    val conflictItems: List<FormalSyncConflictItem>,
    val autoMergedCount: Int,
    val autoMergedMaterialCount: Int,
    val localDeviceLabel: String,
    val cloudSpaceLabel: String,
    val cloudStatusLabel: String
)

@Composable
fun FormalSyncConflictOverviewScreen(
    state: FormalSyncConflictUiState,
    onBack: () -> Unit,
    onConflictClick: (String) -> Unit,
    onPostpone: () -> Unit,
    onResolveInOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalBatch6Page(
        title = "同步冲突",
        subtitle = "需要确认 ${state.conflictCount} 项",
        onBack = onBack,
        modifier = modifier,
        trailing = {
            Box(Modifier.size(38.dp).background(colors.primarySoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CloudDone, "云端已连接", tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("未确认前不会覆盖任何内容", color = colors.muted, style = type.style(8f, 13f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormalSecondaryButton("稍后处理", onPostpone, Modifier.weight(1f))
                    FormalPrimaryButton("逐项选择", onResolveInOrder, Modifier.weight(1.8f))
                }
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 18.dp, 22.dp, 22.dp)) {
            item {
                SyncWarningCard(state)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("需要处理")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp)) {
                    Column {
                        state.conflictItems.forEachIndexed { index, item ->
                            SyncConflictRow(item, onConflictClick)
                            if (index != state.conflictItems.lastIndex) SyncDivider()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalInfoBanner("其余内容已自动合并", "${state.autoMergedCount} 条消息与 ${state.autoMergedMaterialCount} 个资料引用已完成同步", success = true)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("连接状态")
                Spacer(Modifier.height(8.dp))
                SyncConnectionCard(state)
            }
        }
    }
}

@Composable
private fun SyncWarningCard(state: FormalSyncConflictUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth(), colors.warningSoft, Color(0xFFE9C27F), PaddingValues(13.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormalGlossySquare(Icons.Rounded.WarningAmber, null, Color(0xFFD3932F), size = 43.dp, glyphSize = 21.dp)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("两端内容都发生了修改", color = Color(0xFF70522B), style = type.style(13f, 19f, FontWeight.SemiBold))
                    Text("可自动合并的内容已经处理，只剩同一设置的差异。", color = colors.muted, style = type.style(8f, 13f))
                }
                Text("${state.conflictCount} 项", color = colors.warning, style = type.style(8f, 13f), modifier = Modifier.background(Color(0xFFFFE9BD), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE9C27F)))
            Spacer(Modifier.height(11.dp))
            Row {
                Text("检测时间：${state.detectedAtLabel}", color = colors.faint, style = type.style(8f, 13f), modifier = Modifier.weight(1f))
                Text("云端已连接", color = colors.success, style = type.style(8f, 13f))
            }
        }
    }
}

@Composable
private fun SyncConflictRow(item: FormalSyncConflictItem, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = item.title) { onClick(item.id) }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.Top
    ) {
        FormalGlossySquare(
            if (item.icon == FormalSyncConflictIcon.Tree) Icons.Rounded.Share else Icons.Rounded.CalendarMonth,
            null,
            if (item.icon == FormalSyncConflictIcon.Tree) Color(0xFF5B92D0) else Color(0xFF45A07D),
            size = 40.dp,
            glyphSize = 19.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(item.title, color = colors.ink, style = type.style(12f, 18f, FontWeight.Medium), modifier = Modifier.weight(1f))
                Text("待选择", color = colors.warning, style = type.style(8f, 13f))
            }
            Text(item.categoryLabel, color = colors.faint, style = type.style(8f, 13f))
            Text(item.differenceSummary, color = colors.muted, style = type.style(8.5f, 13f, FontWeight.Medium))
            Text(item.versionSummary, color = colors.faint, style = type.style(7.5f, 12f))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.padding(top = 28.dp).size(19.dp))
    }
}

@Composable
private fun SyncConnectionCard(state: FormalSyncConflictUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = Color(0xFF4F8CCB), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(state.localDeviceLabel, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
                    Text("当前设备 · 在线", color = colors.faint, style = type.style(7.5f, 12f))
                }
            }
            Box(Modifier.width(1.dp).height(45.dp).background(colors.divider))
            Row(Modifier.weight(1f).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, null, tint = colors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(state.cloudSpaceLabel, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
                    Text(state.cloudStatusLabel, color = colors.faint, style = type.style(7.5f, 12f))
                }
            }
        }
    }
}

@Immutable
data class FormalSyncChoiceOption(
    val source: FormalSyncSource,
    val title: String,
    val subtitle: String,
    val values: List<FormalSyncChoiceValue>
)

@Immutable
data class FormalSyncChoiceValue(
    val label: String,
    val value: String
)

enum class FormalSyncSource { Device, Cloud }

@Immutable
data class FormalSyncChoiceUiState(
    val title: String,
    val stepLabel: String,
    val progressFraction: Float,
    val differenceCount: Int,
    val differenceSummary: String,
    val options: List<FormalSyncChoiceOption>,
    val selectedSource: FormalSyncSource
)

@Composable
fun FormalSyncConflictChoiceScreen(
    state: FormalSyncChoiceUiState,
    onBack: () -> Unit,
    onSourceSelected: (FormalSyncSource) -> Unit,
    onReturnToList: () -> Unit,
    onConfirmAndContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalBatch6Page(
        title = "选择保留内容",
        subtitle = "${state.title} · ${state.stepLabel}",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前选择：${state.options.firstOrNull { it.source == state.selectedSource }?.title.orEmpty()}", color = colors.muted, style = type.style(8f, 13f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormalSecondaryButton("返回列表", onReturnToList, Modifier.weight(1f))
                    FormalPrimaryButton("确认并继续", onConfirmAndContinue, Modifier.weight(1.8f))
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(colors.divider)) {
                Box(Modifier.fillMaxWidth(state.progressFraction.coerceIn(0f, 1f)).height(3.dp).background(Color(0xFF4B8CCB)))
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 24.dp)) {
                item {
                    FormalOutlinedCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormalGlossySquare(Icons.Rounded.Share, null, Color(0xFF5B92D0), size = 38.dp, glyphSize = 18.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("世界树设置", color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
                                Text(state.differenceSummary, color = colors.faint, style = type.style(8f, 13f))
                            }
                            Text("${state.differenceCount} 项差异", color = colors.primary, style = type.style(8f, 13f), modifier = Modifier.background(colors.primarySoft, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 5.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    state.options.forEach { option ->
                        SyncChoiceCard(option, option.source == state.selectedSource, onSourceSelected)
                        Spacer(Modifier.height(16.dp))
                    }
                    FormalInfoBanner("确认后，本次选择会同步到另一端", "返回列表不会丢失当前选择")
                }
            }
        }
    }
}

@Composable
private fun SyncChoiceCard(option: FormalSyncChoiceOption, selected: Boolean, onSelect: (FormalSyncSource) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val accent = if (option.source == FormalSyncSource.Device) Color(0xFF4F8CCB) else colors.success
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClickLabel = option.title) { onSelect(option.source) },
        color = if (selected) colors.primarySoft else colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else colors.border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormalGlossySquare(
                    if (option.source == FormalSyncSource.Device) Icons.Rounded.PhoneAndroid else Icons.Rounded.Cloud,
                    null,
                    accent,
                    size = 37.dp,
                    glyphSize = 17.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.title, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
                    Text(option.subtitle, color = colors.faint, style = type.style(8f, 13f))
                }
                Box(
                    Modifier.size(20.dp).background(if (selected) accent else colors.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.dp, if (selected) accent else colors.border)) {
                        if (selected) Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            option.values.forEachIndexed { index, value ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Text(value.label, color = colors.faint, style = type.style(10f, 15f), modifier = Modifier.weight(1f))
                    Text(value.value, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
                }
                if (index != option.values.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun SyncDivider() {
    val colors = formalBatch6Colors()
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
}
