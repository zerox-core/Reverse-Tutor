package com.reversetutor.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

enum class FormalDraftField(val label: String) {
    Name("世界树名称"),
    Identity("学生角色"),
    Goal("学习目标"),
    Schedule("学习计划时间"),
    Portrait("画像系统"),
    Story("故事剧情"),
    Sources("资料文档")
}

data class FormalDraftEditorState(
    private val values: Map<FormalDraftField, String>
) {
    fun value(field: FormalDraftField): String = values[field].orEmpty()

    fun update(field: FormalDraftField, value: String): FormalDraftEditorState =
        copy(values = values + (field to value))

    companion object {
        fun from(preset: FormalLearningPreset): FormalDraftEditorState =
            FormalDraftEditorState(
                values = mapOf(
                    FormalDraftField.Name to preset.title,
                    FormalDraftField.Identity to listOf(
                        preset.learnerName,
                        preset.learnerProfile
                    ).joinToString("\n"),
                    FormalDraftField.Goal to preset.goal,
                    FormalDraftField.Schedule to preset.schedule,
                    FormalDraftField.Portrait to preset.scopeSummary,
                    FormalDraftField.Story to listOf(
                        preset.episodeTitle,
                        preset.episodeBody
                    ).joinToString("\n"),
                    FormalDraftField.Sources to listOf(
                        preset.sourceTitle,
                        preset.sourceSummary
                    ).joinToString("\n")
                )
            )
    }
}

@Composable
fun FormalDraftEditorScreen(
    field: FormalDraftField,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    BackHandler(onBack = onBack)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(FormalColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.width(maxWidth.coerceAtMost(390.dp)).fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp)
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier.width(44.dp).height(44.dp),
                        color = FormalColors.Surface,
                        shape = RoundedCornerShape(FormalShapes.CardRadius),
                        border = BorderStroke(1.dp, FormalColors.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = FormalColors.Ink
                            )
                        }
                    }
                    Text(
                        field.label,
                        style = type.style(18f, 24f, FontWeight.Bold, FormalColors.Ink),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Text(
                    "编辑后仅保存在当前新建会话草稿中",
                    style = type.style(13f, 19f, color = FormalColors.Muted)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        minLines = if (field == FormalDraftField.Story || field == FormalDraftField.Sources) 7 else 5,
                        textStyle = type.style(15f, 23f, color = FormalColors.Ink),
                        label = {
                            Text(field.label, style = type.style(13f, 18f, color = FormalColors.Muted))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FormalColors.Primary,
                            unfocusedBorderColor = FormalColors.Border,
                            focusedLabelColor = FormalColors.Primary,
                            unfocusedLabelColor = FormalColors.Muted,
                            cursorColor = FormalColors.Primary
                        )
                    )
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                Text(
                    "保存修改",
                    style = type.style(14f, 20f, FontWeight.Bold, Color.White),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
