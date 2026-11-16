package com.streamvault.data.cloud

import android.content.Context
import android.util.Log
import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.data.local.dao.PlaybackHistoryDao
import com.streamvault.data.local.dao.SeriesDao
import com.streamvault.data.local.entity.PlaybackHistoryEntity
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.PlaybackHistory
import com.streamvault.domain.model.PlaybackWatchedStatus
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.CloudSyncErrors
import com.streamvault.domain.repository.CloudSyncRepository
import com.streamvault.domain.repository.CloudSyncState
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-device watch progress over Firestore, stored under the signed-in account. Items are matched by the provider's own ids (movie stream id; series
 * id + episode id) under a hash of the provider's server and username, because local database ids
 * differ per device. The newest watch wins; downloaded progress keeps its original watch time so
 * devices never keep re-sending the same item.
 */
@Singleton
class CloudSyncRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebase: FirebaseRestClient,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val providerRepository: ProviderRepository,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val seriesRepository: SeriesRepository
) : CloudSyncRepository {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val syncMutex = Mutex()
    private val mutableState by lazy {
        MutableStateFlow(
            CloudSyncState(
                isConfigured = firebase.isConfigured,
                signedInEmail = firebase.signedInEmail,
                lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
            )
        )
    }

    override val state: Flow<CloudSyncState> get() = mutableState.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        authenticate { firebase.signIn(email, password) }

    override suspend fun createAccount(email: String, password: String): Result<Unit> =
        authenticate { firebase.createAccount(email, password) }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = accountCall { firebase.sendPasswordReset(email) }

    override suspend fun signOut() {
        firebase.signOut()
        prefs.edit().clear().apply()
        mutableState.value = CloudSyncState(isConfigured = firebase.isConfigured)
    }

    private suspend fun authenticate(block: suspend () -> Unit): Result<Unit> {
        val result = accountCall(block)
        if (result is Result.Error) return result
        // A new sign-in fetches everything the account has, not only what changed recently.
        prefs.edit().putLong(KEY_LAST_PULLED_AT, 0L).putLong(KEY_LAST_PUSHED_AT, 0L).apply()
        mutableState.value = mutableState.value.copy(signedInEmail = firebase.signedInEmail, lastError = null)
        return syncNow()
    }

    private suspend fun accountCall(block: suspend () -> Unit): Result<Unit> {
        if (!firebase.isConfigured) return Result.error(CloudSyncErrors.NOT_CONFIGURED)
        return try {
            block()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseAuthException) {
            Result.error(authErrorCode(error.code), error)
        } catch (error: Exception) {
            Result.error(CloudSyncErrors.NETWORK, error)
        }
    }

    private fun authErrorCode(firebaseCode: String): String = when (firebaseCode) {
        "EMAIL_EXISTS" -> CloudSyncErrors.EMAIL_IN_USE
        "WEAK_PASSWORD" -> CloudSyncErrors.WEAK_PASSWORD
        "INVALID_EMAIL", "MISSING_EMAIL" -> CloudSyncErrors.INVALID_EMAIL
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> CloudSyncErrors.TOO_MANY_ATTEMPTS
        else -> CloudSyncErrors.WRONG_EMAIL_OR_PASSWORD
    }

    override suspend fun syncNow(): Result<Unit> = syncMutex.withLock {
        if (!firebase.isConfigured || firebase.signedInEmail == null) return@withLock Result.success(Unit)
        mutableState.value = mutableState.value.copy(isSyncing = true)
        try {
            playbackHistoryRepository.flushPendingProgress()
            push()
            pull()
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, now).apply()
            mutableState.value = mutableState.value.copy(isSyncing = false, lastSyncedAt = now, lastError = null)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(isSyncing = false)
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Cloud sync failed: ${error.javaClass.simpleName}: ${error.message}")
            mutableState.value = mutableState.value.copy(isSyncing = false, lastError = error.message ?: error.javaClass.simpleName)
            Result.error(CloudSyncErrors.NETWORK, error)
        }
    }

    private suspend fun push() {
        val lastPushed = prefs.getLong(KEY_LAST_PUSHED_AT, 0L)
        val changed = playbackHistoryRepository.getRecentlyWatched(PUSH_SCAN_LIMIT).first()
            .filter { it.lastWatchedAt > lastPushed }
            .filter { it.contentType == ContentType.MOVIE || it.contentType == ContentType.SERIES_EPISODE }
        var newestPushed = lastPushed
        val providerKeys = HashMap<Long, String?>()
        changed.sortedBy { it.lastWatchedAt }.forEach { history ->
            val providerKey = providerKeys.getOrPut(history.providerId) { providerKey(history.providerId) } ?: return@forEach
            val identity = remoteIdentity(history) ?: return@forEach
            val fields = JSONObject()
                .put("providerKey", string(providerKey))
                .put("contentType", string(history.contentType.name))
                .put("remoteContentId", string(identity.first))
                .put("remoteSeriesId", string(identity.second.orEmpty()))
                .put("title", string(history.title))
                .put("posterUrl", string(history.posterUrl.orEmpty()))
                .put("seasonNumber", integer((history.seasonNumber ?: 0).toLong()))
                .put("episodeNumber", integer((history.episodeNumber ?: 0).toLong()))
                .put("positionMs", integer(history.resumePositionMs))
                .put("durationMs", integer(history.totalDurationMs))
                .put("watchedStatus", string(history.watchedStatus.name))
                .put("lastWatchedAt", integer(history.lastWatchedAt))
                .put("updatedAt", integer(System.currentTimeMillis()))
            val documentId = sha256("$providerKey|${history.contentType.name}|${identity.second.orEmpty()}|${identity.first}").take(40)
            firebase.putHistoryDocument(documentId, fields)
            newestPushed = maxOf(newestPushed, history.lastWatchedAt)
        }
        prefs.edit().putLong(KEY_LAST_PUSHED_AT, newestPushed).apply()
    }

    private suspend fun pull() {
        val lastPulled = prefs.getLong(KEY_LAST_PULLED_AT, 0L)
        // Device clocks differ, so look back a little and rely on lastWatchedAt to skip repeats.
        val since = (lastPulled - PULL_OVERLAP_MS).coerceAtLeast(0L)
        val documents = firebase.historyUpdatedSince(since)
        val localProviders = providerRepository.getProviders().first().associateBy { providerKeyOf(it.serverUrl, it.username) }
        var newestSeen = lastPulled
        documents.forEach { fields ->
            newestSeen = maxOf(newestSeen, fields.long("updatedAt"))
            val provider = localProviders[fields.str("providerKey")] ?: return@forEach
            applyRemote(provider.id, fields)
        }
        prefs.edit().putLong(KEY_LAST_PULLED_AT, newestSeen).apply()
    }

    private suspend fun applyRemote(providerId: Long, fields: JSONObject) {
        val type = runCatching { ContentType.valueOf(fields.str("contentType")) }.getOrNull() ?: return
        val remoteContentId = fields.str("remoteContentId").toLongOrNull() ?: return
        val lastWatchedAt = fields.long("lastWatchedAt")
        val local: Triple<Long, Long?, String> = when (type) {
            ContentType.MOVIE -> movieDao.getByStreamId(providerId, remoteContentId)?.let { Triple(it.id, null, it.streamUrl) }
            ContentType.SERIES_EPISODE -> {
                val remoteSeriesId = fields.str("remoteSeriesId").toLongOrNull() ?: return
                val series = seriesDao.getBySeriesId(providerId, remoteSeriesId) ?: return
                var episode = episodeDao.getByProviderSeriesAndEpisodeId(providerId, series.id, remoteContentId)
                if (episode == null) {
                    // The episode list loads on first open; fetch it so the episode can be matched.
                    seriesRepository.getSeriesDetails(providerId, series.id)
                    episode = episodeDao.getByProviderSeriesAndEpisodeId(providerId, series.id, remoteContentId)
                }
                episode?.let { Triple(it.id, series.id, it.streamUrl) }
            }
            else -> null
        } ?: return
        val existing = playbackHistoryDao.get(local.first, type.name, providerId)
        if (existing != null && existing.lastWatchedAt >= lastWatchedAt) return
        playbackHistoryDao.insertOrUpdate(
            PlaybackHistoryEntity(
                id = existing?.id ?: 0L,
                contentId = local.first,
                contentType = type,
                providerId = providerId,
                title = fields.str("title").ifBlank { existing?.title.orEmpty() },
                posterUrl = fields.str("posterUrl").ifBlank { null } ?: existing?.posterUrl,
                streamUrl = existing?.streamUrl ?: local.third,
                resumePositionMs = fields.long("positionMs"),
                totalDurationMs = fields.long("durationMs"),
                lastWatchedAt = lastWatchedAt,
                watchCount = existing?.watchCount ?: 1,
                watchedStatus = fields.str("watchedStatus").ifBlank { PlaybackWatchedStatus.IN_PROGRESS.name },
                seriesId = local.second,
                seasonNumber = fields.long("seasonNumber").toInt().takeIf { it > 0 },
                episodeNumber = fields.long("episodeNumber").toInt().takeIf { it > 0 }
            )
        )
        when (type) {
            ContentType.MOVIE -> movieDao.syncWatchProgressFromHistory(local.first, providerId)
            ContentType.SERIES_EPISODE -> episodeDao.syncWatchProgressFromHistory(local.first, providerId)
            else -> Unit
        }
    }

    /** The provider's id for this item: (content id, series id or null). */
    private suspend fun remoteIdentity(history: PlaybackHistory): Pair<String, String?>? = when (history.contentType) {
        ContentType.MOVIE -> movieDao.getById(history.contentId)
            ?.takeIf { it.providerId == history.providerId && it.streamId > 0L }
            ?.let { it.streamId.toString() to null }
        ContentType.SERIES_EPISODE -> {
            val episode = episodeDao.getById(history.contentId)?.takeIf { it.providerId == history.providerId }
            val series = episode?.let { seriesDao.getById(it.seriesId) }
            if (episode != null && series != null && episode.episodeId > 0L && series.seriesId > 0L) {
                episode.episodeId.toString() to series.seriesId.toString()
            } else {
                null
            }
        }
        else -> null
    }

    private suspend fun providerKey(providerId: Long): String? =
        providerRepository.getProvider(providerId)?.let { providerKeyOf(it.serverUrl, it.username) }

    private fun providerKeyOf(serverUrl: String, username: String): String =
        sha256(serverUrl.trim().trimEnd('/').lowercase(Locale.ROOT) + "|" + username.trim().lowercase(Locale.ROOT)).take(24)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun string(value: String) = JSONObject().put("stringValue", value)
    private fun integer(value: Long) = JSONObject().put("integerValue", value.toString())
    private fun JSONObject.str(name: String) = optJSONObject(name)?.optString("stringValue").orEmpty()
    private fun JSONObject.long(name: String) = optJSONObject(name)?.optString("integerValue")?.toLongOrNull() ?: 0L

    private companion object {
        const val TAG = "CloudSync"
        const val PREFS_NAME = "cloud_sync"
        const val KEY_LAST_PUSHED_AT = "last_pushed_at"
        const val KEY_LAST_PULLED_AT = "last_pulled_at"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val PUSH_SCAN_LIMIT = 200
        const val PULL_OVERLAP_MS = 3L * 24 * 60 * 60 * 1000
    }
}
