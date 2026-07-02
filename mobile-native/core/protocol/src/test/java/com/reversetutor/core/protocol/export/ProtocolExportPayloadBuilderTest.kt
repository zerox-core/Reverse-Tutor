package com.reversetutor.core.protocol.export

import com.reversetutor.core.protocol.ProtocolDocumentType
import com.reversetutor.core.protocol.VersionedProtocolValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolExportPayloadBuilderTest {
    @Test
    fun buildsCurrentSessionExportPayloadThatMatchesVersionedContract() {
        val payload = ProtocolExportPayloadBuilder.currentSession(
            createdAt = fixtureCreatedAt,
            session = ExportSessionRecord(
                id = "session-1",
                title = "Limits review",
                extraFields = mapOf(
                    "secretRef" to ExportFieldValue.StringValue("secret-session-ref"),
                    "source" to ExportFieldValue.StringValue("native")
                )
            ),
            messages = listOf(
                ExportMessageRecord(
                    id = "message-1",
                    role = "user",
                    text = "Explain limits intuitively",
                    createdAt = "2026-07-01T00:00:01Z"
                )
            )
        )

        assertEquals(ProtocolDocumentType.SessionExport, payload.documentType)
        assertFalse(payload.json.contains("secretRef"))
        assertFalse(payload.json.contains("secret-session-ref"))

        val validation = VersionedProtocolValidator.validate(payload.json)
        assertTrue(validation.errors.toString(), validation.isValid)
        assertEquals(ProtocolDocumentType.SessionExport, validation.documentType)
    }

    @Test
    fun buildsFullBackupPayloadWithoutLlmSecretReferencesOrApiKeys() {
        val graph = ExportGraphSnapshotPayload(
            createdAt = fixtureCreatedAt,
            nodes = listOf(ExportGraphNodeRecord(id = "node-1", kind = "concept", title = "Derivative")),
            edges = emptyList()
        )

        val payload = ProtocolExportPayloadBuilder.fullBackup(
            createdAt = fixtureCreatedAt,
            sessions = listOf(ExportSessionRecord(id = "session-1", title = "Calculus")),
            llmProfiles = listOf(
                ExportLlmProfileRecord(
                    id = "profile-1",
                    name = "OpenAI compatible",
                    provider = "openai_compatible",
                    apiType = "openai_compatible",
                    baseUrl = "https://example.invalid/v1",
                    model = "fixture-model",
                    secretRef = "secretRef-123",
                    apiKey = "sk-test-secret",
                    extraFields = mapOf("api_key" to ExportFieldValue.StringValue("sk-extra-secret"))
                )
            ),
            graph = graph
        )

        assertEquals(ProtocolDocumentType.FullBackup, payload.documentType)
        assertFalse(payload.json.contains("secretRef"))
        assertFalse(payload.json.contains("secretRef-123"))
        assertFalse(payload.json.contains("apiKey"))
        assertFalse(payload.json.contains("api_key"))
        assertFalse(payload.json.contains("sk-test-secret"))
        assertFalse(payload.json.contains("sk-extra-secret"))
        assertTrue(payload.json.contains("\"secret_status\":\"excluded\""))

        val validation = VersionedProtocolValidator.validate(payload.json)
        assertTrue(validation.errors.toString(), validation.isValid)
        assertEquals(ProtocolDocumentType.FullBackup, validation.documentType)
    }

    @Test
    fun buildsGraphSnapshotPayloadThatMatchesVersionedContract() {
        val payload = ProtocolExportPayloadBuilder.graphSnapshot(
            ExportGraphSnapshotPayload(
                createdAt = fixtureCreatedAt,
                nodes = listOf(
                    ExportGraphNodeRecord(
                        id = "node-1",
                        kind = "concept",
                        title = "Integration",
                        extraFields = mapOf("mastery" to ExportFieldValue.NumberValue("0.75"))
                    )
                ),
                edges = listOf(
                    ExportGraphEdgeRecord(
                        id = "edge-1",
                        sourceNodeId = "node-1",
                        targetNodeId = "node-1",
                        kind = "self"
                    )
                )
            )
        )

        val validation = VersionedProtocolValidator.validate(payload.json)
        assertTrue(validation.errors.toString(), validation.isValid)
        assertEquals(ProtocolDocumentType.GraphSnapshot, validation.documentType)
        assertTrue(payload.json.contains("\"mastery\":0.75"))
    }

    @Test
    fun buildsPresetPayloadThatMatchesVersionedContract() {
        val payload = ProtocolExportPayloadBuilder.preset(
            ExportPresetPayload(
                title = "Exam sprint",
                role = "Socratic exam coach",
                goal = "Prepare for weekly math tests",
                profile = "Concise, strict, and encouraging",
                sourceHandoff = "deferred",
                extraFields = mapOf("apiKey" to ExportFieldValue.StringValue("sk-preset-secret"))
            )
        )

        assertFalse(payload.json.contains("apiKey"))
        assertFalse(payload.json.contains("sk-preset-secret"))

        val validation = VersionedProtocolValidator.validate(payload.json)
        assertTrue(validation.errors.toString(), validation.isValid)
        assertEquals(ProtocolDocumentType.Preset, validation.documentType)
    }

    @Test
    fun exportPayloadValidatorRejectsNonExportDocumentTypes() {
        val result = ProtocolExportPayloadValidator.validate(
            """
            {
              "schema": "native_import_result_v1",
              "version": 1,
              "type": "import_result",
              "status": "completed",
              "mode": "append",
              "inserted_counts": {},
              "skipped_counts": {},
              "warnings": [],
              "errors": []
            }
            """.trimIndent()
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("export", ignoreCase = true) })
    }

    private companion object {
        const val fixtureCreatedAt = "2026-07-01T00:00:00Z"
    }
}
