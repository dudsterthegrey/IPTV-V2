package com.streamvault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.streamvault.data.local.entity.TrackedSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedSeriesDao {
    @Query("SELECT * FROM tracked_series WHERE provider_id = :providerId ORDER BY has_new_episodes DESC, new_episodes_found_at DESC, tracked_at DESC")
    fun observeByProvider(providerId: Long): Flow<List<TrackedSeriesEntity>>

    @Query("SELECT * FROM tracked_series WHERE provider_id = :providerId AND series_id = :seriesId LIMIT 1")
    fun observe(providerId: Long, seriesId: Long): Flow<TrackedSeriesEntity?>

    @Query("SELECT * FROM tracked_series WHERE has_new_episodes = 1")
    fun observeWithNewEpisodes(): Flow<List<TrackedSeriesEntity>>

    @Query("SELECT * FROM tracked_series WHERE provider_id = :providerId AND series_id = :seriesId LIMIT 1")
    suspend fun get(providerId: Long, seriesId: Long): TrackedSeriesEntity?

    @Query("SELECT * FROM tracked_series WHERE provider_id = :providerId AND last_checked_at <= :checkedBefore")
    suspend fun getDueForCheck(providerId: Long, checkedBefore: Long): List<TrackedSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackedSeriesEntity)

    @Query("DELETE FROM tracked_series WHERE provider_id = :providerId AND series_id = :seriesId")
    suspend fun delete(providerId: Long, seriesId: Long)

    @Query("UPDATE tracked_series SET has_new_episodes = 0, new_episode_count = 0 WHERE provider_id = :providerId AND series_id = :seriesId")
    suspend fun clearNewEpisodes(providerId: Long, seriesId: Long)

    @Query("DELETE FROM tracked_series WHERE provider_id = :providerId")
    suspend fun deleteByProvider(providerId: Long)
}
