package com.streamvault.domain.repository

import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.TrackedSeries
import kotlinx.coroutines.flow.Flow

/** New-episode tracking: series the user follows, and whether each one has unseen new episodes. */
interface TrackedSeriesRepository {
    fun observeTrackedSeries(providerId: Long): Flow<List<TrackedSeries>>
    fun observeTrackedSeries(providerId: Long, seriesId: Long): Flow<TrackedSeries?>

    /** Ids of series with new episodes, across every provider, as (providerId, seriesId) pairs. */
    fun observeSeriesWithNewEpisodes(): Flow<Set<Pair<Long, Long>>>

    /** Starts tracking [series]; its current episode count becomes the baseline. */
    suspend fun track(series: Series): Result<Unit>
    suspend fun untrack(providerId: Long, seriesId: Long): Result<Unit>
    suspend fun clearNewEpisodes(providerId: Long, seriesId: Long): Result<Unit>

    /**
     * Re-counts episodes for tracked series of [providerId] that were not checked within
     * [minIntervalMs] and flags any that gained episodes. Returns how many series were flagged.
     */
    suspend fun checkForNewEpisodes(providerId: Long, minIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS): Result<Int>

    companion object {
        const val DEFAULT_CHECK_INTERVAL_MS: Long = 30 * 60 * 1000L
    }
}
