package com.reversetutor.core.data.wipe

import com.reversetutor.core.data.llm.SecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataWipeRepositoryTest {
    @Test
    fun wipeDeletesDistinctSecretRefsClearsTablesResetsPreferencesAndReseedsDefaults() = runBlocking {
        val store = FakeLocalDataWipeStore(
            refs = listOf("llm-secret-one", "llm-secret-two", "llm-secret-one")
        )
        val secretStore = FakeSecretStore()
        var preferencesReset = false
        val repository = LocalDataWipeRepository(
            store = store,
            secretStore = secretStore,
            resetPreferences = { preferencesReset = true }
        )

        val result = repository.wipeLocalData(nowEpochMillis = 42L)

        assertEquals(2, result.deletedSecretRefCount)
        assertTrue(result.reseededDefaults)
        assertEquals(42L, result.completedAtEpochMillis)
        assertEquals(listOf("llm-secret-one", "llm-secret-two"), secretStore.deletedRefs)
        assertTrue(store.cleared)
        assertEquals(42L, store.reseededAt)
        assertTrue(preferencesReset)
        assertEquals(
            listOf("listSecretRefs", "clearAllUserTables", "reseedDefaults"),
            store.calls
        )
    }
}

private class FakeLocalDataWipeStore(
    private val refs: List<String>
) : LocalDataWipeStore {
    val calls = mutableListOf<String>()
    var cleared = false
    var reseededAt: Long? = null

    override suspend fun listSecretRefs(): List<String> {
        calls += "listSecretRefs"
        return refs
    }

    override suspend fun clearAllUserTables() {
        calls += "clearAllUserTables"
        cleared = true
    }

    override suspend fun reseedDefaults(nowEpochMillis: Long) {
        calls += "reseedDefaults"
        reseededAt = nowEpochMillis
    }
}

private class FakeSecretStore : SecretStore {
    val deletedRefs = mutableListOf<String>()

    override suspend fun put(ref: String, secret: String) = Unit

    override suspend fun get(ref: String): String? = null

    override suspend fun delete(ref: String) {
        deletedRefs += ref
    }
}
