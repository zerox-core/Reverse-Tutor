package com.reversetutor.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

/**
 * A deliberately replaceable branch-management host. It renders only a published
 * [WindowBranchUiState] and emits user intents; data access, timers, and navigation
 * remain owned by the app shell.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WindowBranchPanel(
    state: WindowBranchUiState,
    onAction: (WindowBranchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    TextButton(
                        onClick = { onAction(WindowBranchAction.Back) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = state.current.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text("窗口类型：${state.current.kind.name}", style = MaterialTheme.typography.bodyMedium)

            OutlinedButton(
                onClick = { onAction(WindowBranchAction.Create) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("window-branch-create")
            ) { Text("创建分支") }

            state.parent?.let { parent ->
                OutlinedButton(
                    onClick = { onAction(WindowBranchAction.OpenWindow(parent.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("window-branch-parent-${parent.id}")
                ) { Text("返回父分支：${parent.title}") }
            }

            BranchSectionTitle("直接子分支")
            if (state.children.isEmpty()) {
                Text("暂无子分支", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.children.forEach { child ->
                    OutlinedButton(
                        onClick = { onAction(WindowBranchAction.OpenWindow(child.id)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("window-branch-child-${child.id}")
                    ) { Text(child.title) }
                }
            }

            BranchSectionTitle("主动对话")
            Button(
                onClick = {
                    onAction(WindowBranchAction.SetHeartbeat(!state.heartbeat.enabled))
                },
                enabled = state.heartbeat.canToggle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("window-branch-heartbeat")
            ) { Text(state.heartbeat.stateLabel) }

            BranchSectionTitle("合并到父分支")
            Button(
                onClick = {},
                enabled = state.mergeEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("window-branch-merge")
            ) { Text("合并到父分支") }
            state.mergeReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            if (state.current.kind == com.reversetutor.core.domain.WindowKind.CHILD) {
                BranchSectionTitle("删除当前分支")
                Button(
                    onClick = { onAction(WindowBranchAction.RequestDelete) },
                    enabled = state.deleteEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("window-branch-delete")
                ) { Text("删除当前分支") }
                state.deleteReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (state.deleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onAction(WindowBranchAction.CancelDelete) },
            title = { Text("删除当前分支？") },
            text = { Text("此操作会清理当前分支的局部会话数据。") },
            dismissButton = {
                TextButton(
                    onClick = { onAction(WindowBranchAction.CancelDelete) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("window-branch-delete-cancel")
                ) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(WindowBranchAction.ConfirmDelete) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("window-branch-delete-confirm")
                ) { Text("确认删除") }
            }
        )
    }
}

@Composable
private fun BranchSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
