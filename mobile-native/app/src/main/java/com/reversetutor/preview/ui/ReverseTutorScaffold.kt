package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    navigationLabel: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
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
            if (navigationLabel != null && onNavigationClick != null) {
                ReverseTutorActionButton(
                    label = if (navigationLabel == "菜单") "☰" else "‹",
                    onClick = onNavigationClick,
                    tone = ReverseTutorActionTone.Quiet
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
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
            actions()
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
                .heightIn(min = spacing.minTouchTarget + spacing.space2)
                .padding(horizontal = spacing.space3, vertical = spacing.space2),
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                ReverseTutorNavigationChip(
                    label = item.label,
                    selected = item.key == selectedKey,
                    onClick = { onItemSelected(item.key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReverseTutorNavigationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        shape = RoundedCornerShape(shapes.radiusMedium),
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .padding(horizontal = spacing.space1, vertical = spacing.space2),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
