package com.reversetutor.preview.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.model.GraphSnapshotResult
import com.reversetutor.feature.memory.FormalSessionWorldTreeScreen
import com.reversetutor.feature.memory.GraphScope
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import com.reversetutor.core.model.GraphScope as DataGraphScope

@Composable
internal fun SessionWorldTreeRoute(
    graphRepository: GraphRepository,
    sessionId: String?,
    sessionTitle: String,
    onBack: () -> Unit,
    highlightedNodeId: String? = null,
    modifier: Modifier = Modifier,
    onGraphInteractionChanged: (Boolean) -> Unit = {}
) {
    val dataScope = remember(sessionId) { DataGraphScope.Session(sessionId ?: "missing-session") }
    var state by remember(sessionId) { mutableStateOf(KnowledgeGraphUiState.loading(GraphScope.Session)) }
    var selectedNodeId by remember(sessionId, highlightedNodeId) { mutableStateOf(highlightedNodeId) }
    var showLockedNodes by remember(sessionId) { mutableStateOf(false) }
    var refreshKey by remember(sessionId) { mutableIntStateOf(0) }

    LaunchedEffect(dataScope, refreshKey) {
        state = KnowledgeGraphUiState.loading(GraphScope.Session)
        state = when (val result = graphRepository.snapshot(dataScope)) {
            is GraphSnapshotResult.Ready -> KnowledgeGraphUiState.from(
                nodes = result.snapshot.nodes,
                edges = result.snapshot.edges,
                selectedNodeId = selectedNodeId,
                scope = GraphScope.Session
            )
            is GraphSnapshotResult.Empty -> KnowledgeGraphUiState.from(
                nodes = emptyList(),
                edges = emptyList(),
                selectedNodeId = null,
                scope = GraphScope.Session
            )
            is GraphSnapshotResult.Error -> KnowledgeGraphUiState.error(
                scope = GraphScope.Session,
                message = result.error.safeMessage
            )
        }
    }

    FormalSessionWorldTreeScreen(
        title = sessionTitle,
        subtitle = "当前会话世界树",
        state = state.withSelection(selectedNodeId),
        showLockedNodes = showLockedNodes,
        onShowLockedNodesChange = { showLockedNodes = it },
        onBack = onBack,
        onSelectedNodeChange = { selectedNodeId = it },
        onRetry = { refreshKey += 1 },
        onGraphInteractionChanged = onGraphInteractionChanged,
        modifier = modifier
    )
}
