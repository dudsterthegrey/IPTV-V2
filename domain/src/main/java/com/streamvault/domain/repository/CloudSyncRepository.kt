package com.streamvault.domain.repository

import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

/** Where cross-device sync stands. [syncCode] is shown so the user can type it on another device. */
data class CloudSyncState(
    val isConfigured: Boolean = false,
    val syncCode: String? = null,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
    val isSyncing: Boolean = false
)

/**
 * Keeps watch progress (movies and episodes) the same on every device that shares a sync code,
 * so something started on one TV can be finished on another.
 */
interface CloudSyncRepository {
    val state: Flow<CloudSyncState>

    /** Starts a new sync group on this device and returns its code. */
    suspend fun createSyncCode(): Result<String>

    /** Joins the group of a code made on another device. */
    suspend fun joinSyncCode(code: String): Result<Unit>

    /** Stops syncing on this device; other devices keep their copy. */
    suspend fun leave()

    /** Sends this device's new progress, then fetches the other devices'. */
    suspend fun syncNow(): Result<Unit>
}
