package com.reversetutor.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundGenerationPolicyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReverseTutorDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationFromFiveAddsNullablePolicySnapshotToLegacyJob() {
        val databaseName = "background-policy-migration"
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                "INSERT INTO background_jobs " +
                    "(id, spaceId, kind, status, createdAtEpochMillis) VALUES " +
                    "('legacy-job', 'space-1', 'Generation', 'Queued', 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            DatabaseSchema.migration5To6
        ).apply {
            query(
                "SELECT sessionPolicyPayload FROM background_jobs WHERE id = 'legacy-job'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            close()
        }
    }
}
