package com.reversetutor.preview.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.theme.ReverseTutorStatusTone

@Composable
fun ReverseTutorConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    tone: ReverseTutorStatusTone = ReverseTutorStatusTone.Info
) {
    val actionTone = when (tone) {
        ReverseTutorStatusTone.Error,
        ReverseTutorStatusTone.ParserFailed -> ReverseTutorActionTone.Destructive
        else -> ReverseTutorActionTone.Primary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            ReverseTutorActionButton(
                label = confirmLabel,
                onClick = onConfirm,
                tone = actionTone
            )
        },
        dismissButton = {
            ReverseTutorActionButton(
                label = dismissLabel,
                onClick = onDismiss,
                tone = ReverseTutorActionTone.Quiet
            )
        }
    )
}

@Composable
fun ReverseTutorDestructiveConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "取消"
) {
    ReverseTutorConfirmationDialog(
        title = title,
        body = body,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = ReverseTutorStatusTone.Error
    )
}

object ReverseTutorSheetDefaults {
    val shape: Shape
        @Composable
        get() {
            val radius = ReverseTutorDesign.shapes.radiusSheet
            return RoundedCornerShape(topStart = radius, topEnd = radius)
        }

    val tonalElevation: Dp
        @Composable
        get() = ReverseTutorDesign.elevations.level4
}
