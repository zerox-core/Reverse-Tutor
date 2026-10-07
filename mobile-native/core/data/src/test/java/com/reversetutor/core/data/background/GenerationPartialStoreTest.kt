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

    @Test
    fun monologueSnapshotIsTokenScopedOverwrittenAndClearedWithEntry() {
        val store = GenerationPartialStore()

        // 独白先于正文到达：条目可以只有独白。
        assertEquals("我在想", store.setMonologue("job-1", "token-a", "我在想"))
        assertEquals("我在想", store.getMonologue("job-1", "token-a"))
        assertEquals("", store.get("job-1", "token-a"))

        // 覆盖语义：快照全量替换，不是追加。
        assertEquals("我在想第二段", store.setMonologue("job-1", "token-a", "我在想第二段"))
        assertEquals("我在想第二段", store.getMonologue("job-1", "token-a"))

        // 正文追加不清独白。
        store.append("job-1", "token-a", "正文")
        assertEquals("我在想第二段", store.getMonologue("job-1", "token-a"))
        assertEquals("正文", store.get("job-1", "token-a"))

        // token 不匹配：不写不读。
        assertNull(store.setMonologue("job-1", "token-b", "过期独白"))
        assertNull(store.getMonologue("job-1", "token-b"))

        store.clear("job-1", "token-a")
        assertNull(store.getMonologue("job-1", "token-a"))
    }
}
