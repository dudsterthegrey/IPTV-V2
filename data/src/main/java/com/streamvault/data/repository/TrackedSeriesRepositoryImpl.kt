package com.streamvault.data.repository

import com.streamvault.data.local.dao.TrackedSeriesDao
import com.streamvault.data.local.entity.TrackedSeriesEntity
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.TrackedSeries
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.repository.TrackedSeriesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackedSeriesRepositoryImpl @Inject constructor(
    private val dao: TrackedSeriesDao,
    private val seriesRepository: SeriesRepository
) : TrackedSeriesRepository {
    private val checkMutex = Mutex()

    override fun observeTrackedSeries(providerId: Long): Flow<List<TrackedSeries>> =
        dao.observeByProvider(providerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeTrackedSeries(providerId: Long, seriesId: Long): Flow<TrackedSeries?> =
        dao.observe(providerId, seriesId).map { it?.toDomain() }

    override fun observeSeriesWithNewEpisodes(): Flow<Set<Pair<Long, Long>>> =
        dao.observeWithNewEpisodes()
            .map { rows -> rows.mapTo(mutableSetOf()) { it.providerId to it.seriesId } }
            .distinctUntilChanged()

    override suspend fun track(series: Series): Result<Unit> = try {
        val now = System.currentTimeMillis()
        val existing = dao.get(series.providerId, series.id)
        dao.upsert(
            TrackedSeriesEntity(
                providerId = series.providerId,
                seriesId = series.id,
                name = series.name,
                posterUrl = series.posterUrl,
                knownEpisodeCount = existing?.knownEpisodeCount?.takeIf { it > 0 } ?: series.episodeTotal(),
                hasNewEpisodes = existing?.hasNewEpisodes ?: false,
                newEpisodeCount = existing?.newEpisodeCount ?: 0,
                trackedAt = existing?.trackedAt ?: now,
                lastCheckedAt = now,
                newEpisodesFoundAt = existing?.newEpisodesFoundAt ?: 0L
            )
        )
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to track series", e)
    }

    override suspend fun untrack(providerId: Long, seriesId: Long): Result<Unit> = try {
        dao.delete(providerId, seriesId)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to stop tracking series", e)
    }

    override suspend fun clearNewEpisodes(providerId: Long, seriesId: Long): Result<Unit> = try {
        dao.clearNewEpisodes(providerId, seriesId)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to clear new-episode badge", e)
    }

    override suspend fun checkForNewEpisodes(providerId: Long, minIntervalMs: Long): Result<Int> = try {
        // One check at a time: Home and the details page can both ask on the same screen visit.
        val flagged = checkMutex.withLock {
            val now = System.currentTimeMillis()
            var count = 0
            dao.getDueForCheck(providerId, checkedBefore = now - minIntervalMs).forEach { tracked ->
                val details = seriesRepository.getSeriesDetails(providerId, tracked.seriesId)
                if (details !is Result.Success) {
                    return@forEach
                }
                val episodeCount = details.data.episodeTotal()
                // Re-fetch: the row may have been cleared or untracked while details were loading.
                val current = dao.get(providerId, tracked.seriesId) ?: return@forEach
                val gained = episodeCount - current.knownEpisodeCount
                val updated = if (gained > 0 && current.knownEpisodeCount > 0) {
                    count++
                    current.copy(
                        knownEpisodeCount = episodeCount,
                        hasNewEpisodes = true,
                        newEpisodeCount = current.newEpisodeCount + gained,
                        lastCheckedAt = now,
                        newEpisodesFoundAt = now
                    )
                } else {
                    // A zero baseline means the show's episodes weren't loaded when it was
                    // tracked; adopt the real count instead of flagging every episode as new.
                    current.copy(
                        knownEpisodeCount = maxOf(episodeCount, current.knownEpisodeCount),
                        lastCheckedAt = now
                    )
                }
                dao.upsert(updated)
            }
            count
        }
        Result.success(flagged)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to check for new episodes", e)
    }
}

private fun Series.episodeTotal(): Int =
    seasons.sumOf { season -> maxOf(season.episodes.size, season.episodeCount) }

private fun TrackedSeriesEntity.toDomain(): TrackedSeries = TrackedSeries(
    providerId = providerId,
    seriesId = seriesId,
    name = name,
    posterUrl = posterUrl,
    knownEpisodeCount = knownEpisodeCount,
    hasNewEpisodes = hasNewEpisodes,
    newEpisodeCount = newEpisodeCount,
    trackedAt = trackedAt,
    lastCheckedAt = lastCheckedAt,
    newEpisodesFoundAt = newEpisodesFoundAt
)
