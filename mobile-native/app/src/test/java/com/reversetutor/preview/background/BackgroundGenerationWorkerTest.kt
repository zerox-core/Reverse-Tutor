package com.reversetutor.preview.background

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundGenerationWorkerTest {
    @Test
    fun workRequestCarriesPersistedJobId() {
        val request = BackgroundGenerationWorker.request("job-1")

        assertEquals("job-1", request.workSpec.input.getString(BackgroundGenerationWorker.InputJobId))
        assertEquals("background-generation-job-1", BackgroundGenerationWorker.uniqueWorkName("job-1"))
    }
}
