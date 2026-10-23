package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Rebuilds catalog FTS indexes with Latin diacritic folding (upstream StreamVault's
 * 87→88 and 88→89, folded together; renumbered because IPTV Player already used 88 and 89 for its own tables).
 */
internal object FeatureMigrationsV89To90 {
    val MIGRATION_89_90 = object : Migration(89, 90) {
        override fun migrate(db: SupportSQLiteDatabase) {
            rebuildFtsIndex(db, "channels_fts", "channels")
            rebuildFtsIndex(db, "movies_fts", "movies")
            rebuildFtsIndex(db, "series_fts", "series")
        }

        private fun rebuildFtsIndex(db: SupportSQLiteDatabase, index: String, contentTable: String) {
            db.execSQL("DROP TABLE IF EXISTS `$index`")
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `$index` " +
                    "USING FTS4(`name` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=1`, " +
                    "content=`$contentTable`)"
            )
            db.execSQL("INSERT INTO `$index`(`$index`) VALUES('rebuild')")
        }
    }
}
