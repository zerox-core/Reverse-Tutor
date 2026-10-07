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
class ReverseTutorDatabaseMigration9To10Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesVersionNineToTenWithNullEnvelopeColumn() {
        helper.createDatabase(TestDatabase, 9).apply {
            execSQL(
                "INSERT INTO background_jobs (id, spaceId, kind, status, createdAtEpochMillis, sessionId, userMessageId, userText, generationToken) " +
                    "VALUES ('job-1', 'space-1', 'Generation', 'Queued', 1, 'session-1', 'user-1', 'hi', 'tok-1')"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            10,
            true,
            DatabaseSchema.migration9To10
        )
        val cursor = database.query(
            "SELECT id, assistantTurnEnvelopePayload FROM background_jobs WHERE id = 'job-1'"
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("job-1", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        cursor.close()
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-9-to-10"
    }
}
