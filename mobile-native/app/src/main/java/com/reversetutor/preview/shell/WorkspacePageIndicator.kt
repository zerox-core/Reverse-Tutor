package com.reversetutor.preview.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val WorkspaceIndicatorProgressKey =
    SemanticsPropertyKey<Float>("WorkspaceIndicatorProgress")

internal fun workspaceIndicatorProgress(
    page: Int,
    offsetFraction: Float,
    pageCount: Int
): Float {
    if (pageCount <= 0) return 0f
    return (page + offsetFraction).coerceIn(0f, (pageCount - 1).toFloat())
}

@Composable
internal fun WorkspacePageIndicator(
    pagePosition: Float,
    pageCount: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 0) return

    val clampedPosition = pagePosition.coerceIn(0f, (pageCount - 1).toFloat())
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = "打开本周学习概览",
            onClick = onClick
        )
    }

    Box(
        modifier = modifier
            .width(122.dp)
            .height(56.dp)
            .then(interactionModifier)
            .testTag("workspace-page-indicator")
            .semantics {
                this[WorkspaceIndicatorProgressKey] = clampedPosition
                if (onClick != null) {
                    contentDescription = "本周学习概览"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(122.dp)
                .height(38.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(99.dp),
                    ambientColor = Color(0x242E3B54),
                    spotColor = Color(0x242E3B54)
                ),
            shape = RoundedCornerShape(99.dp),
            color = Color(0xFF505766)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val anchorSpacing = 16.dp.toPx()
                val firstAnchorX = center.x - anchorSpacing * (pageCount - 1) / 2f
                val dotRadius = 3.5.dp.toPx()

                repeat(pageCount) { index ->
                    val proximity = (1f - abs(index - clampedPosition)).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color(0xFF8A92A2).copy(alpha = 0.72f - 0.34f * proximity),
                        radius = dotRadius * (1f + 0.12f * proximity),
                        center = Offset(
                            x = firstAnchorX + anchorSpacing * index,
                            y = center.y
                        )
                    )
                }

                val capsuleWidth = 22.dp.toPx()
                val capsuleHeight = 8.dp.toPx()
                val capsuleCenterX = firstAnchorX + anchorSpacing * clampedPosition
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(
                        x = capsuleCenterX - capsuleWidth / 2f,
                        y = center.y - capsuleHeight / 2f
                    ),
                    size = Size(capsuleWidth, capsuleHeight),
                    cornerRadius = CornerRadius(capsuleHeight / 2f)
                )
            }
        }
    }
}
