package com.reversetutor.core.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.data.local.DatabaseSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReverseTutorDatabaseMigration4To5Test {
    @Test
    fun existingErrorLogsBecomeLearningRecordsWithoutLosingTheirDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TestDatabase)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            """
                            CREATE TABLE error_logs (
                                id TEXT NOT NULL,
                                spaceId TEXT NOT NULL,
                                title TEXT NOT NULL,
                                detail TEXT NOT NULL,
                                createdAtEpochMillis INTEGER NOT NULL,
                                sourceMessageId TEXT,
                                resolved INTEGER NOT NULL,
                                PRIMARY KEY(id)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        database: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        helper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO error_logs (
                    id, spaceId, title, detail, createdAtEpochMillis, sourceMessageId, resolved
                ) VALUES ('error-1', 'space-1', '旧错误', '旧错误详情', 1, 'message-1', 0)
                """.trimIndent()
            )
            close()
        }

        val database = helper.writableDatabase
        DatabaseSchema.migration4To5.migrate(database)

        database.query("SELECT title, detail, origin, code FROM error_logs WHERE id = 'error-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧错误", cursor.getString(0))
            assertEquals("旧错误详情", cursor.getString(1))
            assertEquals("Learning", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
        database.close()
        helper.close()
    }

    @After
    fun deleteTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TestDatabase)
    }

    private companion object {
        const val TestDatabase = "reverse-tutor-migration-4-5-test"
    }
}
