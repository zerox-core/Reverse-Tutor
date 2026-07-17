package com.reversetutor.preview.wiring

import org.junit.Assert.assertEquals
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
}
