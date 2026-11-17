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
    const val PAIRING_EXPIRED = "PAIRING_EXPIRED"
    const val GOOGLE_NOT_ENABLED = "GOOGLE_NOT_ENABLED"
}

/** A "sign in with Google on your phone" request: the phone opens [url] (shown as a QR code). */
data class GooglePhonePairing(val code: String, val url: String)

/**
 * Keeps watch progress (movies and episodes) the same on every device signed in to the same
 * account, so something started on one TV can be finished on another.
 */
interface CloudSyncRepository {
    val state: Flow<CloudSyncState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun createAccount(email: String, password: String): Result<Unit>

    /** Starts a Google sign-in on a phone; null when this build has no cloud project. */
    fun newGooglePhonePairing(): GooglePhonePairing?

    /**
     * Waits until the phone finishes signing in with Google for [pairing], then signs this device
     * in to the same account. Fails with [CloudSyncErrors.PAIRING_EXPIRED] after [timeoutMs].
     */
    suspend fun completeGooglePhonePairing(pairing: GooglePhonePairing, timeoutMs: Long = 5 * 60_000L): Result<Unit>

    /** Emails a link to choose a new password. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Stops syncing on this device; the account and other devices keep their progress. */
    suspend fun signOut()

    /** Sends this device's new progress, then fetches the other devices'. */
    suspend fun syncNow(): Result<Unit>

    /** Like [syncNow], but fetches everything again; used after switching profile. */
    suspend fun syncEverything(): Result<Unit> = syncNow()
}
