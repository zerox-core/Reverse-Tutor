package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reversetutor.preview.theme.ReverseTutorDesign

enum class ReverseTutorActionTone {
    Primary,
    Secondary,
    Quiet,
    Destructive
}

@Composable
fun ReverseTutorActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ReverseTutorActionTone = ReverseTutorActionTone.Primary
) {
    val touchTargetModifier = modifier.heightIn(min = ReverseTutorDesign.spacing.minTouchTarget)
    val labelContent: @Composable () -> Unit = {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }

    when (tone) {
        ReverseTutorActionTone.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = touchTargetModifier,
            content = { labelContent() }
        )
        ReverseTutorActionTone.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = touchTargetModifier,
            content = { labelContent() }
        )
        ReverseTutorActionTone.Quiet -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = touchTargetModifier,
            content = { labelContent() }
        )
        ReverseTutorActionTone.Destructive -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = touchTargetModifier,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            content = { labelContent() }
        )
    }
}
