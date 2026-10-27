package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Saved Streams (IPTV Player): direct stream URLs and Stream API endpoints outside providers. */
internal object FeatureMigrationsV88To89 {
    val MIGRATION_88_89 = object : Migration(88, 89) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_streams` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL)"
            )
        }
    }
}
