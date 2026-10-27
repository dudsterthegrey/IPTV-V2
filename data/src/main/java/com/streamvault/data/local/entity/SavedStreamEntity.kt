package com.streamvault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_streams")
data class SavedStreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** [com.streamvault.domain.model.SavedStreamKind] name. */
    val kind: String,
    val url: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val position: Int
)
