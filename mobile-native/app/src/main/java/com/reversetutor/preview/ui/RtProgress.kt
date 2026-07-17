package com.reversetutor.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign

@Composable
fun RtLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    showFraction: Boolean = false,
    total: Int? = null
) {
    val spacing = ReverseTutorDesign.spacing
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space2)
    ) {
        if (label != null || showFraction) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                label?.let { Text(text = it, style = MaterialTheme.typography.labelMedium) }
                if (showFraction && total != null) {
                    Text(
                        text = "${(progress * total).toInt()} / $total",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(primary.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(primary)
            )
        }
    }
}

@Composable
fun RtCircularProgress(
    modifier: Modifier = Modifier,
    size: RtProgressSize = RtProgressSize.Medium
) {
    val sizeDp = when (size) {
        RtProgressSize.Small -> 16.dp
        RtProgressSize.Medium -> 24.dp
        RtProgressSize.Large -> 32.dp
    }
    CircularProgressIndicator(
        modifier = modifier.size(sizeDp),
        strokeWidth = when (size) {
            RtProgressSize.Small -> 2.dp
            RtProgressSize.Medium -> 3.dp
            RtProgressSize.Large -> 4.dp
        }
    )
}

enum class RtProgressSize {
    Small,
    Medium,
    Large
}

@Composable
fun RtLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val surfaces = ReverseTutorDesign.surfaces
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ReverseTutorDesign.shapes.radiusMedium))
            .background(surfaces.cardBorder.copy(alpha = 0.5f))
    )
}
