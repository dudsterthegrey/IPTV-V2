package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds when each episode was added (IPTV Player), for the "Recently added episodes" Home row. */
internal object FeatureMigrationsV91To92 {
    val MIGRATION_91_92 = object : Migration(91, 92) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE episodes ADD COLUMN added_at INTEGER NOT NULL DEFAULT 0")
        }
    }
}
