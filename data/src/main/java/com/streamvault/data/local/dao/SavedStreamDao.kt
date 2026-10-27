package com.streamvault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.streamvault.data.local.entity.SavedStreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedStreamDao {
    @Query("SELECT * FROM saved_streams ORDER BY position ASC, created_at ASC, id ASC")
    fun observeAll(): Flow<List<SavedStreamEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM saved_streams")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedStreamEntity): Long

    @Query("DELETE FROM saved_streams WHERE id = :id")
    suspend fun delete(id: Long)
}
