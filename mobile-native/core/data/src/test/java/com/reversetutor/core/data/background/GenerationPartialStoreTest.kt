package com.reversetutor.core.data.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationPartialStoreTest {
    @Test
    fun previewIsTokenScopedBoundedAndRemovedOnCompletion() {
        val store = GenerationPartialStore()

        assertEquals("first", store.append("job-1", "token-a", "first"))
        assertEquals("first second", store.append("job-1", "token-a", " second"))
        assertNull(store.append("job-1", "token-b", "stale"))
        assertEquals("first second", store.get("job-1", "token-a"))
        assertNull(store.get("job-1", "token-b"))

        store.clear("job-1", "token-a")
        assertNull(store.get("job-1", "token-a"))
    }
}
