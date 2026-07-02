package com.reversetutor.core.data.local

import androidx.room.migration.Migration

object DatabaseSchema {
    const val version = 1
    const val exportSchema = true

    val migrations: Array<Migration> = emptyArray()
}
