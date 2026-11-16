package com.streamvault.data.cloud

import com.streamvault.domain.repository.CloudSyncRepository
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.sync.AutomaticSyncActivity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs cross-device sync for the life of the app process: once at start, shortly after watch
 * progress changes (so another device sees it), and every few minutes to pick up other devices'
 * progress. Does nothing while no sync code is set.
 */
@Singleton
class CloudSyncRunner @Inject constructor(
    private val cloudSync: CloudSyncRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository
) {
    @OptIn(FlowPreview::class)
    suspend fun run() = coroutineScope {
        launch {
            while (true) {
                cloudSync.syncNow()
                delay(PERIODIC_SYNC_MS)
            }
        }
        launch {
            playbackHistoryRepository.getRecentlyWatched(limit = 20)
                .map { list -> list.maxOfOrNull { it.lastWatchedAt } ?: 0L }
                .distinctUntilChanged()
                .drop(1)
                .debounce(AFTER_CHANGE_DELAY_MS)
                // Progress saves every few seconds while watching; send it when playback pauses or ends.
                .filter { !AutomaticSyncActivity.hasPlayback }
                .collect { cloudSync.syncNow() }
        }
        launch {
            AutomaticSyncActivity.playbackActive
                .drop(1)
                .filter { active -> !active }
                .collect {
                    delay(STOPPED_PLAYBACK_DELAY_MS)
                    cloudSync.syncNow()
                }
        }
    }

    private companion object {
        const val PERIODIC_SYNC_MS = 5L * 60 * 1000
        const val AFTER_CHANGE_DELAY_MS = 20_000L
        const val STOPPED_PLAYBACK_DELAY_MS = 5_000L
    }
}
