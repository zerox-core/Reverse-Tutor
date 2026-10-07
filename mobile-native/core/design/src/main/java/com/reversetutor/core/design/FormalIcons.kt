package com.reversetutor.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FormalGlossyIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = FormalColors.Primary,
    size: Dp = 40.dp,
    glyphSize: Dp = 20.dp
) {
    val shape = RoundedCornerShape(FormalShapes.IconRadius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, shape, ambientColor = Color(0x1A1B3F73), spotColor = Color(0x241B3F73))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(red = (color.red + 0.14f).coerceAtMost(1f)),
                        color
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(glyphSize)
        )
    }
}
