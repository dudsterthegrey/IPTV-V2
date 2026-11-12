package com.streamvault.domain.repository

import com.streamvault.domain.model.EpisodeShelfItem
import kotlinx.coroutines.flow.Flow

/** Episode rows for Home: what to watch next, and what was just added to tracked shows. */
interface EpisodeShelfRepository {
    /**
     * For each show whose most recently watched episode was finished, the episode after it.
     * Most recently watched shows first. Shows with nothing left to watch are left out.
     */
    fun observeUpNext(providerId: Long, limit: Int): Flow<List<EpisodeShelfItem>>

    /** Episodes of tracked shows, newest-added first; episodes with no known date are left out. */
    fun observeRecentlyAddedForTrackedSeries(providerId: Long, limit: Int): Flow<List<EpisodeShelfItem>>
}
