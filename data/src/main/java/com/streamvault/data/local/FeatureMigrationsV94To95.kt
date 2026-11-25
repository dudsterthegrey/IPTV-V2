package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Faster Live TV lists and category counts for large providers. */
internal object FeatureMigrationsV94To95 {
    val MIGRATION_94_95 = object : Migration(94, 95) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_channels_provider_id_category_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_channels_provider_id_category_id_logical_group_id`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_channels_provider_id_number` " +
                    "ON `channels` (`provider_id`, `number`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_channels_provider_id_category_id_number` " +
                    "ON `channels` (`provider_id`, `category_id`, `number`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_channels_provider_id_category_id_logical_group_id_name` " +
                    "ON `channels` (`provider_id`, `category_id`, `logical_group_id`, `name`)"
            )
        }
    }
}
