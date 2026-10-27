package com.streamvault.domain.manager

import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class OpenSubtitlesAccount(
    val username: String,
    val allowedDownloads: Int,
    val remainingDownloads: Int?,
    val level: String?,
    val vip: Boolean,
)

/** Which OpenSubtitles API key requests are sent with. */
enum class OpenSubtitlesApiKeyStatus {
    /** The key the user entered in Settings. */
    CUSTOM,
    /** The key compiled into this build. */
    BUILT_IN,
    /** No key at all: online subtitle search cannot work until one is entered. */
    MISSING,
}

/** Account operations and current sign-in state for the OpenSubtitles provider. */
interface OpenSubtitlesAccountManager {
    val account: Flow<OpenSubtitlesAccount?>

    suspend fun signIn(username: String, password: String): Result<OpenSubtitlesAccount>

    suspend fun signOut(): Result<Unit>

    val apiKeyStatus: Flow<OpenSubtitlesApiKeyStatus> get() = flowOf(OpenSubtitlesApiKeyStatus.BUILT_IN)

    /** Saves the user's own API key, or removes it (falling back to the built-in key) when [apiKey] is null or blank. */
    suspend fun setCustomApiKey(apiKey: String?): Result<Unit> =
        Result.error("Custom OpenSubtitles API keys are not supported.")
}
