package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds new-episode tracking (IPTV Player): series the user follows and their new-episode badge state. */
internal object FeatureMigrationsV87To88 {
    val MIGRATION_87_88 = object : Migration(87, 88) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tracked_series` (" +
                    "`provider_id` INTEGER NOT NULL, " +
                    "`series_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`poster_url` TEXT, " +
                    "`known_episode_count` INTEGER NOT NULL, " +
                    "`has_new_episodes` INTEGER NOT NULL, " +
                    "`new_episode_count` INTEGER NOT NULL, " +
                    "`tracked_at` INTEGER NOT NULL, " +
                    "`last_checked_at` INTEGER NOT NULL, " +
                    "`new_episodes_found_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`provider_id`, `series_id`))"
            )
        }
    }
}
