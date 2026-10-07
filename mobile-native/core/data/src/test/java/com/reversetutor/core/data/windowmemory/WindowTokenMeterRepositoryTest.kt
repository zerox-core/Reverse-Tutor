package com.reversetutor.core.data.windowmemory

import com.reversetutor.core.data.local.dao.WindowMemoryTokenMeterDao
import com.reversetutor.core.data.local.entity.WindowMemoryTokenMeterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V2-006: token metering persistence. Records window-kept and injection
 * estimates per event so the default window budget can be tuned against
 * real usage (locked decision: meter first, tune later).
 */
class WindowTokenMeterRepositoryTest {

    private class FakeMeterDao : WindowMemoryTokenMeterDao {
        val rows = mutableListOf<WindowMemoryTokenMeterEntity>()

        override suspend fun insertMeter(meter: WindowMemoryTokenMeterEntity) {
            rows += meter
        }

        override suspend fun listMeters(sessionId: String): List<WindowMemoryTokenMeterEntity> =
            rows.filter { it.sessionId == sessionId }.sortedBy { it.createdAtEpochMillis }
    }

    @Test
    fun meterRoundTripsInChronologicalOrder() = runBlocking {
        val repository = WindowTokenMeterRepository(FakeMeterDao())

        repository.recordTokenMeter("ses", "window_kept", 1200, "kept=40", 2L)
        repository.recordTokenMeter("ses", "injection", 320, "values=2", 1L)

        val meters = repository.listTokenMeters("ses")
        assertEquals(2, meters.size)
        assertEquals("injection", meters[0].kind)
        assertEquals(320, meters[0].estimatedTokens)
        assertEquals("values=2", meters[0].detail)
        assertEquals("window_kept", meters[1].kind)
        assertEquals(1200, meters[1].estimatedTokens)
    }

    @Test
    fun metersAreIsolatedPerSession() = runBlocking {
        val repository = WindowTokenMeterRepository(FakeMeterDao())

        repository.recordTokenMeter("ses-a", "window_kept", 100, "", 1L)
        repository.recordTokenMeter("ses-b", "window_kept", 200, "", 2L)

        assertEquals(1, repository.listTokenMeters("ses-a").size)
        assertEquals(200, repository.listTokenMeters("ses-b").single().estimatedTokens)
    }
}
