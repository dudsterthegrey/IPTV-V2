package com.streamvault.data.metadata

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.domain.sync.AutomaticSyncActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Keeps genres, TMDB matches and IMDb ratings up to date for every provider. A run looks up a
 * limited number of titles and queues the next run, so the first full pass over a large catalog
 * happens in the background over a while. It steps aside while anything is playing.
 */
class TitleMetadataWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TitleMetadataWorkerEntryPoint {
        fun titleMetadataRefresher(): TitleMetadataRefresher
        fun providerDao(): ProviderDao
    }

    override suspend fun doWork(): Result {
        if (AutomaticSyncActivity.hasPlayback) {
            enqueueContinuation(applicationContext)
            return Result.success()
        }
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, TitleMetadataWorkerEntryPoint::class.java)
        val refresher = entryPoint.titleMetadataRefresher()
        return try {
            val providerIds = entryPoint.providerDao().getAllSync().map { it.id }
            var pending = 0
            providerIds.forEach { providerId ->
                refresher.refreshLocalGenres(providerId)
                pending += refresher.lookUpOnTmdb(
                    providerId = providerId,
                    maxTitles = TITLES_PER_RUN,
                    shouldPause = { AutomaticSyncActivity.hasPlayback || isStopped }
                )
                // Countries are only for the Home filters, and filling them in for a whole library
                // slows search on a TV box; do it while the app is off screen. Leaving the app
                // schedules the next run (see backgroundFill), so this is not counted as pending.
                if (!AutomaticSyncActivity.appOnScreen) {
                    refresher.lookUpCountries(
                        providerId = providerId,
                        maxTitles = TITLES_PER_RUN,
                        shouldPause = { AutomaticSyncActivity.hasPlayback || AutomaticSyncActivity.appOnScreen || isStopped }
                    ).let { left -> if (left > 0 && !AutomaticSyncActivity.appOnScreen) pending += left }
                }
            }
            refresher.refreshImdbRatings(providerIds)
            if (pending > 0) enqueueContinuation(applicationContext)
            Result.success()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "Title metadata refresh failed: ${error.javaClass.simpleName}", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TitleMetadataWorker"
        private const val PERIODIC_NAME = "title_metadata_periodic"
        private const val ONE_SHOT_NAME = "title_metadata_continue"
        private const val TITLES_PER_RUN = 3_000

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Twice a day, plus a run soon after launch. */
        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TitleMetadataWorker>(12, TimeUnit.HOURS)
                    .setConstraints(networkConstraints)
                    .build()
            )
            workManager.enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TitleMetadataWorker>()
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setConstraints(networkConstraints)
                    .build()
            )
        }

        /** Called when the app leaves the screen: continue filling in metadata soon. */
        fun backgroundFill(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<TitleMetadataWorker>()
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setConstraints(networkConstraints)
                    .build()
            )
        }

        private fun enqueueContinuation(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<TitleMetadataWorker>()
                    .setInitialDelay(2, TimeUnit.MINUTES)
                    .setConstraints(networkConstraints)
                    .build()
            )
        }
    }
}
