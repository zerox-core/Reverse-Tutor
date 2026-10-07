package com.reversetutor.preview.wiring

import android.content.SharedPreferences
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatGenerationUiState
import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.LearnerAvatarReference
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.buildChatRouteUiState
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesSessionSnapshotJvmTest {
    @Test
    fun productionSharedPreferencesPersistenceRoundTripsCurrentSnapshotOnJvm() {
        val preferences = inMemorySharedPreferences()
        val persistence = SharedPreferencesNewSessionPersistence(preferences)
        val snapshot = NewSessionConfiguration(
            title = "概率论",
            learnerDisplayName = "小概",
            learnerImageRef = "content://avatar/current",
            avatarVisible = false,
            story = "latest tree",
            sourceSelections = listOf("source-a")
        )

        persistence.saveSessionSnapshot("session-a", snapshot)

        assertEquals(snapshot, SharedPreferencesNewSessionPersistence(preferences).loadSessionSnapshot("session-a"))
    }

    @Test
    fun realPresetRoundTripsIntoChatIdentityRoleAndTypedPackagedAvatar() {
        val preset = FormalLearningPresets.all.first()
        val configuration = NewSessionConfiguration.fromPreset(preset)
        val preferences = inMemorySharedPreferences()
        val persistence = SharedPreferencesNewSessionPersistence(preferences)

        assertEquals(preset.learnerName, configuration.learnerDisplayName)
        assertNotEquals(preset.avatarRes.toString(), configuration.learnerImageRef)
        persistence.saveSessionSnapshot("preset-session", configuration)

        val restored = SharedPreferencesNewSessionPersistence(preferences)
            .loadSessionSnapshot("preset-session")!!
        val chatState = buildChatRouteUiState(
            sessionTitle = restored.title,
            records = emptyList(),
            composer = ChatComposerState(""),
            generation = ChatGenerationUiState.Idle,
            learnerRoleFallback = "fallback role",
            sessionSnapshot = restored
        )

        assertEquals(preset.learnerName, chatState.learnerName)
        assertEquals(configuration.learnerRole, chatState.learnerStatus)
        assertEquals(
            LearnerAvatarReference.PackagedDrawable(preset.avatarRes),
            chatState.learnerAvatarReference
        )
        assertEquals(configuration, restored)
    }

    @Test
    fun legacyThirteenFieldBuiltInPresetMigratesThroughPersistenceIntoChat() {
        val preset = FormalLearningPresets.all.first { it.id == "formal-math-sprint" }
        val encoded = legacyConfiguration(
            presetTitle = preset.title,
            learnerRole = "AI 学生 ${preset.learnerName}：${preset.learnerProfile}",
            learnerProfile = preset.learnerProfile,
            learnerImageRef = preset.avatarRes.toString(),
            builtInPresetId = preset.id,
            includeCustomColumns = false
        )
        val preferences = inMemorySharedPreferences(
            mapOf("session_snapshot_v1_legacy-13" to encoded)
        )

        val restored = SharedPreferencesNewSessionPersistence(preferences)
            .loadSessionSnapshot("legacy-13")!!
        val chatState = restored.toChatState()

        assertEquals(preset.learnerName, restored.learnerDisplayName)
        assertEquals("drawable:${preset.avatarRes}", restored.learnerImageRef)
        assertEquals(preset.learnerName, chatState.learnerName)
        assertEquals(restored.learnerRole, chatState.learnerStatus)
        assertEquals(
            LearnerAvatarReference.PackagedDrawable(preset.avatarRes),
            chatState.learnerAvatarReference
        )
    }

    @Test
    fun legacyFourteenFieldPresetRoleRecoversDirectStoredNameWithoutPresetId() {
        val preset = FormalLearningPresets.all.first { it.id == "formal-python-concepts" }
        val encoded = legacyConfiguration(
            presetTitle = preset.title,
            learnerRole = "AI 学生 ${preset.learnerName}：${preset.learnerProfile}",
            learnerProfile = preset.learnerProfile,
            learnerImageRef = preset.avatarRes.toString(),
            builtInPresetId = "",
            includeCustomColumns = true
        )
        val preferences = inMemorySharedPreferences(
            mapOf("session_snapshot_v1_legacy-14-preset" to encoded)
        )

        val restored = SharedPreferencesNewSessionPersistence(preferences)
            .loadSessionSnapshot("legacy-14-preset")!!
        val chatState = restored.toChatState()

        assertEquals(preset.learnerName, restored.learnerDisplayName)
        assertEquals(
            LearnerAvatarReference.PackagedDrawable(preset.avatarRes),
            chatState.learnerAvatarReference
        )
        assertEquals(preset.learnerName, chatState.learnerName)
    }

    @Test
    fun legacyFourteenFieldUnknownCustomSessionUsesExplicitFallbacks() {
        val encoded = legacyConfiguration(
            presetTitle = "我的自定义会话",
            learnerRole = "谨慎、会追问的自定义学习者",
            learnerProfile = "用户自行填写",
            learnerImageRef = "not-a-valid-avatar-reference",
            builtInPresetId = "",
            includeCustomColumns = true
        )
        val preferences = inMemorySharedPreferences(
            mapOf("session_snapshot_v1_legacy-14-custom" to encoded)
        )

        val restored = SharedPreferencesNewSessionPersistence(preferences)
            .loadSessionSnapshot("legacy-14-custom")!!
        val chatState = restored.toChatState()

        assertEquals("学习者", restored.learnerDisplayName)
        assertNull(restored.learnerImageRef)
        assertEquals("学习者", chatState.learnerName)
        assertNull(chatState.learnerAvatarReference)
    }

    private fun NewSessionConfiguration.toChatState() = buildChatRouteUiState(
        sessionTitle = title,
        records = emptyList(),
        composer = ChatComposerState(""),
        generation = ChatGenerationUiState.Idle,
        learnerRoleFallback = "fallback role",
        sessionSnapshot = this
    )

    private fun legacyConfiguration(
        presetTitle: String,
        learnerRole: String,
        learnerProfile: String,
        learnerImageRef: String,
        builtInPresetId: String,
        includeCustomColumns: Boolean
    ): String {
        val fields = listOf(
            presetTitle,
            learnerRole,
            learnerProfile,
            "legacy goal",
            "legacy plan",
            "legacy dialogue",
            "legacy story",
            "",
            "",
            "legacy opening",
            learnerImageRef,
            "",
            builtInPresetId
        )
        return pack(if (includeCustomColumns) fields + "" else fields)
    }

    private fun pack(values: List<String>): String = buildString {
        values.forEach { value -> append(value.length).append(':').append(value) }
    }

    private fun inMemorySharedPreferences(
        initialValues: Map<String, String?> = emptyMap()
    ): SharedPreferences {
        val values = linkedMapOf<String, String?>().apply { putAll(initialValues) }
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
