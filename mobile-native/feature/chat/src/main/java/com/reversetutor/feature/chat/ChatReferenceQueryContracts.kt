package com.reversetutor.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ChatQueryCategory(val label: String) {
    Session("会话"),
    Graph("图谱"),
    Source("资料")
}

enum class ChatQueryScope {
    CurrentSession,
    AllSessions
}

sealed interface ChatQueryTarget {
    val sessionId: String?

    data class Message(override val sessionId: String, val messageId: String) : ChatQueryTarget
    data class GraphNode(override val sessionId: String, val nodeId: String) : ChatQueryTarget
    data class Source(override val sessionId: String?, val sourceId: String) : ChatQueryTarget
}

data class ChatQueryResult(
    val id: String,
    val category: ChatQueryCategory,
    val summary: String,
    val sourceIdentity: String,
    val target: ChatQueryTarget
)

sealed interface ChatQueryLoadState {
    object Idle : ChatQueryLoadState
    object Loading : ChatQueryLoadState
    object Empty : ChatQueryLoadState
    data class Ready(val results: List<ChatQueryResult>) : ChatQueryLoadState
    data class Offline(val results: List<ChatQueryResult>) : ChatQueryLoadState
    data class Failure(val message: String, val retryable: Boolean = true) : ChatQueryLoadState
}

sealed interface ChatQueryHighlight {
    data class Message(val sessionId: String, val messageId: String) : ChatQueryHighlight
    data class GraphNode(val sessionId: String, val nodeId: String) : ChatQueryHighlight
    data class Source(val sessionId: String?, val sourceId: String) : ChatQueryHighlight
}

data class ChatQueryNavigationRequest(val highlight: ChatQueryHighlight)

data class ChatReferenceQueryState(
    val query: String = "",
    val scope: ChatQueryScope = ChatQueryScope.CurrentSession,
    val selectedCategory: ChatQueryCategory = ChatQueryCategory.Session,
    val loadState: ChatQueryLoadState = ChatQueryLoadState.Idle,
    val results: List<ChatQueryResult> = emptyList()
) {
    val selectionRequired: Boolean
        get() = results.size > 1
    val automaticNavigation: ChatQueryNavigationRequest?
        get() = results.singleOrNull()?.toNavigationRequest()

    fun withScope(scope: ChatQueryScope): ChatReferenceQueryState = copy(scope = scope)

    fun withResults(results: List<ChatQueryResult>, offline: Boolean): ChatReferenceQueryState = copy(
        results = results,
        loadState = when {
            results.isEmpty() -> ChatQueryLoadState.Empty
            offline -> ChatQueryLoadState.Offline(results)
            else -> ChatQueryLoadState.Ready(results)
        }
    )

    fun select(resultId: String): ChatQueryNavigationRequest? =
        results.firstOrNull { it.id == resultId }?.toNavigationRequest()
}

data class ChatQueryResponse(
    val results: List<ChatQueryResult>,
    val offline: Boolean = false
)

fun interface ChatReferenceQueryPort {
    suspend fun query(
        query: String,
        scope: ChatQueryScope,
        currentSessionId: String
    ): ChatQueryResponse
}

class ChatReferenceQueryCoordinator(
    private val sessionId: String,
    private val queryPort: ChatReferenceQueryPort,
    private val scope: CoroutineScope,
    initialState: ChatReferenceQueryState = ChatReferenceQueryState(),
    private val onStateChanged: (ChatReferenceQueryState) -> Unit = {},
    private val onNavigate: (ChatQueryNavigationRequest) -> Unit = {}
) {
    var state by mutableStateOf(initialState)
        private set
    var requestToken: Long = 0L
        private set
    private var activeRequest: Job? = null

    fun onQueryChange(query: String) {
        invalidateActiveRequest()
        update(
            state.copy(
                query = query,
                results = emptyList(),
                loadState = ChatQueryLoadState.Idle
            )
        )
    }

    fun onScopeChange(scope: ChatQueryScope) {
        if (state.scope == scope) return
        val next = state.copy(scope = scope, results = emptyList())
        if (next.query.isBlank()) {
            invalidateActiveRequest()
            update(next.copy(loadState = ChatQueryLoadState.Empty))
        } else {
            startRequest(next)
        }
    }

    fun submit() = startRequest(state)

    fun updateCategory(category: ChatQueryCategory) {
        update(state.copy(selectedCategory = category))
    }

    fun close() {
        invalidateActiveRequest()
    }

    private fun startRequest(baseState: ChatReferenceQueryState) {
        val normalized = baseState.query.trim()
        if (normalized.isEmpty()) {
            invalidateActiveRequest()
            update(baseState.copy(query = "", results = emptyList(), loadState = ChatQueryLoadState.Empty))
            return
        }
        invalidateActiveRequest()
        val token = requestToken
        val requestedScope = baseState.scope
        val loading = baseState.copy(
            query = normalized,
            scope = requestedScope,
            results = emptyList(),
            loadState = ChatQueryLoadState.Loading
        )
        update(loading)
        activeRequest = scope.launch {
            try {
                val response = queryPort.query(normalized, requestedScope, sessionId)
                if (!isCurrent(token, normalized, requestedScope)) return@launch
                val next = state.withResults(response.results, response.offline)
                update(next)
                next.automaticNavigation?.let(onNavigate)
            } catch (cancelled: CancellationException) {
                if (token == requestToken) throw cancelled
            } catch (_: Exception) {
                if (!isCurrent(token, normalized, requestedScope)) return@launch
                update(
                    state.copy(
                        results = emptyList(),
                        loadState = ChatQueryLoadState.Failure("查询失败，请重试。")
                    )
                )
            }
        }
    }

    private fun invalidateActiveRequest() {
        requestToken += 1L
        activeRequest?.cancel()
        activeRequest = null
    }

    private fun isCurrent(token: Long, query: String, scope: ChatQueryScope): Boolean =
        token == requestToken && state.query == query && state.scope == scope

    private fun update(next: ChatReferenceQueryState) {
        state = next
        onStateChanged(next)
    }
}

private fun ChatQueryResult.toNavigationRequest(): ChatQueryNavigationRequest =
    ChatQueryNavigationRequest(
        highlight = when (val target = target) {
            is ChatQueryTarget.Message -> ChatQueryHighlight.Message(target.sessionId, target.messageId)
            is ChatQueryTarget.GraphNode -> ChatQueryHighlight.GraphNode(target.sessionId, target.nodeId)
            is ChatQueryTarget.Source -> ChatQueryHighlight.Source(target.sessionId, target.sourceId)
        }
    )
