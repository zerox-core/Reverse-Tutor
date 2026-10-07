package com.reversetutor.preview.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign

enum class RtCardVariant {
    Filled,
    Outlined,
    Elevated
}

@Composable
fun RtCard(
    modifier: Modifier = Modifier,
    variant: RtCardVariant = RtCardVariant.Filled,
    selected: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = ReverseTutorDesign.surfaces
    val spacing = ReverseTutorDesign.spacing

    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        variant == RtCardVariant.Outlined -> surfaces.cardBorder
        else -> surfaces.cardBorder.copy(alpha = 0.35f)
    }
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val backgroundColor = when (variant) {
        RtCardVariant.Filled -> surfaces.card
        RtCardVariant.Outlined -> surfaces.card
        RtCardVariant.Elevated -> surfaces.elevated
    }
    val tonalElevation = when (variant) {
        RtCardVariant.Filled -> ReverseTutorDesign.elevations.level1
        RtCardVariant.Outlined -> ReverseTutorDesign.elevations.level0
        RtCardVariant.Elevated -> ReverseTutorDesign.elevations.level2
    }

    val baseModifier = when (variant) {
        RtCardVariant.Elevated -> modifier.shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
        else -> modifier
    }

    Surface(
        modifier = baseModifier,
        color = backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = tonalElevation,
        shape = shape,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            content = content
        )
    }
}
