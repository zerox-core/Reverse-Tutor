package com.reversetutor.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.data.local.DatabaseSchema
import com.reversetutor.core.data.local.MigrationIds
import com.reversetutor.core.data.local.ReverseTutorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesProfilesToConnectionsAndBindingsAndPreservesSessionSelection() {
        helper.createDatabase(TestDatabase, 2).apply {
            execSQL(
                """
                INSERT INTO spaces VALUES ('space-1', 'Default', 'Default', 1, 1, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_profiles VALUES (
                    'profile-1', 'space-1', 'Primary', 'OpenAiCompatible', 'gpt-test',
                    'secret-ref', 10, 20, 'https://example.invalid/v1', 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO sessions VALUES (
                    'session-1', 'space-1', 'Session', 1, 2, 0, 0, 'profile-1', NULL, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO background_jobs (
                    id, spaceId, kind, status, createdAtEpochMillis, sessionId,
                    startedAtEpochMillis, completedAtEpochMillis, errorMessage,
                    userMessageId, userText, generationToken, quoteExcerpt,
                    imageAttachmentsPayload, contextEvidencePayload
                ) VALUES (
                    'job-1', 'space-1', 'Generation', 'Queued', 3, 'session-1',
                    NULL, NULL, NULL, 'message-1', 'Explain', 'token-1', NULL, NULL, NULL
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            3,
            true,
            DatabaseSchema.migration2To3
        )

        database.query(
            "SELECT id, secretRef FROM provider_connections WHERE id = ?",
            arrayOf(MigrationIds.providerConnectionId("profile-1"))
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("secret-ref", cursor.getString(1))
        }
        database.query(
            "SELECT id, connectionId, modelId FROM model_bindings WHERE id = 'profile-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("profile-1", cursor.getString(0))
            assertEquals(MigrationIds.providerConnectionId("profile-1"), cursor.getString(1))
            assertEquals("gpt-test", cursor.getString(2))
        }
        database.query(
            "SELECT modelBindingId FROM sessions WHERE id = 'session-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("profile-1", cursor.getString(0))
        }
        database.query(
            "SELECT modelBindingId FROM background_jobs WHERE id = 'job-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        database.close()
    }

    @Test
    fun migratesLegacyGeminiAndGeminiNativeProvidersToGeminiNativeProtocol() {
        helper.createDatabase(GeminiTestDatabase, 2).apply {
            execSQL(
                "INSERT INTO spaces VALUES ('space-gemini', 'Gemini', 'Default', 1, 1, NULL)"
            )
            execSQL(
                """
                INSERT INTO llm_profiles VALUES (
                    'profile-gemini', 'space-gemini', 'Gemini legacy', 'Gemini', 'gemini-legacy',
                    NULL, 10, 20, NULL, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_profiles VALUES (
                    'profile-gemini-native', 'space-gemini', 'Gemini native', 'GeminiNative', 'gemini-native',
                    NULL, 11, 21, NULL, 1
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            GeminiTestDatabase,
            3,
            true,
            DatabaseSchema.migration2To3
        )
        database.query(
            """
            SELECT id, protocol
            FROM provider_connections
            WHERE id IN ('connection-profile-gemini', 'connection-profile-gemini-native')
            ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GeminiNative", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("GeminiNative", cursor.getString(1))
        }
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-2-3-test"
        const val GeminiTestDatabase = "reverse-tutor-migration-gemini-2-3-test"
    }
}
