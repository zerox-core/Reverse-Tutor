package com.reversetutor.feature.chat

import com.reversetutor.core.model.TutorSession

enum class SessionListFilter {
    All,
    Pinned
}

data class SessionListItem(
    val id: String,
    val title: String,
    val updatedAtEpochMillis: Long,
    val pinned: Boolean,
    val statusLabel: String,
    val unreadCount: Int,
    val avatarLabel: String
) {
    val unreadLabel: String =
        if (unreadCount == 0) "No unread" else "$unreadCount unread"
}

data class SessionListUiState(
    val visibleSessions: List<SessionListItem>,
    val query: String,
    val filter: SessionListFilter,
    val summary: String,
    val emptyStateTitle: String
) {
    companion object {
        fun from(
            sessions: List<SessionListItem>,
            query: String,
            filter: SessionListFilter,
            avatarVisible: Boolean
        ): SessionListUiState {
            val normalizedQuery = query.trim()
            val visible = sessions
                .asSequence()
                .map { item ->
                    if (avatarVisible) item else item.copy(avatarLabel = "Avatar hidden")
                }
                .filter { item ->
                    normalizedQuery.isEmpty() ||
                        item.title.contains(normalizedQuery, ignoreCase = true)
                }
                .filter { item ->
                    filter == SessionListFilter.All || item.pinned
                }
                .sortedWith(
                    compareByDescending<SessionListItem> { it.pinned }
                        .thenByDescending { it.updatedAtEpochMillis }
                )
                .toList()

            val emptyTitle = when {
                sessions.isEmpty() -> "No sessions yet"
                normalizedQuery.isNotEmpty() -> "No matching sessions"
                filter == SessionListFilter.Pinned -> "No pinned sessions"
                else -> "No sessions yet"
            }

            return SessionListUiState(
                visibleSessions = visible,
                query = query,
                filter = filter,
                summary = "${visible.size} ${if (visible.size == 1) "session" else "sessions"}",
                emptyStateTitle = emptyTitle
            )
        }
    }
}

fun TutorSession.toSessionListItem(avatarVisible: Boolean): SessionListItem =
    SessionListItem(
        id = id,
        title = title,
        updatedAtEpochMillis = updatedAtEpochMillis,
        pinned = pinned,
        statusLabel = if (llmProfileId == null) {
            "No model configured; proactive deferred"
        } else {
            "Ready; proactive deferred"
        },
        unreadCount = 0,
        avatarLabel = if (avatarVisible) "Avatar placeholder" else "Avatar hidden"
    )
