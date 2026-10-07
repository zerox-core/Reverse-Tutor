package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.theme.ReverseTutorTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemShowcase(modifier: Modifier = Modifier) {
    val spacing = ReverseTutorDesign.spacing
    var toggle by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.5f) }
    var selectedChips by remember { mutableStateOf(setOf("经济学")) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ReverseTutorDesign.surfaces.home
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(spacing.space5),
            verticalArrangement = Arrangement.spacedBy(spacing.space4)
        ) {
            Text(
                text = "Reverse Tutor 设计系统",
                style = MaterialTheme.typography.displaySmall
            )

            RtSectionTitle(
                title = "按钮 Button",
                description = "Primary / Secondary / Quiet / Destructive"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                RtButton(label = "Primary", onClick = {})
                RtButton(label = "Secondary", onClick = {}, tone = RtButtonTone.Secondary)
                RtButton(label = "Quiet", onClick = {}, tone = RtButtonTone.Quiet)
                RtButton(label = "Destructive", onClick = {}, tone = RtButtonTone.Destructive)
                RtButton(
                    label = "Loading",
                    onClick = {},
                    loading = true,
                    size = RtButtonSize.Small
                )
            }

            RtSectionTitle(
                title = "卡片 Card",
                description = "Filled / Outlined / Elevated"
            )
            RtCard {
                Text("Filled Card", style = MaterialTheme.typography.bodyLarge)
            }
            RtCard(variant = RtCardVariant.Outlined) {
                Text("Outlined Card", style = MaterialTheme.typography.bodyLarge)
            }
            RtCard(variant = RtCardVariant.Elevated) {
                Text("Elevated Card", style = MaterialTheme.typography.bodyLarge)
            }

            RtSectionTitle(
                title = "标签 Chip",
                description = "Single / Multi / Selected / Disabled"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                RtChip(label = "未选中", selected = false, onClick = {})
                RtChip(label = "已选中", selected = true, onClick = {})
                RtChip(label = "禁用", selected = false, enabled = false, onClick = {})
                listOf("经济学", "重点", "IS-LM", "＋ 新增").forEach { label ->
                    RtChip(
                        label = label,
                        selected = selectedChips.contains(label),
                        onClick = {
                            selectedChips = if (selectedChips.contains(label)) {
                                selectedChips - label
                            } else {
                                selectedChips + label
                            }
                        }
                    )
                }
            }

            RtSectionTitle(
                title = "图标 Icon",
                description = "Material Icons with semantic descriptions"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                RtIcon(key = RtIconKey.Folder, size = 24.dp)
                RtIcon(key = RtIconKey.FileUpload, size = 24.dp)
                RtIcon(key = RtIconKey.GraphicEq, size = 24.dp)
                RtIcon(key = RtIconKey.Psychology, size = 24.dp)
                RtIcon(key = RtIconKey.Check, size = 24.dp)
                RtIcon(key = RtIconKey.Trophy, size = 24.dp)
            }

            RtSectionTitle(
                title = "开关与滑块"
            )
            RtCard {
                RtToggleRow(
                    label = "自动引用资料",
                    checked = toggle,
                    onCheckedChange = { toggle = it }
                )
                RtSliderRow(
                    label = "追问强度",
                    valueLabel = "${(slider * 100).toInt()}%",
                    value = slider,
                    onValueChange = { slider = it }
                )
            }

            RtSectionTitle(
                title = "进度 Progress"
            )
            RtLinearProgress(
                progress = 0.57f,
                label = "学习进度",
                showFraction = true,
                total = 21
            )

            RtSectionTitle(
                title = "空状态 Empty"
            )
            RtEmptyState(
                title = "暂无会话",
                description = "点击新建按钮开始学习之旅",
                icon = RtIconKey.School
            )
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
fun DesignSystemShowcaseLightPreview() {
    ReverseTutorTheme(darkTheme = false) {
        DesignSystemShowcase()
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
fun DesignSystemShowcaseDarkPreview() {
    ReverseTutorTheme(darkTheme = true) {
        DesignSystemShowcase()
    }
}
