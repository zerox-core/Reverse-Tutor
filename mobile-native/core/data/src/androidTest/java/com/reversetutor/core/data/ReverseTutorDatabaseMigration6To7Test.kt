package com.reversetutor.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.data.local.DatabaseSchema
import com.reversetutor.core.data.local.ReverseTutorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesExistingSessionsToTaskRootWindows() {
        helper.createDatabase(TestDatabase, 6).apply {
            execSQL(
                "INSERT INTO sessions (id, spaceId, title, createdAtEpochMillis, updatedAtEpochMillis, pinned, archived) " +
                    "VALUES ('session-1', 'space-1', 'S1', 1, 2, 0, 0)"
            )
            execSQL(
                "INSERT INTO sessions (id, spaceId, title, createdAtEpochMillis, updatedAtEpochMillis, pinned, archived) " +
                    "VALUES ('session-2', 'space-1', 'S2', 3, 4, 0, 0)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TestDatabase,
            7,
            true,
            DatabaseSchema.migration6To7
        )
        val cursor = database.query(
            "SELECT sessionId, rootId, parentId, kind FROM windows ORDER BY sessionId"
        )
        assertEquals(2, cursor.count)
        cursor.moveToFirst()
        assertEquals("session-1", cursor.getString(0))
        assertEquals("session-1", cursor.getString(1))
        assertNull(cursor.getString(2))
        assertEquals("TASK_ROOT", cursor.getString(3))
        cursor.moveToNext()
        assertEquals("session-2", cursor.getString(0))
        assertEquals("session-2", cursor.getString(1))
        assertNull(cursor.getString(2))
        assertEquals("TASK_ROOT", cursor.getString(3))
        cursor.close()
        database.close()
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-6-to-7"
    }
}
