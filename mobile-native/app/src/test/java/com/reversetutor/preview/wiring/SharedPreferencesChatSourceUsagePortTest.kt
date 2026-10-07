package com.reversetutor.preview.wiring

import android.content.SharedPreferences
import com.reversetutor.feature.chat.InMemorySessionSettingsStore
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.SessionSettingsCoordinator
import com.reversetutor.feature.chat.SessionSettingsDocument
import com.reversetutor.feature.chat.SessionSettingsStoredState
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedPreferencesChatSourceUsagePortTest {
    @Test
    fun evidenceUsageRoundTripsThroughProductionSharedPreferencesStore() {
        val preferences = inMemorySharedPreferences()
        val store = SharedPreferencesSessionSettingsStore(preferences)

        SharedPreferencesChatSourceUsagePort(store)
            .recordSourcesUsed("session-a", setOf("older"), 100L)

        val restoredStore = SharedPreferencesSessionSettingsStore(preferences)
        assertEquals(100L, restoredStore.sourceLastUsedAt("session-a")["older"])
    }

    @Test
    fun evidenceUsageUpdatesStoredTimestampAndMovesUsedSourceToTop() {
        val store = InMemorySessionSettingsStore()
        val snapshot = NewSessionConfiguration(title = "代数", learnerRole = "学生")
        val document = SessionSettingsDocument.fromSnapshot(snapshot)
        val older = source("older", 10L)
        val newer = source("newer", 20L)
        store.save(
            "session-a",
            SessionSettingsStoredState(
                applied = document,
                form = document,
                sources = listOf(older, newer),
                sourceLastUsedAt = mapOf("older" to 10L, "newer" to 20L)
            )
        )

        SharedPreferencesChatSourceUsagePort(store)
            .recordSourcesUsed("session-a", setOf("older"), 100L)

        val restored = store.load("session-a")!!
        assertEquals(100L, restored.sourceLastUsedAt["older"])
        assertEquals(100L, restored.sources.single { it.id == "older" }.lastUsedAtEpochMillis)

        val coordinator = SessionSettingsCoordinator(
            sessionId = "session-a",
            initial = document,
            initialSources = listOf(older, newer),
            store = store
        )
        assertEquals(listOf("older", "newer"), coordinator.visibleSources().map(SessionSource::id))
    }

    private fun source(id: String, lastUsedAt: Long) = SessionSource(
        id = id,
        displayName = id,
        managedName = id,
        typeLabel = "TXT",
        readState = SourceReadState.Ready,
        currentSessionReferenced = true,
        referenceOwnerIds = listOf("session-a"),
        lastUsedAtEpochMillis = lastUsedAt,
        preview = "body",
        bytesRetained = true
    )

    private fun inMemorySharedPreferences(): SharedPreferences {
        val values = linkedMapOf<String, String?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString" -> editor.also { values[args!![0] as String] = args[1] as String? }
                "remove" -> editor.also { values.remove(args!![0] as String) }
                "clear" -> editor.also { values.clear() }
                "commit" -> true
                "apply" -> null
                else -> editor
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1] as String?
                "edit" -> editor
                "contains" -> values.containsKey(args!![0] as String)
                "getAll" -> values.toMap()
                else -> method.returnType.defaultValue()
            }
        } as SharedPreferences
    }

    private fun Class<*>.defaultValue(): Any? = when (this) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        else -> null
    }
}
