package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds TMDB's countries to title metadata, for the Home shelf country filter. */
internal object FeatureMigrationsV93To94 {
    val MIGRATION_93_94 = object : Migration(93, 94) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // "|US|GB|"; null until TMDB has been asked, "" when it lists none.
            db.execSQL("ALTER TABLE `title_metadata` ADD COLUMN `countries` TEXT")
        }
    }
}
