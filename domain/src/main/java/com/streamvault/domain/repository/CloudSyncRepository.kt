package com.streamvault.domain.repository

import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

/** Where cross-device sync stands. [signedInEmail] is null while signed out. */
data class CloudSyncState(
    val isConfigured: Boolean = false,
    val signedInEmail: String? = null,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
    val isSyncing: Boolean = false
)

/** Why a sign-in or sync failed; returned as the [Result.Error] message. */
object CloudSyncErrors {
    const val WRONG_EMAIL_OR_PASSWORD = "WRONG_EMAIL_OR_PASSWORD"
    const val EMAIL_IN_USE = "EMAIL_IN_USE"
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val INVALID_EMAIL = "INVALID_EMAIL"
    const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
    const val NOT_CONFIGURED = "NOT_CONFIGURED"
    const val NETWORK = "NETWORK"
}

/**
 * Keeps watch progress (movies and episodes) the same on every device signed in to the same
 * account, so something started on one TV can be finished on another.
 */
interface CloudSyncRepository {
    val state: Flow<CloudSyncState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun createAccount(email: String, password: String): Result<Unit>

    /** Emails a link to choose a new password. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Stops syncing on this device; the account and other devices keep their progress. */
    suspend fun signOut()

    /** Sends this device's new progress, then fetches the other devices'. */
    suspend fun syncNow(): Result<Unit>
}
