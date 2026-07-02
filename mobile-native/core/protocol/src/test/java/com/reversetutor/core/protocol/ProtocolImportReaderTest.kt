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
    fun invalidWholeFileProducesNoDocument() {
        val result = ProtocolImportReader.read(
            ProtocolFixtureLoader.read("invalid/full-backup-missing-version.json")
        )

        assertFalse(result.isValid)
        assertEquals(null, result.document)
        assertTrue(result.validation.errors.any { it.contains("version") })
    }
}
