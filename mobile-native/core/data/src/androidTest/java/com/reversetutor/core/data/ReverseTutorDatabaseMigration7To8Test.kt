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
class ReverseTutorDatabaseMigration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesVersionSevenToEightWithEmptyLedgerAndScopeTables() {
        helper.createDatabase(TestDatabase, 7).apply {
            // Seed a v7 window row to confirm v7 state exists before migration.
            execSQL(
                "INSERT INTO windows (sessionId, spaceId, rootId, parentId, kind, createdAtEpochMillis) " +
                    "VALUES ('session-1', 'space-1', 'session-1', NULL, 'TASK_ROOT', 1)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            8,
            true,
            DatabaseSchema.migration7To8
        )

        val windows = database.query("SELECT sessionId, kind FROM windows WHERE sessionId = 'session-1'")
        assertTrue(windows.moveToFirst())
        assertEquals("session-1", windows.getString(0))
        assertEquals("TASK_ROOT", windows.getString(1))
        windows.close()

        val facts = database.query("SELECT COUNT(*) FROM learning_fact_receipts")
        assertTrue(facts.moveToFirst())
        assertEquals(0, facts.getInt(0))
        facts.close()

        val signals = database.query("SELECT COUNT(*) FROM scope_signals")
        assertTrue(signals.moveToFirst())
        assertEquals(0, signals.getInt(0))
        signals.close()

        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-7-to-8"
    }
}
