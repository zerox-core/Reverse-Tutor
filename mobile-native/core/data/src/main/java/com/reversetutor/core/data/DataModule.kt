package com.reversetutor.core.data

import android.content.Context
import androidx.room.Room
import androidx.datastore.preferences.preferencesDataStore
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.llm.AndroidKeystoreSecretStore
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.data.llm.SecretStore
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.migration.NativeExportRepository
import com.reversetutor.core.data.migration.NativeImportRepository
import com.reversetutor.core.data.migration.RoomNativeExportStore
import com.reversetutor.core.data.migration.RoomNativeImportStore
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.preferences.AppPreferencesRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.wipe.LocalDataWipeRepository
import com.reversetutor.core.data.wipe.RoomLocalDataWipeStore
import com.reversetutor.core.llm.FakeLlmGenerationRuntime

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "reverse_tutor_app_preferences"
)

object DataModule {
    const val databaseName = "reverse_tutor_native.db"
    @Volatile private var databaseInstance: ReverseTutorDatabase? = null

    fun appPreferencesRepository(context: Context): AppPreferencesRepository =
        AppPreferencesRepository(context.applicationContext.appPreferencesDataStore)

    fun database(context: Context): ReverseTutorDatabase =
        databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                ReverseTutorDatabase::class.java,
                databaseName
            ).build().also { databaseInstance = it }
        }

    fun sessionRepository(context: Context): SessionRepository {
        val database = database(context)
        return SessionRepository(
            spaceDao = database.spaceDao(),
            sessionDao = database.sessionDao(),
            sessionSettingsDao = database.sessionSettingsDao()
        )
    }

    fun messageRepository(context: Context): MessageRepository {
        val database = database(context)
        return MessageRepository(
            messageDao = database.messageDao(),
            messageAttachmentDao = database.messageAttachmentDao(),
            messageQuoteDao = database.messageQuoteDao()
        )
    }

    fun secretStore(context: Context): SecretStore =
        AndroidKeystoreSecretStore(context.applicationContext)

    fun llmProfileRepository(context: Context): LlmProfileRepository {
        val database = database(context)
        return LlmProfileRepository(
            llmProfileDao = database.llmProfileDao(),
            secretStore = secretStore(context)
        )
    }

    fun chatGenerationRepository(context: Context): ChatGenerationRepository =
        ChatGenerationRepository(
            messageRepository = messageRepository(context),
            llmProfileRepository = llmProfileRepository(context),
            runtime = FakeLlmGenerationRuntime()
        )

    fun localDataWipeRepository(context: Context): LocalDataWipeRepository {
        val appContext = context.applicationContext
        return LocalDataWipeRepository(
            store = RoomLocalDataWipeStore(database(appContext)),
            secretStore = secretStore(appContext),
            resetPreferences = {
                appPreferencesRepository(appContext).resetToDefaults()
            }
        )
    }

    fun nativeImportRepository(context: Context): NativeImportRepository =
        NativeImportRepository(
            store = RoomNativeImportStore(database(context.applicationContext))
        )

    fun nativeExportRepository(context: Context): NativeExportRepository =
        NativeExportRepository(
            store = RoomNativeExportStore(database(context.applicationContext))
        )

    fun sourceRepository(context: Context): SourceRepository =
        SourceRepository(
            sourceDao = database(context.applicationContext).sourceDao()
        )

    fun memoryRepository(context: Context): MemoryRepository =
        MemoryRepository(
            memoryDao = database(context.applicationContext).memoryDao()
        )

    fun graphRepository(context: Context): GraphRepository =
        GraphRepository(
            graphDao = database(context.applicationContext).graphDao()
        )
}
