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

/**
 * NEWMP-V1-003 Task 4: the 11 -> 12 migration adds the nullable
 * `checkPlanPayload` column to `assistant_reply_artifacts` without losing any
 * pre-existing session, message, source or artifact row, and old artifacts
 * keep reading with a null check plan (backward compatible).
 */
@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesElevenToTwelveWithoutLosingExistingRows() {
        helper.createDatabase(TestDatabase, 11).apply {
            execSQL("INSERT INTO spaces (id, name, kind, createdAtEpochMillis, updatedAtEpochMillis) VALUES ('space-1', 'Space', 'Personal', 1, 1)")
            execSQL("INSERT INTO sessions (id, spaceId, title, createdAtEpochMillis, updatedAtEpochMillis, pinned, archived) VALUES ('session-1', 'space-1', 'Session', 1, 1, 0, 0)")
            execSQL("INSERT INTO messages (id, spaceId, sessionId, role, text, createdAtEpochMillis) VALUES ('message-1', 'space-1', 'session-1', 'User', 'existing', 1)")
            execSQL("INSERT INTO sources (id, spaceId, title, type, parserStatus, createdAtEpochMillis) VALUES ('src-1', 'space-1', 'lecture', 'Text', 'FullyLocal', 100)")
            execSQL(
                "INSERT INTO assistant_reply_artifacts (assistantMessageId, sessionId, blocksPayload, evidenceReferencesPayload, toolResultsPayload, createdAtEpochMillis) " +
                    "VALUES ('assistant-1', 'session-1', 'p1', '', '', 1)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase, 12, true, DatabaseSchema.migration11To12
        )

        // existing data survives
        database.query("SELECT id FROM sessions WHERE id = 'session-1'").use { assertTrue(it.moveToFirst()) }
        database.query("SELECT id FROM messages WHERE id = 'message-1'").use { assertTrue(it.moveToFirst()) }
        database.query("SELECT id FROM sources WHERE id = 'src-1'").use { assertTrue(it.moveToFirst()) }

        // new nullable column exists and old artifacts read with NULL check plan
        database.query("PRAGMA table_info(assistant_reply_artifacts)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertTrue("checkPlanPayload column must exist", "checkPlanPayload" in names)
        }
        database.query(
            "SELECT checkPlanPayload FROM assistant_reply_artifacts WHERE assistantMessageId = 'assistant-1'"
        ).use {
            assertTrue(it.moveToFirst())
            assertTrue("legacy artifact must have a null check plan payload", it.isNull(0))
        }
        assertEquals(1, database.query("SELECT COUNT(*) FROM assistant_reply_artifacts").use { c -> c.let { it.moveToFirst(); it.getInt(0) } })
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-11-to-12"
    }
}
