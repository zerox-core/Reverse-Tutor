package com.reversetutor.preview.shell

import com.reversetutor.core.data.migration.NativeExportKind
import com.reversetutor.core.data.migration.NativeExportResult
import com.reversetutor.core.data.migration.NativeExportSnapshot
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.data.migration.NativeImportResult
import com.reversetutor.core.data.migration.NativeImportStatus
import com.reversetutor.core.domain.ReleaseMetadata
import com.reversetutor.core.model.SyncConflict
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.TutorSession
import com.reversetutor.feature.settings.FormalImportIssueSeverity
import com.reversetutor.feature.settings.FormalImportMode
import com.reversetutor.feature.settings.FormalSyncSource
import com.reversetutor.feature.settings.FormalTokenPeriod
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalBatch6RuntimeStateTest {
    @Test
    fun tokenStateIsCalculatedFromPersistedUsageRecords() {
        val now = 1_800_000_000_000L
        val session = TutorSession(
            id = "session-1",
            spaceId = "default-space",
            title = "线性代数",
            createdAtEpochMillis = now - 10_000,
            updatedAtEpochMillis = now
        )
        val usage = listOf(
            TokenUsageRecord(
                id = "usage-1",
                spaceId = "default-space",
                turnId = "turn-1",
                attempt = 1,
                modelBindingId = "model-1",
                inputTokens = 1_000,
                outputTokens = 500,
                cachedTokens = 100,
                reasoningTokens = 50,
                totalTokens = 1_500,
                estimated = false,
                createdAtEpochMillis = now - 1_000
            )
        )

        val state = FormalBatch6RuntimeStateFactory.tokenSnapshot(
            allUsage = usage,
            sessions = listOf(session),
            turnToSession = mapOf("turn-1" to "session-1"),
            modelNames = mapOf("model-1" to "DeepSeek Chat"),
            period = FormalTokenPeriod.Week,
            query = "",
            nowEpochMillis = now
        )

        assertEquals("1.5K", state.overview.summary.totalLabel)
        assertEquals("实测", state.overview.summary.estimateLabel)
        assertEquals("DeepSeek Chat", state.byModel.items.single().name)
        assertEquals("线性代数", state.bySession.items.single().title)
        assertTrue(state.overview.trend.all { it.fraction in 0f..1f })
    }

    @Test
    fun importPreviewUsesRepositoryErrorsAndKeepsSecretsBlocked() {
        val result = NativeImportResult(
            batchId = "dry-run",
            sourceFileName = "backup.json",
            sourceSchema = "reverse_tutor_full_backup_v1",
            documentType = null,
            mode = NativeImportMode.Append,
            status = NativeImportStatus.DryRun,
            insertedCounts = mapOf("sessions" to 2, "messages" to 8),
            skippedCounts = emptyMap(),
            warnings = emptyList(),
            errors = listOf("API key fields are not allowed."),
            startedAtEpochMillis = 1L,
            completedAtEpochMillis = null
        )

        val state = FormalBatch6RuntimeStateFactory.importPreview(
            RuntimeImportDocument("backup.json", "{}", 2L),
            result,
            FormalImportMode.Append
        )

        assertFalse(state.canImport)
        assertFalse(state.isValidated)
        assertEquals(2, state.sessionCount)
        assertEquals(FormalImportIssueSeverity.Secret, state.issues.single().severity)
    }

    @Test
    fun multipleSessionExportsRemainIndividualValidatedJsonDocumentsInsideZip() {
        val first = validExport("session-a.json", "{\"schema\":\"a\"}")
        val second = validExport("session-b.json", "{\"schema\":\"b\"}")

        val document = FormalBatch6RuntimeStateFactory.sessionExportDocument(listOf(first, second))

        assertNotNull(document)
        assertTrue(document!!.fileName.endsWith(".zip"))
        val entries = mutableListOf<String>()
        ZipInputStream(document.bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }
        assertEquals(listOf("session-a.json", "session-b.json"), entries)
    }

    @Test
    fun updateStateOnlyMarksLatestAfterSuccessfulVersionComparison() {
        val current = RuntimeAppInfo("1.2.0", 120)
        val unavailable = FormalBatch6RuntimeStateFactory.update(
            release = null,
            checkedSuccessfully = false,
            lastCheckedLabel = "检查失败",
            automaticUpdatesEnabled = false,
            wifiOnlyEnabled = false,
            appInfo = current
        )
        val newer = FormalBatch6RuntimeStateFactory.update(
            release = ReleaseMetadata("1.3.0", 130, 100, "https://example.com/app.apk"),
            checkedSuccessfully = true,
            lastCheckedLabel = "10:00",
            automaticUpdatesEnabled = false,
            wifiOnlyEnabled = false,
            appInfo = current
        )

        assertFalse(unavailable.isLatest)
        assertTrue(unavailable.availableUpdate == null)
        assertFalse(newer.isLatest)
        assertEquals("v1.3.0", newer.availableUpdate?.versionLabel)
    }

    @Test
    fun syncScreensAreMappedOnlyFromPendingRepositoryConflicts() {
        val conflicts = listOf(
            SyncConflict(
                id = "conflict-1",
                spaceId = "default-space",
                entityId = "math-plan",
                entityType = "study_plan",
                localRevision = 4,
                remoteRevision = 5,
                createdAtEpochMillis = 10L
            )
        )

        val overview = FormalBatch6RuntimeStateFactory.syncOverview(conflicts, online = true)
        val choice = FormalBatch6RuntimeStateFactory.syncChoice(
            conflicts.single(),
            conflicts,
            FormalSyncSource.Cloud
        )

        assertEquals(1, overview.conflictCount)
        assertEquals("math-plan", overview.conflictItems.single().title)
        assertEquals(setOf(FormalSyncSource.Device, FormalSyncSource.Cloud), choice.options.map { it.source }.toSet())
        assertEquals(FormalSyncSource.Cloud, choice.selectedSource)
    }

    private fun validExport(fileName: String, json: String): NativeExportResult =
        NativeExportResult(
            kind = NativeExportKind.CurrentSession,
            targetFileName = fileName,
            documentType = null,
            json = json,
            snapshot = NativeExportSnapshot(),
            validation = null,
            warnings = emptyList(),
            errors = emptyList()
        ).copy(
            validation = com.reversetutor.core.protocol.ProtocolValidationResult(
                schema = "test",
                version = 1,
                documentType = null,
                errors = emptyList(),
                warnings = emptyList(),
                redactedJson = json
            )
        )
}
