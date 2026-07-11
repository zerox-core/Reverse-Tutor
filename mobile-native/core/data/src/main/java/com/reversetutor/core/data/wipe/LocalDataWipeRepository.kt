package com.reversetutor.core.data.wipe

import com.reversetutor.core.data.llm.SecretStore
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.session.SessionRepository

class LocalDataWipeRepository(
    private val store: LocalDataWipeStore,
    private val secretStore: SecretStore,
    private val resetPreferences: suspend () -> Unit = {}
) {
    suspend fun wipeLocalData(nowEpochMillis: Long): LocalDataWipeResult {
        val secretRefs = store.listSecretRefs().distinct()
        secretRefs.forEach { ref ->
            secretStore.delete(ref)
        }
        store.clearAllUserTables()
        resetPreferences()
        store.reseedDefaults(nowEpochMillis)
        return LocalDataWipeResult(
            deletedSecretRefCount = secretRefs.size,
            reseededDefaults = true,
            completedAtEpochMillis = nowEpochMillis
        )
    }
}

interface LocalDataWipeStore {
    suspend fun listSecretRefs(): List<String>
    suspend fun clearAllUserTables()
    suspend fun reseedDefaults(nowEpochMillis: Long)
}

data class LocalDataWipeResult(
    val deletedSecretRefCount: Int,
    val reseededDefaults: Boolean,
    val completedAtEpochMillis: Long
)

class RoomLocalDataWipeStore(
    private val database: ReverseTutorDatabase
) : LocalDataWipeStore {
    override suspend fun listSecretRefs(): List<String> =
        buildList {
            database.llmProfileDao().listAll().mapNotNullTo(this) { it.secretRef }
            database.modelConnectionDao().listAllConnections().mapNotNullTo(this) { it.secretRef }
        }

    override suspend fun clearAllUserTables() {
        database.clearAllTables()
    }

    override suspend fun reseedDefaults(nowEpochMillis: Long) {
        SessionRepository(
            spaceDao = database.spaceDao(),
            sessionDao = database.sessionDao(),
            sessionSettingsDao = database.sessionSettingsDao()
        ).ensurePreviewSeed(nowEpochMillis)
    }
}
