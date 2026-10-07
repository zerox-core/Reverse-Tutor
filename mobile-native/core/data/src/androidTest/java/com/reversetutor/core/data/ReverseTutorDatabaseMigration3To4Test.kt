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
class ReverseTutorDatabaseMigration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun createsWorldTreeTablesWithoutChangingExistingLocalData() {
        helper.createDatabase(TestDatabase, 3).apply {
            execSQL("INSERT INTO spaces VALUES ('space-1', 'Default', 'Default', 1, 1, NULL)")
            execSQL(
                """
                INSERT INTO sessions (
                    id, spaceId, title, createdAtEpochMillis, updatedAtEpochMillis,
                    pinned, archived, llmProfileId, settingsId, sourceImportId, modelBindingId
                ) VALUES ('session-1', 'space-1', 'Math', 1, 2, 0, 0, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, spaceId, sessionId, role, text, createdAtEpochMillis,
                    parentMessageId, sourceImportId
                ) VALUES ('message-1', 'space-1', 'session-1', 'User', 'Question', 3, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO sources (
                    id, spaceId, title, type, parserStatus, createdAtEpochMillis,
                    uri, extractedText, importBatchId
                ) VALUES ('source-1', 'space-1', 'Notes', 'Text', 'FullyLocal', 4, NULL, 'Body', NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO graph_nodes (
                    id, spaceId, label, kind, createdAtEpochMillis, status, sourceMemoryId
                ) VALUES ('node-1', 'space-1', 'Functions', 'Concept', 5, 'Active', NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO graph_edges (
                    id, spaceId, fromNodeId, toNodeId, relation, createdAtEpochMillis, sourceMemoryId
                ) VALUES ('edge-1', 'space-1', 'node-1', 'node-1', 'related', 6, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            4,
            true,
            DatabaseSchema.migration3To4
        )
        database.query("SELECT title FROM sessions WHERE id = 'session-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Math", cursor.getString(0))
        }
        database.query("SELECT role, text FROM messages WHERE id = 'message-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("User", cursor.getString(0))
            assertEquals("Question", cursor.getString(1))
        }
        database.query("SELECT title, extractedText FROM sources WHERE id = 'source-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Notes", cursor.getString(0))
            assertEquals("Body", cursor.getString(1))
        }
        database.query("SELECT label, status FROM graph_nodes WHERE id = 'node-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Functions", cursor.getString(0))
            assertEquals("Active", cursor.getString(1))
        }
        database.query("SELECT relation FROM graph_edges WHERE id = 'edge-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("related", cursor.getString(0))
        }
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'world_tree_%' ORDER BY name"
        ).use { cursor ->
            val names = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(
                listOf("world_tree_drafts", "world_tree_sections", "world_tree_source_cross_ref"),
                names
            )
        }
        assertEquals(
            listOf(
                "id", "draftId", "type", "title", "orderIndex", "payloadJson",
                "required", "completed", "updatedAtEpochMillis"
            ),
            tableColumns(database, "world_tree_sections")
        )
        assertEquals(
            listOf("draftId", "sourceId", "orderIndex"),
            tableColumns(database, "world_tree_source_cross_ref")
        )
        database.close()
    }

    private fun tableColumns(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String
    ): List<String> = database.query("PRAGMA table_info(`$table`)").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(1))
        }
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-3-4-test"
    }
}
