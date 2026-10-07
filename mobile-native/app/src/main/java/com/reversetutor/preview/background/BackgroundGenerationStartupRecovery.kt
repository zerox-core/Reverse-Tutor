package com.reversetutor.preview.background

internal class BackgroundGenerationStartupRecovery(
    private val recoverJobIds: suspend (Long) -> List<String>,
    private val enqueue: (String) -> Unit
) {
    suspend fun recoverAndSchedule(nowEpochMillis: Long): List<String> =
        recoverJobIds(nowEpochMillis).also { jobIds ->
            jobIds.forEach(enqueue)
        }
}
