package com.reversetutor.preview.background

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundGenerationStartupRecoveryDeviceTest {
    @Test
    fun recoveredJobIsHandedToWorkManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val jobId = "device-recovery-${System.nanoTime()}"
        val uniqueWorkName = BackgroundGenerationWorker.uniqueWorkName(jobId)
        val workManager = WorkManager.getInstance(context)
        try {
            val recovery = BackgroundGenerationStartupRecovery(
                recoverJobIds = { listOf(jobId) },
                enqueue = { recoveredJobId ->
                    BackgroundGenerationWorker.enqueue(context, recoveredJobId)
                }
            )

            assertEquals(listOf(jobId), recovery.recoverAndSchedule(nowEpochMillis = 100L))
            val workInfo = workManager
                .getWorkInfosForUniqueWork(uniqueWorkName)
                .get(10, TimeUnit.SECONDS)
                .single()

            assertTrue(
                workInfo.state in setOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.FAILED
                )
            )
        } finally {
            workManager.cancelUniqueWork(uniqueWorkName).result.get(10, TimeUnit.SECONDS)
        }
    }
}
