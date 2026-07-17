package com.reversetutor.preview.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign

@Composable
fun RtChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: RtIconKey? = null,
    trailingIcon: RtIconKey? = null,
    style: TextStyle = MaterialTheme.typography.labelMedium
) {
    val surfaces = ReverseTutorDesign.surfaces
    val spacing = ReverseTutorDesign.spacing
    val text = ReverseTutorDesign.text

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> surfaces.input
    }
    val contentColor = when {
        !enabled -> text.muted
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> surfaces.inputBorder
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .sizeIn(minHeight = 32.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space3, vertical = spacing.space2),
            horizontalArrangement = Arrangement.spacedBy(spacing.space1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let { icon ->
                RtIcon(
                    key = icon,
                    size = 16.dp,
                    tint = contentColor
                )
            }
            Text(text = label, style = style)
            trailingIcon?.let { icon ->
                RtIcon(
                    key = icon,
                    size = 16.dp,
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
fun RtChipGroup(
    items: List<String>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    singleSelect: Boolean = false
) {
    val spacing = ReverseTutorDesign.spacing
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            RtChip(
                label = item,
                selected = selected.contains(item),
                onClick = {
                    val newSelection = when {
                        singleSelect -> setOf(item)
                        selected.contains(item) -> selected - item
                        else -> selected + item
                    }
                    onSelectionChange(newSelection)
                }
            )
        }
    }
}
