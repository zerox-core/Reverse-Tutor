package com.reversetutor.preview.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

internal object ActivityAnnouncementPresentation {
    val DialogElevation = 0.dp
    val HeroColors = listOf(Color(0xFFE7F8FF), Color(0xFFF1ECFF))
    const val UsesOutlinedHero = false
}

@Composable
fun ActivityAnnouncementDialog(
    onDismiss: () -> Unit,
    onViewChallenge: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .testTag("formal-activity-announcement-716-470"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = FormalColors.SurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = ActivityAnnouncementPresentation.DialogElevation
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(end = 34.dp)) {
                            Text("新活动已发布", style = type.style(20f, 27f, FontWeight.Bold, FormalColors.Ink))
                            Text(
                                "可从挑战页加入，加入后会自动生成挑战会话。",
                                style = type.style(10f, 16f, color = FormalColors.Muted)
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
                            color = Color(0xFFEDEFF3),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = FormalColors.Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(FormalShapes.CardRadius),
                        border = if (ActivityAnnouncementPresentation.UsesOutlinedHero) {
                            BorderStroke(1.dp, FormalColors.Primary.copy(alpha = .16f))
                        } else {
                            null
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(ActivityAnnouncementPresentation.HeroColors),
                                    shape = RoundedCornerShape(FormalShapes.CardRadius)
                                )
                                .padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "21天 Python\n学习挑战",
                                style = type.style(20f, 27f, FontWeight.Bold, FormalColors.Ink),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "掌握核心语法，用每日挑战构建稳定的编程学习节奏。",
                                style = type.style(9f, 14f, color = FormalColors.Muted),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Surface(
                        color = Color(0xFFF3F4F7),
                        shape = RoundedCornerShape(FormalShapes.CardRadius)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "首次进入新建页时提醒。关闭后不打断创建流程。",
                                style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Ink)
                            )
                            Spacer(Modifier.height(7.dp))
                            Text("后台可按活动发布时间控制展示。", style = type.style(9f, 13f, color = FormalColors.Muted))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(FormalShapes.CardRadius),
                            border = BorderStroke(1.dp, FormalColors.Border)
                        ) {
                            Text("稍后再说", style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Muted))
                        }
                        Button(
                            onClick = onViewChallenge,
                            modifier = Modifier.weight(1.65f).height(44.dp),
                            shape = RoundedCornerShape(FormalShapes.CardRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary)
                        ) {
                            Text("查看挑战", style = type.style(10f, 15f, FontWeight.Medium, Color.White))
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
