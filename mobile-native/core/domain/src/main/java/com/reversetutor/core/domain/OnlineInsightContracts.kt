package com.reversetutor.core.domain

data class WeeklyOnlineInsight(
    val spaceId: String,
    val weekStartEpochMillis: Long,
    val sourceRevision: Long,
    val summary: String
)

interface OnlineInsightRepositoryContract {
    suspend fun weekly(
        userId: String,
        deviceId: String,
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        statistics: Map<String, Long>
    ): OnlineData<WeeklyOnlineInsight>
}
