package com.reversetutor.core.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineCapabilitiesTest {
    @Test
    fun onlineApiComposesAllV1Capabilities() {
        assertTrue(ActivityApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertTrue(ContentApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertTrue(SyncApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertTrue(InsightApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertTrue(ReleaseApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertFalse(AuthApi::class.java.isAssignableFrom(OnlineApi::class.java))
        assertTrue(AuthApi::class.java.isAssignableFrom(HttpOnlineApi::class.java))
    }
}
