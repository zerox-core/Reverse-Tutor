package com.reversetutor.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.data.local.DatabaseSchema
import com.reversetutor.core.data.local.ReverseTutorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesBackgroundGenerationJobColumnsFromVersionOneToTwo() {
        helper.createDatabase(TestDatabase, 1).apply {
            execSQL(
                """
                INSERT INTO background_jobs (
                    id,
                    spaceId,
                    kind,
                    status,
                    createdAtEpochMillis,
                    sessionId,
                    completedAtEpochMillis,
                    errorMessage
                ) VALUES (
                    'job-1',
                    'space-1',
                    'Generation',
                    'Queued',
                    10,
                    'session-1',
                    NULL,
                    NULL
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            2,
            true,
            DatabaseSchema.migration1To2
        )
        val cursor = database.query(
            """
            SELECT
                id,
                startedAtEpochMillis,
                userMessageId,
                userText,
                generationToken,
                quoteExcerpt,
                imageAttachmentsPayload,
                contextEvidencePayload
            FROM background_jobs
            WHERE id = 'job-1'
            """.trimIndent()
        )

        assertTrue(cursor.moveToFirst())
        assertEquals("job-1", cursor.getString(0))
        for (index in 1..7) {
            assertTrue(cursor.isNull(index))
        }
        cursor.close()
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-test"
    }
}
