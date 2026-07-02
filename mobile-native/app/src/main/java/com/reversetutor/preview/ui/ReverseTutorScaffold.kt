package com.reversetutor.preview.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.reversetutor.preview.theme.ReverseTutorDesign

@Composable
fun ReverseTutorScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun ReverseTutorTopAppBar(
    title: String,
    subtitle: String?,
    actionLabel: String?,
    onActionClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val spacing = ReverseTutorDesign.spacing
    val elevations = ReverseTutorDesign.elevations

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = elevations.level2,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space5, vertical = spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (actionLabel != null && onActionClick != null) {
                ReverseTutorActionButton(
                    label = actionLabel,
                    onClick = onActionClick,
                    tone = ReverseTutorActionTone.Quiet
                )
            }
        }
    }
}

data class ReverseTutorNavigationItem(
    val key: String,
    val label: String,
    val contentDescription: String = label
)

@Composable
fun ReverseTutorNavigationStrip(
    items: List<ReverseTutorNavigationItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = ReverseTutorDesign.spacing
    val elevations = ReverseTutorDesign.elevations

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = elevations.level3,
        modifier = modifier.navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .heightIn(min = spacing.minTouchTarget + spacing.space2)
                .padding(horizontal = spacing.space3, vertical = spacing.space2),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                ReverseTutorNavigationChip(
                    label = item.label,
                    selected = item.key == selectedKey,
                    onClick = { onItemSelected(item.key) }
                )
            }
        }
    }
}

@Composable
private fun ReverseTutorNavigationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spacing = ReverseTutorDesign.spacing
    val shapes = ReverseTutorDesign.shapes
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(shapes.radiusMedium)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = spacing.minTouchTarget)
                .padding(horizontal = spacing.space3, vertical = spacing.space2),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
