package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Persists M3U catch-up mode and the channel-specific archive time correction (upstream
 * StreamVault's 89→90, renumbered after IPTV Player's own 88 and 89).
 */
internal object FeatureMigrationsV90To91 {
    val MIGRATION_90_91 = object : Migration(90, 91) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channels ADD COLUMN catch_up_type TEXT")
            db.execSQL(
                "ALTER TABLE channels ADD COLUMN catch_up_correction_seconds INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE channel_import_stage ADD COLUMN catch_up_type TEXT")
            db.execSQL(
                "ALTER TABLE channel_import_stage ADD COLUMN catch_up_correction_seconds INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
}
