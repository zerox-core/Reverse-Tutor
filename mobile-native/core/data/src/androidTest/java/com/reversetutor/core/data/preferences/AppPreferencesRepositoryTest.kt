package com.reversetutor.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesRepositoryTest {
    private lateinit var storeFile: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storeFile = File(context.filesDir, "settings-foundation-test.preferences_pb")
        storeFile.delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        storeFile.delete()
    }

    @Test
    fun preferencesPersistAcrossRepositoryRecreation() = runBlocking {
        val firstRepository = AppPreferencesRepository(newDataStore())
        firstRepository.setTheme(ThemePreference.Focus)
        firstRepository.setGlobalAvatarVisible(false)
        firstRepository.updateMemo(MemoSlot.Primary, "first memo")
        firstRepository.updateMemo(MemoSlot.Secondary, "second memo")
        firstRepository.updateMemo(MemoSlot.Scratch, "scratch memo")
        scopes.removeFirst().cancel()

        val recreatedRepository = AppPreferencesRepository(newDataStore())
        val preferences = recreatedRepository.preferences.first()

        assertEquals(ThemePreference.Focus, preferences.theme)
        assertFalse(preferences.globalAvatarVisible)
        assertEquals("first memo", preferences.primaryMemo)
        assertEquals("second memo", preferences.secondaryMemo)
        assertEquals("scratch memo", preferences.scratchMemo)
    }

    private fun newDataStore() = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add),
        produceFile = { storeFile }
    )
}
