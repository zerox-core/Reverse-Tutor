package com.reversetutor.feature.memory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

data class BrainReviewItem(
    val title: String,
    val description: String
)

data class BrainMemoryItem(
    val title: String,
    val description: String
)

data class BrainUiState(
    val reviews: List<BrainReviewItem> = emptyList(),
    val memories: List<BrainMemoryItem> = emptyList(),
    val graphState: KnowledgeGraphUiState = KnowledgeGraphUiState.from(
        nodes = emptyList(),
        edges = emptyList(),
        scope = GraphScope.Global
    )
) {
    companion object {
        fun empty(): BrainUiState = BrainUiState()

        // Kept as a source-compatible alias; formal routes never inject preview learning data.
        fun preview(): BrainUiState = empty()
    }
}

@Composable
fun BrainScreen(
    state: BrainUiState = BrainUiState.empty(),
    onStartReview: (BrainReviewItem) -> Unit = {},
    onWidgetDragChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onEditNode: ((GraphLayoutNode) -> Unit)? = null,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onSelectedNodeChange: (String?) -> Unit = {}
) {
    var localSelection by remember(state.graphState.selectedNodeId) {
        mutableStateOf(state.graphState.selectedNodeId)
    }
    FormalGlobalKnowledgeGraphScreen(
        state = state.graphState.withSelection(localSelection),
        onSelectedNodeChange = { selectedId ->
            localSelection = selectedId
            onSelectedNodeChange(selectedId)
        },
        onBack = onBack,
        onSearch = onSearch,
        onMore = onMore,
        onEditNode = onEditNode,
        onOpenChatEvidence = onOpenChatEvidence,
        onOpenSourceEvidence = onOpenSourceEvidence,
        onGraphInteractionChanged = onWidgetDragChanged,
        modifier = modifier
    )
}
