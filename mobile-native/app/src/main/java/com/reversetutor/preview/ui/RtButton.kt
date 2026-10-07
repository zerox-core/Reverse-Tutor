package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign

enum class RtButtonSize {
    Small,
    Medium,
    Large
}

enum class RtButtonTone {
    Primary,
    Secondary,
    Quiet,
    Destructive
}

@Composable
fun RtButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RtButtonSize = RtButtonSize.Medium,
    tone: RtButtonTone = RtButtonTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: RtIconKey? = null,
    trailingIcon: RtIconKey? = null,
    fillWidth: Boolean = false
) {
    val spacing = ReverseTutorDesign.spacing
    val minHeight = when (size) {
        RtButtonSize.Small -> 36.dp
        RtButtonSize.Medium -> 44.dp
        RtButtonSize.Large -> 48.dp
    }
    val horizontalPadding = when (size) {
        RtButtonSize.Small -> spacing.space3
        RtButtonSize.Medium -> spacing.space4
        RtButtonSize.Large -> spacing.space5
    }
    val textStyle = when (size) {
        RtButtonSize.Small -> MaterialTheme.typography.labelMedium
        RtButtonSize.Medium -> MaterialTheme.typography.labelLarge
        RtButtonSize.Large -> MaterialTheme.typography.bodyLarge
    }

    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp
            )
        } else {
            leadingIcon?.let { icon ->
                RtIcon(
                    key = icon,
                    size = 20.dp,
                    tint = LocalContentColor.current
                )
            }
            Text(text = label, style = textStyle)
            trailingIcon?.let { icon ->
                RtIcon(
                    key = icon,
                    size = 20.dp,
                    tint = LocalContentColor.current
                )
            }
        }
    }

    val baseModifier = modifier
        .heightIn(min = minHeight)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)

    val isActuallyEnabled = enabled && !loading

    when (tone) {
        RtButtonTone.Primary -> Button(
            onClick = onClick,
            enabled = isActuallyEnabled,
            modifier = baseModifier,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = spacing.space2)
        ) {
            content()
        }

        RtButtonTone.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = isActuallyEnabled,
            modifier = baseModifier,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = spacing.space2)
        ) {
            content()
        }

        RtButtonTone.Quiet -> TextButton(
            onClick = onClick,
            enabled = isActuallyEnabled,
            modifier = baseModifier,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = spacing.space2)
        ) {
            content()
        }

        RtButtonTone.Destructive -> TextButton(
            onClick = onClick,
            enabled = isActuallyEnabled,
            modifier = baseModifier,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = spacing.space2)
        ) {
            content()
        }
    }
}

@Composable
fun RtPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RtButtonSize = RtButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false
) {
    RtButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        size = size,
        tone = RtButtonTone.Primary,
        enabled = enabled,
        loading = loading,
        fillWidth = fillWidth
    )
}
