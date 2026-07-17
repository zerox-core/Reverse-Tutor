package com.reversetutor.preview.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalGlossyIcon
import com.reversetutor.core.design.FormalShapes

data class CommunityUiState(
    val title: String,
    val body: String,
    val statusLabel: String
) {
    companion object {
        fun formal(): CommunityUiState = CommunityUiState(
            title = "社区暂未开放",
            body = "这一入口当前仅作为页面占位。开放方式将在内容边界与可行性确认后决定。",
            statusLabel = "正在进行可行性评估"
        )
    }
}

@Composable
fun CommunityRoute(
    state: CommunityUiState = CommunityUiState.formal(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
    ) {
        CommunityTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(98.dp))
            CommunityIllustration()
            Spacer(Modifier.height(92.dp))
            Text(
                text = state.title,
                color = FormalColors.Ink,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.body,
                color = FormalColors.Muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(286.dp)
            )
            Spacer(Modifier.height(32.dp))
            Surface(
                color = FormalColors.PrimarySoft,
                contentColor = FormalColors.Primary,
                shape = RoundedCornerShape(FormalShapes.PillRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBCD0F5))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(FormalColors.Primary, RoundedCornerShape(99.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.statusLabel, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun CommunityTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(FormalColors.Surface)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回全局图谱",
                tint = Color(0xFF3D5370),
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = "社区",
            color = FormalColors.Ink,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(FormalColors.Divider)
        )
    }
}

@Composable
private fun CommunityIllustration() {
    Box(
        modifier = Modifier
            .size(width = 270.dp, height = 205.dp)
            .semantics { contentDescription = "社区关系插画" },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .offset(x = (-62).dp, y = 22.dp)
                .size(width = 142.dp, height = 142.dp),
            color = Color(0xFFF8FAFE),
            shape = RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBD0F0))
        ) {
            IllustrationLines()
        }
        Surface(
            modifier = Modifier
                .offset(x = 62.dp, y = 12.dp)
                .size(width = 142.dp, height = 142.dp),
            color = Color(0xFFEAF3FF),
            shape = RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBD0F0))
        ) {
            IllustrationLines()
        }
        Surface(
            modifier = Modifier.size(width = 140.dp, height = 176.dp),
            color = Color.White,
            shape = RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB8CDEF)),
            shadowElevation = 5.dp
        ) {
            RelationDiagram()
        }
        FormalGlossyIcon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 8.dp),
            size = 58.dp,
            glyphSize = 24.dp
        )
    }
}

@Composable
private fun IllustrationLines() {
    Column(
        modifier = Modifier.padding(start = 22.dp, top = 28.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                Modifier
                    .width(74.dp)
                    .height(3.dp)
                    .background(Color(0xFFB8C7DC), RoundedCornerShape(99.dp))
            )
        }
    }
}

@Composable
private fun RelationDiagram() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val points = listOf(
            Offset(size.width * 0.50f, size.height * 0.31f),
            Offset(size.width * 0.31f, size.height * 0.47f),
            Offset(size.width * 0.39f, size.height * 0.67f),
            Offset(size.width * 0.63f, size.height * 0.67f),
            Offset(size.width * 0.70f, size.height * 0.48f)
        )
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Color(0xFF7294CC), style = Stroke(width = 2.dp.toPx()))
        val colors = listOf(
            FormalColors.Primary,
            Color(0xFF5DB49F),
            FormalColors.Warning,
            Color(0xFF688BC7),
            Color(0xFF8A79D6)
        )
        points.forEachIndexed { index, point -> drawCircle(colors[index], 7.dp.toPx(), point) }
    }
}
