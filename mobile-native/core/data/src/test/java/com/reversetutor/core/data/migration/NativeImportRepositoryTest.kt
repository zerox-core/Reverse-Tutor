package com.reversetutor.core.data.migration

import com.reversetutor.core.data.local.entity.ImportBatchEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeImportRepositoryTest {
    @Test
    fun dryRunValidatesSessionExportWithoutWriting() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.dryRun(sessionExportJson, "session.json", nowEpochMillis = 100L)

        assertEquals(NativeImportStatus.DryRun, result.status)
        assertEquals(mapOf("sessions" to 1, "messages" to 1, "llm_profiles" to 0), result.insertedCounts)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.canWrite)
        assertFalse(store.written)
    }

    @Test
    fun importWritesValidRecordsAndBatchResult() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 100L,
            batchId = "import-1"
        )

        assertEquals(NativeImportStatus.Completed, result.status)
        assertTrue(store.written)
        assertEquals("import-1", store.writeSet?.batch?.id)
        assertEquals("session-1", store.writeSet?.sessions?.single()?.id)
        assertEquals("message-1", store.writeSet?.messages?.single()?.id)
        assertEquals("import-1", store.writeSet?.messages?.single()?.sourceImportId)
        assertEquals(NativeImportMode.Append.wireValue, store.writeSet?.batch?.mode)
        assertFalse(store.writeSet?.replaceExistingSpace == true)
        assertTrue(result.toProtocolJson().contains("\"schema\":\"native_import_result_v1\""))
    }

    @Test
    fun overwriteRequiresConfirmationBeforeWriting() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 100L,
            mode = NativeImportMode.Overwrite,
            batchId = "import-overwrite"
        )

        assertEquals(NativeImportStatus.Failed, result.status)
        assertTrue(result.errors.any { it.contains("requires destructive confirmation") })
        assertFalse(store.written)
    }

    @Test
    fun overwriteWritesReplaceSpaceTransactionIntentAndAuditMode() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 100L,
            mode = NativeImportMode.Overwrite,
            overwriteConfirmed = true,
            batchId = "import-overwrite"
        )

        assertEquals(NativeImportStatus.Completed, result.status)
        assertTrue(store.written)
        assertEquals(NativeImportMode.Overwrite.wireValue, store.writeSet?.batch?.mode)
        assertTrue(store.writeSet?.replaceExistingSpace == true)
        assertEquals("default-space", store.writeSet?.space?.id)
    }

    @Test
    fun newSpaceRewritesRecordsIntoStableImportedSpace() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 100L,
            mode = NativeImportMode.NewSpace,
            batchId = "import-new-1"
        )
        val firstWriteSet = requireNotNull(store.writeSet)
        repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 200L,
            mode = NativeImportMode.NewSpace,
            batchId = "import-new-2"
        )
        val secondWriteSet = requireNotNull(store.writeSet)

        assertEquals(NativeImportMode.NewSpace.wireValue, firstWriteSet.batch.mode)
        assertTrue(firstWriteSet.space.id.startsWith("import-space-"))
        assertEquals(firstWriteSet.space.id, secondWriteSet.space.id)
        assertEquals(firstWriteSet.sessions.single().id, secondWriteSet.sessions.single().id)
        assertEquals(firstWriteSet.messages.single().id, secondWriteSet.messages.single().id)
        assertEquals(firstWriteSet.sessions.single().id, firstWriteSet.messages.single().sessionId)
        assertFalse(firstWriteSet.replaceExistingSpace)
    }

    @Test
    fun appendRerunsWithSourceIdsKeepStableRecordIds() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 100L,
            mode = NativeImportMode.Append,
            batchId = "import-1"
        )
        val firstWriteSet = requireNotNull(store.writeSet)
        repository.importJson(
            json = sessionExportJson,
            sourceFileName = "session.json",
            nowEpochMillis = 200L,
            mode = NativeImportMode.Append,
            batchId = "import-2"
        )
        val secondWriteSet = requireNotNull(store.writeSet)

        assertEquals("session-1", firstWriteSet.sessions.single().id)
        assertEquals(firstWriteSet.sessions.single().id, secondWriteSet.sessions.single().id)
        assertEquals(firstWriteSet.messages.single().id, secondWriteSet.messages.single().id)
    }

    @Test
    fun invalidWholeFileWritesNothing() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.importJson(
            json = """{"schema":"reverse_tutor_full_backup_v1","type":"full_backup","created_at":"now","sessions":[]}""",
            sourceFileName = "broken.json",
            nowEpochMillis = 100L,
            batchId = "import-broken"
        )

        assertEquals(NativeImportStatus.Failed, result.status)
        assertTrue(result.errors.any { it.contains("version") })
        assertFalse(store.written)
    }

    @Test
    fun importedConnectionsAndLegacyProfilesNeverRestoreSecretReferences() = runBlocking {
        val directStore = FakeNativeImportStore()
        NativeImportRepository(directStore).importJson(
            json = connectionBackupJson,
            sourceFileName = "connections.json",
            nowEpochMillis = 100L,
            batchId = "import-connections"
        )

        assertEquals(
            null,
            directStore.writeSet?.providerConnections?.single()?.secretRef
        )

        val legacyStore = FakeNativeImportStore()
        NativeImportRepository(legacyStore).importJson(
            json = legacyProfileBackupJson,
            sourceFileName = "legacy-profile.json",
            nowEpochMillis = 100L,
            batchId = "import-legacy"
        )

        assertEquals(null, legacyStore.writeSet?.llmProfiles?.single()?.secretRef)
        assertEquals(
            null,
            legacyStore.writeSet?.providerConnections?.single()?.secretRef
        )
    }

    @Test
    fun unsafeRecordIsSkippedAndReportedAsPartial() = runBlocking {
        val store = FakeNativeImportStore()
        val repository = NativeImportRepository(store)

        val result = repository.importJson(
            json = sessionExportJson.replace("\"role\":\"user\"", "\"role\":\"unknown\""),
            sourceFileName = "partial.json",
            nowEpochMillis = 100L,
            batchId = "import-partial"
        )

        assertEquals(NativeImportStatus.Partial, result.status)
        assertEquals(1, result.insertedCounts.getValue("sessions"))
        assertEquals(0, result.insertedCounts.getValue("messages"))
        assertEquals(1, result.skippedCounts.getValue("messages"))
        assertTrue(store.written)
    }

    private val sessionExportJson = """
        {
          "schema":"reverse_tutor_session_export_v1",
          "version":1,
          "type":"session_export",
          "created_at":"2026-07-01T00:00:00Z",
          "session":{"id":"session-1","title":"Imported session"},
          "messages":[{"id":"message-1","role":"user","text":"Hello"}]
        }
    """.trimIndent()

    private val connectionBackupJson = """
        {
          "schema":"reverse_tutor_full_backup_v1",
          "version":2,
          "type":"full_backup",
          "created_at":"2026-07-11T00:00:00Z",
          "sessions":[],
          "provider_connections":[{
            "id":"connection-1",
            "name":"Provider",
            "protocol":"OpenAiCompatible",
            "secret_status":"excluded"
          }],
          "model_bindings":[{
            "id":"binding-1",
            "connection_id":"connection-1",
            "model_id":"model-1",
            "display_name":"Model"
          }],
          "graph":{}
        }
    """.trimIndent()

    private val legacyProfileBackupJson = """
        {
          "schema":"reverse_tutor_full_backup_v1",
          "version":1,
          "type":"full_backup",
          "created_at":"2026-07-01T00:00:00Z",
          "sessions":[],
          "llm_profiles":[{
            "schema":"native_llm_profile_v1",
            "version":1,
            "type":"llm_profile",
            "id":"profile-1",
            "name":"Legacy profile",
            "provider":"local",
            "api_type":"local",
            "model":"fixture-model",
            "secret_status":"excluded"
          }],
          "graph":{}
        }
    """.trimIndent()
}

private class FakeNativeImportStore : NativeImportStore {
    var written = false
    var writeSet: NativeImportWriteSet? = null
    val writeSets = mutableListOf<NativeImportWriteSet>()

    override suspend fun writeImport(writeSet: NativeImportWriteSet) {
        written = true
        this.writeSet = writeSet
        writeSets += writeSet
    }
}
