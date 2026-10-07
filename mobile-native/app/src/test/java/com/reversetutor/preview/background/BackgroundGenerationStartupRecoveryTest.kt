package com.reversetutor.preview.background

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundGenerationStartupRecoveryTest {
    @Test
    fun recoveredJobsAreScheduledInRepositoryOrder() = runTest {
        val scheduled = mutableListOf<String>()
        val recovery = BackgroundGenerationStartupRecovery(
            recoverJobIds = { listOf("job-queued", "job-interrupted") },
            enqueue = scheduled::add
        )

        val result = recovery.recoverAndSchedule(nowEpochMillis = 100L)

        assertEquals(listOf("job-queued", "job-interrupted"), result)
        assertEquals(listOf("job-queued", "job-interrupted"), scheduled)
    }

    @Test
    fun noRecoveredJobsDoNotScheduleWork() = runTest {
        val scheduled = mutableListOf<String>()
        val recovery = BackgroundGenerationStartupRecovery(
            recoverJobIds = { emptyList() },
            enqueue = scheduled::add
        )

        val result = recovery.recoverAndSchedule(nowEpochMillis = 100L)

        assertEquals(emptyList<String>(), result)
        assertEquals(emptyList<String>(), scheduled)
    }
}
