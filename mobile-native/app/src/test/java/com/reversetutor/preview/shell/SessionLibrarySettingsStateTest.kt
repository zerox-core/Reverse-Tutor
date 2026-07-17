package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLibrarySettingsStateTest {
    @Test
    fun referenceLibraryStartsWithSixConnectedAndFiveEnabledSources() {
        val state = SessionLibrarySettingsState.reference()

        assertEquals(6, state.sources.size)
        assertEquals(5, state.enabledCount)
        assertEquals("本会话已启用 5 份资料", state.enabledSummary)
        assertTrue(state.sources.first().enabled)
        assertFalse(state.sources.single { it.title == "函数与作用域.pdf" }.enabled)
    }

    @Test
    fun referenceLibraryKeepsImportingAndFailureStatesVisible() {
        val state = SessionLibrarySettingsState.reference()

        assertEquals(
            SessionSourceStatus.Importing(60),
            state.sources.single { it.title == "错题截图 07-12.jpg" }.status
        )
        assertEquals(
            SessionSourceStatus.ReadFailed,
            state.sources.single { it.title == "函数与作用域.pdf" }.status
        )
    }
}
