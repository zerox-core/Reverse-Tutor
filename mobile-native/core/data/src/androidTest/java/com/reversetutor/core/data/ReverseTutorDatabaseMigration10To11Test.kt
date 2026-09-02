package com.reversetutor.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.data.local.DatabaseSchema
import com.reversetutor.core.data.local.ReverseTutorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesTenToElevenWithoutLosingExistingConversationRows() {
        helper.createDatabase(TestDatabase, 10).apply {
            execSQL("INSERT INTO spaces (id, name, kind, createdAtEpochMillis, updatedAtEpochMillis) VALUES ('space-1', 'Space', 'Personal', 1, 1)")
            execSQL("INSERT INTO sessions (id, spaceId, title, createdAtEpochMillis, updatedAtEpochMillis, pinned, archived) VALUES ('session-1', 'space-1', 'Session', 1, 1, 0, 0)")
            execSQL("INSERT INTO messages (id, spaceId, sessionId, role, text, createdAtEpochMillis) VALUES ('message-1', 'space-1', 'session-1', 'User', 'existing', 1)")
            execSQL("INSERT INTO background_jobs (id, spaceId, kind, status, createdAtEpochMillis) VALUES ('job-1', 'space-1', 'Generation', 'Queued', 1)")
            close()
        }

        val database = helper.runMigrationsAndValidate(TestDatabase, 11, true, DatabaseSchema.migration10To11)
        database.query("SELECT id FROM sessions WHERE id = 'session-1'").use { assertTrue(it.moveToFirst()) }
        database.query("SELECT id FROM messages WHERE id = 'message-1'").use { assertTrue(it.moveToFirst()) }
        database.query("SELECT id FROM background_jobs WHERE id = 'job-1'").use { assertTrue(it.moveToFirst()) }

        val forbiddenColumns = setOf("rawTranscript", "providerText", "authorization", "secret", "url")
        val newTables = listOf(
            "assistant_reply_artifacts", "session_documents", "session_document_blocks",
            "session_tables", "session_table_columns", "session_table_rows", "tool_call_receipts"
        )
        newTables.forEach { table ->
            database.query("PRAGMA table_info($table)").use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                assertFalse("$table must not contain forbidden storage columns", names.any { it in forbiddenColumns })
            }
        }
        database.query("SELECT COUNT(*) FROM session_documents").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-10-to-11"
    }
}
