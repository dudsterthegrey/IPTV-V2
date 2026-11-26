package com.streamvault.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import com.streamvault.data.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background jobs are stopped by Android after about ten minutes. A large library or guide on a
 * TV stick can take longer, and a stopped import throws away its staged rows, so the same work
 * restarted and never finished. Jobs still running after [PROMOTION_DELAY_MILLIS] become a
 * foreground service with a quiet notification; quick checks finish before that and show nothing.
 */
internal object SyncForegroundPromotion {
    const val PROMOTION_DELAY_MILLIS = 60_000L
    private const val CHANNEL_ID = "library_sync"
    private const val TAG = "SyncForeground"

    enum class Kind(val notificationId: Int, @StringRes val titleRes: Int) {
        LIBRARY(0x51_4C_01, R.string.sync_notification_library),
        GUIDE(0x51_4C_02, R.string.sync_notification_guide),
    }

    suspend fun <T> runPromotingWhenLong(
        worker: CoroutineWorker,
        kind: Kind,
        delayMillis: Long = PROMOTION_DELAY_MILLIS,
        block: suspend () -> T,
    ): T = runPromotingAfter(
        delayMillis = delayMillis,
        promote = { worker.setForeground(foregroundInfo(worker.applicationContext, kind)) },
        onPromotionFailed = { error ->
            // Android 12+ refuses foreground services started while the app is in the background.
            // The job keeps running as before; it is only exposed to the usual time limit.
            Log.w(TAG, "Could not move ${kind.name.lowercase()} sync to the foreground: ${error.javaClass.simpleName}")
        },
        block = block,
    )

    internal suspend fun <T> runPromotingAfter(
        delayMillis: Long,
        promote: suspend () -> Unit,
        onPromotionFailed: (Exception) -> Unit,
        block: suspend () -> T,
    ): T = coroutineScope {
        val promoter = launch {
            delay(delayMillis)
            try {
                promote()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onPromotionFailed(error)
            }
        }
        try {
            block()
        } finally {
            promoter.cancel()
        }
    }

    private fun foregroundInfo(context: Context, kind: Kind): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(kind.titleRes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(kind.notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(kind.notificationId, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }
}
