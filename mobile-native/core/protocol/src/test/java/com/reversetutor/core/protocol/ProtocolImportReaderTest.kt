package com.reversetutor.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolImportReaderTest {
    @Test
    fun readsSessionExportAsWritableSessionAndMessages() {
        val result = ProtocolImportReader.read(
            ProtocolFixtureLoader.read("valid/session-export.json")
        )

        assertTrue(result.isValid)
        assertEquals(ProtocolDocumentType.SessionExport, result.document?.documentType)
        assertEquals(listOf("session-fixture-1"), result.document?.sessions?.map { it.id })
        assertEquals(listOf("message-fixture-1"), result.document?.messages?.map { it.id })
        assertEquals("session-fixture-1", result.document?.messages?.single()?.sessionId)
    }

    @Test
    fun readsFullBackupSessionsAndRedactedProfiles() {
        val result = ProtocolImportReader.read(
            ProtocolFixtureLoader.read("valid/full-backup.json")
        )

        assertTrue(result.isValid)
        assertEquals(ProtocolDocumentType.FullBackup, result.document?.documentType)
        assertEquals(1, result.document?.sessions?.size)
        assertEquals(1, result.document?.llmProfiles?.size)
        assertTrue(result.warnings.any { it.contains("Graph payload") })
    }

    @Test
    fun readsVersionOneFullBackupAfterVersionTwoBecomesCurrent() {
        val result = ProtocolImportReader.read(
            ProtocolFixtureLoader.read("valid/full-backup.json")
        )

        assertTrue(result.validation.errors.toString(), result.isValid)
        assertEquals(1, result.document?.version)
        assertEquals("profile-fixture-1", result.document?.llmProfiles?.single()?.id)
    }

    @Test
    fun readsVersionTwoConnectionsAndBindingsWithoutSecretMaterial() {
        val result = ProtocolImportReader.read(
            """
            {
              "schema": "reverse_tutor_full_backup_v1",
              "version": 2,
              "type": "full_backup",
              "created_at": "2026-07-11T00:00:00Z",
              "sessions": [],
              "provider_connections": [{
                "id": "connection-1",
                "name": "Provider",
                "protocol": "OpenAiCompatible",
                "secret_status": "excluded"
              }],
              "model_bindings": [{
                "id": "binding-1",
                "connection_id": "connection-1",
                "model_id": "model-1",
                "display_name": "Model"
              }],
              "graph": {}
            }
            """.trimIndent()
        )

        assertTrue(result.validation.errors.toString(), result.isValid)
        assertEquals("connection-1", result.document?.providerConnections?.single()?.id)
        assertEquals("binding-1", result.document?.modelBindings?.single()?.id)
        assertTrue(result.document?.llmProfiles.orEmpty().isEmpty())
    }

    @Test
    fun invalidWholeFileProducesNoDocument() {
        val result = ProtocolImportReader.read(
            ProtocolFixtureLoader.read("invalid/full-backup-missing-version.json")
        )

        assertFalse(result.isValid)
        assertEquals(null, result.document)
        assertTrue(result.validation.errors.any { it.contains("version") })
    }
}
