package com.reversetutor.preview.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal fun WorkspaceBackHandler(
    navigationState: AppNavigationState,
    workspaceState: WorkspaceUiState,
    pageLocalActionSurfaceActive: Boolean,
    onDismissPageLocalActionSurface: () -> Unit,
    onExitGraphCanvas: () -> Unit,
    onNavigationStateChange: (AppNavigationState) -> Unit,
    onExitRequested: () -> Unit
) {
    BackHandler {
        when (
            workspaceBackTarget(
                navigationState = navigationState,
                workspaceState = workspaceState,
                pageLocalActionSurfaceActive = pageLocalActionSurfaceActive
            )
        ) {
            WorkspaceBackTarget.PageLocalActionSurface -> onDismissPageLocalActionSurface()
            WorkspaceBackTarget.GraphCanvas -> onExitGraphCanvas()
            WorkspaceBackTarget.AppNavigationSurface,
            WorkspaceBackTarget.Navigation -> {
                val transition = navigationState.handleSystemBack()
                if (transition.result == BackResult.AllowSystemExit) {
                    onExitRequested()
                } else {
                    onNavigationStateChange(transition.state)
                }
            }
        }
    }
}
