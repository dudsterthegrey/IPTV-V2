package com.streamvault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tracked_series",
    primaryKeys = ["provider_id", "series_id"]
)
data class TrackedSeriesEntity(
    @ColumnInfo(name = "provider_id") val providerId: Long,
    @ColumnInfo(name = "series_id") val seriesId: Long,
    val name: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "known_episode_count") val knownEpisodeCount: Int,
    @ColumnInfo(name = "has_new_episodes") val hasNewEpisodes: Boolean,
    @ColumnInfo(name = "new_episode_count") val newEpisodeCount: Int,
    @ColumnInfo(name = "tracked_at") val trackedAt: Long,
    @ColumnInfo(name = "last_checked_at") val lastCheckedAt: Long,
    @ColumnInfo(name = "new_episodes_found_at") val newEpisodesFoundAt: Long
)
