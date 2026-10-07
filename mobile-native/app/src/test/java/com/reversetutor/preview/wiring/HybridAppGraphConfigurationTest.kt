package com.reversetutor.preview.wiring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HybridAppGraphConfigurationTest {
    @Test
    fun onlineModesKeepLocalMockAndHttpSelectionExplicit() {
        assertEquals(HybridOnlineMode.LocalOnly, HybridOnlineConfiguration.LocalOnly.mode)
        assertEquals(HybridOnlineMode.ContractMock, HybridOnlineConfiguration.ContractMock.mode)

        val http = HybridOnlineConfiguration.Http("https://api.example.com")
        assertEquals(HybridOnlineMode.Http, http.mode)
        assertEquals("https://api.example.com", http.baseUrl)
    }

    @Test
    fun blankRuntimeBaseUrlKeepsOnlineServicesOptional() {
        assertSame(
            HybridOnlineConfiguration.LocalOnly,
            HybridOnlineConfiguration.fromBaseUrl("   ")
        )

        val configured = HybridOnlineConfiguration.fromBaseUrl(" https://api.example.com/ ")
        assertEquals("https://api.example.com", (configured as HybridOnlineConfiguration.Http).baseUrl)
    }
}
