package com.reversetutor.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionedProtocolSchemaValidatorTest {
    @Test
    fun validatesEverySupportedContractFixture() {
        val cases = listOf(
            "valid/legacy-export.json" to ProtocolDocumentType.LegacyExport,
            "valid/full-backup.json" to ProtocolDocumentType.FullBackup,
            "valid/session-export.json" to ProtocolDocumentType.SessionExport,
            "valid/graph-snapshot.json" to ProtocolDocumentType.GraphSnapshot,
            "valid/preset.json" to ProtocolDocumentType.Preset,
            "valid/llm-profile.json" to ProtocolDocumentType.LlmProfile,
            "valid/import-result.json" to ProtocolDocumentType.ImportResult
        )

        cases.forEach { (fixturePath, expectedType) ->
            val result = VersionedProtocolValidator.validate(ProtocolFixtureLoader.read(fixturePath))

            assertTrue("$fixturePath should be valid: ${result.errors}", result.isValid)
            assertEquals("$fixturePath type", expectedType, result.documentType)
            assertEquals("$fixturePath version", 1, result.version)
            assertTrue("$fixturePath errors", result.errors.isEmpty())
            assertTrue("$fixturePath warnings", result.warnings.isEmpty())
            assertNotNull("$fixturePath redacted output", result.redactedJson)
        }
    }

    @Test
    fun reportsWarningFixtureWithoutRejectingIt() {
        val result = VersionedProtocolValidator.validate(
            ProtocolFixtureLoader.read("warning/session-export-empty-messages.json")
        )

        assertTrue(result.isValid)
        assertEquals(ProtocolDocumentType.SessionExport, result.documentType)
        assertTrue(result.warnings.any { it.contains("messages", ignoreCase = true) })
    }

    @Test
    fun acceptsUnknownFieldsAsDeterministicWarnings() {
        val result = VersionedProtocolValidator.validate(
            ProtocolFixtureLoader.read("unknown-field/graph-snapshot-extra-field.json")
        )

        assertTrue(result.isValid)
        assertEquals(ProtocolDocumentType.GraphSnapshot, result.documentType)
        assertEquals(
            listOf("Unknown top-level fields for reverse_tutor_graph_snapshot_v1: legacy_canvas_hint."),
            result.warnings
        )
    }

    @Test
    fun rejectsInvalidFixtureWithStableErrorMessages() {
        val result = VersionedProtocolValidator.validate(
            ProtocolFixtureLoader.read("invalid/full-backup-missing-version.json")
        )

        assertFalse(result.isValid)
        assertEquals(ProtocolDocumentType.FullBackup, result.documentType)
        assertEquals(
            listOf("Field version is required.", "Schema reverse_tutor_full_backup_v1 requires version 1."),
            result.errors
        )
    }

    @Test
    fun redactsSecretMaterialFromValidationResult() {
        val result = VersionedProtocolValidator.validate(
            ProtocolFixtureLoader.read("secret-redaction/llm-profile-raw-key.json")
        )

        assertFalse(result.isValid)
        assertEquals(ProtocolDocumentType.LlmProfile, result.documentType)
        assertTrue(result.errors.any { it.contains("secret", ignoreCase = true) })
        assertFalse(result.redactedJson.contains("sk-test-secret"))
        assertFalse(result.redactedJson.contains("secretRef-123"))
        assertTrue(result.redactedJson.contains("[REDACTED]"))
    }
}
