package com.reversetutor.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.llm.LlmProfileInput

@Composable
fun FigmaSettingsScreen(
    llmProfileState: LlmProfileSettingsUiState,
    localDataWipeState: LocalDataWipeUiState,
    onSaveLlmProfile: (LlmProfileInput) -> Unit,
    onTestLlmProfile: (String) -> Unit,
    onOpenImportExport: () -> Unit,
    onWipeLocalData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProfile = llmProfileState.profileItems.firstOrNull { it.active }
        ?: llmProfileState.profileItems.firstOrNull()
    val preset = llmProfileState.presets.firstOrNull()
    var model by remember(preset?.model) { mutableStateOf(preset?.model ?: "gpt-4.1-mini") }
    var apiKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(true) }
    var showWipeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("模型配置")
        SettingsSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("当前连接", color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        activeProfile?.providerModelLabel ?: "OpenAI 兼容 · $model",
                        color = SettingsMuted,
                        fontSize = 11.sp
                    )
                }
                StatusChip(if (activeProfile == null) "待配置" else "已连接")
            }
            Surface(
                color = Color(0xFFF7F8FC),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE1ED))
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("••••••••••••••••") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = {}, enabled = activeProfile != null, shape = RoundedCornerShape(16.dp)) {
                    Text("管理连接", fontSize = 12.sp)
                }
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { activeProfile?.id?.let(onTestLlmProfile) },
                    enabled = activeProfile != null,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SettingsPrimary)
                ) {
                    Text("测试连接", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionHeading("模型预模板")
        SettingsSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OpenAI / A", color = SettingsInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
                    Text(if (expanded) "⌃" else "⌄", modifier = Modifier.padding(8.dp), color = SettingsInk)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("GPT-4o", "GPT-4.1", "o3-mini").forEach { Text(it, color = SettingsInk, fontSize = 12.sp) }
            }
            if (expanded) {
                Surface(
                    color = Color(0xFFFCFCFF),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE1ED))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入模型名", fontSize = 11.sp) },
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val source = preset ?: return@Button
                                    onSaveLlmProfile(
                                        LlmProfileInput(
                                            name = model,
                                            provider = source.provider,
                                            model = model,
                                            baseUrl = source.baseUrl.orEmpty(),
                                            apiKey = apiKey
                                        )
                                    )
                                    apiKey = ""
                                },
                                enabled = model.isNotBlank() && preset != null,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SettingsPrimary)
                            ) { Text("保存", fontSize = 12.sp) }
                        }
                        Text("保存后显示在下方，可点击快速使用", color = SettingsMuted, fontSize = 10.sp)
                        StatusChip(model)
                    }
                }
            }
            listOf("Kimi", "DeepSeek", "GLM", "自定义").forEach { label ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = SettingsInk, fontSize = 13.sp)
                    Text("⌄", color = SettingsInk, fontSize = 12.sp)
                }
                Divider()
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionHeading("导入与导出")
        SettingsSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("本地优先保存，会话、Memory 和设置可迁移。", modifier = Modifier.weight(1f), color = SettingsMuted, fontSize = 11.sp)
                OutlinedButton(onClick = onOpenImportExport, shape = RoundedCornerShape(16.dp)) { Text("导出数据", fontSize = 11.sp) }
                Spacer(Modifier.padding(3.dp))
                OutlinedButton(onClick = onOpenImportExport, shape = RoundedCornerShape(16.dp)) { Text("导入", fontSize = 11.sp) }
            }
        }

        SectionHeading("危险区域")
        SettingsSurface {
            Text("清空本地数据会删除会话、资料索引和模型配置。", color = SettingsMuted, fontSize = 11.sp)
            TextButton(onClick = { showWipeDialog = true }) { Text("清空本地数据", color = Color(0xFFC53F3F)) }
            localDataWipeState.statusLabel?.let { Text(it, color = SettingsPrimary, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(localDataWipeState.confirmationTitle) },
            text = { Text(localDataWipeState.confirmationBody) },
            confirmButton = {
                TextButton(onClick = { showWipeDialog = false; onWipeLocalData() }) {
                    Text(localDataWipeState.confirmLabel, color = Color(0xFFC53F3F))
                }
            },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text(localDataWipeState.dismissLabel) } }
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, color = SettingsInk, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1217203A), spotColor = Color(0x1217203A)),
        color = Color(0xFFFCFCFF),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE1ED))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun StatusChip(label: String) {
    Surface(color = Color(0xFFF0F1FF), contentColor = SettingsPrimary, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9DCFF))) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp)
    }
}

@Composable
private fun Divider() {
    Surface(modifier = Modifier.fillMaxWidth().height(1.dp), color = Color(0xFFDDE1ED)) {}
}

private val SettingsPrimary = Color(0xFF575CE6)
private val SettingsInk = Color(0xFF202637)
private val SettingsMuted = Color(0xFF687186)
