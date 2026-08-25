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
class ReverseTutorDatabaseMigration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesVersionEightToNineWithEmptyCompanionAndHeartbeatTables() {
        helper.createDatabase(TestDatabase, 8).apply {
            // Seed a v8 scope signal to confirm v8 state exists before migration.
            execSQL(
                "INSERT INTO scope_signals (id, windowId, spaceId, category, count, sourceTurnId, occurredAtEpochMillis) " +
                    "VALUES ('signal-1', 'session-1', 'space-1', 'unrelated', 1, 'turn-1', 1)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            9,
            true,
            DatabaseSchema.migration8To9
        )

        val signal = database.query("SELECT category, count FROM scope_signals WHERE id = 'signal-1'")
        assertTrue(signal.moveToFirst())
        assertEquals("unrelated", signal.getString(0))
        assertEquals(1, signal.getInt(1))
        signal.close()

        for (countQuery in listOf("companion_memory_versions", "memory_observations", "window_heartbeats")) {
            val cursor = database.query("SELECT COUNT(*) FROM $countQuery")
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }

        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-8-to-9"
    }
}
