package com.reversetutor.core.remote

data class ActivityPage(
    val items: List<OnlineActivity>,
    val nextCursor: String?,
    val updatedAtEpochMillis: Long
)

data class LeaderboardItem(
    val rank: Long,
    val displayName: String,
    val avatarUrl: String?,
    val progress: Long,
    val isCurrentUser: Boolean
)

data class LeaderboardPage(
    val items: List<LeaderboardItem>,
    val nextCursor: String?,
    val updatedAtEpochMillis: Long
)
