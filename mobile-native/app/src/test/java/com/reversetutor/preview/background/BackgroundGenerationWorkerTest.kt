package com.reversetutor.preview.background

import com.reversetutor.core.llm.FakeLlmGenerationRuntime
import com.reversetutor.preview.wiring.HybridLlmRuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundGenerationWorkerTest {
    @Test
    fun workRequestCarriesPersistedJobId() {
        val request = BackgroundGenerationWorker.request("job-1")

        assertEquals("job-1", request.workSpec.input.getString(BackgroundGenerationWorker.InputJobId))
        assertEquals("background-generation-job-1", BackgroundGenerationWorker.uniqueWorkName("job-1"))
    }

    @Test
    fun configuredDebugWorkerUsesProductionRuntime() {
        assertNull(backgroundGenerationRuntimeFor(HybridLlmRuntimeMode.Production))
        assertTrue(backgroundGenerationRuntimeFor(HybridLlmRuntimeMode.Fake) is FakeLlmGenerationRuntime)
    }
}
