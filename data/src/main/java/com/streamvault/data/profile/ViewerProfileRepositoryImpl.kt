package com.streamvault.data.profile

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamvault.data.local.StreamVaultDatabase
import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.ViewerProfile
import com.streamvault.domain.repository.ViewerProfileErrors
import com.streamvault.domain.repository.ViewerProfileRepository
import com.streamvault.domain.repository.ViewerProfileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profiles under a provider, done by swapping: the live tables only ever hold the active profile's
 * rows, and the other profiles' rows wait in `profile_parked_<table>` copies. Switching moves the
 * current profile's rows out and the chosen profile's rows in, in one transaction, so every
 * existing screen and query keeps working unchanged.
 *
 * The extra tables are plain SQL created on first use (not Room entities), and their columns are
 * matched to the live tables each time, so later schema changes don't break them. Room's ids are
 * AUTOINCREMENT, so parked rows keep their ids and favorites keep pointing at their groups.
 * Transactions go through Room's runInTransaction so open lists refresh afterwards.
 */
@Singleton
class ViewerProfileRepositoryImpl @Inject constructor(
    private val database: StreamVaultDatabase,
    private val providerRepository: ProviderRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val movieDao: MovieDao,
    private val episodeDao: EpisodeDao
) : ViewerProfileRepository {
    private val mutex = Mutex()
    private val version = MutableStateFlow(0)
    @Volatile private var tablesReady = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<ViewerProfileState> =
        combine(providerRepository.getActiveProvider().map { it?.id }.distinctUntilChanged(), version) { providerId, _ ->
            if (providerId == null) {
                ViewerProfileState()
            } else {
                ViewerProfileState(providerId = providerId, profiles = profilesOf(providerId))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun addProfile(providerId: Long, name: String, primaryName: String): Result<ViewerProfile> =
        write {
            val clean = name.trim()
            if (clean.isEmpty()) return@write Result.error(ViewerProfileErrors.NAME_EMPTY)
            if (providerRepository.getProvider(providerId) == null) return@write Result.error(ViewerProfileErrors.NO_PROVIDER)
            val existing = profilesOf(providerId)
            if (existing.any { it.name.equals(clean, ignoreCase = true) } ||
                (existing.isEmpty() && primaryName.trim().equals(clean, ignoreCase = true))
            ) {
                return@write Result.error(ViewerProfileErrors.NAME_TAKEN)
            }
            val db = db()
            val now = System.currentTimeMillis()
            if (existing.isEmpty()) {
                db.execSQL(
                    "INSERT INTO $PROFILES (provider_id, name, color_index, is_primary, is_active, position, created_at) VALUES (?, ?, 0, 1, 1, 0, ?)",
                    arrayOf<Any>(providerId, primaryName.trim().ifEmpty { "1" }, now)
                )
            }
            val position = maxOf(existing.size, 1)
            db.execSQL(
                "INSERT INTO $PROFILES (provider_id, name, color_index, is_primary, is_active, position, created_at) VALUES (?, ?, ?, 0, 0, ?, ?)",
                arrayOf<Any>(providerId, clean, position % COLOR_COUNT, position, now)
            )
            val created = profilesOf(providerId).first { it.name == clean }
            Result.success(created)
        }

    override suspend fun renameProfile(profileId: Long, name: String): Result<Unit> = write {
        val clean = name.trim()
        if (clean.isEmpty()) return@write Result.error(ViewerProfileErrors.NAME_EMPTY)
        val profile = profile(profileId) ?: return@write Result.success(Unit)
        if (profilesOf(profile.providerId).any { it.id != profileId && it.name.equals(clean, ignoreCase = true) }) {
            return@write Result.error(ViewerProfileErrors.NAME_TAKEN)
        }
        db().execSQL("UPDATE $PROFILES SET name = ? WHERE id = ?", arrayOf<Any>(clean, profileId))
        Result.success(Unit)
    }

    override suspend fun deleteProfile(profileId: Long): Result<Unit> = write {
        val profile = profile(profileId) ?: return@write Result.success(Unit)
        if (profile.isPrimary) return@write Result.success(Unit)
        if (profile.isActive) {
            val primary = profilesOf(profile.providerId).first { it.isPrimary }
            swap(profile.providerId, from = profile.id, to = primary.id)
        }
        database.runInTransaction {
            val db = db()
            PROFILE_TABLES.forEach { table ->
                db.execSQL("DELETE FROM `${parked(table)}` WHERE $PARKED_PROFILE = ?", arrayOf<Any>(profileId))
            }
            db.execSQL("DELETE FROM $PROFILES WHERE id = ?", arrayOf<Any>(profileId))
            // With only the primary left, go back to having no profiles at all.
            val left = profilesOf(profile.providerId)
            if (left.size == 1 && left.single().isPrimary) {
                db.execSQL("DELETE FROM $PROFILES WHERE provider_id = ?", arrayOf<Any>(profile.providerId))
            }
        }
        Result.success(Unit)
    }

    override suspend fun switchTo(profileId: Long): Result<Unit> = write {
        val target = profile(profileId) ?: return@write Result.success(Unit)
        if (target.isActive) return@write Result.success(Unit)
        val current = profilesOf(target.providerId).firstOrNull { it.isActive }
            ?: profilesOf(target.providerId).first { it.isPrimary }
        swap(target.providerId, from = current.id, to = target.id)
        Result.success(Unit)
    }

    override suspend fun activeProfileKey(providerId: Long): String = withContext(Dispatchers.IO) {
        ensureTables()
        profilesOf(providerId).firstOrNull { it.isActive }
            ?.takeUnless { it.isPrimary }
            ?.name?.trim()?.lowercase(Locale.ROOT)
            .orEmpty()
    }

    /** Runs a change under the lock, on IO, then tells [state] to reload. */
    private suspend fun <T> write(block: suspend () -> Result<T>): Result<T> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                ensureTables()
                block()
            } catch (error: Exception) {
                Log.e(TAG, "Profile change failed", error)
                Result.error(error.message ?: error.javaClass.simpleName, error)
            } finally {
                version.value++
            }
        }
    }

    private suspend fun swap(providerId: Long, from: Long, to: Long) {
        // Progress still held in memory belongs to the profile being left.
        playbackHistoryRepository.flushPendingProgress()
        database.runInTransaction {
            val db = db()
            PROFILE_TABLES.forEach { table -> syncParkedColumns(db, table) }
            // Park everything first, then clear, so cascades between tables can't lose rows.
            PROFILE_TABLES.forEach { table ->
                val columns = sharedColumns(db, table)
                db.execSQL(
                    "INSERT INTO `${parked(table)}` ($PARKED_PROFILE, $columns) SELECT ?, $columns FROM `$table` WHERE provider_id = ?",
                    arrayOf<Any>(from, providerId)
                )
            }
            PROFILE_TABLES.forEach { table ->
                db.execSQL("DELETE FROM `$table` WHERE provider_id = ?", arrayOf<Any>(providerId))
            }
            // Groups go back before favorites, which point at them.
            PROFILE_TABLES.forEach { table ->
                val columns = sharedColumns(db, table)
                db.execSQL(
                    "INSERT INTO `$table` ($columns) SELECT $columns FROM `${parked(table)}` WHERE $PARKED_PROFILE = ? AND provider_id = ?",
                    arrayOf<Any>(to, providerId)
                )
                db.execSQL(
                    "DELETE FROM `${parked(table)}` WHERE $PARKED_PROFILE = ? AND provider_id = ?",
                    arrayOf<Any>(to, providerId)
                )
            }
            db.execSQL("UPDATE $PROFILES SET is_active = CASE WHEN id = ? THEN 1 ELSE 0 END WHERE provider_id = ?", arrayOf<Any>(to, providerId))
        }
        // Movies and episodes keep a copy of their progress; rebuild it from the new history.
        movieDao.syncWatchProgressFromHistoryByProvider(providerId)
        episodeDao.syncWatchProgressFromHistoryByProvider(providerId)
    }

    private fun db(): SupportSQLiteDatabase = database.openHelper.writableDatabase

    private fun ensureTables() {
        if (tablesReady) return
        val db = db()
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $PROFILES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                color_index INTEGER NOT NULL,
                is_primary INTEGER NOT NULL,
                is_active INTEGER NOT NULL,
                position INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        PROFILE_TABLES.forEach { table -> syncParkedColumns(db, table) }
        // Forget profiles of providers that were removed.
        db.execSQL("DELETE FROM $PROFILES WHERE provider_id NOT IN (SELECT id FROM providers)")
        PROFILE_TABLES.forEach { table ->
            db.execSQL("DELETE FROM `${parked(table)}` WHERE provider_id NOT IN (SELECT id FROM providers)")
        }
        tablesReady = true
    }

    /** Creates the parked copy of [table] or adds columns the live table gained since. */
    private fun syncParkedColumns(db: SupportSQLiteDatabase, table: String) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `${parked(table)}` AS SELECT 0 AS $PARKED_PROFILE, * FROM `$table` WHERE 0")
        val have = columnsOf(db, parked(table)).toSet()
        columnsOf(db, table).filterNot { it in have }.forEach { column ->
            db.execSQL("ALTER TABLE `${parked(table)}` ADD COLUMN `$column`")
        }
    }

    private fun sharedColumns(db: SupportSQLiteDatabase, table: String): String {
        val parkedColumns = columnsOf(db, parked(table)).toSet()
        return columnsOf(db, table).filter { it in parkedColumns }.joinToString(", ") { "`$it`" }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }

    private fun profilesOf(providerId: Long): List<ViewerProfile> {
        ensureTables()
        return db().query(
            "SELECT id, provider_id, name, color_index, is_primary, is_active FROM $PROFILES WHERE provider_id = ? ORDER BY position, id",
            arrayOf<Any>(providerId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ViewerProfile(
                            id = cursor.getLong(0),
                            providerId = cursor.getLong(1),
                            name = cursor.getString(2),
                            colorIndex = cursor.getInt(3),
                            isPrimary = cursor.getInt(4) == 1,
                            isActive = cursor.getInt(5) == 1
                        )
                    )
                }
            }
        }
    }

    private fun profile(profileId: Long): ViewerProfile? =
        db().query("SELECT provider_id FROM $PROFILES WHERE id = ?", arrayOf<Any>(profileId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }?.let { providerId -> profilesOf(providerId).firstOrNull { it.id == profileId } }

    private fun parked(table: String) = "profile_parked_$table"

    private companion object {
        const val TAG = "ViewerProfiles"
        const val PROFILES = "viewer_profiles"
        const val PARKED_PROFILE = "parked_profile_id"
        const val COLOR_COUNT = 6

        /** Per-profile tables, in the order they are restored (groups before favorites). */
        val PROFILE_TABLES = listOf("virtual_groups", "favorites", "playback_history", "tracked_series", "search_history")
    }
}
