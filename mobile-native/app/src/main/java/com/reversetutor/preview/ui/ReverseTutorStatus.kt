package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.theme.ReverseTutorStatusTone

@Composable
fun ReverseTutorStatusBadge(
    label: String,
    tone: ReverseTutorStatusTone,
    modifier: Modifier = Modifier
) {
    val spacing = ReverseTutorDesign.spacing
    val shapes = ReverseTutorDesign.shapes
    val colors = ReverseTutorDesign.semanticColors.forTone(tone)

    Surface(
        modifier = modifier,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(shapes.radiusSmall)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.space2, vertical = spacing.space1),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun ReverseTutorStatusStrip(
    title: String,
    modifier: Modifier = Modifier,
    tone: ReverseTutorStatusTone = ReverseTutorStatusTone.Info,
    message: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val spacing = ReverseTutorDesign.spacing
    val shapes = ReverseTutorDesign.shapes
    val colors = ReverseTutorDesign.semanticColors.forTone(tone)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(shapes.radiusCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .padding(spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (message != null) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
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

@Composable
fun ReverseTutorStatePanel(
    title: String,
    cause: String,
    impact: String,
    modifier: Modifier = Modifier,
    tone: ReverseTutorStatusTone = ReverseTutorStatusTone.Neutral,
    recoveryLabel: String? = null,
    onRecoveryClick: (() -> Unit)? = null
) {
    val spacing = ReverseTutorDesign.spacing

    ReverseTutorCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            ReverseTutorStatusBadge(label = title, tone = tone)
            Text(
                text = cause,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = impact,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (recoveryLabel != null && onRecoveryClick != null) {
                ReverseTutorActionButton(
                    label = recoveryLabel,
                    onClick = onRecoveryClick,
                    tone = ReverseTutorActionTone.Secondary
                )
            }
        }
    }
}
