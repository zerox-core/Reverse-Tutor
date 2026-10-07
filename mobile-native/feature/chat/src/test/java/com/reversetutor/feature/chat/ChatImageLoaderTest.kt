package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageLoaderTest {
    @Test
    fun highPixelImageUsesPowerOfTwoSamplingForThumbnailAndViewer() {
        val thumbnail = calculateChatImageSampleSize(
            sourceWidth = 12_000,
            sourceHeight = 9_000,
            targetWidth = ChatImageTargets.Thumbnail.widthPixels,
            targetHeight = ChatImageTargets.Thumbnail.heightPixels
        )
        val viewer = calculateChatImageSampleSize(
            sourceWidth = 12_000,
            sourceHeight = 9_000,
            targetWidth = 1_440,
            targetHeight = 3_200
        )

        assertEquals(16, thumbnail)
        assertEquals(8, viewer)
        assertTrue(thumbnail > 1)
        assertTrue(viewer > 1)
        assertEquals(0, thumbnail and (thumbnail - 1))
        assertEquals(0, viewer and (viewer - 1))
    }

    @Test
    fun invalidBoundsFallBackToSafeNoSampling() {
        assertEquals(1, calculateChatImageSampleSize(0, 9_000, 720, 440))
        assertEquals(1, calculateChatImageSampleSize(12_000, 9_000, 0, 440))
    }

    @Test
    fun cacheReusesAdequateResolutionAndEvictsToPixelBudget() {
        val cache = ChatImageLruCache<String>(maxPixelCount = 100L)
        val viewer = CachedChatImage(
            uri = "content://image/one",
            sampleSize = 2,
            width = 8,
            height = 8,
            value = "viewer"
        )
        cache.put(viewer)

        assertSame(viewer, cache.get("content://image/one", requestedSampleSize = 4))
        assertNull(cache.get("content://image/one", requestedSampleSize = 1))

        cache.put(CachedChatImage("file:///two.jpg", 2, 7, 7, "second"))

        assertTrue(cache.pixelCount <= 100L)
        assertEquals(1, cache.entryCount)
        assertNull(cache.get("content://image/one", requestedSampleSize = 4))
    }

    @Test
    fun cacheDoesNotKeepAnEntryLargerThanItsWholeBudget() {
        val cache = ChatImageLruCache<String>(maxPixelCount = 10L)

        cache.put(CachedChatImage("content://huge", 1, 4, 4, "huge"))

        assertEquals(0, cache.entryCount)
        assertEquals(0L, cache.pixelCount)
    }
}
