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
    val avatarLabel: String,
    val learnerRole: String = "学习者",
    val latestMessageSummary: String = statusLabel,
    val perSessionAvatarVisible: Boolean = true,
    val pinnedAtEpochMillis: Long? = if (pinned) updatedAtEpochMillis else null,
    val isWelcomeMock: Boolean = false
) {
    val unreadLabel: String =
        if (unreadCount == 0) "无未读" else "$unreadCount 条未读"
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
                    if (avatarVisible && item.perSessionAvatarVisible) {
                        item
                    } else {
                        item.copy(avatarLabel = "", perSessionAvatarVisible = false)
                    }
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
                        .thenByDescending { it.pinnedAtEpochMillis ?: Long.MIN_VALUE }
                        .thenByDescending { it.updatedAtEpochMillis }
                )
                .toList()

            val emptyTitle = when {
                sessions.isEmpty() -> "还没有会话"
                normalizedQuery.isNotEmpty() -> "没有匹配的会话"
                filter == SessionListFilter.Pinned -> "还没有置顶会话"
                else -> "还没有会话"
            }

            return SessionListUiState(
                visibleSessions = visible,
                query = query,
                filter = filter,
                summary = "${visible.size} 个会话",
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
            "未配置模型 · 主动生成待启用"
        } else {
            "已就绪 · 主动生成待启用"
        },
        unreadCount = 0,
        avatarLabel = if (avatarVisible) title.trim().take(1) else "",
        perSessionAvatarVisible = avatarVisible,
        pinnedAtEpochMillis = updatedAtEpochMillis.takeIf { pinned }
    )
