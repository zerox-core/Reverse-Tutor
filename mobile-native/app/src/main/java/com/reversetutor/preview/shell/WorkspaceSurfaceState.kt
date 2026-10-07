package com.reversetutor.preview.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface WorkspaceSurfaceState {
    data object Content : WorkspaceSurfaceState
    data object Loading : WorkspaceSurfaceState
    data class Empty(
        val message: String = "这里还没有内容"
    ) : WorkspaceSurfaceState
    data class Offline(
        val message: String = "当前处于离线状态"
    ) : WorkspaceSurfaceState
    data class Error(
        val message: String = "加载失败，请稍后重试"
    ) : WorkspaceSurfaceState
    data class PermissionDenied(
        val message: String = "缺少显示此内容所需的权限"
    ) : WorkspaceSurfaceState
}

internal data class WorkspaceSurfacePresentation(
    val title: String,
    val message: String,
    val actionLabel: String?
)

internal fun workspaceSurfacePresentation(
    state: WorkspaceSurfaceState
): WorkspaceSurfacePresentation? = when (state) {
    WorkspaceSurfaceState.Content -> null
    WorkspaceSurfaceState.Loading -> WorkspaceSurfacePresentation(
        title = "正在加载",
        message = "请稍候",
        actionLabel = null
    )
    is WorkspaceSurfaceState.Empty -> WorkspaceSurfacePresentation(
        title = "暂无内容",
        message = state.message,
        actionLabel = "刷新"
    )
    is WorkspaceSurfaceState.Offline -> WorkspaceSurfacePresentation(
        title = "网络不可用",
        message = state.message,
        actionLabel = "重试"
    )
    is WorkspaceSurfaceState.Error -> WorkspaceSurfacePresentation(
        title = "出现错误",
        message = state.message,
        actionLabel = "重试"
    )
    is WorkspaceSurfaceState.PermissionDenied -> WorkspaceSurfacePresentation(
        title = "权限受限",
        message = state.message,
        actionLabel = "重新检查权限"
    )
}

@Composable
internal fun WorkspaceSurfaceShell(
    state: WorkspaceSurfaceState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingSlot: @Composable () -> Unit = { DefaultWorkspaceLoadingSlot() },
    emptySlot: @Composable (WorkspaceSurfaceState.Empty) -> Unit = {
        DefaultWorkspaceMessageSlot(workspaceSurfacePresentation(it)!!, onRetry)
    },
    offlineSlot: @Composable (WorkspaceSurfaceState.Offline) -> Unit = {
        DefaultWorkspaceMessageSlot(workspaceSurfacePresentation(it)!!, onRetry)
    },
    errorSlot: @Composable (WorkspaceSurfaceState.Error) -> Unit = {
        DefaultWorkspaceMessageSlot(workspaceSurfacePresentation(it)!!, onRetry)
    },
    permissionDeniedSlot: @Composable (WorkspaceSurfaceState.PermissionDenied) -> Unit = {
        DefaultWorkspaceMessageSlot(workspaceSurfacePresentation(it)!!, onRetry)
    },
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            WorkspaceSurfaceState.Content -> content()
            WorkspaceSurfaceState.Loading -> loadingSlot()
            is WorkspaceSurfaceState.Empty -> emptySlot(state)
            is WorkspaceSurfaceState.Offline -> offlineSlot(state)
            is WorkspaceSurfaceState.Error -> errorSlot(state)
            is WorkspaceSurfaceState.PermissionDenied -> permissionDeniedSlot(state)
        }
    }
}

@Composable
private fun DefaultWorkspaceLoadingSlot() {
    CircularProgressIndicator()
    Text(
        text = "正在加载",
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun DefaultWorkspaceMessageSlot(
    presentation: WorkspaceSurfacePresentation,
    onRetry: () -> Unit
) {
    Text(text = presentation.title, style = MaterialTheme.typography.titleMedium)
    Text(
        text = presentation.message,
        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        style = MaterialTheme.typography.bodyMedium
    )
    presentation.actionLabel?.let { label ->
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(label)
        }
    }
}
