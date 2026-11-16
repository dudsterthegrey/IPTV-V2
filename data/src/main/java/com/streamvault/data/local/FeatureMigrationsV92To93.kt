package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds title metadata (IPTV Player): TMDB/IMDb identity, IMDb ratings and normalized genres. */
internal object FeatureMigrationsV92To93 {
    val MIGRATION_92_93 = object : Migration(92, 93) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `title_metadata` (" +
                    "`provider_id` INTEGER NOT NULL, `content_type` TEXT NOT NULL, `remote_id` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `year` INTEGER, `genres` TEXT NOT NULL DEFAULT '', " +
                    "`tmdb_id` INTEGER, `imdb_id` TEXT, `original_language` TEXT, " +
                    "`tmdb_genre_ids` TEXT NOT NULL DEFAULT '', `popularity` REAL NOT NULL DEFAULT 0, " +
                    "`imdb_rating` REAL, `imdb_votes` INTEGER NOT NULL DEFAULT 0, " +
                    "`tmdb_checked_at` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`provider_id`, `content_type`, `remote_id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_title_metadata_provider_id_content_type_genres` " +
                    "ON `title_metadata` (`provider_id`, `content_type`, `genres`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_title_metadata_provider_id_tmdb_id` " +
                    "ON `title_metadata` (`provider_id`, `tmdb_id`)"
            )
        }
    }
}
