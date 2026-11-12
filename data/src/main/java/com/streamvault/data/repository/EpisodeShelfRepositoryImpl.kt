package com.streamvault.data.repository

import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.data.local.dao.EpisodeWithSeriesRow
import com.streamvault.data.mapper.toDomain
import com.streamvault.domain.model.EpisodeShelfItem
import com.streamvault.domain.repository.EpisodeShelfRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeShelfRepositoryImpl @Inject constructor(
    private val episodeDao: EpisodeDao
) : EpisodeShelfRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUpNext(providerId: Long, limit: Int): Flow<List<EpisodeShelfItem>> =
        episodeDao.observeLastFinishedEpisodes(providerId, limit)
            .distinctUntilChanged()
            .mapLatest { finished ->
                // A show only appears once its episode list is stored (it is, after it was played).
                finished.mapNotNull { last ->
                    episodeDao.getNextEpisode(last.seriesId, last.seasonNumber, last.episodeNumber)
                        ?.toShelfItem()
                }
            }

    override fun observeRecentlyAddedForTrackedSeries(providerId: Long, limit: Int): Flow<List<EpisodeShelfItem>> =
        episodeDao.observeRecentlyAddedForTrackedSeries(providerId, limit)
            .map { rows -> rows.map { it.toShelfItem() } }
            .distinctUntilChanged()
}

private fun EpisodeWithSeriesRow.toShelfItem(): EpisodeShelfItem = EpisodeShelfItem(
    episode = episode.toDomain(),
    seriesName = seriesName,
    seriesPosterUrl = seriesPosterUrl
)
